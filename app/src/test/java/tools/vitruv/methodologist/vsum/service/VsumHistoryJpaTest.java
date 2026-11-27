package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.vitruv.methodologist.user.RoleType;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumRepresentation;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.mapper.LowCodeReactionRequestMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumHistoryMapperImpl;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumHistory;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.VsumHistoryRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;

/**
 * Stores history snapshots through JPA in an in-memory H2 database, to check the queries behind the
 * snapshots of collaboration server writebacks (issue #445) and behind the history limit.
 *
 * <p>As in production, {@code created_at} has no time zone and {@code NULL} sorts above every
 * value. In the tests with several snapshots of one VSUM, the snapshot stored first is mostly not
 * the oldest, so ordering by id instead of by creation time fails them. The service dates its
 * snapshots by the test's clock, which only the test moves; the snapshots the test stores itself
 * get an explicit creation time.
 */
@SpringBootTest(
    classes = VsumHistoryJpaTest.JpaTestApplication.class,
    properties = {
      // H2 has no jsonb type, which the snapshot's representation maps to.
      "spring.datasource.url=jdbc:h2:mem:vsum-history;DB_CLOSE_DELAY=-1"
          + ";DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false"
    })
class VsumHistoryJpaTest {

  /** Not the default of 5 minutes, so that a hard-coded interval fails the tests. */
  private static final Duration INTERVAL = Duration.ofSeconds(90);

  private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
  private static final long LIMIT = 2;

  @Autowired private VsumHistoryRepository vsumHistoryRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;

  private final UserRepository userRepository = mock(UserRepository.class);
  private final VsumUserRepository vsumUserRepository = mock(VsumUserRepository.class);
  private VsumHistoryService service;
  private TransactionTemplate transaction;
  private SettableClock clock;

  @BeforeEach
  void setUp() {
    clock = new SettableClock(NOW);
    service =
        new VsumHistoryService(
            vsumHistoryRepository,
            new VsumHistoryMapperImpl(),
            LIMIT,
            INTERVAL,
            userRepository,
            vsumUserRepository,
            mock(VsumSyncService.class),
            mock(LowCodeReactionRequestMapper.class),
            mock(ApplicationEventPublisher.class),
            clock);
    transaction = new TransactionTemplate(transactionManager);
    // V6 creates created_at without a time zone; Hibernate would create it with one.
    transaction.executeWithoutResult(
        status ->
            entityManager
                .createNativeQuery(
                    "ALTER TABLE vsum_history ALTER COLUMN created_at SET DATA TYPE TIMESTAMP")
                .executeUpdate());
  }

  @Test
  void writeback_storesAMarkedSnapshotOfTheOwner_whenTheVsumHasNone() {
    Vsum vsum = transaction.execute(status -> owner("first"));

    writeback(vsum);

    List<VsumHistory> snapshots = snapshots(vsum);
    assertThat(snapshots).hasSize(1);
    assertThat(snapshots.get(0).isCollaborationWriteback()).isTrue();
    assertThat(snapshots.get(0).getCreator().getId()).isEqualTo(vsum.getUser().getId());
  }

  @Test
  void writeback_storesNoSnapshot_untilTheLatestWritebackSnapshotIsAnIntervalOld() {
    Vsum vsum = transaction.execute(status -> owner("interval"));
    Long latest = snapshot(vsum, true, NOW.minus(INTERVAL).plusSeconds(1));
    snapshot(vsum, false, NOW.minus(INTERVAL).minusSeconds(1));

    writeback(vsum);
    assertThat(snapshots(vsum)).hasSize(2);

    setCreatedAt(latest, NOW.minus(INTERVAL));
    writeback(vsum);
    assertThat(snapshots(vsum)).hasSize(3);
  }

  @Test
  void writeback_datesItsSnapshotByTheClock_andStoresNoOtherUntilItIsAnIntervalOld() {
    Vsum vsum = transaction.execute(status -> owner("service-dated"));

    writeback(vsum);
    assertThat(snapshots(vsum)).extracting(VsumHistory::getCreatedAt).containsExactly(NOW);

    clock.set(NOW.plus(INTERVAL).minusMillis(1));
    writeback(vsum);
    assertThat(snapshots(vsum)).hasSize(1);

    clock.set(NOW.plus(INTERVAL));
    writeback(vsum);
    assertThat(snapshots(vsum))
        .extracting(VsumHistory::getCreatedAt)
        .containsExactlyInAnyOrder(NOW, NOW.plus(INTERVAL));
  }

  @Test
  void writeback_takesTheLaterStoredOfTwoEquallyOldSnapshotsAsTheLatest() {
    Vsum writebackLast = transaction.execute(status -> owner("tie-writeback-last"));
    snapshot(writebackLast, false, NOW.minusSeconds(30));
    snapshot(writebackLast, true, NOW.minusSeconds(30));
    Vsum userLast = transaction.execute(status -> owner("tie-user-last"));
    snapshot(userLast, true, NOW.minusSeconds(30));
    snapshot(userLast, false, NOW.minusSeconds(30));

    writeback(writebackLast);
    writeback(userLast);

    assertThat(snapshots(writebackLast)).hasSize(2);
    assertThat(snapshots(userLast)).hasSize(3);
  }

  @Test
  void writeback_storesASnapshot_whenAUserSnapshotFollowsTheLatestWritebackSnapshot() {
    Vsum vsum = transaction.execute(status -> owner("user-change"));
    snapshot(vsum, false, NOW.minusSeconds(60));
    snapshot(vsum, true, NOW.minusSeconds(120));

    writeback(vsum);

    assertThat(snapshots(vsum)).hasSize(3);
  }

