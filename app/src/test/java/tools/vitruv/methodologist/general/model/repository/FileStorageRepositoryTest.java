package tools.vitruv.methodologist.general.model.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.user.RoleType;
import tools.vitruv.methodologist.user.model.User;

/**
 * Runs the duplicate check of {@link FileStorageRepository} against an in-memory H2 database, as
 * the test profile does. The context contains only the data source, JPA and the file repository.
 */
@SpringBootTest(
    classes = FileStorageRepositoryTest.JpaTestApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:file-storage-repository;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false"
    })
@Transactional
class FileStorageRepositoryTest {

  private static final byte[] CONTENT = "reaction".getBytes(StandardCharsets.UTF_8);
  private static final String SHA = "sha-of-reaction";

  @Autowired private FileStorageRepository fileStorageRepository;
  @Autowired private EntityManager entityManager;

  @Test
  void duplicateCheck_ignoresTheCopiesOfStableVersions() {
    User publisher = user("publisher@example.com");
    fileStorageRepository.save(file(publisher, FileEnumType.VSUM_VERSION_FILE));

    assertThat(exists(publisher)).isFalse();
  }

  @Test
  void duplicateCheck_stillFindsTheUsersOwnUploads() {
    User publisher = user("publisher@example.com");
    fileStorageRepository.save(file(publisher, FileEnumType.VSUM_VERSION_FILE));
    fileStorageRepository.save(file(publisher, FileEnumType.REACTION));

    assertThat(exists(publisher)).isTrue();
  }

  @Test
  void duplicateCheck_ignoresTheFilesOfOtherUsers() {
    User publisher = user("publisher@example.com");
    fileStorageRepository.save(file(user("other@example.com"), FileEnumType.REACTION));

    assertThat(exists(publisher)).isFalse();
  }

  private boolean exists(User user) {
    return fileStorageRepository.existsByUserAndSha256AndSizeBytesAndTypeNot(
        user, SHA, CONTENT.length, FileEnumType.VSUM_VERSION_FILE);
  }

  private User user(String email) {
    User user =
        User.builder()
            .email(email)
            .roleType(RoleType.USER)
            .username(email)
            .firstName("First")
            .lastName("Last")
            .build();
    entityManager.persist(user);
    return user;
  }

  private static FileStorage file(User owner, FileEnumType type) {
    return FileStorage.builder()
        .filename("x.reactions")
        .type(type)
        .contentType("text/plain")
        .sizeBytes(CONTENT.length)
        .sha256(SHA)
        .data(CONTENT)
        .user(owner)
        .build();
  }

  /** Minimal application with only the data source, JPA and the file repository. */
  @SpringBootConfiguration
  @ImportAutoConfiguration({
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    TransactionAutoConfiguration.class
  })
  @EntityScan(basePackages = "tools.vitruv.methodologist")
  @EnableJpaRepositories(
      basePackageClasses = FileStorageRepository.class,
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.ASSIGNABLE_TYPE,
              classes = FileStorageRepository.class))
  static class JpaTestApplication {}
}
