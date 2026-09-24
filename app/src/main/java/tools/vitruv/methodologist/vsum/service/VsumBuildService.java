package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.USER_DOSE_NOT_HAVE_ACCESS;
import static tools.vitruv.methodologist.messages.Error.VSUM_BUILD_ARTIFACT_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.VSUM_BUILD_ID_NOT_FOUND_ERROR;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.vitruv.methodologist.apihandler.SetupServiceApiHandler;
import tools.vitruv.methodologist.config.VsumBuildConfig;
import tools.vitruv.methodologist.config.VsumBuildProperties;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.SetupServiceException;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumBuildResponse;
import tools.vitruv.methodologist.vsum.mapper.VsumBuildMapper;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumBuild;
import tools.vitruv.methodologist.vsum.model.VsumBuildStatus;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.VsumBuildRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;

/**
 * Builds VSUMs through the setup-service as background jobs and keeps the results.
 *
 * <p>A build request first fingerprints the VSUM's current input files. If a successful build of
 * exactly these inputs still has its artifact, that build is returned and nothing is rebuilt; if
 * the same inputs are already queued or running, that build is returned as well. Otherwise a new
 * {@link VsumBuild} is stored as {@link VsumBuildStatus#QUEUED} and handed to the build executor.
 * Builds of one VSUM never run in parallel.
 *
 * <p>All database work goes through a {@link TransactionTemplate} rather than
 * {@code @Transactional}: the worker runs on the executor thread where no request-scoped session
 * exists, and the long setup-service call must not hold a transaction open.
 */
@Service
@Slf4j
public class VsumBuildService {

  static final String ARTIFACT_FILENAME = "vsum.jar";
  static final String ARTIFACT_CONTENT_TYPE = "application/java-archive";
  static final String QUEUE_FULL_ERROR = "Too many builds are waiting, please try again later.";
  static final String RESTART_ERROR = "The build was interrupted by a restart of the backend.";
  static final String TIMEOUT_ERROR = "The build did not finish within %d seconds.";

  private static final Set<VsumBuildStatus> IN_FLIGHT =
      EnumSet.of(VsumBuildStatus.QUEUED, VsumBuildStatus.RUNNING);

  private final VsumUserRepository vsumUserRepository;
  private final VsumBuildRepository vsumBuildRepository;
  private final FileStorageRepository fileStorageRepository;
  private final VsumBuildInputCollector inputCollector;
  private final SetupServiceApiHandler setupServiceApiHandler;
  private final VsumBuildMapper vsumBuildMapper;
  private final VsumBuildProperties properties;
  private final Executor executor;
  private final TransactionTemplate transactionTemplate;

  /** One lock per VSUM so that two builds of the same VSUM never run at the same time. */
  private final ConcurrentMap<Long, ReentrantLock> vsumLocks = new ConcurrentHashMap<>();

