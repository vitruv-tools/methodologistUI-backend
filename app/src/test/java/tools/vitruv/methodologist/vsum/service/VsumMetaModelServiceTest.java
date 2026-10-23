package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.service.FileStorageService;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;

class VsumMetaModelServiceTest {

  private VsumMetaModelRepository vsumMetaModelRepository;
  private FileStorageService fileStorageService;
  private MetaModelService metaModelService;
  private MetaModelRepository metaModelRepository;
  private EntityManager entityManager;

  private VsumMetaModelService service;

  @BeforeEach
  void setUp() {
    vsumMetaModelRepository = mock(VsumMetaModelRepository.class);
    metaModelService = mock(MetaModelService.class);
    metaModelRepository = mock(MetaModelRepository.class);
    entityManager = mock(EntityManager.class);
    fileStorageService = mock(FileStorageService.class);

    service =
        new VsumMetaModelService(
            vsumMetaModelRepository,
            metaModelService,
            metaModelRepository,
            entityManager,
            fileStorageService);
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
    final Vsum vsum = newVsumWithUserEmail("alice@example.com");

    MetaModel originalA = newOriginalMetaModel(10L);
    MetaModel originalB = newOriginalMetaModel(20L);
    originalA.setName("Library A");
    originalB.setName("Library B");

    when(metaModelRepository.findAllByIdInAndSourceIsNull(Set.of(10L, 20L)))
        .thenReturn(List.of(originalA, originalB));

    MetaModel clonedA = newClonedMetaModel(101L, originalA);
    MetaModel clonedB = newClonedMetaModel(102L, originalB);

    when(metaModelService.clone(originalA)).thenReturn(clonedA);
    when(metaModelService.clone(originalB)).thenReturn(clonedB);

    ArgumentCaptor<List<VsumMetaModel>> savedLinksCaptor = ArgumentCaptor.forClass(List.class);

    service.create(vsum, Set.of(10L, 20L));

    verify(vsumMetaModelRepository).saveAll(savedLinksCaptor.capture());
    verify(entityManager).flush();
    List<VsumMetaModel> saved = savedLinksCaptor.getValue();

    assertThat(saved).hasSize(2);
    assertThat(saved.get(0).getVsum()).isSameAs(vsum);
    assertThat(saved.get(1).getVsum()).isSameAs(vsum);
    assertThat(saved)
        .extracting(l -> l.getMetaModel().getSource().getId())
        .containsExactlyInAnyOrder(10L, 20L);
    assertThat(saved)
        .extracting(VsumMetaModel::getName)
        .containsExactlyInAnyOrder("Library A", "Library B");
  }

  @Test
  void create_usesProvidedProjectSpecificName() {
    Vsum vsum = newVsumWithUserEmail("alice@example.com");
    MetaModel original = newOriginalMetaModel(10L);
    original.setName("Library name");
    MetaModel cloned = newClonedMetaModel(101L, original);

    when(metaModelRepository.findAllByIdInAndSourceIsNull(Set.of(10L)))
        .thenReturn(List.of(original));
    when(metaModelService.clone(original)).thenReturn(cloned);

    ArgumentCaptor<List<VsumMetaModel>> savedLinksCaptor = ArgumentCaptor.forClass(List.class);

    service.create(vsum, Set.of(10L), Map.of(10L, "Project name"));

    verify(vsumMetaModelRepository).saveAll(savedLinksCaptor.capture());
    assertThat(savedLinksCaptor.getValue())
        .singleElement()
        .extracting(VsumMetaModel::getName)
        .isEqualTo("Project name");
  }

  @Test
  void delete_removesLinksFromRepository_andDetachesFromVsum_andDeletesCloned() {
    Vsum vsum = newVsumWithUserEmail("alice@example.com");

    MetaModel original = newOriginalMetaModel(10L);
    MetaModel cloned = newClonedMetaModel(100L, original);
    cloned.setEcoreFile(new FileStorage());
    cloned.setGenModelFile(new FileStorage());

    VsumMetaModel link = vsumMetaModel(vsum, cloned);
    vsum.getVsumMetaModels().add(link);

    List<VsumMetaModel> toDelete = List.of(link);

    service.delete(vsum, toDelete);

    verify(vsumMetaModelRepository).deleteAll(toDelete);
    verify(metaModelRepository).deleteAll(List.of(cloned));
    assertThat(vsum.getVsumMetaModels()).doesNotContain(link);
  }

