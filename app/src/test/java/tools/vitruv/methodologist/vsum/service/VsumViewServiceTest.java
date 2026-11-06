package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumView;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewRepository;

@ExtendWith(MockitoExtension.class)
class VsumViewServiceTest {

  @Mock private VsumViewRepository vsumViewRepository;
  @Mock private FileStorageRepository fileStorageRepository;
  @Mock private VsumViewMetaModelRepository vsumViewMetaModelRepository;

  @InjectMocks private VsumViewService service;

  private Vsum vsum;

  @BeforeEach
  void setup() {
    vsum = Vsum.builder().id(1L).name("vsum").views(new HashSet<>()).build();
  }

  @Test
  void create_savesView_addsToVsumViews_andReturnsSavedEntity() {
    Long fileStorageId = 10L;
    FileStorage fileStorage =
        FileStorage.builder().id(fileStorageId).type(FileEnumType.NEO_JOIN).build();

    when(fileStorageRepository.findByIdAndType(fileStorageId, FileEnumType.NEO_JOIN))
        .thenReturn(Optional.of(fileStorage));

    VsumView savedView = VsumView.builder().id(100L).vsum(vsum).fileStorage(fileStorage).build();
    when(vsumViewRepository.save(any(VsumView.class))).thenReturn(savedView);

    VsumView result = service.create(vsum, fileStorageId, "Brake", "brake components");

    ArgumentCaptor<VsumView> captor = ArgumentCaptor.forClass(VsumView.class);
    verify(vsumViewRepository).save(captor.capture());

    VsumView persisted = captor.getValue();
    assertThat(persisted.getVsum()).isSameAs(vsum);
    assertThat(persisted.getFileStorage()).isSameAs(fileStorage);
    assertThat(persisted.getName()).isEqualTo("Brake");
    assertThat(persisted.getDescription()).isEqualTo("brake components");

    assertThat(result).isSameAs(savedView);
    assertThat(vsum.getViews()).contains(savedView);
  }

  @Test
  void create_whenVsumViewsIsNull_savesAndReturnsSavedEntity() {
    vsum.setViews(null);

    Long fileStorageId = 11L;
    FileStorage fileStorage =
        FileStorage.builder().id(fileStorageId).type(FileEnumType.NEO_JOIN).build();

    when(fileStorageRepository.findByIdAndType(fileStorageId, FileEnumType.NEO_JOIN))
        .thenReturn(Optional.of(fileStorage));

    VsumView savedView = VsumView.builder().id(101L).vsum(vsum).fileStorage(fileStorage).build();
    when(vsumViewRepository.save(any(VsumView.class))).thenReturn(savedView);

    VsumView result = service.create(vsum, fileStorageId, "Brake", null);

    verify(vsumViewRepository).save(any(VsumView.class));
    assertThat(result).isSameAs(savedView);
    assertThat(vsum.getViews()).isNull();
  }

  @Test
  void create_whenNeoJoinFileIsMissing_throwsNotFoundException_andDoesNotSave() {
    Long missingFileStorageId = 99L;

    when(fileStorageRepository.findByIdAndType(missingFileStorageId, FileEnumType.NEO_JOIN))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(vsum, missingFileStorageId, "Brake", null))
        .isInstanceOf(NotFoundException.class);

