package tools.vitruv.methodologist.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * Checks on a real Tomcat that uploads above the multipart limits are answered with HTTP 413 and an
 * {@link ErrorResponse}. The limit is exceeded while Tomcat parses the request, before a controller
 * is chosen, which MockMvc cannot reproduce. Security and the database are left out of the context.
 */
@SpringBootTest(
    classes = MaxUploadSizeExceededTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.servlet.multipart.max-file-size=1MB",
      "spring.servlet.multipart.max-request-size=2MB"
    })
class MaxUploadSizeExceededTest {

  private static final int ONE_MB = 1024 * 1024;

  private final ObjectMapper mapper = new ObjectMapper();

  @LocalServerPort int port;

  private record Result(int status, String body) {}

  private Result upload(int size) {
    var body = new LinkedMultiValueMap<String, Object>();
    body.add(
        "file",
        new ByteArrayResource(new byte[size]) {
          @Override
          public String getFilename() {
            return "model.ecore";
          }
        });
    return RestClient.create("http://localhost:" + port)
        .post()
        .uri("/test/upload")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(body)
        .exchange(
            (request, response) ->
                new Result(
                    response.getStatusCode().value(),
                    new String(response.getBody().readAllBytes())));
  }

  @Test
  void fileWithinTheLimit_isAccepted() {
    assertThat(upload(ONE_MB - 1024)).isEqualTo(new Result(200, "ok"));
  }

  @ParameterizedTest
  @ValueSource(ints = {ONE_MB + 1024, 5 * ONE_MB})
  void fileAboveTheLimit_isPayloadTooLargeWithTheLimitsInTheMessage(int size) throws IOException {
    Result result = upload(size);

    assertThat(result.status()).isEqualTo(413);
    JsonNode error = mapper.readTree(result.body());
    assertThat(error.get("error").asText()).isEqualTo("PAYLOAD_TOO_LARGE");
    assertThat(error.get("message").asText())
        .isEqualTo(
            "The upload is too large. Each file may be at most 1 MB and the whole request at most"
                + " 2 MB.");
    assertThat(error.get("path").asText()).isEqualTo("/test/upload");
  }

  @RestController
  static class UploadController {
    @PostMapping("/test/upload")
    String upload(@RequestParam("file") MultipartFile file) {
      return "ok";
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class
      })
  @Import({
    UploadController.class,
    GlobalExceptionHandlerController.class,
    UploadSizeExceptionHandlerController.class
  })
  static class TestApplication {}
}
