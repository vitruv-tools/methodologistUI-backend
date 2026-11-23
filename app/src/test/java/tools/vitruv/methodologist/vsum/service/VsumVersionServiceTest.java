package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.PublishInProgressException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.VsumRepresentation;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.response.FineGranularMetaModelRelationResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelRelationResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.ViewsResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumBuildResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMetaModelResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumVersionContentResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumVersionResponse;
import tools.vitruv.methodologist.vsum.mapper.VsumBuildMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumHistoryMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumVersionMapper;
import tools.vitruv.methodologist.vsum.model.ConstraintRuleSet;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumBuild;
import tools.vitruv.methodologist.vsum.model.VsumBuildStatus;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.VsumVersion;
import tools.vitruv.methodologist.vsum.model.VsumVersionContent;
import tools.vitruv.methodologist.vsum.model.repository.ConstraintRuleSetRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumVersionRepository;

@ExtendWith(MockitoExtension.class)
class VsumVersionServiceTest {

  private static final String EMAIL = "owner@example.com";
  private static final Long VSUM_ID = 7L;
  private static final Long BUILD_ID = 42L;
  private static final long TIMEOUT_SECONDS = 960;
  private static final Long PENDING_BUILD_ID = 99L;
  private static final ObjectNode LAYOUT = JsonNodeFactory.instance.objectNode();

  static {
    LAYOUT.putObject("metaModels").putObject("10").put("x", 1).put("y", 2);
  }

  @Mock private VsumUserRepository vsumUserRepository;
  @Mock private VsumVersionRepository vsumVersionRepository;
  @Mock private FileStorageRepository fileStorageRepository;
  @Mock private VsumBuildInputCollector inputCollector;
  @Mock private VsumBuildService vsumBuildService;
  @Mock private VsumHistoryMapper vsumHistoryMapper;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private VsumService vsumService;
  @Mock private ConstraintRuleSetRepository constraintRuleSetRepository;

  private final AtomicLong fileIds = new AtomicLong(1000);

  private Vsum vsum;
  private User user;
  private VsumUser member;
  private FileStorage ecore;
  private FileStorage reaction;
  private FileStorage relationReaction;
  private FileStorage fineGrainedReaction;
  private FileStorage viewFile;
  private FileStorage ocl;
  private ConstraintRuleSet ruleSet;
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

    // What the canvas shows: the ecore (id 1) is also a build input, the other files are not.
    relationReaction = file(2L, "relation.reactions", "relation", null);
    fineGrainedReaction = file(3L, "fine.reactions", "fine", null);
    viewFile = file(4L, "view.nj", "view", null);
    ocl = file(6L, "rules.ocl", "context A inv: true", null);
    for (FileStorage live : List.of(ecore, relationReaction, fineGrainedReaction, viewFile, ocl)) {
      lenient().when(fileStorageRepository.findById(live.getId())).thenReturn(Optional.of(live));
    }
    lenient().when(vsumService.toDetailsResponse(vsum)).thenReturn(details(2L, 3L));
    ruleSet =
        ConstraintRuleSet.builder()
            .id(20L)
            .name("rules")
            .color("#123456")
            .description("d")
            .oclFile(ocl)
            .build();
    lenient().when(constraintRuleSetRepository.findByVsumId(VSUM_ID)).thenReturn(List.of(ruleSet));

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
            vsumService,
            constraintRuleSetRepository,
            TIMEOUT_SECONDS,
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
  void publish_storesTheProjectAsOnTheCanvas_pointingAtCopies() {
    service.publish(EMAIL, VSUM_ID);
    VsumVersion version = savedVersion();
    VsumVersionContent content = version.getContent();

    MetaModelResponse metaModel = content.project().getMetaModels().get(0);
    assertThat(metaModel.getEcoreFileId()).isEqualTo(version.getEcores().get(0).getId());
    assertThat(metaModel.getGenModelFileId()).isNull();
    MetaModelRelationResponse relation = content.project().getMetaModelsRelation().get(0);
    assertCopyOf(copyWithId(version, relation.getReactionFileStorageId()), relationReaction);
    assertCopyOf(
        copyWithId(
            version,
            relation
                .getFineGranularMetaModelRelationSet()
                .iterator()
                .next()
                .getReactionFileStorageId()),
        fineGrainedReaction);
    assertCopyOf(
        copyWithId(version, content.project().getViews().get(0).getFileStorageId()), viewFile);
    assertThat(content.ruleSets())
        .singleElement()
        .satisfies(
            copiedRuleSet -> {
              assertThat(copiedRuleSet.id()).isEqualTo(20L);
              assertThat(copiedRuleSet.name()).isEqualTo("rules");
              assertThat(copiedRuleSet.color()).isEqualTo("#123456");
              assertThat(copiedRuleSet.description()).isEqualTo("d");
              assertCopyOf(copyWithId(version, copiedRuleSet.oclFileId()), ocl);
            });
    assertThat(content.project().getName()).isEqualTo("p");
    assertThat(content.project().getLayout()).isEqualTo(LAYOUT);
    assertThat(version.getContentFiles()).hasSize(5);
  }