    verify(vsumViewRepository, never()).save(any(VsumView.class));
  }

  @Test
  void create_whenFileExistsButNotNeoJoin_throwsNotFoundException_andDoesNotSave() {
    Long nonNeoJoinFileStorageId = 120L;

    // Repository method already filters by NEO_JOIN type, so non-matching type returns empty.
    when(fileStorageRepository.findByIdAndType(nonNeoJoinFileStorageId, FileEnumType.NEO_JOIN))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(vsum, nonNeoJoinFileStorageId, "Brake", null))
        .isInstanceOf(NotFoundException.class);

    verify(vsumViewRepository, never()).save(any(VsumView.class));
  }

  @Test
  void create_whenFileStorageIdIsNull_throwsNotFoundException_andDoesNotSave() {
    when(fileStorageRepository.findByIdAndType(null, FileEnumType.NEO_JOIN))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(vsum, null, "Brake", null))
        .isInstanceOf(NotFoundException.class);

    verify(vsumViewRepository, never()).save(any(VsumView.class));
  }

  @Test
  void delete_deletesAssociationsFirst_thenDeletesView_andRemovesItFromVsumViews() {
    VsumView view = VsumView.builder().id(200L).vsum(vsum).build();
    vsum.setViews(new HashSet<>(Set.of(view)));

    service.delete(vsum, view);

    verify(vsumViewMetaModelRepository).deleteAllByVsumView(view);
    verify(vsumViewRepository).delete(view);
    assertThat(vsum.getViews()).doesNotContain(view);
  }

  @Test
  void delete_whenVsumViewsIsNull_deletesAssociationsAndViewFromRepositoryOnly() {
    vsum.setViews(null);
    VsumView view = VsumView.builder().id(201L).vsum(vsum).build();

    service.delete(vsum, view);

    verify(vsumViewMetaModelRepository).deleteAllByVsumView(view);
    verify(vsumViewRepository).delete(view);
    assertThat(vsum.getViews()).isNull();
  }

  @Test
  void delete_whenViewIsNotPartOfVsumViews_keepsCollectionUnchanged() {
    VsumView existing = VsumView.builder().id(202L).vsum(vsum).build();
    VsumView other = VsumView.builder().id(203L).vsum(vsum).build();
    vsum.setViews(new HashSet<>(Set.of(existing)));

    service.delete(vsum, other);

    verify(vsumViewMetaModelRepository).deleteAllByVsumView(other);
    verify(vsumViewRepository).delete(other);
    assertThat(vsum.getViews()).containsExactly(existing);
  }

  @Test
  void deleteByVsum_deletesAssociationsBulk_thenDeletesAllViews_andClearsCollection() {
    VsumView view1 = VsumView.builder().id(300L).vsum(vsum).build();
    VsumView view2 = VsumView.builder().id(301L).vsum(vsum).build();
    vsum.setViews(new HashSet<>(Set.of(view1, view2)));

    when(vsumViewRepository.findAllByVsum(vsum)).thenReturn(List.of(view1, view2));

    service.deleteByVsum(vsum);

    verify(vsumViewRepository).findAllByVsum(vsum);
    verify(vsumViewMetaModelRepository)
        .deleteAllByVsumViewIn(
            argThat(
                c ->
                    c instanceof Collection<?>
                        && ((Collection<?>) c).containsAll(List.of(view1, view2))));
    verify(vsumViewRepository).deleteAllByVsum(vsum);
    assertThat(vsum.getViews()).isEmpty();
  }

  @Test
  void deleteByVsum_whenVsumViewsIsNull_deletesFromRepositoryOnly() {
    vsum.setViews(null);

    when(vsumViewRepository.findAllByVsum(vsum)).thenReturn(List.of());

    service.deleteByVsum(vsum);

    verify(vsumViewRepository).findAllByVsum(vsum);
    verify(vsumViewMetaModelRepository, never()).deleteAllByVsumViewIn(anyCollection());
    verify(vsumViewRepository).deleteAllByVsum(vsum);
    assertThat(vsum.getViews()).isNull();
  }

  @Test
  void deleteByVsum_whenVsumViewsIsEmpty_keepsCollectionEmpty() {
    vsum.setViews(new HashSet<>());

    when(vsumViewRepository.findAllByVsum(vsum)).thenReturn(List.of());

    service.deleteByVsum(vsum);

    verify(vsumViewRepository).findAllByVsum(vsum);
    verify(vsumViewMetaModelRepository, never()).deleteAllByVsumViewIn(anyCollection());
    verify(vsumViewRepository).deleteAllByVsum(vsum);
    assertThat(vsum.getViews()).isEmpty();
  }

  @Test
  void deleteByVsum_whenViewsExist_deletesAssociationsBeforeViews() {
    VsumView view = VsumView.builder().id(400L).vsum(vsum).build();
    vsum.setViews(new HashSet<>(Set.of(view)));

    when(vsumViewRepository.findAllByVsum(vsum)).thenReturn(List.of(view));

    service.deleteByVsum(vsum);

    org.mockito.InOrder inOrder =
        org.mockito.Mockito.inOrder(vsumViewMetaModelRepository, vsumViewRepository);
    inOrder.verify(vsumViewMetaModelRepository).deleteAllByVsumViewIn(anyCollection());
    inOrder.verify(vsumViewRepository).deleteAllByVsum(vsum);
  }

  @Test
  void update_changesNameAndDescription_keepsTheFile_andKeepsTheId() {
    FileStorage file = FileStorage.builder().id(10L).type(FileEnumType.NEO_JOIN).build();
    VsumView view =
        VsumView.builder()
            .id(100L)
            .vsum(vsum)
            .name("Brake")
            .description("old")
            .fileStorage(file)
            .build();
    when(vsumViewRepository.save(view)).thenReturn(view);

    VsumView result = service.update(view, 10L, "Braking", null);

    assertThat(result).isSameAs(view);
    assertThat(view.getId()).isEqualTo(100L);
    assertThat(view.getName()).isEqualTo("Braking");
    assertThat(view.getDescription()).isNull();
    assertThat(view.getFileStorage()).isSameAs(file);
    verify(fileStorageRepository, never()).findByIdAndType(any(), any());
  }

  @Test
  void update_replacesTheFile_withAnotherNeoJoinFile() {
    FileStorage oldFile = FileStorage.builder().id(10L).type(FileEnumType.NEO_JOIN).build();
    FileStorage newFile = FileStorage.builder().id(11L).type(FileEnumType.NEO_JOIN).build();
    VsumView view =
        VsumView.builder().id(100L).vsum(vsum).name("Brake").fileStorage(oldFile).build();
    when(fileStorageRepository.findByIdAndType(11L, FileEnumType.NEO_JOIN))
        .thenReturn(Optional.of(newFile));
    when(vsumViewRepository.save(view)).thenReturn(view);

    service.update(view, 11L, "Brake", "new query");

    assertThat(view.getFileStorage()).isSameAs(newFile);
    assertThat(view.getDescription()).isEqualTo("new query");
  }

  @Test
  void update_whenNewFileIsNotNeoJoin_throwsNotFoundException_andDoesNotSave() {
    FileStorage oldFile = FileStorage.builder().id(10L).type(FileEnumType.NEO_JOIN).build();
    VsumView view =
        VsumView.builder().id(100L).vsum(vsum).name("Brake").fileStorage(oldFile).build();
    when(fileStorageRepository.findByIdAndType(12L, FileEnumType.NEO_JOIN))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(view, 12L, "Braking", null))
        .isInstanceOf(NotFoundException.class);

    assertThat(view.getFileStorage()).isSameAs(oldFile);
    assertThat(view.getName()).isEqualTo("Brake");
    verify(vsumViewRepository, never()).save(any(VsumView.class));
  }
}
