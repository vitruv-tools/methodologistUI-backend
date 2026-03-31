package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.MemoizedSupplier;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.mapper.LowCodeReactionRequestMapper;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRelationRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;

@ExtendWith(MockitoExtension.class)
class MetaModelRelationServiceTest {

  @Mock MetaModelRelationRepository metaModelRelationRepository;

  @Mock FileStorageRepository fileStorageRepository;

  @Mock VsumMetaModelRepository vsumMetaModelRepository;

  @Mock FineGranularMetaModelRelationService fineGranularMetaModelRelationService;

  @Mock LowCodeReactionRequestMapper lowCodeReactionRequestMapper;

  @InjectMocks MetaModelRelationService service;

  Vsum vsum;

  @BeforeEach
  void setup() {
    vsum = new Vsum();
    vsum.setId(1L);
  }

  @Test
  void create_savesRelations_whenAllValid() {
    MetaModel sourceMM =
        MetaModel.builder().id(10L).source(MetaModel.builder().id(100L).build()).build();
    MetaModel targetMM =
        MetaModel.builder().id(20L).source(MetaModel.builder().id(200L).build()).build();

    VsumMetaModel vmm1 = new VsumMetaModel(null, vsum, sourceMM, null, null, null);
    VsumMetaModel vmm2 = new VsumMetaModel(null, vsum, targetMM, null, null, null);

    FileStorage file = FileStorage.builder().id(300L).type(FileEnumType.REACTION).build();

    MetaModelRelationRequest req = new MetaModelRelationRequest(100L, 200L, 300L, new HashSet<>());

    when(vsumMetaModelRepository.findAllByVsumAndMetaModel_source_idIn(eq(vsum), anySet()))
        .thenReturn(List.of(vmm1, vmm2));
    when(fileStorageRepository.findAllByIdInAndType(anySet(), eq(FileEnumType.REACTION)))
        .thenReturn(List.of(file));

    service.create(vsum, List.of(req));

    ArgumentCaptor<List<MetaModelRelation>> captor = ArgumentCaptor.forClass(List.class);
    verify(metaModelRelationRepository).saveAll(captor.capture());

    List<MetaModelRelation> saved = captor.getValue();

    assertThat(saved).hasSize(1);
    MetaModelRelation rel = saved.get(0);
    assertThat(sourceMM).isEqualTo(rel.getSource());
    assertThat(targetMM).isEqualTo(rel.getTarget());
    assertThat(file).isEqualTo(rel.getReactionFileStorage());
  }

  @Test
  void create_throwsNotFound_whenMetaModelMissing() {
    MetaModelRelationRequest req = new MetaModelRelationRequest(111L, 222L, 333L, new HashSet<>());

    when(vsumMetaModelRepository.findAllByVsumAndMetaModel_source_idIn(eq(vsum), anySet()))
        .thenReturn(List.of());

    List<MetaModelRelationRequest> requests = List.of(req);

    assertThrows(NotFoundException.class, () -> service.create(vsum, requests));
  }

  @Test
  void create_throwsNotFound_whenReactionFileMissing() {
    MetaModel mm = MetaModel.builder().id(10L).source(MetaModel.builder().id(111L).build()).build();
    VsumMetaModel vmm = new VsumMetaModel(null, vsum, mm, null, null, null);

    MetaModelRelationRequest req = new MetaModelRelationRequest(111L, 222L, 333L, new HashSet<>());

    when(vsumMetaModelRepository.findAllByVsumAndMetaModel_source_idIn(eq(vsum), anySet()))
        .thenReturn(List.of(vmm));
    when(fileStorageRepository.findAllByIdInAndType(anySet(), eq(FileEnumType.REACTION)))
        .thenReturn(List.of());

    List<MetaModelRelationRequest> requests = List.of(req);

    assertThrows(NotFoundException.class, () -> service.create(vsum, requests));
    verifyNoInteractions(metaModelRelationRepository);
  }

  @Test
  void create_skipsDuplicates() {
    MetaModel src =
        MetaModel.builder().id(10L).source(MetaModel.builder().id(100L).build()).build();
    MetaModel tgt =
        MetaModel.builder().id(20L).source(MetaModel.builder().id(200L).build()).build();
    VsumMetaModel vmm1 = new VsumMetaModel(null, vsum, src, null, null, null);
    VsumMetaModel vmm2 = new VsumMetaModel(null, vsum, tgt, null, null, null);
    FileStorage file = FileStorage.builder().id(300L).type(FileEnumType.REACTION).build();

    MetaModelRelationRequest r1 = new MetaModelRelationRequest(100L, 200L, 300L, new HashSet<>());
    MetaModelRelationRequest r2 = new MetaModelRelationRequest(100L, 200L, 300L, new HashSet<>());

    when(vsumMetaModelRepository.findAllByVsumAndMetaModel_source_idIn(eq(vsum), anySet()))
        .thenReturn(List.of(vmm1, vmm2));
    when(fileStorageRepository.findAllByIdInAndType(anySet(), eq(FileEnumType.REACTION)))
        .thenReturn(List.of(file));

    service.create(vsum, List.of(r1, r2));

    verify(metaModelRelationRepository)
        .saveAll(
            argThat(
                iter -> {
                  List<MetaModelRelation> list =
                      StreamSupport.stream(iter.spliterator(), false).toList();
                  return list.size() == 1;
                }));
  }

