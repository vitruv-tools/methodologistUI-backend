package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import tools.vitruv.methodologist.apihandler.SetupServiceApiHandler;
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
 * Drives {@link VsumBuildService} with a synchronous executor and an in-memory stand-in for the
 * build repository, so every state transition of a build can be observed deterministically.
 */
@ExtendWith(MockitoExtension.class)
class VsumBuildServiceTest {

  private static final String EMAIL = "x@y.com";
  private static final Long VSUM_ID = 7L;
  private static final byte[] JAR = "FAKEJAR".getBytes(StandardCharsets.UTF_8);

  @Mock private VsumUserRepository vsumUserRepository;
  @Mock private VsumBuildRepository vsumBuildRepository;
  @Mock private FileStorageRepository fileStorageRepository;
  @Mock private VsumBuildInputCollector inputCollector;
  @Mock private SetupServiceApiHandler setupServiceApiHandler;
  @Mock private PlatformTransactionManager transactionManager;

  private final VsumBuildProperties properties = new VsumBuildProperties();
  private final Map<Long, VsumBuild> builds = new LinkedHashMap<>();
  private final AtomicLong ids = new AtomicLong();
  private final List<FileStorage> deletedArtifacts = new ArrayList<>();

  private Vsum vsum;
  private User user;
  private VsumBuildInputs inputs;
  private VsumBuildService service;

  @BeforeEach
  void setUp() {
    properties.setPollIntervalMillis(1);
    properties.setWaitTimeoutSeconds(5);
    properties.setRetentionKeep(5);

    user = new User();
    user.setEmail(EMAIL);
    vsum = new Vsum();
    vsum.setId(VSUM_ID);
    VsumUser member = new VsumUser();
    member.setVsum(vsum);
    member.setUser(user);
    lenient()
        .when(
            vsumUserRepository
                .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                    VSUM_ID, EMAIL))
        .thenReturn(Optional.of(member));

    inputs =
        new VsumBuildInputs(
            List.of(fs(1L, "a.ecore", new byte[] {1})),
            List.of(fs(2L, "a.genmodel", new byte[] {2})),
            List.of(fs(3L, "x.reactions", new byte[] {3})));
    lenient().when(inputCollector.collect(vsum)).thenReturn(inputs);
    lenient()
        .when(setupServiceApiHandler.buildVsumJarOrThrow(anyList(), anyList(), anyList()))
        .thenReturn(JAR);

