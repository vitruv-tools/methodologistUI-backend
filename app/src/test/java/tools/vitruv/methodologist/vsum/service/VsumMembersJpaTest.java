package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.hibernate.Hibernate;
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
import tools.vitruv.methodologist.general.event.VsumAccessChangedEvent;
import tools.vitruv.methodologist.user.RoleType;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMemberAccessResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMetaModelResponse;
import tools.vitruv.methodologist.vsum.mapper.LowCodeReactionRequestMapper;
import tools.vitruv.methodologist.vsum.mapper.MetaModelMapper;
import tools.vitruv.methodologist.vsum.mapper.MetaModelRelationMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumUserMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumViewMapper;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewRepository;

/**
 * Lists and removes the members of VSUMs through JPA in an in-memory H2 database. It checks the
 * derived queries behind the collaboration server's members endpoint, which must neither load the
 * VSUM nor its users, and that removing a member deletes the membership for good: the VSUM fetches
 * its members eagerly and cascades to them, so loading it during the removal would store the
 * deleted membership again. It also checks that a user with two memberships in one VSUM gets the
 * same role from the details and from the members endpoint, whatever order the VSUM's set of
 * members happens to have.
 */
@SpringBootTest(
    classes = VsumMembersJpaTest.JpaTestApplication.class,
    properties = {
      // jsonb columns are created as JSON
      "spring.datasource.url=jdbc:h2:mem:vsum-members;DB_CLOSE_DELAY=-1"
          + ";INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false"
    })
class VsumMembersJpaTest {

  @Autowired private VsumRepository vsumRepository;
  @Autowired private VsumUserRepository vsumUserRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;

  private TransactionTemplate transaction;

  @BeforeEach
  void setUp() {
    transaction = new TransactionTemplate(transactionManager);
  }

  @Test
  void activeMembers_areTheUsersOfTheVsumWithoutRemovedAccounts_andAreNotLoaded() {
    Fixture fixture = transaction.execute(status -> fixture("list"));

    transaction.executeWithoutResult(
        status -> {
          List<VsumUser> members =
              vsumUserRepository.findAllByVsum_IdAndUser_RemovedAtIsNull(fixture.vsumId());

          assertThat(members)
              .extracting(member -> member.getUser().getId(), VsumUser::getRole)
              .containsExactlyInAnyOrder(
                  tuple(fixture.ownerId(), VsumRole.OWNER),
                  tuple(fixture.memberId(), VsumRole.MEMBER),
                  tuple(fixture.viewerId(), VsumRole.VIEWER));
          assertThat(members)
              .allSatisfy(
                  member -> {
                    assertThat(Hibernate.isInitialized(member.getUser())).isFalse();
                    assertThat(Hibernate.isInitialized(member.getVsum())).isFalse();
                  });
        });
  }

  @Test
  void activeVsum_existsOnlyUntilItIsMovedToTheTrash() {
    Fixture fixture = transaction.execute(status -> fixture("exists"));

    assertThat(vsumRepository.existsByIdAndRemovedAtIsNull(fixture.vsumId())).isTrue();
    assertThat(vsumRepository.existsByIdAndRemovedAtIsNull(-1L)).isFalse();

    transaction.executeWithoutResult(
        status ->
            vsumRepository.findById(fixture.vsumId()).orElseThrow().setRemovedAt(Instant.now()));

    assertThat(vsumRepository.existsByIdAndRemovedAtIsNull(fixture.vsumId())).isFalse();
  }

  @Test
  void deleteMember_deletesTheMembershipForGood_andPublishesTheAccessChange() {
    Fixture fixture = transaction.execute(status -> fixture("delete"));
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    VsumUserService service =
        new VsumUserService(
            vsumRepository,
            vsumUserRepository,
            userRepository,
            mock(VsumUserMapper.class),
            publisher);

    transaction.executeWithoutResult(
        status -> service.deleteMember(fixture.ownerEmail(), fixture.memberMembershipId()));

    assertThat(vsumUserRepository.existsById(fixture.memberMembershipId())).isFalse();
    List<Long> remainingUserIds =
        transaction.execute(
            status ->
                vsumUserRepository
                    .findAllByVsum_IdAndUser_RemovedAtIsNull(fixture.vsumId())
                    .stream()
                    .map(member -> member.getUser().getId())
                    .toList());
    assertThat(remainingUserIds).containsExactlyInAnyOrder(fixture.ownerId(), fixture.viewerId());
    verify(publisher).publishEvent(new VsumAccessChangedEvent(fixture.vsumId()));
  }

