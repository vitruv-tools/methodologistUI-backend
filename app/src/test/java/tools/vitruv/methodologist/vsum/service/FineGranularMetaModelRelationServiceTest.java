package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Error.FINE_GRANULAR_RELATION_UPDATE_NOT_ALLOWED_ERROR;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.exception.MetaModelRelationCreationException;
import tools.vitruv.methodologist.exception.NoTemplateProvidedException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.MemoizedSupplier;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.FineGranularMetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.CreateCorrespondingRootOnInsertRootRequest;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.service.LowCodeReactionService;
import tools.vitruv.methodologist.vsum.mapper.LowCodeReactionRequestMapper;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.repository.FineGranularMetaModelRelationRepository;

@ExtendWith(MockitoExtension.class)
class FineGranularMetaModelRelationServiceTest {

  @Mock FineGranularMetaModelRelationRepository fineGranularMetaModelRelationRepository;

  @Mock FileStorageRepository fileStorageRepository;

  @Mock LowCodeReactionService lowCodeReactionService;

  @Mock LowCodeReactionRequestMapper lowCodeReactionRequestMapper;

  FineGranularMetaModelRelationService service;

  @BeforeEach
  void setup() {
    service =
        spy(
            new FineGranularMetaModelRelationService(
                fineGranularMetaModelRelationRepository,
                fileStorageRepository,
                lowCodeReactionService,
                lowCodeReactionRequestMapper));
  }

  @Test
  void update_syncsFineGranularCreateUpdateDelete_andHistoryOnce() {
    String callerEmail = "u@ex.com";
    AtomicInteger historyCalls = new AtomicInteger();
    MemoizedSupplier<Boolean> historySupplier =
        new MemoizedSupplier<>(
            () -> {
              historyCalls.incrementAndGet();
              return true;
            });

    MetaModelRelation relationA = relation(100L, 200L);
    MetaModelRelation relationB = relation(300L, 400L);

    FineGranularMetaModelRelation existingKeep = fgRelation(1L, "A", "B", 11L, relationA);
    FineGranularMetaModelRelation existingUpdate = fgRelation(3L, "OLD_X", "OLD_Y", 13L, relationA);
    FineGranularMetaModelRelation existingRemove = fgRelation(2L, "C", "D", 12L, relationB);

    relationA.getFineGranularMetaModelRelationSet().add(existingKeep);
    relationA.getFineGranularMetaModelRelationSet().add(existingUpdate);
    relationB.getFineGranularMetaModelRelationSet().add(existingRemove);

    FineGranularMetaModelRelationRequest keepReq =
        new FineGranularMetaModelRelationRequest(1L, "A", "B", 11L, null);
    FineGranularMetaModelRelationRequest updateReq =
        new FineGranularMetaModelRelationRequest(3L, "NEW_X", "NEW_Y", 13L, null);
    FineGranularMetaModelRelationRequest createReq =
        new FineGranularMetaModelRelationRequest(null, "N", "M", 14L, null);

    MetaModelRelationRequest requestA =
        new MetaModelRelationRequest(null, 100L, 200L, null, new HashSet<>());
    requestA.getFineGranularMetaModelRelationSet().add(keepReq);
    requestA.getFineGranularMetaModelRelationSet().add(updateReq);
    requestA.getFineGranularMetaModelRelationSet().add(createReq);

    MetaModelRelationRequest requestB =
        new MetaModelRelationRequest(null, 300L, 400L, null, new HashSet<>());

    Map<MetaModelRelationRequest, MetaModelRelation> requestToRelation = new HashMap<>();
    requestToRelation.put(requestA, relationA);
    requestToRelation.put(requestB, relationB);

    doNothing().when(service).delete(anyList());
    doNothing().when(service).create(eq(callerEmail), anyMap());
    doNothing().when(service).update(eq(callerEmail), anyMap());

    service.update(callerEmail, requestToRelation, historySupplier);

    verify(service).create(eq(callerEmail), eq(Map.of(createReq, relationA)));
    verify(service).update(eq(callerEmail), eq(Map.of(updateReq, relationA)));
    verify(service, times(1)).delete(eq(java.util.List.of(existingRemove)));
    assertThat(historyCalls.get()).isEqualTo(1);
  }

