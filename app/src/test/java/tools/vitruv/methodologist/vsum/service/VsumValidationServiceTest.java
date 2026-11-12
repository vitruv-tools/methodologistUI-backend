package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import tools.vitruv.methodologist.apihandler.SetupServiceApiHandler;
import tools.vitruv.methodologist.apihandler.dto.response.VsumValidationResult;
import tools.vitruv.methodologist.exception.BuildProblem;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.SetupServiceException;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumValidationResponse;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;

@ExtendWith(MockitoExtension.class)
class VsumValidationServiceTest {

  private static final Long VSUM_ID = 7L;
  private static final String EMAIL = "member@example.com";
  private static final long WAIT_MILLIS = 5_000;
  private static final BuildProblem PROBLEM =
      new BuildProblem("REACTIONS", "r.reactions", 21L, 3, 1, "broken", null, null, null);

  @Mock private VsumRepository vsumRepository;
  @Mock private VsumUserRepository vsumUserRepository;
  @Mock private VsumBuildInputCollector inputCollector;
  @Mock private SetupServiceApiHandler setupServiceApiHandler;
  @Mock private PlatformTransactionManager transactionManager;

  private final Vsum vsum = Vsum.builder().id(VSUM_ID).name("vsum").build();
  private VsumValidationService service;

  @BeforeEach
  void setUp() {
    service =
        new VsumValidationService(
            vsumRepository,
            vsumUserRepository,
            inputCollector,
            setupServiceApiHandler,
            transactionManager);
    lenient()
        .when(vsumRepository.findByIdAndRemovedAtIsNull(VSUM_ID))
        .thenReturn(Optional.of(vsum));
  }

  @AfterEach
  void tearDown() {
    service.shutdown();
  }

  @Test
  void runValidation_storesTheResult_withValidatedAt() {
    givenInputs(inputs("a"));
    when(setupServiceApiHandler.validateVsum(anyList(), anyList(), anyList(), anyList()))
        .thenReturn(new VsumValidationResult(false, List.of(PROBLEM)));
    VsumValidationService.ValidationState state = new VsumValidationService.ValidationState();
    Instant before = Instant.now();

    service.runValidation(VSUM_ID, state);

    VsumValidationService.LatestValidation latest = state.result().orElseThrow();
    assertThat(latest.validatedAt()).isBetween(before, Instant.now());
    assertThat(latest.valid()).isFalse();
    assertThat(latest.problems()).containsExactly(PROBLEM);
  }

  @Test
  void runValidation_skipsTheSetupService_whenTheInputsAreUnchanged() {
    givenInputs(inputs("a"), inputs("a"));
    when(setupServiceApiHandler.validateVsum(anyList(), anyList(), anyList(), anyList()))
        .thenReturn(new VsumValidationResult(true, List.of()));
    VsumValidationService.ValidationState state = new VsumValidationService.ValidationState();

    service.runValidation(VSUM_ID, state);
    VsumValidationService.LatestValidation first = state.result().orElseThrow();
    service.runValidation(VSUM_ID, state);

    verify(setupServiceApiHandler, times(1))
        .validateVsum(anyList(), anyList(), anyList(), anyList());
    assertThat(state.result()).containsSame(first);
  }

  @Test
  void runValidation_validatesAgain_whenTheInputsChanged() {
    givenInputs(inputs("a"), inputs("b"));
    when(setupServiceApiHandler.validateVsum(anyList(), anyList(), anyList(), anyList()))
        .thenReturn(new VsumValidationResult(true, List.of()))
        .thenReturn(new VsumValidationResult(false, List.of(PROBLEM)));
    VsumValidationService.ValidationState state = new VsumValidationService.ValidationState();

    service.runValidation(VSUM_ID, state);
    service.runValidation(VSUM_ID, state);

    verify(setupServiceApiHandler, times(2))
        .validateVsum(anyList(), anyList(), anyList(), anyList());
    assertThat(state.result().orElseThrow().valid()).isFalse();
  }

