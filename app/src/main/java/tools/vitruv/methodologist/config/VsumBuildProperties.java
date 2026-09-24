package tools.vitruv.methodologist.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings of the asynchronous VSUM build pipeline, prefix {@code vsum.build}. */
@Getter
@Setter
@ConfigurationProperties(prefix = "vsum.build")
public class VsumBuildProperties {

  /** Number of builds the setup-service is asked to run at the same time. */
  private int poolSize = 2;

  /** Builds accepted while all workers are busy before new requests are rejected. */
  private int queueCapacity = 20;

  /** Successful builds per VSUM whose artifact is kept; older artifacts are deleted. */
  private int retentionKeep = 5;

  /**
   * How long the synchronous download endpoints wait for a build before giving up. Matches the
   * setup-service response timeout by default.
   */
  private long waitTimeoutSeconds = 300;

  /** How often the synchronous download endpoints re-check the build status. */
  private long pollIntervalMillis = 500;
}
