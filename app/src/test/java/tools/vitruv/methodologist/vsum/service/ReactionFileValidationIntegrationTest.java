package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.event.ReactionFileUpdatedEvent;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.user.RoleType;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.repository.FineGranularMetaModelRelationRepository;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRelationRepository;

@SpringBootTest(
    classes = ReactionFileValidationIntegrationTest.JpaTestApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:reaction-file-validation;DB_CLOSE_DELAY=-1;"
          + "INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false"
    })
class ReactionFileValidationIntegrationTest {

  private static final long WAIT_MILLIS = 5_000;

  @Autowired private MetaModelRelationRepository metaModelRelationRepository;

  @Autowired
  private FineGranularMetaModelRelationRepository fineGranularMetaModelRelationRepository;

  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private VsumValidationService vsumValidationService;

  private TransactionTemplate transaction;
  private Fixture fixture;

  @BeforeEach
  void setUp() {
    transaction = new TransactionTemplate(transactionManager);
    fixture = transaction.execute(status -> createFixture());
  }

  @Test
  void findVsumIdsByReactionFileId_findsTheVsumsOfCoarseRelations() {
    assertThat(metaModelRelationRepository.findVsumIdsByReactionFileId(fixture.sharedFileId()))
        .containsExactlyInAnyOrder(fixture.coarseVsumId(), fixture.bothVsumId());
    assertThat(metaModelRelationRepository.findVsumIdsByReactionFileId(fixture.otherFileId()))
        .isEmpty();
  }

  @Test
  void findVsumIdsByReactionFileId_findsTheVsumsOfFineGranularRelations() {
    assertThat(
            fineGranularMetaModelRelationRepository.findVsumIdsByReactionFileId(
                fixture.sharedFileId()))
        .containsExactlyInAnyOrder(fixture.fineVsumId(), fixture.bothVsumId());
    assertThat(
            fineGranularMetaModelRelationRepository.findVsumIdsByReactionFileId(
                fixture.otherFileId()))
        .containsExactly(fixture.fineVsumId());
  }

  @Test
  void requestsValidationOfEveryAffectedVsumOnce_afterTheCommit() {
    transaction.executeWithoutResult(
        status ->
            eventPublisher.publishEvent(new ReactionFileUpdatedEvent(fixture.sharedFileId())));

    verify(vsumValidationService, timeout(WAIT_MILLIS)).requestValidation(fixture.coarseVsumId());
    verify(vsumValidationService, timeout(WAIT_MILLIS)).requestValidation(fixture.fineVsumId());
    verify(vsumValidationService, timeout(WAIT_MILLIS)).requestValidation(fixture.bothVsumId());
    verifyNoMoreInteractions(vsumValidationService);
  }

  @Test
  void requestsNoValidation_whenTheTransactionRollsBack() {
    transaction.executeWithoutResult(
        status -> {
          eventPublisher.publishEvent(new ReactionFileUpdatedEvent(fixture.sharedFileId()));
          status.setRollbackOnly();
        });
    transaction.executeWithoutResult(
        status -> eventPublisher.publishEvent(new ReactionFileUpdatedEvent(fixture.otherFileId())));

    verify(vsumValidationService, timeout(WAIT_MILLIS)).requestValidation(fixture.fineVsumId());
    verifyNoMoreInteractions(vsumValidationService);
  }

  @Test
  void requestsNoValidation_whenPublishedOutsideATransaction() {
    eventPublisher.publishEvent(new ReactionFileUpdatedEvent(fixture.sharedFileId()));
    transaction.executeWithoutResult(
        status -> eventPublisher.publishEvent(new ReactionFileUpdatedEvent(fixture.otherFileId())));

    verify(vsumValidationService, timeout(WAIT_MILLIS)).requestValidation(fixture.fineVsumId());
    verifyNoMoreInteractions(vsumValidationService);
  }

  private Fixture createFixture() {
    User user =
        User.builder()
            .email("owner-" + System.nanoTime() + "@example.com")
            .roleType(RoleType.USER)
            .username("owner-" + System.nanoTime())
            .firstName("O")
            .lastName("W")
            .build();
    entityManager.persist(user);
    FileStorage sharedFile = file(user, "shared.reactions", FileEnumType.REACTION);
    FileStorage otherFile = file(user, "other.reactions", FileEnumType.REACTION);
    MetaModel source = metaModel(user, "source");
    MetaModel target = metaModel(user, "target");

    Vsum coarseVsum = vsum(user, "coarse");
    relation(coarseVsum, source, target, sharedFile);

    Vsum fineVsum = vsum(user, "fine");
    MetaModelRelation fineRelation = relation(fineVsum, source, target, null);
    fineGranular(fineRelation, sharedFile);
    fineGranular(fineRelation, otherFile);

    Vsum bothVsum = vsum(user, "both");
    MetaModelRelation bothRelation = relation(bothVsum, source, target, sharedFile);
    fineGranular(bothRelation, sharedFile);

    return new Fixture(
        sharedFile.getId(),
        otherFile.getId(),
        coarseVsum.getId(),
        fineVsum.getId(),
        bothVsum.getId());
  }

  private FileStorage file(User user, String name, FileEnumType type) {
    byte[] data = name.getBytes(StandardCharsets.UTF_8);
    FileStorage file =
        FileStorage.builder()
            .filename(name)
            .type(type)
            .contentType("text/plain")
            .sizeBytes(data.length)
            .sha256(name)
            .data(data)
            .user(user)
            .build();
    entityManager.persist(file);
    return file;
  }

  private MetaModel metaModel(User user, String name) {
    MetaModel metaModel =
        MetaModel.builder()
            .name(name)
            .description(name)
            .domain("test")
            .keyword(List.of())
            .user(user)
            .ecoreFile(file(user, name + ".ecore", FileEnumType.ECORE))
            .genModelFile(file(user, name + ".genmodel", FileEnumType.GEN_MODEL))
            .build();
    entityManager.persist(metaModel);
    return metaModel;
  }

  private Vsum vsum(User user, String name) {
    Vsum vsum = Vsum.builder().name(name).user(user).build();
    entityManager.persist(vsum);
    return vsum;
  }

  private MetaModelRelation relation(
      Vsum vsum, MetaModel source, MetaModel target, FileStorage reactionFile) {
    MetaModelRelation relation =
        MetaModelRelation.builder()
            .vsum(vsum)
            .source(source)
            .target(target)
            .reactionFileStorage(reactionFile)
            .build();
    entityManager.persist(relation);
    return relation;
  }

  private void fineGranular(MetaModelRelation relation, FileStorage reactionFile) {
    entityManager.persist(
        FineGranularMetaModelRelation.builder()
            .sourceId("A")
            .targetId("B")
            .metaModelRelation(relation)
            .reactionFileStorage(reactionFile)
            .build());
  }

  private record Fixture(
      Long sharedFileId, Long otherFileId, Long coarseVsumId, Long fineVsumId, Long bothVsumId) {}

  @SpringBootConfiguration
  @ImportAutoConfiguration({
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    TransactionAutoConfiguration.class
  })
  @EntityScan(basePackages = "tools.vitruv.methodologist")
  @EnableJpaRepositories(
      basePackageClasses = MetaModelRelationRepository.class,
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.ASSIGNABLE_TYPE,
              classes = {
                MetaModelRelationRepository.class,
                FineGranularMetaModelRelationRepository.class
              }))
  @Import(ReactionFileValidationListener.class)
  static class JpaTestApplication {}
}