  @Test
  void publish_pointsTheMetaModelsAssignedToAViewAtCopies() {
    service.publish(EMAIL, VSUM_ID);
    VsumVersion version = savedVersion();

    MetaModelResponse assigned =
        version.getContent().project().getViews().get(0).getAssignedModels().get(0);
    assertThat(assigned.getEcoreFileId()).isEqualTo(version.getEcores().get(0).getId());
    assertThat(assigned.getGenModelFileId()).isNull();
  }

  @Test
  void publish_copiesEachLiveFileOnce() {
    // The fine-grained relation uses the relation's own reaction file.
    when(vsumService.toDetailsResponse(vsum)).thenReturn(details(2L, 2L));

    service.publish(EMAIL, VSUM_ID);
    VsumVersion version = savedVersion();

    MetaModelRelationResponse relation =
        version.getContent().project().getMetaModelsRelation().get(0);
    assertThat(
            relation
                .getFineGranularMetaModelRelationSet()
                .iterator()
                .next()
                .getReactionFileStorageId())
        .isEqualTo(relation.getReactionFileStorageId());
    assertThat(version.getContentFiles()).doesNotHaveDuplicates();
    // ecore (shared by build and content), composite reaction, relation reaction, view, OCL
    verify(fileStorageRepository, times(5)).save(any(FileStorage.class));
  }

  @Test
  void publish_keepsMissingFilesMissing() {
    when(vsumService.toDetailsResponse(vsum)).thenReturn(details(null, null));
    ruleSet.setOclFile(null);

    service.publish(EMAIL, VSUM_ID);
    VsumVersionContent content = savedVersion().getContent();

    assertThat(content.project().getMetaModelsRelation().get(0).getReactionFileStorageId())
        .isNull();
    assertThat(content.ruleSets()).singleElement().satisfies(rs -> assertThat(rs.oclFileId()).isNull());
  }