  @Test
  void update_noFineGranularDelta_noHistory_andNoCrudCalls() {
    String callerEmail = "u@ex.com";
    AtomicInteger historyCalls = new AtomicInteger();
    MemoizedSupplier<Boolean> historySupplier =
        new MemoizedSupplier<>(
            () -> {
              historyCalls.incrementAndGet();
              return true;
            });

    MetaModelRelation relation = relation(10L, 20L);
    FineGranularMetaModelRelation existing = fgRelation(1L, "A", "B", 11L, relation);
    relation.getFineGranularMetaModelRelationSet().add(existing);

    FineGranularMetaModelRelationRequest same =
        new FineGranularMetaModelRelationRequest(1L, "A", "B", 11L, null);
    MetaModelRelationRequest request =
        new MetaModelRelationRequest(null, 10L, 20L, null, new HashSet<>());
    request.getFineGranularMetaModelRelationSet().add(same);

    Map<MetaModelRelationRequest, MetaModelRelation> requestToRelation = new HashMap<>();
    requestToRelation.put(request, relation);

    service.update(callerEmail, requestToRelation, historySupplier);

    verify(service, never()).delete(anyList());
    verify(service, never()).create(eq(callerEmail), anyMap());
    verify(service, never()).update(eq(callerEmail), anyMap());
    assertThat(historyCalls.get()).isEqualTo(0);
    assertThat(relation.getFineGranularMetaModelRelationSet()).containsExactly(existing);
  }

  @Test
  void create_savesWithExistingFile_whenReactionFileIdPresent() {
    MetaModelRelation relation = relation(10L, 20L);
    FileStorage file = file(14L);
    FineGranularMetaModelRelationRequest request =
        new FineGranularMetaModelRelationRequest(null, "Component", "Class", 14L, null);
    when(fileStorageRepository.findById(14L)).thenReturn(Optional.of(file));

    service.create("u@ex.com", Map.of(request, relation));

    verify(lowCodeReactionService, never()).generateAndSaveReaction(any(), any(), any());
    verify(fineGranularMetaModelRelationRepository).saveAll(anyList());
    assertThat(relation.getFineGranularMetaModelRelationSet())
        .singleElement()
        .extracting(FineGranularMetaModelRelation::getReactionFileStorage)
        .isEqualTo(file);
  }

  @Test
  void create_generatesReaction_whenTemplateProvidedWithoutFile() {
    MetaModelRelation relation = relation(10L, 20L);
    FileStorage generated = file(99L);
    CreateCorrespondingRootOnInsertRootRequest template = templateRequest(false);
    FineGranularMetaModelRelationRequest request =
        new FineGranularMetaModelRelationRequest(null, "Component", "Class", null, template);
    when(lowCodeReactionService.generateAndSaveReaction(eq("u@ex.com"), eq(template), isNull()))
        .thenReturn(generated);

    service.create("u@ex.com", Map.of(request, relation));

    verify(lowCodeReactionService).generateAndSaveReaction("u@ex.com", template, null);
    assertThat(template.isRegenerate()).isFalse();
    FineGranularMetaModelRelation saved =
        relation.getFineGranularMetaModelRelationSet().iterator().next();
    assertThat(saved.getReactionFileStorage()).isEqualTo(generated);
    assertThat(saved.getLowCodeReactionTemplate())
        .isEqualTo("create_corresponding_root_on_insert_root");
    assertThat(saved.getLowCodeReactionTemplateParams()).containsEntry("model1Alias", "pcm");
  }

  @Test
  void create_throwsNoTemplate_whenFileAndTemplateMissing() {
    MetaModelRelation relation = relation(10L, 20L);
    FineGranularMetaModelRelationRequest request =
        new FineGranularMetaModelRelationRequest(null, "Component", "Class", null, null);

    assertThatThrownBy(() -> service.create("u@ex.com", Map.of(request, relation)))
        .isInstanceOf(NoTemplateProvidedException.class);
    verify(fineGranularMetaModelRelationRepository, never()).saveAll(anyList());
  }

  @Test
  void create_throws_whenRequestHasId() {
    MetaModelRelation relation = relation(10L, 20L);
    FineGranularMetaModelRelationRequest request =
        new FineGranularMetaModelRelationRequest(1L, "A", "B", 11L, null);

    assertThatThrownBy(() -> service.create("u@ex.com", Map.of(request, relation)))
        .isInstanceOf(MetaModelRelationCreationException.class)
        .hasMessageContaining(FINE_GRANULAR_RELATION_UPDATE_NOT_ALLOWED_ERROR);
  }

