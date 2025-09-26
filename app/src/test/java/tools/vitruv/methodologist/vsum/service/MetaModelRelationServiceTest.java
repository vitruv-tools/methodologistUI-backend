package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
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
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest;
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

    MetaModelRelationRequest req = new MetaModelRelationRequest(100L, 200L, 300L);

    when(vsumMetaModelRepository.findAllByVsumAndMetaModel_source_idIn(eq(vsum), anySet()))
        .thenReturn(List.of(vmm1, vmm2));
    when(fileStorageRepository.findAllByIdInAndType(anySet(), eq(FileEnumType.REACTION)))
        .thenReturn(List.of(file));

    service.create(vsum, List.of(req));

    ArgumentCaptor<List<MetaModelRelation>> captor = ArgumentCaptor.forClass(List.class);
    verify(metaModelRelationRepository).saveAll(captor.capture());

    List<MetaModelRelation> saved = captor.getValue();

    assertThat(1).isEqualTo(saved.size());
    MetaModelRelation rel = saved.get(0);
    assertThat(sourceMM).isEqualTo(rel.getSource());
    assertThat(targetMM).isEqualTo(rel.getTarget());
    assertThat(file).isEqualTo(rel.getReactionFileStorage());
  }

  @Test
  void create_throwsNotFound_whenMetaModelMissing() {
    MetaModelRelationRequest req = new MetaModelRelationRequest(111L, 222L, 333L);

    when(vsumMetaModelRepository.findAllByVsumAndMetaModel_source_idIn(eq(vsum), anySet()))
        .thenReturn(List.of());

    assertThrows(NotFoundException.class, () -> service.create(vsum, List.of(req)));
  }

  @Test
  void create_throwsNotFound_whenReactionFileMissing() {
    MetaModel mm = MetaModel.builder().id(10L).source(MetaModel.builder().id(111L).build()).build();
    VsumMetaModel vmm = new VsumMetaModel(null, vsum, mm, null, null, null);

    MetaModelRelationRequest req = new MetaModelRelationRequest(111L, 222L, 333L);

    when(vsumMetaModelRepository.findAllByVsumAndMetaModel_source_idIn(eq(vsum), anySet()))
        .thenReturn(List.of(vmm));
    when(fileStorageRepository.findAllByIdInAndType(anySet(), eq(FileEnumType.REACTION)))
        .thenReturn(List.of());

    assertThrows(NotFoundException.class, () -> service.create(vsum, List.of(req)));

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

    MetaModelRelationRequest r1 = new MetaModelRelationRequest(100L, 200L, 300L);
    MetaModelRelationRequest r2 = new MetaModelRelationRequest(100L, 200L, 300L);

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
}