  @Test
  void publish_keepsTheContentUnchanged_whenAFileIsEditedAfterwards() {
    service.publish(EMAIL, VSUM_ID);
    VsumVersion version = savedVersion();
    FileStorage copy =
        copyWithId(version, version.getContent().project().getViews().get(0).getFileStorageId());

    viewFile.getData()[0] = 'X';

    assertThat(copy.getData()).isEqualTo("view".getBytes(StandardCharsets.UTF_8));
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
        .thenReturn(Optional.of(pending(VsumBuildStatus.RUNNING, Instant.now(), Instant.now())));

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
  void publish_isRejected_whileTheEarlierPublishIsStillQueued_howeverLongItWaits() {
    VsumVersion queued =
        pending(VsumBuildStatus.QUEUED, Instant.now().minus(1, ChronoUnit.DAYS), null);
    when(vsumVersionRepository.findFirstByVsumAndNumberIsNull(vsum))
        .thenReturn(Optional.of(queued));

    assertThatThrownBy(() -> service.publish(EMAIL, VSUM_ID))
        .isInstanceOf(PublishInProgressException.class);

    verify(vsumBuildService, never()).fail(anyLong(), anyString());
  }

  @Test
  void publish_isRejected_whileTheEarlierBuildRunsWithinTheSetupServiceTimeout() {
    Instant startedAt = Instant.now().minusSeconds(TIMEOUT_SECONDS);
    VsumVersion running = pending(VsumBuildStatus.RUNNING, startedAt, startedAt);
    when(vsumVersionRepository.findFirstByVsumAndNumberIsNull(vsum))
        .thenReturn(Optional.of(running));

    assertThatThrownBy(() -> service.publish(EMAIL, VSUM_ID))
        .isInstanceOf(PublishInProgressException.class);

    verify(vsumBuildService, never()).fail(anyLong(), anyString());
  }

  @Test
  void publish_abandonsAPublishWhoseBuildRanLongerThanTheSetupServiceTimeout() {
    Instant startedAt =
        Instant.now()
            .minusSeconds(TIMEOUT_SECONDS)
            .minus(VsumVersionService.ABANDON_MARGIN)
            .minus(1, ChronoUnit.MINUTES);
    VsumVersion stuck = pending(VsumBuildStatus.RUNNING, startedAt, startedAt);
    when(vsumVersionRepository.findFirstByVsumAndNumberIsNull(vsum)).thenReturn(Optional.of(stuck));

    service.publish(EMAIL, VSUM_ID);

    InOrder order = inOrder(vsumBuildService, vsumVersionRepository);
    order.verify(vsumBuildService).fail(PENDING_BUILD_ID, VsumVersionService.STUCK_ERROR);
    order.verify(vsumVersionRepository).flush();
    order.verify(vsumVersionRepository).save(any(VsumVersion.class));
    verify(vsumBuildService).submit(BUILD_ID);
  }

  @Test
  void publish_abandonsAPendingVersionWhoseBuildAlreadyFinished() {
    VsumVersion leftOver = pending(VsumBuildStatus.FAILED, Instant.now(), Instant.now());
    when(vsumVersionRepository.findFirstByVsumAndNumberIsNull(vsum))
        .thenReturn(Optional.of(leftOver));

    service.publish(EMAIL, VSUM_ID);

    verify(vsumBuildService).fail(PENDING_BUILD_ID, VsumVersionService.STUCK_ERROR);
    verify(vsumBuildService).submit(BUILD_ID);
  }

  @Test
  void publish_leavesTheEarlierPublishAlone_whenTheNewSnapshotCannotBeTaken() {
    Instant longAgo = Instant.now().minus(1, ChronoUnit.DAYS);
    lenient()
        .when(vsumVersionRepository.findFirstByVsumAndNumberIsNull(vsum))
        .thenReturn(Optional.of(pending(VsumBuildStatus.RUNNING, longAgo, longAgo)));
    when(inputCollector.collect(vsum)).thenThrow(new NotFoundException("metamodels"));

    assertThatThrownBy(() -> service.publish(EMAIL, VSUM_ID)).isInstanceOf(NotFoundException.class);

    verify(vsumBuildService, never()).fail(anyLong(), anyString());
    verify(vsumBuildService, never()).submit(anyLong());
  }

  @Test
  void findLatest_returnsTheNewestStableVersion() {
    member.setRole(VsumRole.VIEWER);
    VsumVersion latest = pending(VsumBuildStatus.SUCCEEDED, Instant.now(), Instant.now());
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

  @Test
  void findLatestContent_returnsTheContentOfTheNewestVersion_withTheCallersRole() {
    member.setRole(VsumRole.VIEWER);
    VsumVersion latest = publishedVersionWithContent(ocl.getId());

    VsumVersionContentResponse response = service.findLatestContent(EMAIL, VSUM_ID);

    assertThat(response.getNumber()).isEqualTo(3);
    assertThat(response.getCreatedBy()).isEqualTo(EMAIL);
    assertThat(response.getCreatedAt()).isEqualTo(latest.getCreatedAt());
    assertThat(response.getProject().getName()).isEqualTo("p");
    assertThat(response.getProject().getRole()).isEqualTo(VsumRole.VIEWER);
    assertThat(response.getRuleSets())
        .singleElement()
        .satisfies(
            rs -> {
              assertThat(rs.id()).isEqualTo(20L);
              assertThat(rs.vsumId()).isEqualTo(VSUM_ID);
              assertThat(rs.name()).isEqualTo("rules");
              assertThat(rs.color()).isEqualTo("#123456");
              assertThat(rs.description()).isEqualTo("d");
              assertThat(rs.oclContent()).isEqualTo("context A inv: true");
            });
  }

  @Test
  void findLatestContent_returnsEmptyOcl_forARuleSetWithoutFile() {
    publishedVersionWithContent(null);

    VsumVersionContentResponse response = service.findLatestContent(EMAIL, VSUM_ID);

    assertThat(response.getRuleSets()).singleElement().satisfies(rs -> assertThat(rs.oclContent()).isEmpty());
  }

  @Test
  void findLatestContent_throwsNotFound_whenNothingWasPublished() {
    assertThatThrownBy(() -> service.findLatestContent(EMAIL, VSUM_ID))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void findLatestContent_throwsNotFound_whenTheVersionHasNoContent() {
    VsumVersion latest = pending(VsumBuildStatus.SUCCEEDED, Instant.now(), Instant.now());
    latest.setNumber(1);
    when(vsumVersionRepository.findFirstByVsumAndNumberIsNotNullOrderByNumberDesc(vsum))
        .thenReturn(Optional.of(latest));

    assertThatThrownBy(() -> service.findLatestContent(EMAIL, VSUM_ID))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void findLatestContent_isDeniedForUsersWithoutAccess() {
    assertThatThrownBy(() -> service.findLatestContent("stranger@example.com", VSUM_ID))
        .isInstanceOf(AccessDeniedException.class);
  }

  // ---- fixtures -------------------------------------------------------------------------------

  /**
   * Version 3, published with content: the project "p" and the rule set "rules", whose OCL file is
   * the copy {@code oclCopyId} of {@link #ocl}, or none.
   */
  private VsumVersion publishedVersionWithContent(Long oclCopyId) {
    VsumVersion latest = pending(VsumBuildStatus.SUCCEEDED, Instant.now(), Instant.now());
    latest.setNumber(3);
    latest.setContent(
        new VsumVersionContent(
            VsumMetaModelResponse.builder().name("p").build(),
            List.of(
                new VsumVersionContent.RuleSet(
                    20L, "rules", "#123456", "d", oclCopyId, Instant.EPOCH, Instant.EPOCH))));
    latest.setContentFiles(List.of(ocl));
    when(vsumVersionRepository.findFirstByVsumAndNumberIsNotNullOrderByNumberDesc(vsum))
        .thenReturn(Optional.of(latest));
    return latest;
  }

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

  /** A version created at {@code createdAt} whose build started at {@code startedAt}, if at all. */
  private VsumVersion pending(VsumBuildStatus buildStatus, Instant createdAt, Instant startedAt) {
    VsumBuild build =
        VsumBuild.builder()
            .id(PENDING_BUILD_ID)
            .startedAt(startedAt)
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

  /** The project as the canvas shows it; the reaction file ids may be {@code null}. */
  private static VsumMetaModelResponse details(Long relationReactionId, Long fineGrainedReactionId) {
    Set<FineGranularMetaModelRelationResponse> fineGrained =
        fineGrainedReactionId == null
            ? Set.of()
            : Set.of(
                FineGranularMetaModelRelationResponse.builder()
                    .sourceId("A")
                    .targetId("B")
                    .reactionFileStorageId(fineGrainedReactionId)
                    .build());
    return VsumMetaModelResponse.builder()
        .name("p")
        .layout(LAYOUT)
        .metaModels(
            List.of(
                MetaModelResponse.builder()
                    .id(10L)
                    .name("mm")
                    .ecoreFileId(1L)
                    .genModelFileId(5L)
                    .build()))
        .metaModelsRelation(
            List.of(
                MetaModelRelationResponse.builder()
                    .sourceId(10L)
                    .targetId(11L)
                    .reactionFileStorageId(relationReactionId)
                    .fineGranularMetaModelRelationSet(fineGrained)
                    .build()))
        .views(
            List.of(
                ViewsResponse.builder()
                    .name("v")
                    .fileStorageId(4L)
                    .assignedModels(
                        List.of(
                            MetaModelResponse.builder()
                                .id(10L)
                                .name("mm")
                                .ecoreFileId(1L)
                                .genModelFileId(5L)
                                .build()))
                    .build()))
        .build();
  }

  private static FileStorage copyWithId(VsumVersion version, Long id) {
    return version.getContentFiles().stream()
        .filter(copy -> id.equals(copy.getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No content copy with id " + id));
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