  @Test
  void runValidation_keepsThePreviousResult_whenTheSetupServiceFails() {
    givenInputs(inputs("a"), inputs("b"), inputs("b"));
    when(setupServiceApiHandler.validateVsum(anyList(), anyList(), anyList(), anyList()))
        .thenReturn(new VsumValidationResult(true, List.of()))
        .thenThrow(new SetupServiceException("setup-service down"))
        .thenReturn(new VsumValidationResult(false, List.of(PROBLEM)));
    VsumValidationService.ValidationState state = new VsumValidationService.ValidationState();

    service.runValidation(VSUM_ID, state);
    VsumValidationService.LatestValidation previous = state.result().orElseThrow();
    service.runValidation(VSUM_ID, state);

    assertThat(state.result()).containsSame(previous);

    service.runValidation(VSUM_ID, state);

    verify(setupServiceApiHandler, times(3))
        .validateVsum(anyList(), anyList(), anyList(), anyList());
    assertThat(state.result().orElseThrow().problems()).containsExactly(PROBLEM);
  }

  @Test
  void runValidation_storesAnInvalidResult_whenTheVsumHasNoMetamodels() {
    when(inputCollector.collect(vsum))
        .thenReturn(inputs("a"))
        .thenThrow(new NotFoundException("Metamodels"));
    when(setupServiceApiHandler.validateVsum(anyList(), anyList(), anyList(), anyList()))
        .thenReturn(new VsumValidationResult(true, List.of()));
    VsumValidationService.ValidationState state = new VsumValidationService.ValidationState();
    service.runValidation(VSUM_ID, state);
    Instant before = Instant.now();

    service.runValidation(VSUM_ID, state);

    VsumValidationService.LatestValidation latest = state.result().orElseThrow();
    assertThat(latest.validatedAt()).isBetween(before, Instant.now());
    assertThat(latest.valid()).isFalse();
    assertThat(latest.problems())
        .containsExactly(
            new BuildProblem(
                VsumValidationService.METAMODEL_SOURCE,
                null,
                null,
                null,
                null,
                "Metamodels not found!",
                null,
                null,
                null));
    verify(setupServiceApiHandler, times(1))
        .validateVsum(anyList(), anyList(), anyList(), anyList());
  }

  @Test
  void runValidation_storesAnInvalidResult_whenTheInputsCannotBeComposed() {
    when(inputCollector.collect(vsum))
        .thenThrow(new VsumBuildingException("Reaction names must be unique."));
    VsumValidationService.ValidationState state = new VsumValidationService.ValidationState();

    service.runValidation(VSUM_ID, state);

    VsumValidationService.LatestValidation latest = state.result().orElseThrow();
    assertThat(latest.valid()).isFalse();
    assertThat(latest.problems())
        .singleElement()
        .satisfies(
            problem -> {
              assertThat(problem.source()).isEqualTo("REACTIONS");
              assertThat(problem.message()).isEqualTo("Reaction names must be unique.");
              assertThat(problem.fileId()).isNull();
            });
    verifyNoInteractions(setupServiceApiHandler);
  }

  @Test
  void runValidation_validatesAgain_whenThePreviouslyValidInputsAreRestored() {
    when(inputCollector.collect(vsum))
        .thenReturn(inputs("a"))
        .thenThrow(new VsumBuildingException("The utility methods do not compile"))
        .thenReturn(inputs("a"));
    when(setupServiceApiHandler.validateVsum(anyList(), anyList(), anyList(), anyList()))
        .thenReturn(new VsumValidationResult(true, List.of()));
    VsumValidationService.ValidationState state = new VsumValidationService.ValidationState();

    service.runValidation(VSUM_ID, state);
    service.runValidation(VSUM_ID, state);
    assertThat(state.result().orElseThrow().valid()).isFalse();
    service.runValidation(VSUM_ID, state);

    verify(setupServiceApiHandler, times(2))
        .validateVsum(anyList(), anyList(), anyList(), anyList());
    assertThat(state.result().orElseThrow().valid()).isTrue();
  }