  @Test
  void writeback_ignoresSnapshotsWithoutCreationTime() {
    Vsum vsum = transaction.execute(status -> owner("no-time"));
    snapshot(vsum, false, null);
    snapshot(vsum, true, NOW.minusSeconds(60));

    writeback(vsum);

    assertThat(snapshots(vsum)).hasSize(2);
  }

  @Test
  void writeback_ignoresTheSnapshotsOfOtherVsums() {
    Vsum vsum = transaction.execute(status -> owner("own"));
    Vsum other = transaction.execute(status -> owner("other"));
    snapshot(vsum, true, NOW.minus(Duration.ofHours(1)));
    snapshot(other, true, NOW.minusSeconds(60));

    writeback(vsum);

    assertThat(snapshots(vsum)).hasSize(2);
    assertThat(snapshots(other)).hasSize(1);
  }

  @Test
  void create_deletesTheOldestSnapshot_whenOverTheLimit() {
    Vsum vsum = transaction.execute(status -> owner("limit"));
    Long newest = snapshot(vsum, false, NOW.minus(Duration.ofHours(1)));
    Long oldest = snapshot(vsum, false, NOW.minus(Duration.ofHours(3)));
    Long middle = snapshot(vsum, false, NOW.minus(Duration.ofHours(2)));

    Long created = transaction.execute(status -> service.create(vsum, vsum.getUser()).getId());

    assertThat(ids(vsum)).containsExactlyInAnyOrder(newest, middle, created).doesNotContain(oldest);
  }

  @Test
  void create_deletesTheEarlierStoredOfTwoEquallyOldSnapshots_whenOverTheLimit() {
    Vsum vsum = transaction.execute(status -> owner("tie-limit"));
    Long newest = snapshot(vsum, false, NOW.minus(Duration.ofHours(1)));
    Long earlier = snapshot(vsum, false, NOW.minus(Duration.ofHours(2)));
    Long later = snapshot(vsum, false, NOW.minus(Duration.ofHours(2)));

    Long created = transaction.execute(status -> service.create(vsum, vsum.getUser()).getId());

    assertThat(ids(vsum)).containsExactlyInAnyOrder(newest, later, created).doesNotContain(earlier);
  }

  @Test
  void revert_keepsTheSnapshotRevertedTo_andDeletesTheOldestOther_whenOverTheLimit() {
    Vsum vsum = transaction.execute(status -> owner("revert"));
    Long newest = snapshot(vsum, false, NOW.minus(Duration.ofHours(1)));
    Long target = snapshot(vsum, true, NOW.minus(Duration.ofHours(3)));
    Long oldestOther = snapshot(vsum, false, NOW.minus(Duration.ofHours(2)));
    User user = vsum.getUser();
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(user.getEmail()))
        .thenReturn(Optional.of(user));
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                vsum.getId(), user.getEmail()))
        .thenReturn(Optional.of(VsumUser.builder().role(VsumRole.OWNER).build()));

    transaction.executeWithoutResult(status -> service.revert(user.getEmail(), target));

    List<Long> ids = ids(vsum);
    assertThat(ids).hasSize(3).contains(newest, target).doesNotContain(oldestOther);
  }

  private void writeback(Vsum vsum) {
    transaction.executeWithoutResult(status -> service.createBeforeCollaborationWriteback(vsum));
  }

  /** Stores a snapshot of the given kind and creation time, without the service. */
  private Long snapshot(Vsum vsum, boolean collaborationWriteback, Instant createdAt) {
    Long id =
        transaction.execute(
            status -> {
              VsumHistory history =
                  VsumHistory.builder()
                      .vsum(vsum)
                      .creator(vsum.getUser())
                      .representation(new VsumRepresentation())
                      .collaborationWriteback(collaborationWriteback)
                      .build();
              entityManager.persist(history);
              return history.getId();
            });
    setCreatedAt(id, createdAt);
    return id;
  }

  private void setCreatedAt(Long id, Instant createdAt) {
    transaction.executeWithoutResult(
        status ->
            entityManager
                .createQuery("update VsumHistory h set h.createdAt = :createdAt where h.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", id)
                .executeUpdate());
  }

  private List<VsumHistory> snapshots(Vsum vsum) {
    return transaction.execute(
        status ->
            entityManager
                .createQuery(
                    "select h from VsumHistory h join fetch h.creator where h.vsum.id = :vsumId",
                    VsumHistory.class)
                .setParameter("vsumId", vsum.getId())
                .getResultList());
  }

  private List<Long> ids(Vsum vsum) {
    return snapshots(vsum).stream().map(VsumHistory::getId).toList();
  }

  private Vsum owner(String key) {
    User user =
        User.builder()
            .email(key + "@example.com")
            .roleType(RoleType.USER)
            .username(key)
            .firstName("First")
            .lastName("Last")
            .build();
    entityManager.persist(user);
    Vsum vsum = new Vsum();
    vsum.setName(key);
    vsum.setUser(user);
    entityManager.persist(vsum);
    return vsum;
  }

  /** A clock whose instant only the test changes. */
  private static final class SettableClock extends Clock {
    private Instant instant;

    SettableClock(Instant instant) {
      this.instant = instant;
    }

    void set(Instant instant) {
      this.instant = instant;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }
  }

  /** Minimal application with only the data source, JPA and the history repository. */
  @SpringBootConfiguration
  @ImportAutoConfiguration({
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    TransactionAutoConfiguration.class
  })
  @EntityScan(basePackages = "tools.vitruv.methodologist")
  @EnableJpaRepositories(
      basePackages = "tools.vitruv.methodologist",
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.ASSIGNABLE_TYPE,
              classes = VsumHistoryRepository.class))
  static class JpaTestApplication {}
}
