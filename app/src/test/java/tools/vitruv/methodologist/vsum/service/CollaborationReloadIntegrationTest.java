package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.vitruv.methodologist.apihandler.CollaborationServiceApiHandler;
import tools.vitruv.methodologist.apihandler.KeycloakApiHandler;
import tools.vitruv.methodologist.config.CollaborationReloadConfig;
import tools.vitruv.methodologist.exception.OwnerRequiredException;
import tools.vitruv.methodologist.general.service.SmtpMailService;
import tools.vitruv.methodologist.user.mapper.UserMapper;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.user.service.KeycloakService;
import tools.vitruv.methodologist.user.service.UserService;
import tools.vitruv.methodologist.vsum.VsumRepresentation;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumSyncChangesPutRequest;
import tools.vitruv.methodologist.vsum.mapper.LowCodeReactionRequestMapper;
import tools.vitruv.methodologist.vsum.mapper.MetaModelMapper;
import tools.vitruv.methodologist.vsum.mapper.MetaModelRelationMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumHistoryMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumUserMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumViewMapper;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumHistory;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.VsumHistoryRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewRepository;

/**
 * Runs the changes of issue #425 and the removals of projects, members and accounts through
 * Spring's transaction proxies, the real {@link CollaborationReloadListener}, {@link
 * CollaborationServiceApiHandler} and {@link KeycloakApiHandler}, against a fake Keycloak and a
 * fake collaboration server.
 *
 * <p>The notifications are collected by a {@link RecordingExecutor} and run by the test, so the
 * test decides when they are sent and needs no waiting: whatever is scheduled when the user's
 * request returns is all that will ever be sent for it.
 */
@SpringBootTest(
    classes = CollaborationReloadIntegrationTest.TestApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:collaboration-reload;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "vsum.history.limit=10",
      "methodologist.keycloak.client-id=methodologist",
      "keycloak.backend.client-id=" + CollaborationReloadIntegrationTest.CLIENT_ID,
      "keycloak.backend.client-secret=" + CollaborationReloadIntegrationTest.CLIENT_SECRET,
      "collaboration.reload.max-retries=" + CollaborationReloadIntegrationTest.MAX_RETRIES,
      "collaboration.reload.first-retry-delay=1ms",
      "app.otp.ttlMinutes=5"
    })
class CollaborationReloadIntegrationTest {

  static final String CLIENT_ID = "methodologist-backend";
  static final String CLIENT_SECRET = "backend-secret";
  static final int MAX_RETRIES = 2;
  private static final String SERVICE_TOKEN = "service-token";
  private static final String CALLER_EMAIL = "editor@example.com";
  private static final Long VSUM_ID = 5L;
  private static final Long HISTORY_ID = 3L;
  private static final Long META_MODEL_ID = 10L;
  private static final Long MEMBERSHIP_ID = 123L;
  private static final Long USER_ID = 42L;

  private static final MockWebServer KEYCLOAK = new MockWebServer();
  private static final MockWebServer COLLABORATION_SERVER = new MockWebServer();

  /** Every token request the fake Keycloak received; the token is cached across tests. */
  private static final List<String> TOKEN_REQUEST_BODIES = new CopyOnWriteArrayList<>();

  private static final AtomicInteger COLLABORATION_STATUS = new AtomicInteger(202);