  @Test
  void duplicateMemberships_resolveToTheMostPrivilegedRole_inTheDetailsAndInTheMembers() {
    Fixture fixture = transaction.execute(status -> fixture("duplicate"));
    transaction.executeWithoutResult(
        status ->
            membership(
                entityManager.getReference(Vsum.class, fixture.vsumId()),
                entityManager.getReference(User.class, fixture.viewerId()),
                VsumRole.MEMBER));
    VsumService service = vsumService();

    VsumRole detailsRole =
        transaction.execute(
            status ->
                service.findVsumWithDetails(fixture.viewerEmail(), fixture.vsumId()).getRole());
    List<VsumMemberAccessResponse> members =
        transaction.execute(status -> service.findMembersForService(fixture.vsumId()));

    assertThat(detailsRole).isEqualTo(VsumRole.MEMBER);
    assertThat(members)
        .extracting(VsumMemberAccessResponse::getUserId, VsumMemberAccessResponse::getRole)
        .containsExactlyInAnyOrder(
            tuple(fixture.ownerId(), VsumRole.OWNER),
            tuple(fixture.memberId(), VsumRole.MEMBER),
            tuple(fixture.viewerId(), VsumRole.MEMBER));
  }

  /**
   * A VSUM with an owner, a member, a viewer and a member who removed the account, and a second
   * VSUM with its own owner, in which the member is a viewer. The names are unique per test, since
   * the tests share the database.
   */
  private Fixture fixture(String name) {
    User owner = user(name + "-owner", null);
    Vsum vsum = vsum(name, owner);
    membership(vsum, owner, VsumRole.OWNER);
    User viewer = user(name + "-viewer", null);
    membership(vsum, viewer, VsumRole.VIEWER);
    membership(vsum, user(name + "-removed", Instant.now()), VsumRole.MEMBER);

    User other = user(name + "-other", null);
    Vsum otherVsum = vsum(name + "-other", other);
    membership(otherVsum, other, VsumRole.OWNER);
    User member = user(name + "-member", null);
    membership(otherVsum, member, VsumRole.VIEWER);
    VsumUser memberMembership = membership(vsum, member, VsumRole.MEMBER);

    return new Fixture(
        vsum.getId(),
        owner.getId(),
        owner.getEmail(),
        member.getId(),
        viewer.getId(),
        viewer.getEmail(),
        memberMembership.getId());
  }

  /**
   * A VSUM service with the real repositories of VSUMs, users and members. Everything else is a
   * mock; the details of a VSUM are mapped to an otherwise empty response.
   */
  private VsumService vsumService() {
    VsumMapper vsumMapper = mock(VsumMapper.class);
    when(vsumMapper.toVsumMetaModelResponse(any(Vsum.class)))
        .thenAnswer(invocation -> new VsumMetaModelResponse());
    return new VsumService(
        vsumMapper,
        vsumRepository,
        mock(MetaModelMapper.class),
        userRepository,
        vsumUserRepository,
        mock(VsumUserService.class),
        mock(MetaModelRelationMapper.class),
        mock(VsumMetaModelRepository.class),
        mock(VsumHistoryService.class),
        mock(VsumViewRepository.class),
        mock(VsumViewMapper.class),
        mock(LowCodeReactionRequestMapper.class),
        mock(VsumBuildService.class),
        mock(MetaModelService.class),
        mock(VsumSyncService.class),
        mock(VsumCleanupService.class),
        mock(VsumValidationService.class),
        mock(ApplicationEventPublisher.class));
  }

  private User user(String name, Instant removedAt) {
    User user =
        User.builder()
            .email(name + "@example.com")
            .roleType(RoleType.USER)
            .username(name)
            .firstName("First")
            .lastName("Last")
            .removedAt(removedAt)
            .build();
    entityManager.persist(user);
    return user;
  }

  private Vsum vsum(String name, User owner) {
    Vsum vsum = Vsum.builder().name(name).user(owner).build();
    entityManager.persist(vsum);
    return vsum;
  }

  private VsumUser membership(Vsum vsum, User user, VsumRole role) {
    VsumUser vsumUser = VsumUser.builder().vsum(vsum).user(user).role(role).build();
    entityManager.persist(vsumUser);
    return vsumUser;
  }

  private record Fixture(
      Long vsumId,
      Long ownerId,
      String ownerEmail,
      Long memberId,
      Long viewerId,
      String viewerEmail,
      Long memberMembershipId) {}

  /** Minimal application with only the data source, JPA and the repositories of members. */
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
              classes = {VsumRepository.class, VsumUserRepository.class, UserRepository.class}))
  static class JpaTestApplication {}
}
