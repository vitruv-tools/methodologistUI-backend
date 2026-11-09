package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.PUBLISH_IN_PROGRESS_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_DOES_NOT_HAVE_ACCESS;
import static tools.vitruv.methodologist.messages.Error.VSUM_VERSION_NOT_FOUND_ERROR;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.vitruv.methodologist.apihandler.SetupServiceApiHandler;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.PublishInProgressException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumBuildResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumVersionResponse;
import tools.vitruv.methodologist.vsum.mapper.VsumBuildMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumHistoryMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumVersionMapper;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumBuild;
import tools.vitruv.methodologist.vsum.model.VsumBuildStatus;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.VsumVersion;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumVersionRepository;

/**
 * Publishes stable versions of VSUMs.
 *
 * <p>Publishing copies the working copy's build inputs into a pending {@link VsumVersion} and
 * builds that snapshot with the asynchronous build of {@link VsumBuildService}, which also decides
 * the outcome: a successful build gives the version its number, a failed one deletes it again.
 *
 * <p>The snapshot is read in a {@code REPEATABLE READ} transaction so that all of its parts come
 * from the same state, even if another member saves in the meantime. Only one publish per VSUM may
 * run at a time; the database enforces this with a unique index over the pending versions.
 *
 * <p>A pending publish only gives way to a new one if its build can no longer produce a version:
 * the build has finished, or it has been running longer than the setup-service response timeout
 * plus {@link #ABANDON_MARGIN}. A queued build always gets its turn; builds lost in a restart are
 * failed on startup. The decision, the abandonment and the new snapshot share one transaction, so a
 * new publish that fails leaves the earlier one untouched.
 */
@Service
public class VsumVersionService {

  static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
  static final String STUCK_ERROR =
      "The build ran longer than the setup-service timeout and was abandoned for a new publish.";

  /**
   * Added to the setup-service response timeout before a running build is abandoned. A worker can
   * only report success before that timeout expires, so it never races with the abandonment.
   */
  static final Duration ABANDON_MARGIN = Duration.ofMinutes(1);

  private final VsumUserRepository vsumUserRepository;
  private final VsumVersionRepository vsumVersionRepository;
  private final FileStorageRepository fileStorageRepository;
  private final VsumBuildInputCollector inputCollector;
  private final VsumBuildService vsumBuildService;
  private final VsumHistoryMapper vsumHistoryMapper;
  private final VsumBuildMapper vsumBuildMapper;
  private final VsumVersionMapper vsumVersionMapper;
  private final Duration abandonRunningAfter;
  private final TransactionTemplate snapshotTransaction;

