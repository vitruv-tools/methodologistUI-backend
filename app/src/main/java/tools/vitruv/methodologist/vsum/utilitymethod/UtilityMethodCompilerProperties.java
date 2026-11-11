package tools.vitruv.methodologist.vsum.utilitymethod;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings of the utility method compiler, prefix {@code utility-method.compiler}. */
@Getter
@Setter
@ConfigurationProperties(prefix = "utility-method.compiler")
public class UtilityMethodCompilerProperties {

  /** Number of compilations that run at the same time. */
  private int threads = 2;

  /** How long one compilation may take before it is cancelled. */
  private long timeoutSeconds = 10;
}