  @Test
  void delete_shouldDeleteVsumAndMetaModels() {
    MetaModel meta1 = MetaModel.builder().id(1L).name("m1").build();
    MetaModel meta2 = MetaModel.builder().id(2L).name("m2").build();
    meta1.setEcoreFile(new FileStorage());
    meta1.setGenModelFile(new FileStorage());

    meta2.setEcoreFile(new FileStorage());
    meta2.setGenModelFile(new FileStorage());

    Vsum vsum = new Vsum();
    VsumMetaModel vmm1 = VsumMetaModel.builder().vsum(vsum).metaModel(meta1).build();
    VsumMetaModel vmm2 = VsumMetaModel.builder().vsum(vsum).metaModel(meta2).build();

    vsum.setVsumMetaModels(Set.of(vmm1, vmm2));

    service.delete(vsum);

    verify(vsumMetaModelRepository).deleteVsumMetaModelByVsum(vsum);

    ArgumentCaptor<List<MetaModel>> captor = ArgumentCaptor.forClass(List.class);
    verify(metaModelRepository).deleteAll(captor.capture());

    assertThat(captor.getValue()).extracting(MetaModel::getId).containsExactlyInAnyOrder(1L, 2L);

    verifyNoMoreInteractions(vsumMetaModelRepository, metaModelService);
  }

  /**
   * Verifies that finding accessible metamodels by project returns source metamodels.
   *
   * <p>Tests the LSP workspace preparation logic that retrieves all metamodels associated with a
   * given VSUM, preferring source metamodels over clones.
   */
  @Test
  void findAccessibleByProject_returnsSourceMetaModels() {

    MetaModel source1 = new MetaModel();
    source1.setId(1L);
    source1.setName("SourceMM1");

    MetaModel clone1 = new MetaModel();
    clone1.setId(2L);
    clone1.setSource(source1);

    MetaModel original2 = new MetaModel();
    original2.setId(3L);
    original2.setName("OriginalMM2");
    Long vsumId = 10L;
    Vsum vsum = new Vsum();
    vsum.setId(vsumId);

    VsumMetaModel vmm1 = VsumMetaModel.builder().vsum(vsum).metaModel(clone1).build();
    VsumMetaModel vmm2 = VsumMetaModel.builder().vsum(vsum).metaModel(original2).build();

    when(vsumMetaModelRepository.findByVsumId(vsumId)).thenReturn(List.of(vmm1, vmm2));

    List<MetaModel> result = service.findAccessibleByProject(vsumId);

    assertThat(result).hasSize(2).contains(source1, original2).doesNotContain(clone1);
  }

  /**
   * Verifies that finding accessible metamodels with null vsumId returns empty list.
   *
   * <p>Tests defensive handling when no project context is provided.
   */
  @Test
  void findAccessibleByProject_withNullVsumId_returnsEmptyList() {
    List<MetaModel> result = service.findAccessibleByProject(null);

    assertThat(result).isEmpty();
  }

  @Test
  void deleteCloned_deletesModels_thenDeletesBothFileTypes() {
    FileStorage ecoreA = new FileStorage();
    ecoreA.setId(100L);
    FileStorage genA = new FileStorage();
    genA.setId(101L);
    MetaModel cloneA = new MetaModel();
    cloneA.setId(1000L);
    cloneA.setEcoreFile(ecoreA);
    cloneA.setGenModelFile(genA);

    FileStorage ecoreB = new FileStorage();
    ecoreB.setId(200L);
    FileStorage genB = new FileStorage();
    genB.setId(201L);
    MetaModel cloneB = new MetaModel();
    cloneB.setId(2000L);
    cloneB.setEcoreFile(ecoreB);
    cloneB.setGenModelFile(genB);

    List<MetaModel> clones = List.of(cloneA, cloneB);

    ArgumentCaptor<List<FileStorage>> filesCaptor = ArgumentCaptor.forClass(List.class);

    service.deleteCloned(clones);

    verify(metaModelRepository).deleteAll(clones);
    verify(fileStorageService).deleteFiles(filesCaptor.capture());

    List<FileStorage> sentFiles = filesCaptor.getValue();
    assertThat(sentFiles)
        .extracting(FileStorage::getId)
        .containsExactlyInAnyOrder(
            ecoreA.getId(), genA.getId(),
            ecoreB.getId(), genB.getId());
  }

  @Test
  void deleteCloned_handlesEmptyList() {
    List<MetaModel> empty = new ArrayList<>();

    service.deleteCloned(empty);

    verify(metaModelRepository).deleteAll(empty);
    verify(fileStorageService).deleteFiles(List.of());
  }
}