  @Test
  void update_regeneratesFile_whenRegenerateTrue() {
    MetaModelRelation relation = relation(10L, 20L);
    FineGranularMetaModelRelation existing = fgRelation(3L, "A", "B", 13L, relation);
    relation.getFineGranularMetaModelRelationSet().add(existing);
    FileStorage existingFile = existing.getReactionFileStorage();
    FileStorage regenerated = file(13L);
    CreateCorrespondingRootOnInsertRootRequest template = templateRequest(true);
    FineGranularMetaModelRelationRequest request =
        new FineGranularMetaModelRelationRequest(3L, "A", "B", 13L, template);
    when(fileStorageRepository.findById(13L)).thenReturn(Optional.of(existingFile));
    when(lowCodeReactionService.generateAndSaveReaction("u@ex.com", template, existingFile))
        .thenReturn(regenerated);

    service.update("u@ex.com", Map.of(request, relation));

    verify(lowCodeReactionService).generateAndSaveReaction("u@ex.com", template, existingFile);
    assertThat(template.isRegenerate()).isFalse();
    assertThat(relation.getFineGranularMetaModelRelationSet())
        .singleElement()
        .extracting(FineGranularMetaModelRelation::getReactionFileStorage)
        .isEqualTo(regenerated);
  }

  @Test
  void update_keepsExistingFile_whenRegenerateFalse() {
    MetaModelRelation relation = relation(10L, 20L);
    FineGranularMetaModelRelation existing = fgRelation(3L, "A", "B", 13L, relation);
    relation.getFineGranularMetaModelRelationSet().add(existing);
    FileStorage existingFile = existing.getReactionFileStorage();
    FineGranularMetaModelRelationRequest request =
        new FineGranularMetaModelRelationRequest(3L, "A", "B", 13L, null);
    when(fileStorageRepository.findById(13L)).thenReturn(Optional.of(existingFile));

    service.update("u@ex.com", Map.of(request, relation));

    verify(lowCodeReactionService, never()).generateAndSaveReaction(any(), any(), any());
    assertThat(relation.getFineGranularMetaModelRelationSet())
        .singleElement()
        .extracting(FineGranularMetaModelRelation::getReactionFileStorage)
        .isEqualTo(existingFile);
  }

  @Test
  void update_throwsNotFound_whenIdMissingOnRelation() {
    MetaModelRelation relation = relation(10L, 20L);
    FineGranularMetaModelRelationRequest request =
        new FineGranularMetaModelRelationRequest(99L, "A", "B", 13L, null);

    assertThatThrownBy(() -> service.update("u@ex.com", Map.of(request, relation)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void create_doesNothing_whenRequestMapEmpty() {
    service.create("u@ex.com", Map.of());
    verify(fineGranularMetaModelRelationRepository, never()).saveAll(anyList());
  }

  private CreateCorrespondingRootOnInsertRootRequest templateRequest(boolean regenerate) {
    CreateCorrespondingRootOnInsertRootRequest request =
        new CreateCorrespondingRootOnInsertRootRequest();
    request.setRegenerate(regenerate);
    request.setModel1Uri("http://pcm");
    request.setModel2Uri("http://uml");
    request.setModel1Alias("pcm");
    request.setModel2Alias("uml");
    request.setReactionName("createCorrespondingRoot");
    request.setModel1RootType("Component");
    request.setModel2RootType("Class");
    request.setModel1RootVar("component");
    return request;
  }

  private FileStorage file(Long id) {
    FileStorage fileStorage = new FileStorage();
    fileStorage.setId(id);
    return fileStorage;
  }

  private MetaModelRelation relation(long sourceSourceId, long targetSourceId) {
    MetaModel source =
        MetaModel.builder()
            .id(sourceSourceId)
            .source(MetaModel.builder().id(sourceSourceId).build())
            .build();
    MetaModel target =
        MetaModel.builder()
            .id(targetSourceId)
            .source(MetaModel.builder().id(targetSourceId).build())
            .build();
    return MetaModelRelation.builder()
        .source(source)
        .target(target)
        .fineGranularMetaModelRelationSet(new HashSet<>())
        .build();
  }

  private FineGranularMetaModelRelation fgRelation(
      Long id, String sourceId, String targetId, Long fileId, MetaModelRelation relation) {
    FileStorage fileStorage = new FileStorage();
    fileStorage.setId(fileId);

    return FineGranularMetaModelRelation.builder()
        .id(id)
        .sourceId(sourceId)
        .targetId(targetId)
        .reactionFileStorage(fileStorage)
        .metaModelRelation(relation)
        .build();
  }
}