  /**
   * Creates the build service.
   *
   * @param vsumUserRepository resolves the caller's VSUM membership
   * @param vsumBuildRepository stores the builds
   * @param fileStorageRepository stores the built JARs
   * @param inputCollector collects and fingerprints the files a VSUM is built from
   * @param setupServiceApiHandler runs the actual build
   * @param vsumBuildMapper converts builds into responses
   * @param properties pool, queue, retention and wait settings
   * @param executor runs builds in the background
   * @param transactionManager used for the explicit transactions around each build step
   */
  public VsumBuildService(
      VsumUserRepository vsumUserRepository,
      VsumBuildRepository vsumBuildRepository,
      FileStorageRepository fileStorageRepository,
      VsumBuildInputCollector inputCollector,
      SetupServiceApiHandler setupServiceApiHandler,
      VsumBuildMapper vsumBuildMapper,
      VsumBuildProperties properties,
      @Qualifier(VsumBuildConfig.VSUM_BUILD_EXECUTOR) Executor executor,
      PlatformTransactionManager transactionManager) {
    this.vsumUserRepository = vsumUserRepository;
    this.vsumBuildRepository = vsumBuildRepository;
    this.fileStorageRepository = fileStorageRepository;
    this.inputCollector = inputCollector;
    this.setupServiceApiHandler = setupServiceApiHandler;
    this.vsumBuildMapper = vsumBuildMapper;
    this.properties = properties;
    this.executor = executor;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  /**
   * Outcome of a build request.
   *
   * @param build the build to follow, new or reused
   * @param started {@code true} if this request created the build, {@code false} if an existing
   *     build of the same inputs was returned
   */
  public record BuildRequest(VsumBuildResponse build, boolean started) {}

  /**
   * Requests a build of the VSUM's current inputs.
   *
   * @param callerEmail the requesting user, who must be a member of the VSUM
   * @param vsumId the VSUM to build
   * @param force build even if an up-to-date artifact exists
   * @return the build to follow and whether it was newly started
   * @throws AccessDeniedException if the caller is not a member of the VSUM
   * @throws NotFoundException if the VSUM lacks the files needed for a build
   * @throws VsumBuildingException if the build queue is full
   */
  public BuildRequest requestBuild(String callerEmail, Long vsumId, boolean force) {
    BuildRequest request =
        transactionTemplate.execute(
            status -> {
              VsumUser member = findMember(callerEmail, vsumId);
              Vsum vsum = member.getVsum();
              String fingerprint = inputCollector.collect(vsum).fingerprint();

              if (!force) {
                Optional<VsumBuild> reusable = findReusable(vsum, fingerprint);
                if (reusable.isPresent()) {
                  return new BuildRequest(vsumBuildMapper.toResponse(reusable.get()), false);
                }
              }
              Optional<VsumBuild> inFlight =
                  vsumBuildRepository.findFirstByVsumAndFingerprintAndStatusInOrderByCreatedAtDesc(
                      vsum, fingerprint, IN_FLIGHT);
              if (inFlight.isPresent()) {
                return new BuildRequest(vsumBuildMapper.toResponse(inFlight.get()), false);
              }

              VsumBuild build =
                  vsumBuildRepository.save(
                      VsumBuild.builder()
                          .vsum(vsum)
                          .requestedBy(member.getUser())
                          .fingerprint(fingerprint)
                          .status(VsumBuildStatus.QUEUED)
                          .forced(force)
                          .build());
              return new BuildRequest(vsumBuildMapper.toResponse(build), true);
            });

    Objects.requireNonNull(request, "transaction returned no result");
    if (request.started()) {
      submit(request.build().getId());
    }
    return request;
  }

  /**
   * Requests a build (reusing an up-to-date one if possible), waits for it to finish and returns
   * the built JAR. This is what the synchronous download endpoints use.
   *
   * @throws SetupServiceException if the build failed
   * @throws VsumBuildingException if the build did not finish within the configured wait timeout
   */
  public byte[] buildAndWait(String callerEmail, Long vsumId) {
    BuildRequest request = requestBuild(callerEmail, vsumId, false);
    VsumBuild finished = awaitCompletion(request.build().getId());
    if (finished.getStatus() != VsumBuildStatus.SUCCEEDED) {
      throw new SetupServiceException(
          finished.getErrorMessage() == null ? "The build failed." : finished.getErrorMessage());
    }
    return artifactBytes(finished.getId());
  }

  /** Returns one build of the VSUM. */
  public VsumBuildResponse findBuild(String callerEmail, Long vsumId, Long buildId) {
    return transactionTemplate.execute(
        status -> vsumBuildMapper.toResponse(findBuildEntity(callerEmail, vsumId, buildId)));
  }

  /** Returns the builds of the VSUM, newest first. */
  public List<VsumBuildResponse> findAll(String callerEmail, Long vsumId, Pageable pageable) {
    return transactionTemplate.execute(
        status -> {
          Vsum vsum = findMember(callerEmail, vsumId).getVsum();
          return vsumBuildRepository.findAllByVsumOrderByCreatedAtDesc(vsum, pageable).stream()
              .map(vsumBuildMapper::toResponse)
              .toList();
        });
  }

  /**
   * Returns the JAR of a finished build.
   *
   * @throws NotFoundException if the build does not exist for this VSUM or its artifact is gone
   */
  public byte[] getArtifact(String callerEmail, Long vsumId, Long buildId) {
    return transactionTemplate.execute(
        status -> {
          VsumBuild build = findBuildEntity(callerEmail, vsumId, buildId);
          if (build.getArtifact() == null) {
            throw new NotFoundException(VSUM_BUILD_ARTIFACT_NOT_FOUND_ERROR);
          }
          return build.getArtifact().getData();
        });
  }

  /** Deletes every build of the VSUM together with the artifacts nothing else references. */
  public void deleteByVsum(Vsum vsum) {
    transactionTemplate.executeWithoutResult(
        status -> {
          List<VsumBuild> builds = vsumBuildRepository.findAllByVsum(vsum);
          Set<FileStorage> artifacts = new LinkedHashSet<>();
          for (VsumBuild build : builds) {
            if (build.getArtifact() != null) {
              artifacts.add(build.getArtifact());
            }
          }
          vsumBuildRepository.deleteAll(builds);
          deleteUnreferenced(artifacts);
        });
  }

  /**
   * Builds that were queued or running when the backend last stopped can never finish; mark them
   * failed so that they neither block deduplication nor look alive forever.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void failBuildsInterruptedByRestart() {
    transactionTemplate.executeWithoutResult(
        status -> {
          List<VsumBuild> stale = vsumBuildRepository.findAllByStatusIn(IN_FLIGHT);
          Instant now = Instant.now();
          for (VsumBuild build : stale) {
            build.setStatus(VsumBuildStatus.FAILED);
            build.setErrorMessage(RESTART_ERROR);
            build.setFinishedAt(now);
          }
          vsumBuildRepository.saveAll(stale);
          if (!stale.isEmpty()) {
            log.warn(
                "Marked {} VSUM build(s) interrupted by the last shutdown as failed", stale.size());
          }
        });
  }

  /**
   * Runs one queued build. Executed on the build executor; package-private so tests can drive it
   * synchronously.
   */
  void runBuild(Long buildId) {
    Long vsumId =
        transactionTemplate.execute(
            status ->
                vsumBuildRepository
                    .findById(buildId)
                    .filter(build -> build.getStatus() == VsumBuildStatus.QUEUED)
                    .map(build -> build.getVsum().getId())
                    .orElse(null));
    if (vsumId == null) {
      return;
    }

    ReentrantLock lock = vsumLocks.computeIfAbsent(vsumId, id -> new ReentrantLock());
    lock.lock();
    try {
      VsumBuildInputs inputs = start(buildId);
      if (inputs == null) {
        return;
      }
      byte[] jar =
          setupServiceApiHandler.buildVsumJarOrThrow(
              inputs.ecores(), inputs.genmodels(), inputs.reactions());
      succeed(buildId, jar);
    } catch (RuntimeException e) {
      log.error("VSUM build {} failed: {}", buildId, e.getMessage(), e);
      fail(buildId, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    } finally {
      lock.unlock();
    }
  }

  private void submit(Long buildId) {
    try {
      executor.execute(() -> runBuild(buildId));
    } catch (TaskRejectedException e) {
      fail(buildId, QUEUE_FULL_ERROR);
      throw new VsumBuildingException(QUEUE_FULL_ERROR);
    }
  }

  /**
   * Marks the build running and collects its inputs. Returns {@code null} if there is nothing to
   * do: the build is no longer queued, or a twin with the same inputs succeeded in the meantime, in
   * which case this build simply adopts that artifact.
   */
  private VsumBuildInputs start(Long buildId) {
    return transactionTemplate.execute(
        status -> {
          VsumBuild build = vsumBuildRepository.findById(buildId).orElse(null);
          if (build == null || build.getStatus() != VsumBuildStatus.QUEUED) {
            return null;
          }
          Instant now = Instant.now();
          VsumBuildInputs inputs = inputCollector.collect(build.getVsum());
          String fingerprint = inputs.fingerprint();

          if (!build.isForced()) {
            Optional<VsumBuild> twin = findReusable(build.getVsum(), fingerprint);
            if (twin.isPresent()) {
              build.setArtifact(twin.get().getArtifact());
              build.setFingerprint(fingerprint);
              build.setStatus(VsumBuildStatus.SUCCEEDED);
              build.setStartedAt(now);
              build.setFinishedAt(now);
              vsumBuildRepository.save(build);
              return null;
            }
          }

          // The inputs may have changed since the request; record what is actually built.
          build.setFingerprint(fingerprint);
          build.setStatus(VsumBuildStatus.RUNNING);
          build.setStartedAt(now);
          vsumBuildRepository.save(build);
          // Read the file contents while the transaction is open; the setup-service call runs
          // outside of it.
          loadData(inputs.ecores());
          loadData(inputs.genmodels());
          loadData(inputs.reactions());
          return inputs;
        });
  }

  private void succeed(Long buildId, byte[] jar) {
    transactionTemplate.executeWithoutResult(
        status -> {
          VsumBuild build = vsumBuildRepository.findById(buildId).orElseThrow();
          FileStorage artifact = fileStorageRepository.save(artifact(build.getRequestedBy(), jar));
          build.setArtifact(artifact);
          build.setStatus(VsumBuildStatus.SUCCEEDED);
          build.setFinishedAt(Instant.now());
          vsumBuildRepository.save(build);
          applyRetention(build.getVsum());
        });
  }

  private void fail(Long buildId, String message) {
    transactionTemplate.executeWithoutResult(
        status ->
            vsumBuildRepository
                .findById(buildId)
                .ifPresent(
                    build -> {
                      build.setStatus(VsumBuildStatus.FAILED);
                      build.setErrorMessage(message);
                      build.setFinishedAt(Instant.now());
                      vsumBuildRepository.save(build);
                    }));
  }

  /**
   * Keeps the artifacts of the newest successful builds only ({@code vsum.build.retention-keep}).
   */
  private void applyRetention(Vsum vsum) {
    List<VsumBuild> withArtifact =
        vsumBuildRepository.findAllByVsumAndStatusAndArtifactIsNotNullOrderByFinishedAtDesc(
            vsum, VsumBuildStatus.SUCCEEDED);
    int keep = properties.getRetentionKeep();
    if (withArtifact.size() <= keep) {
      return;
    }
    Set<FileStorage> released = new LinkedHashSet<>();
    for (VsumBuild old : withArtifact.subList(keep, withArtifact.size())) {
      released.add(old.getArtifact());
      old.setArtifact(null);
    }
    vsumBuildRepository.saveAll(withArtifact);
    deleteUnreferenced(released);
  }

  /**
   * Deletes the given artifacts unless another build still points at them (a build that adopted a
   * twin's artifact shares the row).
   */
  private void deleteUnreferenced(Set<FileStorage> artifacts) {
    for (FileStorage artifact : artifacts) {
      if (vsumBuildRepository.countByArtifact(artifact) == 0) {
        // Deleting the row alone would leave the JAR behind as an orphaned large object.
        fileStorageRepository.unlinkData(artifact.getId());
        fileStorageRepository.delete(artifact);
      }
    }
  }

  private VsumBuild awaitCompletion(Long buildId) {
    long timeoutSeconds = properties.getWaitTimeoutSeconds();
    Instant deadline = Instant.now().plus(Duration.ofSeconds(timeoutSeconds));
    while (true) {
      VsumBuild build =
          vsumBuildRepository
              .findById(buildId)
              .orElseThrow(() -> new NotFoundException(VSUM_BUILD_ID_NOT_FOUND_ERROR));
      if (build.getStatus().isTerminal()) {
        return build;
      }
      if (!Instant.now().isBefore(deadline)) {
        throw new VsumBuildingException(String.format(TIMEOUT_ERROR, timeoutSeconds));
      }
      sleep(properties.getPollIntervalMillis());
    }
  }

  private byte[] artifactBytes(Long buildId) {
    return transactionTemplate.execute(
        status ->
            vsumBuildRepository
                .findById(buildId)
                .map(VsumBuild::getArtifact)
                .map(FileStorage::getData)
                .orElseThrow(() -> new NotFoundException(VSUM_BUILD_ARTIFACT_NOT_FOUND_ERROR)));
  }

  private VsumBuild findBuildEntity(String callerEmail, Long vsumId, Long buildId) {
    Vsum vsum = findMember(callerEmail, vsumId).getVsum();
    return vsumBuildRepository
        .findByIdAndVsum(buildId, vsum)
        .orElseThrow(() -> new NotFoundException(VSUM_BUILD_ID_NOT_FOUND_ERROR));
  }

  private VsumUser findMember(String callerEmail, Long vsumId) {
    return vsumUserRepository
        .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
            vsumId, callerEmail)
        .orElseThrow(() -> new AccessDeniedException(USER_DOSE_NOT_HAVE_ACCESS));
  }

  private Optional<VsumBuild> findReusable(Vsum vsum, String fingerprint) {
    return vsumBuildRepository
        .findFirstByVsumAndFingerprintAndStatusAndArtifactIsNotNullOrderByFinishedAtDesc(
            vsum, fingerprint, VsumBuildStatus.SUCCEEDED);
  }

  private static FileStorage artifact(User owner, byte[] jar) {
    FileStorage artifact = new FileStorage();
    artifact.setFilename(ARTIFACT_FILENAME);
    artifact.setType(FileEnumType.VSUM_JAR);
    artifact.setContentType(ARTIFACT_CONTENT_TYPE);
    artifact.setSizeBytes(jar.length);
    artifact.setSha256(VsumBuildInputs.sha256Hex(jar));
    artifact.setData(jar);
    artifact.setUser(owner);
    return artifact;
  }

  private static void loadData(List<FileStorage> files) {
    for (FileStorage file : files) {
      file.getData();
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new VsumBuildingException("Interrupted while waiting for the build.");
    }
  }
}
