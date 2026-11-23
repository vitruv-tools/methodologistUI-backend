package tools.vitruv.methodologist.vsum.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.user.RoleType;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.VsumRepresentation;
import tools.vitruv.methodologist.vsum.controller.dto.response.FineGranularMetaModelRelationResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelRelationResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMetaModelResponse;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.CompositeReactionsRequest;

/**
 * Stores a version's content in an in-memory H2 database and loads it again, since the content is
 * kept as JSON and only a real round trip shows that every part of it survives. The persistence
 * unit is built by hand, like in {@code FileStorageRepositoryAccessTest}.
 */
class VsumVersionPersistenceTest {

  private static final Instant PUBLISHED = Instant.parse("2026-11-23T17:56:00Z");

  private static LocalContainerEntityManagerFactoryBean factoryBean;
  private static EntityManagerFactory emf;

  private EntityManager em;
  private User publisher;
  private Vsum vsum;
  private VsumBuild build;
  private FileStorage copy;

  @BeforeAll
  static void startPersistence() {
    var dataSource =
        new DriverManagerDataSource(
            // H2 has no jsonb type, which the version's manifest and content map to.
            "jdbc:h2:mem:version-content;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE DOMAIN IF NOT EXISTS jsonb AS JSON",
            "sa",
            "");
    factoryBean = new LocalContainerEntityManagerFactoryBean();
    factoryBean.setDataSource(dataSource);
    factoryBean.setPackagesToScan("tools.vitruv.methodologist");
    factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factoryBean.setJpaPropertyMap(
        Map.of(
            "hibernate.hbm2ddl.auto",
            "create-drop",
            // Spring Boot's default naming, which the entities' column names assume.
            "hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
            "hibernate.implicit_naming_strategy",
            "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy"));
    factoryBean.afterPropertiesSet();
    emf = factoryBean.getObject();
  }

  @AfterAll
  static void stopPersistence() {
    factoryBean.destroy();
  }

  @BeforeEach
  void setUp() {
    em = emf.createEntityManager();
    em.getTransaction().begin();

    publisher =
        persist(
            User.builder()
                .email("owner@example.com")
                .roleType(RoleType.USER)
                .username("owner@example.com")
                .firstName("f")
                .lastName("l")
                .build());
    vsum = new Vsum();
    vsum.setName("project");
    vsum.setUser(publisher);
    persist(vsum);
    build =
        persist(
            VsumBuild.builder()
                .vsum(vsum)
                .requestedBy(publisher)
                .fingerprint("f")
                .status(VsumBuildStatus.SUCCEEDED)
                .build());
    copy =
        persist(
            FileStorage.builder()
                .filename("a.reactions")
                .type(FileEnumType.VSUM_VERSION_FILE)
                .contentType("application/octet-stream")
                .sizeBytes(1)
                .sha256("x")
                .data(new byte[] {1})
                .user(publisher)
                .build());
  }

  @AfterEach
  void tearDown() {
    em.getTransaction().rollback();
    em.close();
  }

  @Test
  void content_survivesStoringAndLoading() throws Exception {
    JsonNode layout = new ObjectMapper().readTree("{\"metaModels\":{\"6\":{\"x\":1,\"y\":2}}}");
    VsumVersionContent.RuleSet ruleSet =
        new VsumVersionContent.RuleSet(
            3L, "rules", "#3b82f6", "d", copy.getId(), PUBLISHED, PUBLISHED.plusSeconds(1));
    VsumMetaModelResponse project =
        VsumMetaModelResponse.builder()
            .name("p")
            .layout(layout)
            .metaModels(
                List.of(
                    MetaModelResponse.builder()
                        .id(6L)
                        .name("mm")
                        .keyword(List.of("k"))
                        .createdAt(PUBLISHED)
                        .ecoreFileId(copy.getId())
                        .build()))
            .metaModelsRelation(
                List.of(
                    MetaModelRelationResponse.builder()
                        .sourceId(6L)
                        .targetId(12L)
                        .reactionFileStorageId(copy.getId())
                        .fineGranularMetaModelRelationSet(
                            Set.of(
                                FineGranularMetaModelRelationResponse.builder()
                                    .sourceId("A")
                                    .targetId("B")
                                    .reactionFileStorageId(copy.getId())
                                    .lowCodeReactionRequestBase(new CompositeReactionsRequest())
                                    .build()))
                        .build()))
            .build();
    VsumVersion version =
        persist(
            VsumVersion.builder()
                .vsum(vsum)
                .number(1)
                .createdBy(publisher)
                .build(build)
                .manifest(new VsumRepresentation())
                .content(new VsumVersionContent(project, List.of(ruleSet)))
                .contentFiles(List.of(copy))
                .build());

    em.flush();
    em.clear();
    VsumVersion loaded = em.find(VsumVersion.class, version.getId());

    VsumVersionContent content = loaded.getContent();
    assertThat(content.project().getName()).isEqualTo("p");
    assertThat(content.project().getLayout().at("/metaModels/6/x").asInt()).isEqualTo(1);
    assertThat(content.project().getMetaModels())
        .singleElement()
        .satisfies(
            metaModel -> {
              assertThat(metaModel.getKeyword()).containsExactly("k");
              assertThat(metaModel.getCreatedAt()).isEqualTo(PUBLISHED);
              assertThat(metaModel.getEcoreFileId()).isEqualTo(copy.getId());
            });
    assertThat(content.project().getMetaModelsRelation())
        .singleElement()
        .satisfies(
            relation ->
                assertThat(relation.getFineGranularMetaModelRelationSet())
                    .singleElement()
                    .satisfies(
                        fineGrained ->
                            assertThat(fineGrained.getLowCodeReactionRequestBase())
                                .isInstanceOf(CompositeReactionsRequest.class)));
    assertThat(content.ruleSets()).containsExactly(ruleSet);
    assertThat(loaded.getContentFiles())
        .extracting(FileStorage::getId)
        .containsExactly(copy.getId());
  }

  private <T> T persist(T entity) {
    em.persist(entity);
    return entity;
  }
}
