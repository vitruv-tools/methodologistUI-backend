package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.USER_DOES_NOT_HAVE_ACCESS;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.vitruv.methodologist.apihandler.SetupServiceApiHandler;
import tools.vitruv.methodologist.apihandler.dto.response.VsumValidationResult;
import tools.vitruv.methodologist.exception.BuildProblem;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumValidationResponse;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;

/**
 * Validates VSUMs through the setup-service in the background and keeps the latest result of each
 * VSUM in memory.
 *
 * <p>A validation is skipped if the fingerprint of the VSUM's inputs equals the one last validated.
 * Validations of one VSUM never run in parallel; a request arriving during a validation causes one
 * more validation of the latest state afterwards. Inputs that cannot be collected, such as a VSUM
 * without metamodels or reactions that cannot be composed, are reported as an invalid result, while
 * a failing setup-service keeps the previous result.
 */
@Service
@Slf4j
public class VsumValidationService {

  static final int VALIDATION_THREADS = 2;
  static final String THREAD_NAME_PREFIX = "vsum-validation-";
  static final String METAMODEL_SOURCE = "METAMODEL";
  static final String REACTIONS_SOURCE = "REACTIONS";

  private final VsumRepository vsumRepository;
  private final VsumUserRepository vsumUserRepository;
  private final VsumBuildInputCollector inputCollector;
  private final SetupServiceApiHandler setupServiceApiHandler;
  private final TransactionTemplate transactionTemplate;
  private final ExecutorService executor;
  private final ConcurrentMap<Long, ValidationState> states = new ConcurrentHashMap<>();

  /**
   * Creates the validation service with its own executor for background validations.
   *
   * @param vsumRepository loads the VSUM to validate
   * @param vsumUserRepository resolves the caller's VSUM membership
   * @param inputCollector collects and fingerprints the files a VSUM is validated with
   * @param setupServiceApiHandler runs the actual validation
   * @param transactionManager used for the read-only transaction collecting the inputs
   */
  public VsumValidationService(
      VsumRepository vsumRepository,
      VsumUserRepository vsumUserRepository,
      VsumBuildInputCollector inputCollector,
      SetupServiceApiHandler setupServiceApiHandler,
      PlatformTransactionManager transactionManager) {
    this.vsumRepository = vsumRepository;
    this.vsumUserRepository = vsumUserRepository;
    this.inputCollector = inputCollector;
    this.setupServiceApiHandler = setupServiceApiHandler;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setReadOnly(true);
    this.executor =
        Executors.newFixedThreadPool(
            VALIDATION_THREADS,
            Thread.ofPlatform().name(THREAD_NAME_PREFIX, 0).daemon(true).factory());
  }

  /**
   * Requests a validation of the VSUM's current inputs without waiting for it. If a validation of
   * the VSUM is already running, the latest state is validated once it has finished.
   *
   * @param vsumId the VSUM to validate
   */
  public void requestValidation(Long vsumId) {
    ValidationState state = states.computeIfAbsent(vsumId, id -> new ValidationState());
    if (!state.tryStart()) {
      return;
    }
    try {
      executor.execute(() -> runValidation(vsumId, state));
    } catch (RejectedExecutionException e) {
      state.stop();
      log.warn("Validation of VSUM {} could not be scheduled: {}", vsumId, e.getMessage());
    }
  }

