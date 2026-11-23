package tools.vitruv.methodologist.vsum.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.general.model.FileStorage;

class VsumVersionTest {

  @Test
  void allFiles_listsACopySharedByTheBuildAndTheContentOnce() {
    FileStorage ecore = FileStorage.builder().id(1L).filename("a.ecore").build();
    FileStorage view = FileStorage.builder().id(2L).filename("v.nj").build();
    VsumVersion version =
        VsumVersion.builder().ecores(List.of(ecore)).contentFiles(List.of(ecore, view)).build();

    assertThat(version.allFiles()).containsExactly(ecore, view);
  }
}
