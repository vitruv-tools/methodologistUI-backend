package tools.vitruv.methodologist.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Provides the executor that sends the reload and close notifications to the collaboration server.
 */
@Configuration
public class CollaborationReloadConfig {

  public static final String COLLABORATION_RELOAD_EXECUTOR = "collaborationReloadExecutor";
  public static final String THREAD_NAME_PREFIX = "collaboration-reload-";

  /**
   * Notifications waiting for the background thread. If the collaboration server hangs, further
   * notifications are dropped instead of piling up.
   */
  public static final int QUEUE_CAPACITY = 100;

  /**
   * A single thread with a bounded queue, so notifications are sent in the order of the commits. A
   * notification submitted when the queue is full is rejected with a {@link
   * org.springframework.core.task.TaskRejectedException}. Waiting notifications are discarded on
   * shutdown; they are best effort.
   */
  @Bean(name = COLLABORATION_RELOAD_EXECUTOR)
  public ThreadPoolTaskExecutor collaborationReloadExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(QUEUE_CAPACITY);
    executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
    executor.setDaemon(true);
    executor.initialize();
    return executor;
  }
}