  /**
   * Returns the latest validation result of the VSUM, checking the caller's access first.
   *
   * @param callerEmail the requesting user, who must be an owner or member of the VSUM
   * @param vsumId the VSUM
   * @return the latest result, or empty if the VSUM has not been validated yet
   * @throws AccessDeniedException if the caller is not a member of the VSUM or only a viewer
   */
  public Optional<VsumValidationResponse> findLatestResult(String callerEmail, Long vsumId) {
    VsumUser vsumUser =
        vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                vsumId, callerEmail)
            .orElseThrow(() -> new AccessDeniedException(USER_DOES_NOT_HAVE_ACCESS));
    if (vsumUser.getRole() == VsumRole.VIEWER) {
      throw new AccessDeniedException(USER_DOES_NOT_HAVE_ACCESS);
    }
    return findLatestResult(vsumId)
        .map(
            latest ->
                VsumValidationResponse.builder()
                    .valid(latest.valid())
                    .problems(latest.problems())
                    .validatedAt(latest.validatedAt())
                    .build());
  }

  /**
   * Returns the latest validation result of the VSUM without checking access.
   *
   * @param vsumId the VSUM
   * @return the latest result, or empty if the VSUM has not been validated yet
   */
  public Optional<LatestValidation> findLatestResult(Long vsumId) {
    ValidationState state = states.get(vsumId);
    return state == null ? Optional.empty() : state.result();
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }

  void runValidation(Long vsumId, ValidationState state) {
    do {
      validateLatest(vsumId, state);
    } while (state.continueWithPendingChanges());
  }

  private void validateLatest(Long vsumId, ValidationState state) {
    try {
      Optional<ValidationSnapshot> snapshot = loadSnapshotOrStoreInvalidInputs(vsumId, state);
      if (snapshot.isEmpty() || state.isValidated(snapshot.get().fingerprint())) {
        return;
      }
      VsumBuildInputs inputs = snapshot.get().inputs();
      VsumValidationResult result =
          setupServiceApiHandler.validateVsum(
              inputs.ecores(), inputs.reactions(), inputs.constraints(), inputs.views());
      state.store(
          snapshot.get().fingerprint(),
          new LatestValidation(result.valid(), result.problems(), Instant.now()));
    } catch (RuntimeException e) {
      log.warn("Validation of VSUM {} failed: {}", vsumId, e.getMessage());
    }
  }

  private Optional<ValidationSnapshot> loadSnapshotOrStoreInvalidInputs(
      Long vsumId, ValidationState state) {
    try {
      return loadSnapshot(vsumId);
    } catch (NotFoundException e) {
      state.store(null, invalidInputs(METAMODEL_SOURCE, e));
    } catch (VsumBuildingException e) {
      state.store(null, invalidInputs(REACTIONS_SOURCE, e));
    }
    return Optional.empty();
  }

  private static LatestValidation invalidInputs(String source, RuntimeException e) {
    return new LatestValidation(
        false,
        List.of(new BuildProblem(source, null, null, null, null, e.getMessage(), null, null, null)),
        Instant.now());
  }

  private Optional<ValidationSnapshot> loadSnapshot(Long vsumId) {
    return Optional.ofNullable(
        transactionTemplate.execute(
            status ->
                vsumRepository
                    .findByIdAndRemovedAtIsNull(vsumId)
                    .map(this::snapshotOf)
                    .orElse(null)));
  }

  private ValidationSnapshot snapshotOf(Vsum vsum) {
    VsumBuildInputs inputs = inputCollector.collect(vsum);
    loadData(inputs);
    return new ValidationSnapshot(inputs, inputs.fingerprint());
  }

  private static void loadData(VsumBuildInputs inputs) {
    for (List<FileStorage> files :
        List.of(inputs.ecores(), inputs.reactions(), inputs.constraints(), inputs.views())) {
      for (FileStorage file : files) {
        file.getData();
      }
    }
  }

  /**
   * The latest validation result of a VSUM.
   *
   * @param valid whether the VSUM is valid
   * @param problems the problems found, empty if the VSUM is valid
   * @param validatedAt when the validation finished
   */
  public record LatestValidation(boolean valid, List<BuildProblem> problems, Instant validatedAt) {}

  private record ValidationSnapshot(VsumBuildInputs inputs, String fingerprint) {}

  static final class ValidationState {

    private boolean running;
    private boolean pendingChanges;
    private String validatedFingerprint;
    private LatestValidation result;

    synchronized boolean tryStart() {
      if (running) {
        pendingChanges = true;
        return false;
      }
      running = true;
      return true;
    }

    synchronized void stop() {
      running = false;
      pendingChanges = false;
    }

    synchronized boolean continueWithPendingChanges() {
      if (pendingChanges) {
        pendingChanges = false;
        return true;
      }
      running = false;
      return false;
    }

    synchronized boolean isValidated(String fingerprint) {
      return fingerprint.equals(validatedFingerprint);
    }

    synchronized void store(String fingerprint, LatestValidation validationResult) {
      validatedFingerprint = fingerprint;
      result = validationResult;
    }

    synchronized Optional<LatestValidation> result() {
      return Optional.ofNullable(result);
    }
  }
}
