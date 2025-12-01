package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;

class VsumMetaModelServiceTest {

  private VsumMetaModelRepository vsumMetaModelRepository;
  private MetaModelService metaModelService;
  private MetaModelRepository metaModelRepository;

  private VsumMetaModelService service;

  @BeforeEach
  void setUp() {
    vsumMetaModelRepository = mock(VsumMetaModelRepository.class);
    metaModelService = mock(MetaModelService.class);
    metaModelRepository = mock(MetaModelRepository.class);

    service =
        new VsumMetaModelService(vsumMetaModelRepository, metaModelService, metaModelRepository);
  }

  private Vsum newVsumWithUserEmail(String email) {
    Vsum vsum = new Vsum();
    tools.vitruv.methodologist.user.model.User user =
        new tools.vitruv.methodologist.user.model.User();
    user.setEmail(email);
    vsum.setUser(user);
    vsum.setVsumMetaModels(new HashSet<>());
    return vsum;
  }

  private MetaModel newOriginalMetaModel(Long id) {
    MetaModel m = new MetaModel();
    m.setId(id);
    m.setSource(null);
    return m;
  }

  private MetaModel newClonedMetaModel(Long id, MetaModel source) {
    MetaModel m = new MetaModel();
    m.setId(id);
    m.setSource(source);
    return m;
  }

  private VsumMetaModel vsumMetaModel(Vsum vsum, MetaModel metaModel) {
    return VsumMetaModel.builder().vsum(vsum).metaModel(metaModel).build();
  }

  @Test
  void create_clonesEachOriginal_andSavesAllLinks() {
    Vsum vsum = newVsumWithUserEmail("alice@example.com");

    MetaModel originalA = newOriginalMetaModel(10L);
    MetaModel originalB = newOriginalMetaModel(20L);

    when(metaModelRepository.findAllByIdInAndSourceIsNull(Set.of(10L, 20L)))
        .thenReturn(List.of(originalA, originalB));

    MetaModel clonedA = newClonedMetaModel(101L, originalA);
    MetaModel clonedB = newClonedMetaModel(102L, originalB);

    when(metaModelService.clone(originalA)).thenReturn(clonedA);
    when(metaModelService.clone(originalB)).thenReturn(clonedB);

    ArgumentCaptor<List<VsumMetaModel>> savedLinksCaptor = ArgumentCaptor.forClass(List.class);

    service.create(vsum, Set.of(10L, 20L));

    verify(vsumMetaModelRepository).saveAll(savedLinksCaptor.capture());
    List<VsumMetaModel> saved = savedLinksCaptor.getValue();

    assertThat(saved).hasSize(2);
    assertThat(saved.get(0).getVsum()).isSameAs(vsum);
    assertThat(saved.get(1).getVsum()).isSameAs(vsum);
    assertThat(saved)
        .extracting(l -> l.getMetaModel().getSource().getId())
        .containsExactlyInAnyOrder(10L, 20L);
  }

  @Test
  void delete_removesLinksFromRepository_andDetachesFromVsum_andDeletesCloned() {
    Vsum vsum = newVsumWithUserEmail("alice@example.com");

    MetaModel original = newOriginalMetaModel(10L);
    MetaModel cloned = newClonedMetaModel(100L, original);

    VsumMetaModel link = vsumMetaModel(vsum, cloned);
    vsum.getVsumMetaModels().add(link);

    List<VsumMetaModel> toDelete = List.of(link);

    service.delete(vsum, toDelete);

    verify(vsumMetaModelRepository).deleteAll(toDelete);
    verify(metaModelService).deleteCloned(List.of(cloned));
    assertThat(vsum.getVsumMetaModels()).doesNotContain(link);
  }

  @Test
  void delete_shouldDeleteVsumAndMetaModels() {
    MetaModel meta1 = MetaModel.builder().id(1L).name("m1").build();
    MetaModel meta2 = MetaModel.builder().id(2L).name("m2").build();

    Vsum vsum = new Vsum();
    VsumMetaModel vmm1 = VsumMetaModel.builder().vsum(vsum).metaModel(meta1).build();
    VsumMetaModel vmm2 = VsumMetaModel.builder().vsum(vsum).metaModel(meta2).build();

    vsum.setVsumMetaModels(Set.of(vmm1, vmm2));

    service.delete(vsum);

    verify(vsumMetaModelRepository).deleteVsumMetaModelByVsum(vsum);

    ArgumentCaptor<List<MetaModel>> captor = ArgumentCaptor.forClass(List.class);
    verify(metaModelService).deleteCloned(captor.capture());

    assertThat(captor.getValue()).extracting(MetaModel::getId).containsExactlyInAnyOrder(1L, 2L);

    verifyNoMoreInteractions(vsumMetaModelRepository, metaModelService);
  }
}
