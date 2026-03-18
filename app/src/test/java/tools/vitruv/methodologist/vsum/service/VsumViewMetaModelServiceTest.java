package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.VsumView;
import tools.vitruv.methodologist.vsum.model.VsumViewMetaModel;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewMetaModelRepository;

@ExtendWith(MockitoExtension.class)
class VsumViewMetaModelServiceTest {

  @Mock private VsumViewMetaModelRepository vsumViewMetaModelRepository;
  @Mock private MetaModelRepository metaModelRepository;
  @Mock private VsumMetaModelRepository vsumMetaModelRepository;

  @InjectMocks private VsumViewMetaModelService service;

  private Vsum vsum;
  private VsumView vsumView;

  @BeforeEach
  void setUp() {
    vsum = new Vsum();
    vsum.setId(1L);

    vsumView = new VsumView();
    vsumView.setId(10L);
    vsumView.setVsum(vsum);
  }

  @Test
  void create_mapsVsumMetaModelsToViewMetaModels_andSavesAll() {
    MetaModel metaModelA = new MetaModel();
    metaModelA.setId(100L);
    MetaModel sourceA = new MetaModel();
    sourceA.setId(1000L);
    metaModelA.setSource(sourceA);

    MetaModel metaModelB = new MetaModel();
    metaModelB.setId(200L);
    MetaModel sourceB = new MetaModel();
    sourceB.setId(2000L);
    metaModelB.setSource(sourceB);

    VsumMetaModel linkA = VsumMetaModel.builder().vsum(vsum).metaModel(metaModelA).build();
    VsumMetaModel linkB = VsumMetaModel.builder().vsum(vsum).metaModel(metaModelB).build();

    when(vsumMetaModelRepository.findAllByVsumAndMetaModel_source_idIn(vsum, Set.of(1000L, 2000L)))
        .thenReturn(List.of(linkA, linkB));

    VsumViewMetaModel savedA =
        VsumViewMetaModel.builder().id(1L).vsumView(vsumView).metaModel(metaModelA).build();
    VsumViewMetaModel savedB =
        VsumViewMetaModel.builder().id(2L).vsumView(vsumView).metaModel(metaModelB).build();

    when(vsumViewMetaModelRepository.saveAll(argThat(entities -> true)))
        .thenReturn(List.of(savedA, savedB));

    List<VsumViewMetaModel> result = service.create(vsumView, Set.of(1000L, 2000L));

    verify(vsumViewMetaModelRepository)
        .saveAll(
            argThat(
                entities -> {
                  if (!(entities instanceof List<?> list) || list.size() != 2) {
                    return false;
                  }
                  if (!(list.get(0) instanceof VsumViewMetaModel first)
                      || !(list.get(1) instanceof VsumViewMetaModel second)) {
                    return false;
                  }

                  return first.getVsumView() == vsumView
                      && second.getVsumView() == vsumView
                      && Set.of(first.getMetaModel().getId(), second.getMetaModel().getId())
                          .containsAll(Set.of(100L, 200L));
                }));

    assertThat(result).containsExactly(savedA, savedB);
    verifyNoInteractions(metaModelRepository);
  }

  @Test
  void create_whenNoMatchingMetaModels_savesEmptyList_andReturnsEmpty() {
    when(vsumMetaModelRepository.findAllByVsumAndMetaModel_source_idIn(vsum, Set.of(999L)))
        .thenReturn(List.of());
    when(vsumViewMetaModelRepository.saveAll(argThat(entities -> true))).thenReturn(List.of());

    List<VsumViewMetaModel> result = service.create(vsumView, Set.of(999L));

    verify(vsumViewMetaModelRepository)
        .saveAll(
            argThat(
                entities -> {
                  if (entities instanceof List<?> list) {
                    return list.isEmpty();
                  }
                  return false;
                }));
    assertThat(result).isEmpty();
    verifyNoInteractions(metaModelRepository);
  }

  @Test
  void create_whenMetaModelIdsIsNull_delegatesToRepository_andReturnsEmpty() {
    when(vsumMetaModelRepository.findAllByVsumAndMetaModel_source_idIn(vsum, null))
        .thenReturn(List.of());
    when(vsumViewMetaModelRepository.saveAll(argThat(entities -> true))).thenReturn(List.of());

    List<VsumViewMetaModel> result = service.create(vsumView, null);

    verify(vsumMetaModelRepository).findAllByVsumAndMetaModel_source_idIn(vsum, null);
    verify(vsumViewMetaModelRepository).saveAll(argThat(entities -> true));
    assertThat(result).isEmpty();
    verifyNoInteractions(metaModelRepository);
  }

  @Test
  void delete_deletesProvidedAssociations() {
    List<VsumViewMetaModel> viewMetaModels =
        List.of(
            VsumViewMetaModel.builder().id(10L).vsumView(vsumView).build(),
            VsumViewMetaModel.builder().id(20L).vsumView(vsumView).build());

    service.delete(viewMetaModels);

    verify(vsumViewMetaModelRepository).deleteAll(viewMetaModels);
  }

  @Test
  void deleteByVsumView_deletesAllAssociationsForView() {
    service.deleteByVsumView(vsumView);

    verify(vsumViewMetaModelRepository).deleteAllByVsumView(eq(vsumView));
  }
}




