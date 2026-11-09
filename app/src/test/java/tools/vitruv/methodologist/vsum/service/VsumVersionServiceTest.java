package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import tools.vitruv.methodologist.config.VsumBuildProperties;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.PublishInProgressException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.VsumRepresentation;
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

@ExtendWith(MockitoExtension.class)
class VsumVersionServiceTest {

  private static final String EMAIL = "owner@example.com";
  private static final Long VSUM_ID = 7L;
  private static final Long BUILD_ID = 42L;

  @Mock private VsumUserRepository vsumUserRepository;
  @Mock private VsumVersionRepository vsumVersionRepository;
  @Mock private FileStorageRepository fileStorageRepository;
  @Mock private VsumBuildInputCollector inputCollector;
  @Mock private VsumBuildService vsumBuildService;
  @Mock private VsumHistoryMapper vsumHistoryMapper;
  @Mock private PlatformTransactionManager transactionManager;

  private final VsumBuildProperties properties = new VsumBuildProperties();
  private final AtomicLong fileIds = new AtomicLong(1000);

  private Vsum vsum;
  private User user;
  private VsumUser member;
  private FileStorage ecore;
  private FileStorage reaction;
  private VsumBuildInputs inputs;
  private VsumVersionService service;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setEmail(EMAIL);
    vsum = new Vsum();
    vsum.setId(VSUM_ID);
    member = new VsumUser();
    member.setVsum(vsum);
    member.setUser(user);
    member.setRole(VsumRole.MEMBER);
    lenient()
        .when(
            vsumUserRepository
                .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                    VSUM_ID, EMAIL))
        .thenReturn(Optional.of(member));

    ecore = file(1L, "a.ecore", "ecore", null);
    // Generated inputs, like the composite reaction, have no owner, hash or content type.
    reaction = file(null, "composite.reactions", "reaction", null);
    inputs = new VsumBuildInputs(List.of(ecore), List.of(reaction), List.of(), List.of());
    lenient().when(inputCollector.collect(vsum)).thenReturn(inputs);
    lenient()
        .when(vsumBuildService.queue(any(Vsum.class), any(User.class), anyString()))
        .thenAnswer(
            inv ->
                VsumBuild.builder()
                    .id(BUILD_ID)
                    .vsum(inv.getArgument(0))
                    .requestedBy(inv.getArgument(1))
                    .fingerprint(inv.getArgument(2))
                    .status(VsumBuildStatus.QUEUED)
                    .build());
    lenient()
        .when(fileStorageRepository.save(any(FileStorage.class)))
        .thenAnswer(
            inv -> {
              FileStorage saved = inv.getArgument(0);
              saved.setId(fileIds.incrementAndGet());
              return saved;
            });
    lenient()
        .when(vsumHistoryMapper.toVsumRepresentation(vsum))
        .thenReturn(new VsumRepresentation());

    service =
        new VsumVersionService(
            vsumUserRepository,
            vsumVersionRepository,
            fileStorageRepository,
            inputCollector,
            vsumBuildService,
            vsumHistoryMapper,
            Mappers.getMapper(VsumBuildMapper.class),
            Mappers.getMapper(VsumVersionMapper.class),
            properties,
            transactionManager);
  }

  @Test
  void publish_storesAPendingVersionOfCopies_andStartsItsBuild() {
    VsumBuildResponse build = service.publish(EMAIL, VSUM_ID);

    assertThat(build.getId()).isEqualTo(BUILD_ID);
    assertThat(build.getFingerprint()).isEqualTo(inputs.fingerprint());
    verify(vsumBuildService).submit(BUILD_ID);

    VsumVersion version = savedVersion();
    assertThat(version.getNumber()).isNull();
    assertThat(version.getVsum()).isSameAs(vsum);
    assertThat(version.getCreatedBy()).isSameAs(user);
    assertThat(version.getBuild().getId()).isEqualTo(BUILD_ID);
    assertThat(version.getManifest()).isNotNull();
    assertThat(version.getEcores()).singleElement().satisfies(copy -> assertCopyOf(copy, ecore));
    assertThat(version.getReactions())
        .singleElement()
        .satisfies(copy -> assertCopyOf(copy, reaction));
    assertThat(version.getConstraints()).isEmpty();
    assertThat(version.getViews()).isEmpty();
  }

  @Test
  void publish_keepsTheVersionUnchanged_whenTheWorkingCopysFileIsEditedAfterwards() {
    service.publish(EMAIL, VSUM_ID);
    FileStorage copy = savedVersion().getEcores().get(0);

    ecore.getData()[0] = 'X';
    ecore.setFilename("renamed.ecore");

    assertThat(copy.getData()).isEqualTo("ecore".getBytes(StandardCharsets.UTF_8));
    assertThat(copy.getFilename()).isEqualTo("a.ecore");
  }

  @Test
  void publish_isAllowedForOwners() {
    member.setRole(VsumRole.OWNER);

    service.publish(EMAIL, VSUM_ID);

    verify(vsumBuildService).submit(BUILD_ID);
  }

  @Test
  void publish_isDeniedForViewers() {
    member.setRole(VsumRole.VIEWER);

    assertThatThrownBy(() -> service.publish(EMAIL, VSUM_ID))
        .isInstanceOf(AccessDeniedException.class);

    verify(vsumBuildService, never()).queue(any(), any(), anyString());
    verify(vsumBuildService, never()).submit(anyLong());
  }

  @Test
  void publish_isDeniedForUsersWithoutAccess() {
    assertThatThrownBy(() -> service.publish("stranger@example.com", VSUM_ID))
        .isInstanceOf(AccessDeniedException.class);

    verify(vsumBuildService, never()).queue(any(), any(), anyString());
  }

  @Test
  void publish_isRejected_whileAnotherPublishIsBuilding() {
    when(vsumVersionRepository.findFirstByVsumAndNumberIsNull(vsum))
        .thenReturn(Optional.of(pending(VsumBuildStatus.RUNNING, Instant.now())));

    assertThatThrownBy(() -> service.publish(EMAIL, VSUM_ID))
        .isInstanceOf(PublishInProgressException.class);

    verify(vsumBuildService, never()).queue(any(), any(), anyString());
    verify(vsumBuildService, never()).fail(anyLong(), anyString());
  }

  @Test
  void publish_isRejected_whenAConcurrentPublishWinsTheUniqueIndex() {
    when(vsumVersionRepository.save(any(VsumVersion.class)))
        .thenThrow(new DataIntegrityViolationException("uq_vsum_version_one_pending"));

    assertThatThrownBy(() -> service.publish(EMAIL, VSUM_ID))
        .isInstanceOf(PublishInProgressException.class);

    verify(vsumBuildService, never()).submit(anyLong());
  }

  @Test
  void publish_abandonsAPublishThatDidNotFinishInTime() {
    properties.setWaitTimeoutSeconds(60);
    VsumVersion stuck = pending(VsumBuildStatus.RUNNING, Instant.now().minus(1, ChronoUnit.DAYS));
    when(vsumVersionRepository.findFirstByVsumAndNumberIsNull(vsum)).thenReturn(Optional.of(stuck));

    service.publish(EMAIL, VSUM_ID);

    verify(vsumBuildService)
        .fail(stuck.getBuild().getId(), String.format(VsumVersionService.STUCK_ERROR, 60L));
    verify(vsumBuildService).submit(BUILD_ID);
  }

  @Test
  void publish_abandonsAPendingVersionWhoseBuildAlreadyFinished() {
    VsumVersion leftOver = pending(VsumBuildStatus.FAILED, Instant.now());
    when(vsumVersionRepository.findFirstByVsumAndNumberIsNull(vsum))
        .thenReturn(Optional.of(leftOver));

    service.publish(EMAIL, VSUM_ID);

    verify(vsumBuildService).fail(anyLong(), anyString());
    verify(vsumBuildService).submit(BUILD_ID);
  }

  @Test
  void findLatest_returnsTheNewestStableVersion() {
    member.setRole(VsumRole.VIEWER);
    VsumVersion latest = pending(VsumBuildStatus.SUCCEEDED, Instant.now());
    latest.setNumber(3);
    when(vsumVersionRepository.findFirstByVsumAndNumberIsNotNullOrderByNumberDesc(vsum))
        .thenReturn(Optional.of(latest));

    VsumVersionResponse response = service.findLatest(EMAIL, VSUM_ID);

    assertThat(response.getNumber()).isEqualTo(3);
    assertThat(response.getCreatedBy()).isEqualTo(EMAIL);
    assertThat(response.getCreatedAt()).isEqualTo(latest.getCreatedAt());
    assertThat(response.getBuildId()).isEqualTo(latest.getBuild().getId());
  }

  @Test
  void findLatest_throwsNotFound_whenNothingWasPublished() {
    assertThatThrownBy(() -> service.findLatest(EMAIL, VSUM_ID))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void findLatest_isDeniedForUsersWithoutAccess() {
    assertThatThrownBy(() -> service.findLatest("stranger@example.com", VSUM_ID))
        .isInstanceOf(AccessDeniedException.class);
  }

  // ---- fixtures -------------------------------------------------------------------------------

  private VsumVersion savedVersion() {
    ArgumentCaptor<VsumVersion> captor = ArgumentCaptor.forClass(VsumVersion.class);
    verify(vsumVersionRepository).save(captor.capture());
    return captor.getValue();
  }

  private void assertCopyOf(FileStorage copy, FileStorage original) {
    assertThat(copy).isNotSameAs(original);
    assertThat(copy.getId()).isNotNull().isNotEqualTo(original.getId());
    assertThat(copy.getType()).isEqualTo(FileEnumType.VSUM_VERSION_FILE);
    assertThat(copy.getUser()).isSameAs(user);
    assertThat(copy.getFilename()).isEqualTo(original.getFilename());
    assertThat(copy.getData()).isEqualTo(original.getData()).isNotSameAs(original.getData());
    assertThat(copy.getSizeBytes()).isEqualTo(original.getData().length);
    assertThat(copy.getSha256()).isEqualTo(VsumBuildInputs.sha256Hex(original.getData()));
    assertThat(copy.getContentType()).isEqualTo(VsumVersionService.DEFAULT_CONTENT_TYPE);
  }

  private VsumVersion pending(VsumBuildStatus buildStatus, Instant createdAt) {
    VsumBuild build =
        VsumBuild.builder()
            .id(99L)
            .vsum(vsum)
            .requestedBy(user)
            .fingerprint("f")
            .status(buildStatus)
            .build();
    return VsumVersion.builder()
        .vsum(vsum)
        .createdBy(user)
        .build(build)
        .createdAt(createdAt)
        .build();
  }

  private static FileStorage file(Long id, String filename, String content, String contentType) {
    FileStorage file = new FileStorage();
    file.setId(id);
    file.setFilename(filename);
    file.setContentType(contentType);
    file.setData(content.getBytes(StandardCharsets.UTF_8));
    return file;
  }
}