  @Test
  void create_doesNothing_whenEmptyInput() {
    service.create(vsum, List.of());
    verifyNoInteractions(
        vsumMetaModelRepository, fileStorageRepository, metaModelRelationRepository);
  }

  @Test
  void delete_deletesAll() {
    List<MetaModelRelation> rels = List.of(new MetaModelRelation(), new MetaModelRelation());
    service.delete(rels);
    verify(metaModelRelationRepository).deleteAll(rels);
  }

  @Test
  void update_syncsCreateAndDelete_andCallsHistoryOnce() throws Exception {
    String callerEmail = "u@ex.com";
    AtomicInteger historyCalls = new AtomicInteger();
    MemoizedSupplier<Boolean> historySupplier =
        new MemoizedSupplier<>(
            () -> {
              historyCalls.incrementAndGet();
              return true;
            });

    MetaModel oldSource =
        MetaModel.builder().id(10L).source(MetaModel.builder().id(100L).build()).build();
    MetaModel oldTarget =
        MetaModel.builder().id(20L).source(MetaModel.builder().id(200L).build()).build();
    MetaModelRelation toDelete =
        MetaModelRelation.builder().source(oldSource).target(oldTarget).build();

    MetaModel newSource =
        MetaModel.builder().id(30L).source(MetaModel.builder().id(300L).build()).build();
    MetaModel newTarget =
        MetaModel.builder().id(40L).source(MetaModel.builder().id(400L).build()).build();
    VsumMetaModel vmmSource = new VsumMetaModel(null, vsum, newSource, null, null, null);
    VsumMetaModel vmmTarget = new VsumMetaModel(null, vsum, newTarget, null, null, null);
    FileStorage reaction = FileStorage.builder().id(900L).type(FileEnumType.REACTION).build();

    MetaModelRelationRequest createReq =
        new MetaModelRelationRequest(300L, 400L, 900L, new HashSet<>());

    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of(toDelete));
    when(vsumMetaModelRepository.findAllByVsumAndMetaModel_source_idIn(eq(vsum), anySet()))
        .thenReturn(List.of(vmmSource, vmmTarget));
    when(fileStorageRepository.findAllByIdInAndType(anySet(), eq(FileEnumType.REACTION)))
        .thenReturn(List.of(reaction));

    Map<MetaModelRelationRequest, MetaModelRelation> result =
        service.update(callerEmail, vsum, List.of(createReq), historySupplier);

    verify(metaModelRelationRepository)
        .deleteAll(
            argThat(
                rels -> {
                  List<? extends MetaModelRelation> list =
                      StreamSupport.stream(rels.spliterator(), false).toList();
                  return list.size() == 1 && list.contains(toDelete);
                }));
    verify(metaModelRelationRepository)
        .saveAll(
            argThat(
                rels -> {
                  List<? extends MetaModelRelation> list =
                      StreamSupport.stream(rels.spliterator(), false).toList();
                  if (list.size() != 1) {
                    return false;
                  }
                  var relation = list.get(0);
                  return relation.getSource().equals(newSource)
                      && relation.getTarget().equals(newTarget)
                      && relation.getReactionFileStorage().equals(reaction);
                }));
    verify(fineGranularMetaModelRelationService)
        .update(
            eq(callerEmail),
            argThat(map -> map.size() == 1 && map.containsKey(createReq)),
            eq(historySupplier));
    assertThat(historyCalls.get()).isEqualTo(1);
    assertThat(result).hasSize(1);
    assertThat(result).containsKey(createReq);
  }

  @Test
  void update_noDelta_noHistory_andOnlyFineGranularDelegation() throws Exception {
    String callerEmail = "u@ex.com";
    AtomicInteger historyCalls = new AtomicInteger();
    MemoizedSupplier<Boolean> historySupplier =
        new MemoizedSupplier<>(
            () -> {
              historyCalls.incrementAndGet();
              return true;
            });

    FileStorage reaction = FileStorage.builder().id(901L).type(FileEnumType.REACTION).build();
    MetaModel source =
        MetaModel.builder().id(10L).source(MetaModel.builder().id(100L).build()).build();
    MetaModel target =
        MetaModel.builder().id(20L).source(MetaModel.builder().id(200L).build()).build();
    MetaModelRelation existing =
        MetaModelRelation.builder()
            .id(50L)
            .vsum(vsum)
            .source(source)
            .target(target)
            .reactionFileStorage(reaction)
            .fineGranularMetaModelRelationSet(new HashSet<>())
            .build();

    MetaModelRelationRequest sameReq =
        new MetaModelRelationRequest(100L, 200L, 901L, new HashSet<>());

    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of(existing));

    Map<MetaModelRelationRequest, MetaModelRelation> result =
        service.update(callerEmail, vsum, List.of(sameReq), historySupplier);

    verify(metaModelRelationRepository, never()).deleteAll(any(Iterable.class));
    verify(metaModelRelationRepository, never()).saveAll(any(Iterable.class));
    verifyNoInteractions(vsumMetaModelRepository, fileStorageRepository);
    verify(fineGranularMetaModelRelationService)
        .update(eq(callerEmail), argThat(Map::isEmpty), eq(historySupplier));
    assertThat(historyCalls.get()).isEqualTo(0);
    assertThat(result).isEmpty();
  }
}
