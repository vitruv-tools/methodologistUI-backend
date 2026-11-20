package tools.vitruv.methodologist.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.vitruv.methodologist.apihandler.CollaborationServiceApiHandler;
import tools.vitruv.methodologist.general.event.VsumChangedByUserEvent;
import tools.vitruv.methodologist.vsum.service.CollaborationReloadListener;

class CollaborationReloadConfigTest {

  private final CountDownLatch release = new CountDownLatch(1);
  private ThreadPoolTaskExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new CollaborationReloadConfig().collaborationReloadExecutor();
  }

  @AfterEach
  void tearDown() {
    release.countDown();
    executor.shutdown();
  }

  @Test
  void runsTheNotificationsOnOneNamedDaemonThread() throws Exception {
    CompletableFuture<Thread> worker = new CompletableFuture<>();

    executor.execute(() -> worker.complete(Thread.currentThread()));

    Thread thread = worker.get(5, TimeUnit.SECONDS);
    assertThat(thread.getName()).startsWith(CollaborationReloadConfig.THREAD_NAME_PREFIX);
    assertThat(thread.isDaemon()).isTrue();
    assertThat(executor.getMaxPoolSize()).isEqualTo(1);
  }

  @Test
  void rejectsNotifications_onceTheQueueIsFull() {
    // the first task is handed to the single thread directly, the others wait in the queue
    for (int i = 0; i <= CollaborationReloadConfig.QUEUE_CAPACITY; i++) {
      executor.execute(this::awaitRelease);
    }

    assertThatThrownBy(() -> executor.execute(this::awaitRelease))
        .isInstanceOf(TaskRejectedException.class);
  }

  @Test
  void isTheListenersExecutor_nextToTheBuildExecutor_andShutsDownWithTheContext() {
    CollaborationServiceApiHandler handler = mock(CollaborationServiceApiHandler.class);
    when(handler.isEnabled()).thenReturn(true);
    CompletableFuture<String> sendingThread = new CompletableFuture<>();
    doAnswer(
            invocation -> {
              sendingThread.complete(Thread.currentThread().getName());
              return null;
            })
        .when(handler)
        .requestReload(7L);
    AtomicReference<ThreadPoolTaskExecutor> reloadExecutor = new AtomicReference<>();

    new ApplicationContextRunner()
        .withUserConfiguration(CollaborationReloadConfig.class, VsumBuildConfig.class)
        .withBean(CollaborationServiceApiHandler.class, () -> handler)
        .withBean(CollaborationReloadListener.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              context
                  .getBean(CollaborationReloadListener.class)
                  .onVsumChangedByUser(new VsumChangedByUserEvent(7L));

              assertThat(sendingThread.get(5, TimeUnit.SECONDS))
                  .startsWith(CollaborationReloadConfig.THREAD_NAME_PREFIX);
              reloadExecutor.set(
                  context.getBean(
                      CollaborationReloadConfig.COLLABORATION_RELOAD_EXECUTOR,
                      ThreadPoolTaskExecutor.class));
            });

    assertThat(reloadExecutor.get().getThreadPoolExecutor().isShutdown()).isTrue();
  }

  private void awaitRelease() {
    try {
      release.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