  @Test
  void runValidation_keepsThePreviousResult_whenCollectingFailsForOtherReasons() {
    when(inputCollector.collect(vsum))
        .thenReturn(inputs("a"))
        .thenThrow(new IllegalStateException("database down"));
    when(setupServiceApiHandler.validateVsum(anyList(), anyList(), anyList(), anyList()))
        .thenReturn(new VsumValidationResult(true, List.of()));
    VsumValidationService.ValidationState state = new VsumValidationService.ValidationState();

    service.runValidation(VSUM_ID, state);
    VsumValidationService.LatestValidation previous = state.result().orElseThrow();
    service.runValidation(VSUM_ID, state);

    assertThat(state.result()).containsSame(previous);
  }

  @Test
  void runValidation_doesNotTreatSetupServiceFailuresAsInvalidInputs() {
    givenInputs(inputs("a"), inputs("b"));
    when(setupServiceApiHandler.validateVsum(anyList(), anyList(), anyList(), anyList()))
        .thenReturn(new VsumValidationResult(true, List.of()))
        .thenThrow(new VsumBuildingException("unexpected"));
    VsumValidationService.ValidationState state = new VsumValidationService.ValidationState();

    service.runValidation(VSUM_ID, state);
    VsumValidationService.LatestValidation previous = state.result().orElseThrow();
    service.runValidation(VSUM_ID, state);

    assertThat(state.result()).containsSame(previous);
  }

  @Test
  void runValidation_doesNothing_whenTheVsumWasRemoved() {
    when(vsumRepository.findByIdAndRemovedAtIsNull(VSUM_ID)).thenReturn(Optional.empty());
    VsumValidationService.ValidationState state = new VsumValidationService.ValidationState();

    service.runValidation(VSUM_ID, state);

    verifyNoInteractions(inputCollector, setupServiceApiHandler);
    assertThat(state.result()).isEmpty();
  }

  @Test
  void requestValidation_runsInTheBackground_andStoresTheResult() throws Exception {
    givenInputs(inputs("a"));
    when(setupServiceApiHandler.validateVsum(anyList(), anyList(), anyList(), anyList()))
        .thenReturn(new VsumValidationResult(true, List.of()));

    service.requestValidation(VSUM_ID);

    assertThat(awaitResult(r -> true).valid()).isTrue();
  }

