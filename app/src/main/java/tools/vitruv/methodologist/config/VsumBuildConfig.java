package tools.vitruv.methodologist.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Provides the bounded executor that runs VSUM builds in the background. */
@Configuration
@EnableConfigurationProperties(VsumBuildProperties.class)
public class VsumBuildConfig {

  public static final String VSUM_BUILD_EXECUTOR = "vsumBuildExecutor";

  /**
   * A fixed-size pool with a bounded queue. A build submitted when the queue is full is rejected
   * with a {@link org.springframework.core.task.TaskRejectedException}, which the build service
   * turns into a failed build instead of letting requests pile up without limit.
   */
  @Bean(name = VSUM_BUILD_EXECUTOR)
  public ThreadPoolTaskExecutor vsumBuildExecutor(VsumBuildProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.getPoolSize());
    executor.setMaxPoolSize(properties.getPoolSize());
    executor.setQueueCapacity(properties.getQueueCapacity());
    executor.setThreadNamePrefix("vsum-build-");
    // Let a running setup-service call finish on shutdown; a build interrupted by a restart is
    // marked failed by VsumBuildService on the next start.
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}
