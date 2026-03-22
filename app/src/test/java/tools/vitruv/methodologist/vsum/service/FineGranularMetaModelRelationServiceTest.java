package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.general.MemoizedSupplier;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.FineGranularMetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest;
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
  void update_syncsFineGranularCreateUpdateDelete_andHistoryOnce() throws Exception {
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

    FineGranularMetaModelRelation existingKeep =
        fgRelation(1L, "A", "B", 11L, relationA);
    FineGranularMetaModelRelation existingUpdate =
        fgRelation(3L, "OLD_X", "OLD_Y", 13L, relationA);
    FineGranularMetaModelRelation existingRemove =
        fgRelation(2L, "C", "D", 12L, relationB);

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

    verify(service).delete(anyList());
    verify(service)
        .create(
            eq(callerEmail),
            eq(Map.of(createReq, relationA)));
    verify(service)
        .update(
            eq(callerEmail),
            eq(Map.of(updateReq, relationA)));
    verify(service, times(1)).delete(eq(java.util.List.of(existingRemove)));
    assertThat(historyCalls.get()).isEqualTo(1);
  }

  @Test
  void update_noFineGranularDelta_noHistory_andNoCrudCalls() throws Exception {
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

  private MetaModelRelation relation(long sourceSourceId, long targetSourceId) {
    MetaModel source = MetaModel.builder().id(sourceSourceId).source(MetaModel.builder().id(sourceSourceId).build()).build();
    MetaModel target = MetaModel.builder().id(targetSourceId).source(MetaModel.builder().id(targetSourceId).build()).build();
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



