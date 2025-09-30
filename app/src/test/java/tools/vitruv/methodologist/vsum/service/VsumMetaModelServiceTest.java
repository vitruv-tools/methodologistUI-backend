package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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
    vsum.setVsumMetaModels(new ArrayList<>());
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

  private VsumMetaModel link(Vsum vsum, MetaModel metaModel) {
      return VsumMetaModel.builder().vsum(vsum).metaModel(metaModel).build();
  }

  @Test
  void create_clonesEachOriginal_andSavesAllLinks() {
    Vsum vsum = newVsumWithUserEmail("alice@example.com");

    MetaModel originalA = newOriginalMetaModel(10L);
    MetaModel originalB = newOriginalMetaModel(20L);

    when(metaModelRepository.findAllByIdInAndUserAndSourceIsNull(Set.of(10L, 20L), vsum.getUser()))
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

    VsumMetaModel link = link(vsum, cloned);
    vsum.getVsumMetaModels().add(link);

    List<VsumMetaModel> toDelete = List.of(link);

    service.delete(vsum, toDelete);

    verify(vsumMetaModelRepository).deleteAll(toDelete);
    verify(metaModelService).deleteCloned(List.of(cloned));
    assertThat(vsum.getVsumMetaModels()).doesNotContain(link);
  }

  @Test
  void sync_whenIdsNull_deletesAllExisting_andDoesNotCreate() {
    Vsum vsum = newVsumWithUserEmail("alice@example.com");

    MetaModel original = newOriginalMetaModel(10L);
    MetaModel cloned = newClonedMetaModel(100L, original);
    VsumMetaModel link = link(vsum, cloned);

    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(link));

    VsumMetaModelService spyService = spy(service);
    doNothing().when(spyService).delete(eq(vsum), anyList());
    doNothing().when(spyService).create(any(Vsum.class), anySet());

    spyService.sync(vsum, null);

    verify(spyService).delete(eq(vsum), argThat(list -> list.size() == 1 && list.get(0) == link));
    verify(spyService, never()).create(any(Vsum.class), anySet());
  }

  @Test
  void sync_removesUnwanted_andAddsMissing() {
    Vsum vsum = newVsumWithUserEmail("alice@example.com");

    MetaModel original1 = newOriginalMetaModel(1L);
    MetaModel cloned1 = newClonedMetaModel(101L, original1);
    VsumMetaModel existingLink = link(vsum, cloned1);

    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(existingLink));

    List<Long> desired = List.of(2L);

    VsumMetaModelService spyService = spy(service);
    doNothing().when(spyService).delete(eq(vsum), anyList());
    doNothing().when(spyService).create(any(Vsum.class), anySet());

    spyService.sync(vsum, desired);

    verify(spyService)
        .delete(
            eq(vsum),
            argThat(
                list ->
                    list.size() == 1 && list.get(0).getMetaModel().getSource().getId().equals(1L)));
    verify(spyService).create(eq(vsum), eq(Set.of(2L)));
  }

  @Test
  void sync_onlyAdds_whenExistingIsSubset() {
    Vsum vsum = newVsumWithUserEmail("alice@example.com");

    MetaModel original1 = newOriginalMetaModel(1L);
    MetaModel cloned1 = newClonedMetaModel(101L, original1);
    VsumMetaModel link1 = link(vsum, cloned1);

    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(link1));

    List<Long> desired = List.of(1L, 2L);

    VsumMetaModelService spyService = spy(service);
    doNothing().when(spyService).delete(eq(vsum), anyList());
    doNothing().when(spyService).create(any(Vsum.class), anySet());

    spyService.sync(vsum, desired);

    verify(spyService, never()).delete(any(Vsum.class), anyList());
    verify(spyService).create(eq(vsum), eq(Set.of(2L)));
  }

  @Test
  void sync_onlyRemoves_whenDesiredIsSubset() {
    Vsum vsum = newVsumWithUserEmail("alice@example.com");

    MetaModel original1 = newOriginalMetaModel(1L);
    MetaModel cloned1 = newClonedMetaModel(101L, original1);
    VsumMetaModel link1 = link(vsum, cloned1);

    MetaModel original2 = newOriginalMetaModel(2L);
    MetaModel cloned2 = newClonedMetaModel(102L, original2);
    VsumMetaModel link2 = link(vsum, cloned2);

    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(link1, link2));

    List<Long> desired = List.of(1L);

    VsumMetaModelService spyService = spy(service);
    doNothing().when(spyService).delete(eq(vsum), anyList());
    doNothing().when(spyService).create(any(Vsum.class), anySet());

    spyService.sync(vsum, desired);

    verify(spyService)
        .delete(
            eq(vsum),
            argThat(
                list ->
                    list.size() == 1 && list.get(0).getMetaModel().getSource().getId().equals(2L)));
    verify(spyService, never()).create(any(Vsum.class), anySet());
  }

  @Test
  void sync_noOp_whenDesiredEqualsExisting() {
    Vsum vsum = newVsumWithUserEmail("alice@example.com");

    MetaModel original = newOriginalMetaModel(5L);
    MetaModel cloned = newClonedMetaModel(105L, original);
    VsumMetaModel link = link(vsum, cloned);

    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(link));

    List<Long> desired = List.of(5L);

    VsumMetaModelService spyService = spy(service);
    doNothing().when(spyService).delete(eq(vsum), anyList());
    doNothing().when(spyService).create(any(Vsum.class), anySet());

    spyService.sync(vsum, desired);

    verify(spyService, never()).delete(any(Vsum.class), anyList());
    verify(spyService, never()).create(any(Vsum.class), anySet());
  }
}
