package tools.vitruv.methodologist.general.model.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
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
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.VsumUser;

/**
 * Runs the JPQL of {@link FileStorageRepository#isReadableBy(Long, String)} and {@link
 * FileStorageRepository#findAttachableReactionFiles(Set, String, Vsum)} against an in-memory H2
 * database, since a mocked repository cannot show that the access rule itself is right. The
 * persistence unit is built by hand, without a Spring Boot context.
 */
class FileStorageRepositoryAccessTest {

  private static LocalContainerEntityManagerFactoryBean factoryBean;
  private static EntityManagerFactory emf;

  private EntityManager em;
  private User uploader;
  private User member;
  private User stranger;
  private FileStorage ecore;
  private Vsum vsum;

  @BeforeAll
  static void startPersistence() {
    var dataSource =
        new DriverManagerDataSource(
            // H2 has no jsonb type, which FineGranularMetaModelRelation maps to.
            "jdbc:h2:mem:file-access;DB_CLOSE_DELAY=-1;"
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
    em.createQuery("delete from MetaModelRelation").executeUpdate();
    em.createQuery("delete from VsumMetaModel").executeUpdate();
    em.createQuery("delete from VsumUser").executeUpdate();
    em.createQuery("delete from MetaModel").executeUpdate();
    em.createQuery("delete from Vsum").executeUpdate();
    em.createQuery("delete from FileStorage").executeUpdate();
    em.createQuery("delete from User").executeUpdate();

    uploader = persist(user("uploader@example.com"));
    member = persist(user("member@example.com"));
    stranger = persist(user("stranger@example.com"));
    ecore = persist(file(uploader, FileEnumType.ECORE));
    FileStorage genModel = persist(file(uploader, FileEnumType.GEN_MODEL));

    vsum = new Vsum();
    vsum.setName("project");
    vsum.setUser(member);
    persist(vsum);

    MetaModel metaModel =
        MetaModel.builder()
            .name("mm")
            .description("d")
            .domain("d")
            .keyword(List.of("k"))
            .user(uploader)
            .ecoreFile(ecore)
            .genModelFile(genModel)
            .build();
    persist(metaModel);
    persist(VsumMetaModel.builder().vsum(vsum).metaModel(metaModel).name("mm").build());
    persist(VsumUser.builder().vsum(vsum).user(member).role(VsumRole.MEMBER).build());
    em.flush();
  }

  @org.junit.jupiter.api.AfterEach
  void tearDown() {
    em.getTransaction().rollback();
    em.close();
  }

  @Test
  void uploaderCanRead() {
    assertThat(isReadableBy(ecore, "uploader@example.com")).isTrue();
  }

  @Test
  void emailMatchIgnoresCase() {
    assertThat(isReadableBy(ecore, "UPLOADER@Example.com")).isTrue();
  }

  @Test
  void vsumMemberCanReadFileUploadedBySomeoneElse() {
    assertThat(isReadableBy(ecore, "member@example.com")).isTrue();
  }

  @Test
  void strangerCannotRead() {
    assertThat(isReadableBy(ecore, "stranger@example.com")).isFalse();
  }

  @Test
  void removedVsumNoLongerGrantsAccess() {
    vsum.setRemovedAt(Instant.now());
    em.flush();

    assertThat(isReadableBy(ecore, "member@example.com")).isFalse();
  }

  @Test
  void removedMemberCannotRead() {
    member.setRemovedAt(Instant.now());
    em.flush();

    assertThat(isReadableBy(ecore, "member@example.com")).isFalse();
  }

  @Test
  void removedUploaderCannotReadOwnFile() {
    uploader.setRemovedAt(Instant.now());
    em.flush();

    assertThat(isReadableBy(ecore, "uploader@example.com")).isFalse();
  }

  @Test
  void unknownFileIsNotReadable() {
    assertThat(isReadableBy(-1L, "uploader@example.com")).isFalse();
  }

  @Test
  void ownReactionFileIsAttachable() {
    FileStorage reaction = persist(file(uploader, FileEnumType.REACTION));

    assertThat(attachable(reaction, "uploader@example.com")).containsExactly(reaction);
  }

  @Test
  void otherUsersReactionFileIsNotAttachable() {
    FileStorage reaction = persist(file(uploader, FileEnumType.REACTION));

    assertThat(attachable(reaction, "stranger@example.com")).isEmpty();
    assertThat(attachable(reaction, "member@example.com")).isEmpty();
  }

  @Test
  void reactionFileAlreadyUsedInTheVsumStaysAttachableForMembers() {
    FileStorage reaction = persist(file(uploader, FileEnumType.REACTION));
    persistRelation(vsum, reaction);

    assertThat(attachable(reaction, "member@example.com")).containsExactly(reaction);
  }

  @Test
  void reactionFileUsedInAnotherVsumIsNotAttachable() {
    FileStorage reaction = persist(file(uploader, FileEnumType.REACTION));
    Vsum other = new Vsum();
    other.setName("other");
    other.setUser(stranger);
    persist(other);
    persistRelation(other, reaction);

    assertThat(attachable(reaction, "member@example.com")).isEmpty();
  }

  @Test
  void ownFileOfAnotherTypeIsNotAttachableAsReaction() {
    assertThat(attachable(ecore, "uploader@example.com")).isEmpty();
  }

  private List<FileStorage> attachable(FileStorage file, String email) {
    return em.createQuery(
            query("findAttachableReactionFiles", Set.class, String.class, Vsum.class),
            FileStorage.class)
        .setParameter("fileIds", Set.of(file.getId()))
        .setParameter("callerEmail", email)
        .setParameter("vsum", vsum)
        .getResultList();
  }

  private void persistRelation(Vsum owner, FileStorage reaction) {
    MetaModel source = persist(metaModel("src"));
    MetaModel target = persist(metaModel("tgt"));
    persist(
        MetaModelRelation.builder()
            .vsum(owner)
            .source(source)
            .target(target)
            .reactionFileStorage(reaction)
            .build());
    em.flush();
  }

  private MetaModel metaModel(String name) {
    return MetaModel.builder()
        .name(name)
        .description("d")
        .domain("d")
        .keyword(List.of("k"))
        .user(uploader)
        .ecoreFile(persist(file(uploader, FileEnumType.ECORE)))
        .genModelFile(persist(file(uploader, FileEnumType.GEN_MODEL)))
        .build();
  }

  private boolean isReadableBy(FileStorage file, String email) {
    return isReadableBy(file.getId(), email);
  }

  private boolean isReadableBy(Long fileId, String email) {
    return em.createQuery(readableQuery(), Boolean.class)
        .setParameter("fileId", fileId)
        .setParameter("callerEmail", email)
        .getSingleResult();
  }

  private static String readableQuery() {
    return query("isReadableBy", Long.class, String.class);
  }

  /** Reads the JPQL from the repository annotation so the test cannot drift from production. */
  private static String query(String method, Class<?>... parameterTypes) {
    try {
      return FileStorageRepository.class
          .getMethod(method, parameterTypes)
          .getAnnotation(org.springframework.data.jpa.repository.Query.class)
          .value();
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  private <T> T persist(T entity) {
    em.persist(entity);
    return entity;
  }

  private static User user(String email) {
    return User.builder()
        .email(email)
        .roleType(RoleType.USER)
        .username(email)
        .firstName("f")
        .lastName("l")
        .build();
  }

  private static FileStorage file(User owner, FileEnumType type) {
    return FileStorage.builder()
        .filename("a.ecore")
        .type(type)
        .contentType("application/xml")
        .sizeBytes(1)
        .sha256("x")
        .data(new byte[] {1})
        .user(owner)
        .build();
  }
}
