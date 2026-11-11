package tools.vitruv.methodologist.vsum.model.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.vitruv.methodologist.exception.BuildProblem;
import tools.vitruv.methodologist.user.RoleType;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumBuild;
import tools.vitruv.methodologist.vsum.model.VsumBuildStatus;

/**
 * Stores builds through JPA in an in-memory H2 database in PostgreSQL mode, to check that the
 * problems of a failed build survive the round trip through their JSON column. The context contains
 * only the data source, JPA and {@link VsumBuildRepository}.
 */
@SpringBootTest(
    classes = VsumBuildRepositoryTest.JpaTestApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:vsum-build-repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false"
    })
class VsumBuildRepositoryTest {

  private static final BuildProblem PROBLEM =
      new BuildProblem(
          "REACTIONS",
          "my.reactions",
          12L,
          51,
          30,
          "unknown class",
          "new model2::Entitiy",
          "Did you mean 'Entity'?",
          3);

  @Autowired private VsumBuildRepository vsumBuildRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void storesTheErrorCodeAndProblems_andReadsThemAsFailure() {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    List<BuildProblem> problems =
        List.of(
            PROBLEM,
            new BuildProblem(
                "INFRASTRUCTURE", null, null, null, null, "offline", null, null, null));
    Long buildId =
        transactionTemplate.execute(
            status -> {
              VsumBuild build = failedBuild();
              build.setErrorCode("VSUM_REACTIONS_ERROR");
              build.setProblems(problems);
              return vsumBuildRepository.save(build).getId();
            });

    VsumBuild stored =
        transactionTemplate.execute(status -> vsumBuildRepository.findById(buildId).orElseThrow());
    assertThat(stored.getErrorCode()).isEqualTo("VSUM_REACTIONS_ERROR");
    assertThat(stored.getProblems()).isEqualTo(problems);

    VsumBuildRepository.Failure failure =
        transactionTemplate.execute(
            status -> vsumBuildRepository.findFailureById(buildId).orElseThrow());
    assertThat(failure.getErrorMessage()).isEqualTo("rejected");
    assertThat(failure.getErrorCode()).isEqualTo("VSUM_REACTIONS_ERROR");
    assertThat(failure.getProblems()).isEqualTo(problems);
  }

  @Test
  void readsAFailureWithoutProblems() {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    Long buildId =
        transactionTemplate.execute(status -> vsumBuildRepository.save(failedBuild()).getId());

    VsumBuildRepository.Failure failure =
        transactionTemplate.execute(
            status -> vsumBuildRepository.findFailureById(buildId).orElseThrow());
    assertThat(failure.getErrorMessage()).isEqualTo("rejected");
    assertThat(failure.getErrorCode()).isNull();
    assertThat(failure.getProblems()).isNull();
    assertThat(vsumBuildRepository.findFailureById(-1L)).isEmpty();
  }

  private VsumBuild failedBuild() {
    User user =
        User.builder()
            .email("x@y.com")
            .roleType(RoleType.USER)
            .username("x")
            .firstName("X")
            .lastName("Y")
            .build();
    entityManager.persist(user);
    Vsum vsum = Vsum.builder().name("vsum").user(user).build();
    entityManager.persist(vsum);
    return VsumBuild.builder()
        .vsum(vsum)
        .requestedBy(user)
        .fingerprint("f")
        .status(VsumBuildStatus.FAILED)
        .errorMessage("rejected")
        .build();
  }

  /** Minimal application with only the data source, JPA and the build repository. */
  @SpringBootConfiguration
  @ImportAutoConfiguration({
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    TransactionAutoConfiguration.class
  })
  @EntityScan(basePackages = "tools.vitruv.methodologist")
  @EnableJpaRepositories(
      basePackageClasses = VsumBuildRepository.class,
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.ASSIGNABLE_TYPE,
              classes = VsumBuildRepository.class))
  static class JpaTestApplication {}
}
