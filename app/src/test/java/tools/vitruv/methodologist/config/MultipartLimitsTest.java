package tools.vitruv.methodologist.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Checks the upload limits in the main {@code application.properties}, which applies to every
 * profile. The file is read from the sources because the empty test {@code application.properties}
 * hides it on the test classpath.
 */
class MultipartLimitsTest {

  private static Properties properties;

  @BeforeAll
  static void loadApplicationProperties() throws IOException {
    properties = new Properties();
    try (Reader reader =
        Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) {
      properties.load(reader);
    }
  }

  private static DataSize property(String key) {
    return DataSize.parse(properties.getProperty(key));
  }

  @Test
  void maxFileSize_allowsMetamodelsOf10Megabytes() {
    assertThat(property("spring.servlet.multipart.max-file-size"))
        .isEqualTo(DataSize.ofMegabytes(10));
  }

  @Test
  void maxRequestSize_is20MegabytesAndLeavesRoomAboveTheFileLimit() {
    DataSize maxRequestSize = property("spring.servlet.multipart.max-request-size");

    assertThat(maxRequestSize).isEqualTo(DataSize.ofMegabytes(20));
    assertThat(maxRequestSize).isGreaterThan(property("spring.servlet.multipart.max-file-size"));
  }
}