  @Test
  void requestValidation_runsOneValidationPerVsum_andRevalidatesPendingChangesOnce()
      throws Exception {
    givenInputs(inputs("a"), inputs("b"));
    CountDownLatch firstStarted = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    AtomicInteger running = new AtomicInteger();
    AtomicInteger maxRunning = new AtomicInteger();
    AtomicInteger calls = new AtomicInteger();
    when(setupServiceApiHandler.validateVsum(anyList(), anyList(), anyList(), anyList()))
        .thenAnswer(
            invocation -> {
              maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
              try {
                if (calls.incrementAndGet() == 1) {
                  firstStarted.countDown();
                  assertThat(releaseFirst.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
                  return new VsumValidationResult(true, List.of());
                }
                return new VsumValidationResult(false, List.of(PROBLEM));
              } finally {
                running.decrementAndGet();
              }
            });

    service.requestValidation(VSUM_ID);
    assertThat(firstStarted.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
    service.requestValidation(VSUM_ID);
    service.requestValidation(VSUM_ID);
    service.requestValidation(VSUM_ID);
    releaseFirst.countDown();

    VsumValidationService.LatestValidation latest = awaitResult(r -> !r.valid());
    assertThat(latest.problems()).containsExactly(PROBLEM);
    verify(setupServiceApiHandler, timeout(WAIT_MILLIS).times(2))
        .validateVsum(anyList(), anyList(), anyList(), anyList());
    verify(inputCollector, times(2)).collect(vsum);
    assertThat(maxRunning.get()).isEqualTo(1);
  }

  @Test
  void requestValidation_acceptsNewRequests_afterAValidationFinished() throws Exception {
    givenInputs(inputs("a"), inputs("b"));
    when(setupServiceApiHandler.validateVsum(anyList(), anyList(), anyList(), anyList()))
        .thenReturn(new VsumValidationResult(true, List.of()))
        .thenReturn(new VsumValidationResult(false, List.of(PROBLEM)));

    service.requestValidation(VSUM_ID);
    awaitResult(VsumValidationService.LatestValidation::valid);
    service.requestValidation(VSUM_ID);

    assertThat(awaitResult(r -> !r.valid()).problems()).containsExactly(PROBLEM);
  }

  @Test
  void findLatestResult_returnsTheResult_forOwnersAndMembers() throws Exception {
    givenInputs(inputs("a"));
    when(setupServiceApiHandler.validateVsum(anyList(), anyList(), anyList(), anyList()))
        .thenReturn(new VsumValidationResult(false, List.of(PROBLEM)));
    service.requestValidation(VSUM_ID);
    VsumValidationService.LatestValidation latest = awaitResult(r -> true);

    for (VsumRole role : List.of(VsumRole.OWNER, VsumRole.MEMBER)) {
      givenMember(role);

      VsumValidationResponse response = service.findLatestResult(EMAIL, VSUM_ID).orElseThrow();

      assertThat(response.isValid()).isFalse();
      assertThat(response.getProblems()).containsExactly(PROBLEM);
      assertThat(response.getValidatedAt()).isEqualTo(latest.validatedAt());
    }
  }

  @Test
  void findLatestResult_isEmpty_whenNothingWasValidatedYet() {
    givenMember(VsumRole.MEMBER);

    assertThat(service.findLatestResult(EMAIL, VSUM_ID)).isEmpty();
  }

  @Test
  void findLatestResult_deniesViewers_evenWhenAResultExists() throws Exception {
    givenInputs(inputs("a"));
    when(setupServiceApiHandler.validateVsum(anyList(), anyList(), anyList(), anyList()))
        .thenReturn(new VsumValidationResult(true, List.of()));
    service.requestValidation(VSUM_ID);
    awaitResult(r -> true);
    givenMember(VsumRole.VIEWER);

    assertThatThrownBy(() -> service.findLatestResult(EMAIL, VSUM_ID))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void findLatestResult_deniesNonMembers() {
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                VSUM_ID, EMAIL))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findLatestResult(EMAIL, VSUM_ID))
        .isInstanceOf(AccessDeniedException.class);
    verify(vsumRepository, never()).findByIdAndRemovedAtIsNull(any());
  }

  private void givenMember(VsumRole role) {
    VsumUser vsumUser = VsumUser.builder().vsum(vsum).role(role).build();
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                VSUM_ID, EMAIL))
        .thenReturn(Optional.of(vsumUser));
  }

  private void givenInputs(VsumBuildInputs first, VsumBuildInputs... next) {
    when(inputCollector.collect(vsum)).thenReturn(first, next);
  }

  private VsumValidationService.LatestValidation awaitResult(
      Predicate<VsumValidationService.LatestValidation> condition) throws InterruptedException {
    AtomicReference<VsumValidationService.LatestValidation> found = new AtomicReference<>();
    Instant deadline = Instant.now().plus(Duration.ofMillis(WAIT_MILLIS));
    while (Instant.now().isBefore(deadline)) {
      service.findLatestResult(VSUM_ID).filter(condition).ifPresent(found::set);
      if (found.get() != null) {
        return found.get();
      }
      Thread.sleep(10);
    }
    throw new AssertionError("No matching validation result within " + WAIT_MILLIS + " ms");
  }

  private static VsumBuildInputs inputs(String ecoreContent) {
    FileStorage ecore = new FileStorage();
    ecore.setFilename("model.ecore");
    ecore.setData(ecoreContent.getBytes(StandardCharsets.UTF_8));
    return new VsumBuildInputs(List.of(ecore), List.of(), List.of(), List.of());
  }
}
