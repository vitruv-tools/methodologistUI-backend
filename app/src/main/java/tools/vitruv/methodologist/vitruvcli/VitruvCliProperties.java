package tools.vitruv.methodologist.vitruvcli;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Vitruv CLI integration.
 *
 * <p>Bound to the {@code vitruv.cli} prefix in application configuration (for example, {@code
 * application.yml} or environment variables). Each field represents a configurable option used when
 * invoking the external Vitruv CLI process.
 *
 * <p>Note: Lombok generates getters and setters for the fields. These properties are mutable and
 * intended to be configured at application startup via Spring Boot's configuration binding.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "vitruv.cli")
public class VitruvCliProperties {
  private String binary;
  private String jar;
  private String workingDir;
  private long timeoutSeconds;
}