    stubInMemoryRepositories();
    service = newService(Runnable::run);
  }

  @Test
  void requestBuild_throwsAccessDenied_whenCallerIsNotAMember() {
    assertThatThrownBy(() -> service.requestBuild("other@y.com", VSUM_ID, false))
        .isInstanceOf(AccessDeniedException.class);

    assertThat(builds).isEmpty();
    verify(setupServiceApiHandler, never()).buildVsumJarOrThrow(anyList(), anyList(), anyList());
  }

  @Test
  void requestBuild_startsABuild_thatStoresTheJarAsArtifact() {
    VsumBuildService.BuildRequest request = service.requestBuild(EMAIL, VSUM_ID, false);

    assertThat(request.started()).isTrue();
    assertThat(request.build().getStatus()).isEqualTo(VsumBuildStatus.QUEUED);
    assertThat(request.build().getFingerprint()).isEqualTo(inputs.fingerprint());
    assertThat(request.build().getRequestedBy()).isEqualTo(EMAIL);

    // the synchronous executor has run the build by now
    VsumBuild build = builds.get(request.build().getId());
    assertThat(build.getStatus()).isEqualTo(VsumBuildStatus.SUCCEEDED);
    assertThat(build.getStartedAt()).isNotNull();
    assertThat(build.getFinishedAt()).isNotNull();
    assertThat(build.getArtifact().getData()).isEqualTo(JAR);
    assertThat(build.getArtifact().getType()).isEqualTo(FileEnumType.VSUM_JAR);
    assertThat(build.getArtifact().getFilename()).isEqualTo(VsumBuildService.ARTIFACT_FILENAME);
    assertThat(build.getArtifact().getUser()).isSameAs(user);
    verify(setupServiceApiHandler)
        .buildVsumJarOrThrow(inputs.ecores(), inputs.genmodels(), inputs.reactions());

    VsumBuildResponse response = service.findBuild(EMAIL, VSUM_ID, build.getId());
    assertThat(response.getStatus()).isEqualTo(VsumBuildStatus.SUCCEEDED);
    assertThat(response.isArtifactAvailable()).isTrue();
  }

  @Test
  void requestBuild_reusesTheLatestSuccessfulBuildOfTheSameInputs() {
    VsumBuild existing = succeeded(inputs.fingerprint(), Instant.now());

    VsumBuildService.BuildRequest request = service.requestBuild(EMAIL, VSUM_ID, false);

    assertThat(request.started()).isFalse();
    assertThat(request.build().getId()).isEqualTo(existing.getId());
    assertThat(builds).hasSize(1);
    verify(setupServiceApiHandler, never()).buildVsumJarOrThrow(anyList(), anyList(), anyList());
  }

  @Test
  void requestBuild_buildsAgain_whenForced() {
    VsumBuild existing = succeeded(inputs.fingerprint(), Instant.now());

    VsumBuildService.BuildRequest request = service.requestBuild(EMAIL, VSUM_ID, true);

    assertThat(request.started()).isTrue();
    assertThat(request.build().getId()).isNotEqualTo(existing.getId());
    assertThat(request.build().isForced()).isTrue();
    assertThat(builds.get(request.build().getId()).getStatus())
        .isEqualTo(VsumBuildStatus.SUCCEEDED);
    verify(setupServiceApiHandler).buildVsumJarOrThrow(anyList(), anyList(), anyList());
  }

  @Test
  void requestBuild_doesNotBuild_whenInputsChangedButAnOlderArtifactExists() {
    succeeded("0000000000000000000000000000000000000000000000000000000000000000", Instant.now());

    VsumBuildService.BuildRequest request = service.requestBuild(EMAIL, VSUM_ID, false);

    assertThat(request.started()).isTrue();
    assertThat(builds).hasSize(2);
  }

  @Test
  void requestBuild_returnsTheInFlightBuildOfTheSameInputs() {
    VsumBuild running = save(build(inputs.fingerprint(), VsumBuildStatus.RUNNING, false));

    VsumBuildService.BuildRequest request = service.requestBuild(EMAIL, VSUM_ID, false);

    assertThat(request.started()).isFalse();
    assertThat(request.build().getId()).isEqualTo(running.getId());
    assertThat(builds).hasSize(1);
  }

  @Test
  void requestBuild_marksTheBuildFailed_whenTheSetupServiceFails() {
    when(setupServiceApiHandler.buildVsumJarOrThrow(anyList(), anyList(), anyList()))
        .thenThrow(new SetupServiceException("boom"));

    VsumBuildService.BuildRequest request = service.requestBuild(EMAIL, VSUM_ID, false);

    VsumBuild build = builds.get(request.build().getId());
    assertThat(build.getStatus()).isEqualTo(VsumBuildStatus.FAILED);
    assertThat(build.getErrorMessage()).isEqualTo("boom");
    assertThat(build.getArtifact()).isNull();
    assertThat(build.getFinishedAt()).isNotNull();
  }

  @Test
  void requestBuild_marksTheBuildFailed_whenTheQueueIsFull() {
    service =
        newService(
            task -> {
              throw new TaskRejectedException("full");
            });

    assertThatThrownBy(() -> service.requestBuild(EMAIL, VSUM_ID, false))
        .isInstanceOf(VsumBuildingException.class)
        .hasMessageContaining(VsumBuildService.QUEUE_FULL_ERROR);

    assertThat(builds).hasSize(1);
    VsumBuild build = builds.values().iterator().next();
    assertThat(build.getStatus()).isEqualTo(VsumBuildStatus.FAILED);
    assertThat(build.getErrorMessage()).isEqualTo(VsumBuildService.QUEUE_FULL_ERROR);
  }

  @Test
  void runBuild_adoptsTheArtifactOfATwin_thatSucceededMeanwhile() {
    VsumBuild twin = succeeded(inputs.fingerprint(), Instant.now());
    VsumBuild queued = save(build(inputs.fingerprint(), VsumBuildStatus.QUEUED, false));

    service.runBuild(queued.getId());

    assertThat(queued.getStatus()).isEqualTo(VsumBuildStatus.SUCCEEDED);
    assertThat(queued.getArtifact()).isSameAs(twin.getArtifact());
    verify(setupServiceApiHandler, never()).buildVsumJarOrThrow(anyList(), anyList(), anyList());
  }

  @Test
  void runBuild_ignoresBuildsThatAreNoLongerQueued() {
    VsumBuild failed = save(build(inputs.fingerprint(), VsumBuildStatus.FAILED, false));

    service.runBuild(failed.getId());
    service.runBuild(999L);

    assertThat(failed.getStatus()).isEqualTo(VsumBuildStatus.FAILED);
    verify(setupServiceApiHandler, never()).buildVsumJarOrThrow(anyList(), anyList(), anyList());
  }

  @Test
  void succeed_keepsOnlyTheNewestArtifacts_perRetention() {
    properties.setRetentionKeep(1);
    VsumBuild old =
        succeeded(
            "1111111111111111111111111111111111111111111111111111111111111111",
            Instant.now().minusSeconds(60));
    FileStorage oldArtifact = old.getArtifact();

    VsumBuildService.BuildRequest request = service.requestBuild(EMAIL, VSUM_ID, false);

    VsumBuild fresh = builds.get(request.build().getId());
    assertThat(fresh.getArtifact()).isNotNull();
    assertThat(old.getStatus()).isEqualTo(VsumBuildStatus.SUCCEEDED);
    assertThat(old.getArtifact()).isNull();
    assertThat(deletedArtifacts).containsExactly(oldArtifact);
    InOrder inOrder = inOrder(fileStorageRepository);
    inOrder.verify(fileStorageRepository).unlinkData(oldArtifact.getId());
    inOrder.verify(fileStorageRepository).delete(oldArtifact);
    assertThat(service.findBuild(EMAIL, VSUM_ID, old.getId()).isArtifactAvailable()).isFalse();
  }

  @Test
  void succeed_doesNotDeleteAnArtifact_anotherBuildStillReferences() {
    properties.setRetentionKeep(1);
    VsumBuild old =
        succeeded(
            "1111111111111111111111111111111111111111111111111111111111111111",
            Instant.now().minusSeconds(60));
    VsumBuild adopter = save(build(old.getFingerprint(), VsumBuildStatus.SUCCEEDED, false));
    adopter.setArtifact(old.getArtifact());
    adopter.setFinishedAt(Instant.now().minusSeconds(30));

    service.requestBuild(EMAIL, VSUM_ID, false);

    // both older builds lose the reference (keep = 1), so the artifact is deleted exactly once
    assertThat(old.getArtifact()).isNull();
    assertThat(adopter.getArtifact()).isNull();
    assertThat(deletedArtifacts).hasSize(1);
  }

  @Test
  void buildAndWait_returnsTheArtifactOfTheFinishedBuild() {
    assertThat(service.buildAndWait(EMAIL, VSUM_ID)).isEqualTo(JAR);
  }

  @Test
  void buildAndWait_throwsSetupServiceException_whenTheBuildFailed() {
    when(setupServiceApiHandler.buildVsumJarOrThrow(anyList(), anyList(), anyList()))
        .thenThrow(new SetupServiceException("boom"));

    assertThatThrownBy(() -> service.buildAndWait(EMAIL, VSUM_ID))
        .isInstanceOf(SetupServiceException.class)
        .hasMessageContaining("boom");
  }

  @Test
  void buildAndWait_timesOut_whenTheBuildNeverFinishes() {
    properties.setWaitTimeoutSeconds(0);
    service = newService(task -> {});

    assertThatThrownBy(() -> service.buildAndWait(EMAIL, VSUM_ID))
        .isInstanceOf(VsumBuildingException.class)
        .hasMessageContaining("did not finish");
  }

  @Test
  void getArtifact_throwsNotFound_forBuildsOfOtherVsumsOrWithoutArtifact() {
    Vsum other = new Vsum();
    other.setId(8L);
    VsumBuild foreign = save(build(inputs.fingerprint(), VsumBuildStatus.SUCCEEDED, false));
    foreign.setVsum(other);
    VsumBuild withoutArtifact = save(build(inputs.fingerprint(), VsumBuildStatus.FAILED, false));

    Long foreignId = foreign.getId();
    Long withoutArtifactId = withoutArtifact.getId();

    assertThatThrownBy(() -> service.getArtifact(EMAIL, VSUM_ID, foreignId))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.getArtifact(EMAIL, VSUM_ID, withoutArtifactId))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void findAll_listsTheBuildsOfTheVsum() {
    save(build(inputs.fingerprint(), VsumBuildStatus.FAILED, false));
    save(build(inputs.fingerprint(), VsumBuildStatus.SUCCEEDED, false));

    List<VsumBuildResponse> all = service.findAll(EMAIL, VSUM_ID, PageRequest.of(0, 20));

    assertThat(all).hasSize(2).allSatisfy(b -> assertThat(b.getVsumId()).isEqualTo(VSUM_ID));
  }

  @Test
  void failBuildsInterruptedByRestart_failsEveryQueuedOrRunningBuild() {
    final VsumBuild queued = save(build(inputs.fingerprint(), VsumBuildStatus.QUEUED, false));
    final VsumBuild running = save(build(inputs.fingerprint(), VsumBuildStatus.RUNNING, false));
    final VsumBuild done = succeeded(inputs.fingerprint(), Instant.now());

    service.failBuildsInterruptedByRestart();

    assertThat(queued.getStatus()).isEqualTo(VsumBuildStatus.FAILED);
    assertThat(queued.getErrorMessage()).isEqualTo(VsumBuildService.RESTART_ERROR);
    assertThat(running.getStatus()).isEqualTo(VsumBuildStatus.FAILED);
    assertThat(done.getStatus()).isEqualTo(VsumBuildStatus.SUCCEEDED);
  }

  @Test
  void deleteByVsum_removesBuildsAndArtifactsNothingElseReferences() {
    VsumBuild a = succeeded(inputs.fingerprint(), Instant.now());
    VsumBuild b = save(build(inputs.fingerprint(), VsumBuildStatus.SUCCEEDED, false));
    b.setArtifact(a.getArtifact());
    Vsum other = new Vsum();
    other.setId(8L);
    VsumBuild foreign = save(build(inputs.fingerprint(), VsumBuildStatus.FAILED, false));
    foreign.setVsum(other);

    service.deleteByVsum(vsum);

    assertThat(builds.values()).containsExactly(foreign);
    assertThat(deletedArtifacts).containsExactly(a.getArtifact());
    verify(fileStorageRepository).unlinkData(a.getArtifact().getId());
  }

  // ---- fixtures -------------------------------------------------------------------------------

  private VsumBuildService newService(Executor executor) {
    return new VsumBuildService(
        vsumUserRepository,
        vsumBuildRepository,
        fileStorageRepository,
        inputCollector,
        setupServiceApiHandler,
        Mappers.getMapper(VsumBuildMapper.class),
        properties,
        executor,
        transactionManager);
  }

  private VsumBuild build(String fingerprint, VsumBuildStatus status, boolean forced) {
    return VsumBuild.builder()
        .vsum(vsum)
        .requestedBy(user)
        .fingerprint(fingerprint)
        .status(status)
        .forced(forced)
        .build();
  }

  private VsumBuild succeeded(String fingerprint, Instant finishedAt) {
    VsumBuild build = build(fingerprint, VsumBuildStatus.SUCCEEDED, false);
    FileStorage artifact = fs(ids.incrementAndGet() + 1000, "vsum.jar", JAR);
    build.setArtifact(artifact);
    build.setStartedAt(finishedAt.minusSeconds(1));
    build.setFinishedAt(finishedAt);
    return save(build);
  }

  private VsumBuild save(VsumBuild build) {
    if (build.getId() == null) {
      build.setId(ids.incrementAndGet());
    }
    builds.put(build.getId(), build);
    return build;
  }

  private static FileStorage fs(Long id, String filename, byte[] data) {
    FileStorage f = new FileStorage();
    f.setId(id);
    f.setFilename(filename);
    f.setData(data);
    return f;
  }

  /** Makes the mocked repositories behave like a tiny in-memory database. */
  private void stubInMemoryRepositories() {
    lenient()
        .when(vsumBuildRepository.save(any(VsumBuild.class)))
        .thenAnswer(inv -> save(inv.getArgument(0)));
    lenient()
        .when(vsumBuildRepository.saveAll(any()))
        .thenAnswer(
            inv -> {
              Iterable<VsumBuild> all = inv.getArgument(0);
              all.forEach(this::save);
              return all;
            });
    lenient()
        .when(vsumBuildRepository.findById(anyLong()))
        .thenAnswer(inv -> Optional.ofNullable(builds.get(inv.<Long>getArgument(0))));
    lenient()
        .when(vsumBuildRepository.findByIdAndVsum(anyLong(), any(Vsum.class)))
        .thenAnswer(
            inv ->
                Optional.ofNullable(builds.get(inv.<Long>getArgument(0)))
                    .filter(b -> b.getVsum() == inv.<Vsum>getArgument(1)));
    lenient()
        .when(vsumBuildRepository.findAllByVsum(any(Vsum.class)))
        .thenAnswer(inv -> ofVsum(inv.getArgument(0)));
    lenient()
        .when(vsumBuildRepository.findAllByVsumOrderByCreatedAtDesc(any(Vsum.class), any()))
        .thenAnswer(inv -> ofVsum(inv.getArgument(0)));
    lenient()
        .when(
            vsumBuildRepository.findFirstByVsumAndFingerprintAndStatusInOrderByCreatedAtDesc(
                any(Vsum.class), anyString(), any()))
        .thenAnswer(
            inv -> {
              Collection<VsumBuildStatus> statuses = inv.getArgument(2);
              return ofVsum(inv.getArgument(0)).stream()
                  .filter(b -> b.getFingerprint().equals(inv.getArgument(1)))
                  .filter(b -> statuses.contains(b.getStatus()))
                  .max(Comparator.comparing(VsumBuild::getId));
            });
    lenient()
        .when(
            vsumBuildRepository
                .findFirstByVsumAndFingerprintAndStatusAndArtifactIsNotNullOrderByFinishedAtDesc(
                    any(Vsum.class), anyString(), any(VsumBuildStatus.class)))
        .thenAnswer(
            inv ->
                ofVsum(inv.getArgument(0)).stream()
                    .filter(b -> b.getFingerprint().equals(inv.getArgument(1)))
                    .filter(b -> b.getStatus() == inv.getArgument(2))
                    .filter(b -> b.getArtifact() != null)
                    .max(Comparator.comparing(VsumBuild::getFinishedAt)));
    lenient()
        .when(
            vsumBuildRepository.findAllByVsumAndStatusAndArtifactIsNotNullOrderByFinishedAtDesc(
                any(Vsum.class), any(VsumBuildStatus.class)))
        .thenAnswer(
            inv ->
                ofVsum(inv.getArgument(0)).stream()
                    .filter(b -> b.getStatus() == inv.getArgument(1))
                    .filter(b -> b.getArtifact() != null)
                    .sorted(Comparator.comparing(VsumBuild::getFinishedAt).reversed())
                    .toList());
    lenient()
        .when(vsumBuildRepository.findAllByStatusIn(any()))
        .thenAnswer(
            inv -> {
              Collection<VsumBuildStatus> statuses = inv.getArgument(0);
              return builds.values().stream()
                  .filter(b -> statuses.contains(b.getStatus()))
                  .toList();
            });
    lenient()
        .when(vsumBuildRepository.countByArtifact(any(FileStorage.class)))
        .thenAnswer(
            inv ->
                builds.values().stream()
                    .filter(b -> b.getArtifact() == inv.getArgument(0))
                    .count());
    lenient()
        .doAnswer(
            inv -> {
              Iterable<VsumBuild> all = inv.getArgument(0);
              all.forEach(b -> builds.remove(b.getId()));
              return null;
            })
        .when(vsumBuildRepository)
        .deleteAll(any());

    lenient()
        .when(fileStorageRepository.save(any(FileStorage.class)))
        .thenAnswer(
            inv -> {
              FileStorage f = inv.getArgument(0);
              if (f.getId() == null) {
                f.setId(ids.incrementAndGet() + 2000);
              }
              return f;
            });
    lenient()
        .doAnswer(
            inv -> {
              deletedArtifacts.add(inv.getArgument(0));
              return null;
            })
        .when(fileStorageRepository)
        .delete(any(FileStorage.class));
  }

  private List<VsumBuild> ofVsum(Vsum ofVsum) {
    return builds.values().stream().filter(b -> b.getVsum() == ofVsum).toList();
  }
}