  /**
   * Creates the version service.
   *
   * @param vsumUserRepository resolves the caller's VSUM membership
   * @param vsumVersionRepository stores the versions
   * @param fileStorageRepository stores the copied input files
   * @param inputCollector collects the working copy's build inputs
   * @param vsumBuildService queues and runs the build of a snapshot
   * @param vsumHistoryMapper describes the working copy for the version's manifest
   * @param vsumBuildMapper converts the snapshot's build into a response
   * @param vsumVersionMapper converts versions into responses
   * @param setupServiceTimeoutSeconds the setup-service response timeout; a build running longer
   *     than that plus {@link #ABANDON_MARGIN} can no longer succeed
   * @param transactionManager used for the explicit transactions of a publish
   */
  public VsumVersionService(
      VsumUserRepository vsumUserRepository,
      VsumVersionRepository vsumVersionRepository,
      FileStorageRepository fileStorageRepository,
      VsumBuildInputCollector inputCollector,
      VsumBuildService vsumBuildService,
      VsumHistoryMapper vsumHistoryMapper,
      VsumBuildMapper vsumBuildMapper,
      VsumVersionMapper vsumVersionMapper,
      @Value(
              "${third_api.setup_service.timeout-seconds:"
                  + SetupServiceApiHandler.DEFAULT_RESPONSE_TIMEOUT_IN_SECONDS
                  + "}")
          long setupServiceTimeoutSeconds,
      PlatformTransactionManager transactionManager) {
    this.vsumUserRepository = vsumUserRepository;
    this.vsumVersionRepository = vsumVersionRepository;
    this.fileStorageRepository = fileStorageRepository;
    this.inputCollector = inputCollector;
    this.vsumBuildService = vsumBuildService;
    this.vsumHistoryMapper = vsumHistoryMapper;
    this.vsumBuildMapper = vsumBuildMapper;
    this.vsumVersionMapper = vsumVersionMapper;
    this.abandonRunningAfter = Duration.ofSeconds(setupServiceTimeoutSeconds).plus(ABANDON_MARGIN);
    this.snapshotTransaction = new TransactionTemplate(transactionManager);
    this.snapshotTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  /**
   * Publishes the VSUM's working copy: takes a snapshot of its build inputs and starts building it.
   * The snapshot becomes the next stable version once the build succeeds.
   *
   * @param callerEmail the requesting user, who must be an owner or member of the VSUM
   * @param vsumId the VSUM to publish
   * @return the build of the snapshot, to be followed through the build endpoints
   * @throws AccessDeniedException if the caller is a viewer or not part of the VSUM
   * @throws PublishInProgressException if an earlier publish of the VSUM is still building
   * @throws NotFoundException if the VSUM lacks the files needed for a build
   */
  public VsumBuildResponse publish(String callerEmail, Long vsumId) {
    VsumBuildResponse build;
    try {
      build =
          snapshotTransaction.execute(
              status -> {
                VsumUser publisher = findPublisher(callerEmail, vsumId);
                // Collected first: a publish that fails here must not cancel a running one.
                VsumBuildInputs inputs = inputCollector.collect(publisher.getVsum());
                releasePendingPublish(publisher.getVsum());
                return snapshot(publisher, inputs);
              });
    } catch (DataIntegrityViolationException e) {
      // The unique index over pending versions rejected the insert: a concurrent publish won.
      throw new PublishInProgressException(PUBLISH_IN_PROGRESS_ERROR);
    }
    Objects.requireNonNull(build, "transaction returned no result");
    vsumBuildService.submit(build.getId());
    return build;
  }

  /**
   * Returns the newest stable version of the VSUM.
   *
   * @param callerEmail the requesting user, who must be part of the VSUM
   * @param vsumId the VSUM
   * @return the version with the highest number
   * @throws AccessDeniedException if the caller is not part of the VSUM
   * @throws NotFoundException if nothing has been published yet
   */
  @Transactional(readOnly = true)
  public VsumVersionResponse findLatest(String callerEmail, Long vsumId) {
    Vsum vsum = findMember(callerEmail, vsumId).getVsum();
    return vsumVersionRepository
        .findFirstByVsumAndNumberIsNotNullOrderByNumberDesc(vsum)
        .map(vsumVersionMapper::toResponse)
        .orElseThrow(() -> new NotFoundException(VSUM_VERSION_NOT_FOUND_ERROR));
  }

  /**
   * Rejects the publish while another one of the VSUM can still produce a version. Otherwise the
   * pending version is abandoned: its build is marked failed and the version with its copies is
   * deleted.
   */
  private void releasePendingPublish(Vsum vsum) {
    vsumVersionRepository
        .findFirstByVsumAndNumberIsNull(vsum)
        .ifPresent(
            pending -> {
              if (!canBeAbandoned(pending.getBuild())) {
                throw new PublishInProgressException(PUBLISH_IN_PROGRESS_ERROR);
              }
              vsumBuildService.fail(pending.getBuild().getId(), STUCK_ERROR);
              // Delete the old pending version now: Hibernate inserts before it deletes, and the
              // unique index admits only one pending version per VSUM.
              vsumVersionRepository.flush();
            });
  }

  /** {@code true} if the build has finished or has been running too long to still succeed. */
  private boolean canBeAbandoned(VsumBuild build) {
    if (build.getStatus().isTerminal()) {
      return true;
    }
    Instant deadline = Instant.now().minus(abandonRunningAfter);
    return build.getStatus() == VsumBuildStatus.RUNNING
        && build.getStartedAt() != null
        && build.getStartedAt().isBefore(deadline);
  }

  /** Copies the working copy's build inputs into a pending version and queues its build. */
  private VsumBuildResponse snapshot(VsumUser publisher, VsumBuildInputs inputs) {
    Vsum vsum = publisher.getVsum();
    User user = publisher.getUser();

    VsumBuild build = vsumBuildService.queue(vsum, user, inputs.fingerprint());
    vsumVersionRepository.save(
        VsumVersion.builder()
            .vsum(vsum)
            .createdBy(user)
            .build(build)
            .manifest(vsumHistoryMapper.toVsumRepresentation(vsum))
            .ecores(copies(inputs.ecores(), user))
            .reactions(copies(inputs.reactions(), user))
            .constraints(copies(inputs.constraints(), user))
            .views(copies(inputs.views(), user))
            .build());
    return vsumBuildMapper.toResponse(build);
  }

  private List<FileStorage> copies(List<FileStorage> files, User owner) {
    return files.stream().map(file -> fileStorageRepository.save(copyOf(file, owner))).toList();
  }

  /**
   * Copies a build input into a new file of the version. Name and content stay the same, so the
   * snapshot has the same fingerprint as the working copy it was taken from. Generated inputs, such
   * as composite reactions, have no owner, hash or size yet; the copy gets them.
   */
  static FileStorage copyOf(FileStorage file, User owner) {
    byte[] data = file.getData() == null ? new byte[0] : file.getData().clone();
    return FileStorage.builder()
        .filename(file.getFilename())
        .type(FileEnumType.VSUM_VERSION_FILE)
        .contentType(Objects.requireNonNullElse(file.getContentType(), DEFAULT_CONTENT_TYPE))
        .sizeBytes(data.length)
        .sha256(VsumBuildInputs.sha256Hex(data))
        .data(data)
        .user(owner)
        .build();
  }

  private VsumUser findPublisher(String callerEmail, Long vsumId) {
    VsumUser member = findMember(callerEmail, vsumId);
    if (member.getRole() == VsumRole.VIEWER) {
      throw new AccessDeniedException(USER_DOES_NOT_HAVE_ACCESS);
    }
    return member;
  }

  private VsumUser findMember(String callerEmail, Long vsumId) {
    return vsumUserRepository
        .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
            vsumId, callerEmail)
        .orElseThrow(() -> new AccessDeniedException(USER_DOES_NOT_HAVE_ACCESS));
  }
}
