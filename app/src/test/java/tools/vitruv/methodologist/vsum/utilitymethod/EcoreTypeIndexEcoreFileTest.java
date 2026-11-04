package tools.vitruv.methodologist.vsum.utilitymethod;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex.EcoreFile;

class EcoreTypeIndexEcoreFileTest {

  @Test
  void equals_comparesTheContentOfTheData() {
    EcoreFile file = new EcoreFile("a.ecore", new byte[] {1, 2, 3});
    EcoreFile sameContent = new EcoreFile("a.ecore", new byte[] {1, 2, 3});

    assertThat(file)
        .isEqualTo(sameContent)
        .hasSameHashCodeAs(sameContent)
        .isNotEqualTo(new EcoreFile("a.ecore", new byte[] {1, 2}))
        .isNotEqualTo(new EcoreFile("b.ecore", new byte[] {1, 2, 3}))
        .isNotEqualTo("a.ecore");
  }

  @Test
  void toString_showsTheSizeInsteadOfTheBytes() {
    assertThat(new EcoreFile("a.ecore", new byte[] {1, 2, 3}))
        .hasToString("EcoreFile[filename=a.ecore, 3 bytes]");
    assertThat(new EcoreFile(null, null)).hasToString("EcoreFile[filename=null, 0 bytes]");
  }
}
