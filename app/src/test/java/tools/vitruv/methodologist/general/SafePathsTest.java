package tools.vitruv.methodologist.general;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests that {@link SafePaths#resolveInside(Path, String)} never leaves its base. */
class SafePathsTest {

  private static final Path DIR = Path.of("/tmp/ocl-lsp-test/OclProject/ecore");

  @Test
  void plainFileNameResolvesInsideDirectory() {
    assertThat(SafePaths.resolveInside(DIR, "model.ecore")).contains(DIR.resolve("model.ecore"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "../escaped.ecore",
        "../../../../etc/escaped.ecore",
        "sub/../../escaped.ecore",
        "/etc/escaped.ecore",
        "a/b/escaped.ecore"
      })
  void pathLikeNamesAreReducedToTheirLastElementInsideDirectory(String fileName) {
    assertThat(SafePaths.resolveInside(DIR, fileName)).contains(DIR.resolve("escaped.ecore"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "..", ".", "/", "../..", "bad\0name"})
  void unusableNamesAreRejected(String fileName) {
    Optional<Path> result = SafePaths.resolveInside(DIR, fileName);

    assertThat(result).isEmpty();
  }
}