  static {
    KEYCLOAK.setDispatcher(
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            TOKEN_REQUEST_BODIES.add(request.getBody().readUtf8());
            return new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"access_token\":\"" + SERVICE_TOKEN + "\",\"expires_in\":300}");
          }
        });
    COLLABORATION_SERVER.setDispatcher(
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            return new MockResponse().setResponseCode(COLLABORATION_STATUS.get());
          }
        });
    try {
      KEYCLOAK.start();
      COLLABORATION_SERVER.start();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @DynamicPropertySource
  static void servers(DynamicPropertyRegistry registry) {
    registry.add("third_api.keycloak.base_url", () -> KEYCLOAK.url("/").toString());
    registry.add("collaboration.url", () -> COLLABORATION_SERVER.url("/").toString());
  }

  @AfterAll
  static void stopServers() throws IOException {
    KEYCLOAK.shutdown();
    COLLABORATION_SERVER.shutdown();
  }

  @Autowired private VsumService vsumService;
  @Autowired private VsumHistoryService vsumHistoryService;
  @Autowired private VsumUserService vsumUserService;
  @Autowired private UserService userService;
  @Autowired private RecordingExecutor notifications;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private VsumMapper vsumMapper;
  @MockitoBean private VsumRepository vsumRepository;
  @MockitoBean private MetaModelMapper metaModelMapper;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private VsumUserRepository vsumUserRepository;
  @MockitoBean private VsumUserMapper vsumUserMapper;
  @MockitoBean private MetaModelRelationMapper metaModelRelationMapper;
  @MockitoBean private VsumMetaModelRepository vsumMetaModelRepository;
  @MockitoBean private VsumViewRepository vsumViewRepository;
  @MockitoBean private VsumViewMapper vsumViewMapper;
  @MockitoBean private LowCodeReactionRequestMapper lowCodeReactionRequestMapper;
  @MockitoBean private VsumBuildService vsumBuildService;
  @MockitoBean private MetaModelService metaModelService;
  @MockitoBean private VsumSyncService vsumSyncService;
  @MockitoBean private VsumCleanupService vsumCleanupService;
  @MockitoBean private VsumValidationService vsumValidationService;
  @MockitoBean private VsumHistoryRepository vsumHistoryRepository;
  @MockitoBean private VsumHistoryMapper vsumHistoryMapper;
  @MockitoBean private UserMapper userMapper;
  @MockitoBean private KeycloakService keycloakService;
  @MockitoBean private SmtpMailService smtpMailService;
  @MockitoBean private VsumInvitationService vsumInvitationService;

  private TransactionTemplate transaction;
  private Vsum vsum;
  private User editor;

  @BeforeEach
  void setUp() throws InterruptedException {
    transaction = new TransactionTemplate(transactionManager);
    notifications.clear();
    COLLABORATION_STATUS.set(202);
    while (COLLABORATION_SERVER.takeRequest(0, TimeUnit.MILLISECONDS) != null) {
      // drop the requests of earlier tests
    }
    editor = new User();
    editor.setEmail(CALLER_EMAIL);
    vsum = new Vsum();
    vsum.setId(VSUM_ID);
    vsum.setUser(editor);
  }

  @Test
  void syncChanges_sendsOneReload_withTheServiceAccountToken_onlyAfterTheCommit()
      throws InterruptedException {
    givenMember(VsumRole.MEMBER);

    transaction.executeWithoutResult(
        status -> {
          vsumService.update(CALLER_EMAIL, VSUM_ID, new VsumSyncChangesPutRequest());
          assertThat(notifications.scheduled()).isZero();
        });

    assertOneReloadSent();
    assertThat(TOKEN_REQUEST_BODIES)
        .isNotEmpty()
        .allSatisfy(
            body ->
                assertThat(body)
                    .contains("grant_type=client_credentials")
                    .contains("client_id=" + CLIENT_ID));
  }

  @Test
  void metaModelRename_sendsOneReload() throws InterruptedException {
    givenMember(VsumRole.MEMBER);
    givenProjectMetaModelNamed("Old name");

    vsumService.updateMetaModelName(CALLER_EMAIL, VSUM_ID, META_MODEL_ID, "New name");

    assertOneReloadSent();
  }

  @Test
  void metaModelRename_sendsNoReload_whenTheNameIsUnchanged() throws InterruptedException {
    givenMember(VsumRole.MEMBER);
    givenProjectMetaModelNamed("Same name");

    vsumService.updateMetaModelName(CALLER_EMAIL, VSUM_ID, META_MODEL_ID, "Same name");

    assertNoReloadSent();
  }

  @Test
  void ecoreSave_sendsOneReload() throws InterruptedException {
    givenMember(VsumRole.MEMBER);
    givenProjectMetaModelNamed("Name");
    givenEcoreUpdateChanges(true);

    vsumService.updateMetaModelEcore(CALLER_EMAIL, VSUM_ID, META_MODEL_ID, "<edited/>");

    assertOneReloadSent();
  }

  @Test
  void ecoreSave_sendsNoReload_whenTheContentIsUnchanged() throws InterruptedException {
    givenMember(VsumRole.MEMBER);
    givenProjectMetaModelNamed("Name");
    givenEcoreUpdateChanges(false);

    vsumService.updateMetaModelEcore(CALLER_EMAIL, VSUM_ID, META_MODEL_ID, "<edited/>");

    assertNoReloadSent();
  }

  @Test
  void revert_sendsOneReload_onlyAfterTheCommit() throws InterruptedException {
    givenRevertableHistory(VsumRole.MEMBER);

    transaction.executeWithoutResult(
        status -> {
          vsumHistoryService.revert(CALLER_EMAIL, HISTORY_ID);
          assertThat(notifications.scheduled()).isZero();
        });

    assertOneReloadSent();
  }

  @Test
  void internalSync_sendsNoReload() throws InterruptedException {
    when(vsumRepository.findByIdAndRemovedAtIsNull(VSUM_ID)).thenReturn(Optional.of(vsum));

    vsumService.updateForService(VSUM_ID, new VsumSyncChangesPutRequest());

    assertNoReloadSent();
  }

  @Test
  void syncChanges_sendsNoReload_whenTheTransactionRollsBack() throws InterruptedException {
    givenMember(VsumRole.MEMBER);

    transaction.executeWithoutResult(
        status -> {
          vsumService.update(CALLER_EMAIL, VSUM_ID, new VsumSyncChangesPutRequest());
          status.setRollbackOnly();
        });

    assertNoReloadSent();
  }

  @Test
  void revert_sendsNoReload_whenTheRevertIsDenied() throws InterruptedException {
    givenRevertableHistory(VsumRole.VIEWER);

    assertThatThrownBy(() -> vsumHistoryService.revert(CALLER_EMAIL, HISTORY_ID))
        .isInstanceOf(AccessDeniedException.class);

    assertNoReloadSent();
  }

  @Test
  void syncChanges_succeeds_whenTheCollaborationServerFails_afterRetryingTheReload()
      throws InterruptedException {
    givenMember(VsumRole.MEMBER);
    COLLABORATION_STATUS.set(503);

    assertThatCode(() -> vsumService.update(CALLER_EMAIL, VSUM_ID, new VsumSyncChangesPutRequest()))
        .doesNotThrowAnyException();

    assertThat(notifications.scheduled()).isOne();
    assertThatCode(notifications::runAll).doesNotThrowAnyException();
    for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      assertThat(COLLABORATION_SERVER.takeRequest(0, TimeUnit.MILLISECONDS)).isNotNull();
    }
    assertThat(COLLABORATION_SERVER.takeRequest(0, TimeUnit.MILLISECONDS)).isNull();
  }

  @Test
  void remove_sendsOneClose_onlyAfterTheCommit() throws InterruptedException {
    givenOwnedVsum();

    transaction.executeWithoutResult(
        status -> {
          vsumService.remove(CALLER_EMAIL, VSUM_ID);
          assertThat(notifications.scheduled()).isZero();
        });

    assertOneNotificationSent("close");
  }

  @Test
  void remove_sendsNoClose_whenTheTransactionRollsBack() throws InterruptedException {
    givenOwnedVsum();

    transaction.executeWithoutResult(
        status -> {
          vsumService.remove(CALLER_EMAIL, VSUM_ID);
          status.setRollbackOnly();
        });

    assertNoReloadSent();
  }

  @Test
  void deletePermanently_sendsOneClose_afterDeletingTheProjectData() throws InterruptedException {
    vsum.setRemovedAt(Instant.now());
    when(vsumRepository.findByIdAndUser_EmailAndUser_RemovedAtIsNullAndRemovedAtIsNotNull(
            VSUM_ID, CALLER_EMAIL))
        .thenReturn(Optional.of(vsum));

    vsumService.deletePermanently(CALLER_EMAIL, VSUM_ID);

    verify(vsumCleanupService).deleteProjectData(vsum);
    assertOneNotificationSent("close");
  }

  @Test
  void deleteMember_sendsOneAccessCheck_onlyAfterTheCommit() throws InterruptedException {
    givenMembershipToRemove(VsumRole.OWNER);

    int scheduled =
        scheduledBeforeTheCommit(() -> vsumUserService.deleteMember(CALLER_EMAIL, MEMBERSHIP_ID));

    assertThat(scheduled).isZero();
    verify(vsumUserRepository).delete(any(VsumUser.class));
    assertOneNotificationSent("check-access");
  }

  @Test
  void deleteMember_inItsOwnTransaction_sendsOneAccessCheck() throws InterruptedException {
    givenMembershipToRemove(VsumRole.OWNER);

    vsumUserService.deleteMember(CALLER_EMAIL, MEMBERSHIP_ID);

    assertOneNotificationSent("check-access");
  }

  @Test
  void deleteMember_sendsNoAccessCheck_whenTheTransactionRollsBack() throws InterruptedException {
    givenMembershipToRemove(VsumRole.OWNER);

    transaction.executeWithoutResult(
        status -> {
          vsumUserService.deleteMember(CALLER_EMAIL, MEMBERSHIP_ID);
          status.setRollbackOnly();
        });

    assertNoReloadSent();
  }

  @Test
  void deleteMember_sendsNoAccessCheck_whenTheRemovalIsDenied() throws InterruptedException {
    givenMembershipToRemove(VsumRole.MEMBER);

    assertThatThrownBy(() -> vsumUserService.deleteMember(CALLER_EMAIL, MEMBERSHIP_ID))
        .isInstanceOf(OwnerRequiredException.class);

    assertNoReloadSent();
  }

  @Test
  void removeAccount_sendsOneUserAccessCheck_onlyAfterTheCommit() throws InterruptedException {
    givenOwnAccount();

    int scheduled = scheduledBeforeTheCommit(() -> userService.remove(CALLER_EMAIL, USER_ID));

    assertThat(scheduled).isZero();
    assertOneRequestSent("/internal/users/" + USER_ID + "/check-access");
  }

  @Test
  void removeAccount_inItsOwnTransaction_sendsOneUserAccessCheck() throws InterruptedException {
    givenOwnAccount();

    userService.remove(CALLER_EMAIL, USER_ID);

    assertOneRequestSent("/internal/users/" + USER_ID + "/check-access");
  }

  @Test
  void removeAccount_sendsNoUserAccessCheck_whenTheTransactionRollsBack()
      throws InterruptedException {
    givenOwnAccount();

    transaction.executeWithoutResult(
        status -> {
          userService.remove(CALLER_EMAIL, USER_ID);
          status.setRollbackOnly();
        });

    assertNoReloadSent();
  }

  /** Runs the scheduled notifications and checks that exactly one reload request was sent. */
  private void assertOneReloadSent() throws InterruptedException {
    assertOneNotificationSent("reload");
  }

  /**
   * Runs the scheduled notifications and checks that exactly one notification of the given kind
   * ({@code reload}, {@code close} or {@code check-access}) was sent for the VSUM.
   */
  private void assertOneNotificationSent(String kind) throws InterruptedException {
    assertOneRequestSent("/internal/projects/" + VSUM_ID + "/" + kind);
  }

  /**
   * Runs the scheduled notifications and checks that exactly one request, without a body, was sent
   * to the given path.
   */
  private void assertOneRequestSent(String path) throws InterruptedException {
    assertThat(notifications.scheduled()).isOne();
    notifications.runAll();

    RecordedRequest request = COLLABORATION_SERVER.takeRequest(0, TimeUnit.MILLISECONDS);
    assertThat(request).isNotNull();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo(path);
    assertThat(request.getBodySize()).isZero();
    assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + SERVICE_TOKEN);
    assertThat(COLLABORATION_SERVER.takeRequest(0, TimeUnit.MILLISECONDS)).isNull();
  }

  /**
   * Runs the action in a transaction that commits, checks that the action itself scheduled no
   * notification, and returns how many were scheduled when the transaction was about to complete.
   * By then every before-commit callback has run, so a listener that sends before the commit is
   * counted too.
   */
  private int scheduledBeforeTheCommit(Runnable action) {
    AtomicInteger scheduledBeforeCompletion = new AtomicInteger(-1);
    transaction.executeWithoutResult(
        status -> {
          TransactionSynchronizationManager.registerSynchronization(
              new TransactionSynchronization() {
                @Override
                public void beforeCompletion() {
                  // Exceptions thrown here are only logged, so the count is checked afterwards.
                  scheduledBeforeCompletion.set(notifications.scheduled());
                }
              });
          action.run();
          assertThat(notifications.scheduled()).isZero();
        });
    return scheduledBeforeCompletion.get();
  }

  private void assertNoReloadSent() throws InterruptedException {
    assertThat(notifications.scheduled()).isZero();
    assertThat(COLLABORATION_SERVER.takeRequest(0, TimeUnit.MILLISECONDS)).isNull();
  }

  private void givenOwnedVsum() {
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(VSUM_ID, CALLER_EMAIL))
        .thenReturn(Optional.of(vsum));
    when(vsumRepository.save(vsum)).thenReturn(vsum);
  }

  private void givenMember(VsumRole role) {
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                VSUM_ID, CALLER_EMAIL))
        .thenReturn(Optional.of(VsumUser.builder().vsum(vsum).user(editor).role(role).build()));
  }

  /**
   * A membership of another user as {@code MEMBER}, and the caller's membership in the same VSUM
   * with the given role.
   */
  private void givenMembershipToRemove(VsumRole callerRole) {
    User member = new User();
    member.setId(9L);
    when(vsumUserRepository.findById(MEMBERSHIP_ID))
        .thenReturn(
            Optional.of(
                VsumUser.builder()
                    .id(MEMBERSHIP_ID)
                    .vsum(vsum)
                    .user(member)
                    .role(VsumRole.MEMBER)
                    .build()));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, CALLER_EMAIL))
        .thenReturn(
            Optional.of(VsumUser.builder().vsum(vsum).user(editor).role(callerRole).build()));
  }

  private void givenOwnAccount() {
    editor.setId(USER_ID);
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(CALLER_EMAIL))
        .thenReturn(Optional.of(editor));
  }

  private void givenProjectMetaModelNamed(String name) {
    MetaModel projectMetaModel = new MetaModel();
    projectMetaModel.setId(101L);
    VsumMetaModel vsumMetaModel =
        VsumMetaModel.builder().vsum(vsum).metaModel(projectMetaModel).build();
    vsumMetaModel.setName(name);
    when(vsumMetaModelRepository.findByVsumAndMetaModel_Source_Id(vsum, META_MODEL_ID))
        .thenReturn(Optional.of(vsumMetaModel));
  }

  private void givenEcoreUpdateChanges(boolean changed) {
    when(metaModelService.updateProjectEcore(any(MetaModel.class), any(byte[].class)))
        .thenAnswer(
            invocation -> {
              assertThat((byte[]) invocation.getArgument(1))
                  .isEqualTo("<edited/>".getBytes(StandardCharsets.UTF_8));
              return changed;
            });
  }

  private void givenRevertableHistory(VsumRole role) {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(CALLER_EMAIL))
        .thenReturn(Optional.of(editor));
    when(vsumHistoryRepository.findById(HISTORY_ID))
        .thenReturn(
            Optional.of(
                VsumHistory.builder()
                    .id(HISTORY_ID)
                    .vsum(vsum)
                    .representation(new VsumRepresentation())
                    .build()));
    givenMember(role);
  }

  /** Collects the scheduled notifications instead of running them in the background. */
  static class RecordingExecutor implements Executor {
    private final List<Runnable> tasks = new ArrayList<>();

    @Override
    public synchronized void execute(Runnable task) {
      tasks.add(task);
    }

    synchronized int scheduled() {
      return tasks.size();
    }

    synchronized void clear() {
      tasks.clear();
    }

    void runAll() {
      List<Runnable> toRun;
      synchronized (this) {
        toRun = List.copyOf(tasks);
        tasks.clear();
      }
      toRun.forEach(Runnable::run);
    }
  }

  @SpringBootConfiguration
  @ImportAutoConfiguration({
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    TransactionAutoConfiguration.class
  })
  @Import({
    VsumService.class,
    VsumHistoryService.class,
    VsumUserService.class,
    UserService.class,
    CollaborationReloadListener.class,
    CollaborationServiceApiHandler.class,
    KeycloakApiHandler.class
  })
  static class TestApplication {

    @Bean(name = CollaborationReloadConfig.COLLABORATION_RELOAD_EXECUTOR)
    RecordingExecutor collaborationReloadExecutor() {
      return new RecordingExecutor();
    }
  }
}
