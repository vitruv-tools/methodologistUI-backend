package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.mapper.FileStorageMapperImpl;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.general.service.FileStorageService;
import tools.vitruv.methodologist.user.RoleType;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.ViewRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumSyncChangesPutRequest;
import tools.vitruv.methodologist.vsum.mapper.LowCodeReactionRequestMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumHistoryMapperImpl;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.VsumView;
import tools.vitruv.methodologist.vsum.model.VsumViewMetaModel;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRelationRepository;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewRepository;

/**
 * Syncs the views of a VSUM through JPA in an in-memory H2 database, to check the order in which
 * Hibernate writes the changes: links of views have to be deleted before the meta models they
 * reference, and links deleted from a view must not be loaded again when the VSUM is saved. The
 * services that do not touch views are mocked; the mocked history snapshot loads the links of the
 * views, as the real one does.
 */
@SpringBootTest(
    classes = VsumSyncServiceJpaTest.JpaTestApplication.class,
    properties = {
      // jsonb columns, of entities this test does not store, are created as JSON
      "spring.datasource.url=jdbc:h2:mem:vsum-sync;DB_CLOSE_DELAY=-1"
          + ";INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false"
    })
class VsumSyncServiceJpaTest {

  @Autowired private VsumRepository vsumRepository;
  @Autowired private VsumMetaModelRepository vsumMetaModelRepository;
  @Autowired private MetaModelRepository metaModelRepository;
  @Autowired private MetaModelRelationRepository metaModelRelationRepository;
  @Autowired private VsumViewRepository vsumViewRepository;
  @Autowired private VsumViewMetaModelRepository vsumViewMetaModelRepository;
  @Autowired private FileStorageRepository fileStorageRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;

  private VsumSyncService service;
  private TransactionTemplate transaction;

  @BeforeEach
  void setUp() {
    VsumHistoryService vsumHistoryService = mock(VsumHistoryService.class);
    when(vsumHistoryService.create(any(), any()))
        .thenAnswer(
            invocation -> {
              new VsumHistoryMapperImpl().toVsumRepresentation(invocation.getArgument(0));
              return null;
            });
    FileStorageService fileStorageService =
        new FileStorageService(
            fileStorageRepository,
            userRepository,
            new FileStorageMapperImpl(),
            mock(ApplicationEventPublisher.class));
    service =
        new VsumSyncService(
            vsumRepository,
            new VsumMetaModelService(
                vsumMetaModelRepository,
                mock(MetaModelService.class),
                metaModelRepository,
                entityManager,
                fileStorageService),
            vsumMetaModelRepository,
            mock(MetaModelRelationService.class),
            metaModelRelationRepository,
            vsumHistoryService,
            new VsumViewMetaModelService(
                vsumViewMetaModelRepository, metaModelRepository, vsumMetaModelRepository),
            new VsumViewService(
                vsumViewRepository, fileStorageRepository, vsumViewMetaModelRepository),
            vsumViewRepository,
            vsumViewMetaModelRepository,
            mock(FineGranularMetaModelRelationService.class),
            mock(LowCodeReactionRequestMapper.class));
    transaction = new TransactionTemplate(transactionManager);
  }

  @Test
  void changesTheNameAndMetaModelsOfAView_whileRenamingAMetaModel() {
    Fixture fixture = transaction.execute(status -> fixture("rename"));

    VsumSyncChangesPutRequest request = new VsumSyncChangesPutRequest();
    request.setMetaModelIds(List.of(fixture.sourceA(), fixture.sourceB()));
    request.setMetaModelNames(Map.of(fixture.sourceB(), "B2"));
    request.setViewRequests(
        List.of(
            viewRequest(fixture.brakeId(), "Braking", fixture.brakeFileId(), fixture.sourceB())));
    sync(fixture, request);

    transaction.executeWithoutResult(
        status -> {
          VsumView brake = vsumViewRepository.findById(fixture.brakeId()).orElseThrow();
          assertThat(brake.getName()).isEqualTo("Braking");
          assertThat(linkedSourceIds(brake)).containsExactly(fixture.sourceB());
          assertThat(vsumViewRepository.findById(fixture.wheelId())).isEmpty();
        });
  }

  @Test
  void removesAMetaModelFromTheVsum_thatViewsAreLinkedTo() {
    Fixture fixture = transaction.execute(status -> fixture("remove"));

    VsumSyncChangesPutRequest request = new VsumSyncChangesPutRequest();
    request.setMetaModelIds(List.of(fixture.sourceB()));
    request.setViewRequests(
        List.of(
            viewRequest(
                fixture.brakeId(),
                "Brake",
                fixture.brakeFileId(),
                fixture.sourceA(),
                fixture.sourceB())));
    sync(fixture, request);

    transaction.executeWithoutResult(
        status -> {
          VsumView brake = vsumViewRepository.findById(fixture.brakeId()).orElseThrow();
          assertThat(linkedSourceIds(brake)).containsExactly(fixture.sourceB());
          assertThat(vsumViewRepository.findById(fixture.wheelId())).isEmpty();
          Vsum vsum = vsumRepository.findById(fixture.vsumId()).orElseThrow();
          assertThat(vsumMetaModelRepository.findAllByVsum(vsum))
              .extracting(vsumMetaModel -> vsumMetaModel.getMetaModel().getSource().getId())
              .containsExactly(fixture.sourceB());
        });
  }

  private void sync(Fixture fixture, VsumSyncChangesPutRequest request) {
    transaction.executeWithoutResult(
        status -> {
          Vsum vsum = vsumRepository.findById(fixture.vsumId()).orElseThrow();
          User user = userRepository.findById(fixture.userId()).orElseThrow();
          service.applySyncChanges(vsum, user, request, true);
        });
  }

  private List<Long> linkedSourceIds(VsumView view) {
    return vsumViewMetaModelRepository.findAllByVsumView(view).stream()
        .map(VsumViewMetaModelService::sourceMetaModelId)
        .toList();
  }

  private static ViewRequest viewRequest(
      Long id, String name, Long fileStorageId, Long... metaModelIds) {
    return ViewRequest.builder()
        .id(id)
        .name(name)
        .fileStorageId(fileStorageId)
        .metaModelIds(List.of(metaModelIds))
        .build();
  }

  /**
   * A VSUM with copies of the library meta models A and B, a view "Brake" linked to A, and a view
   * "Wheel" linked to A.
   */
  private Fixture fixture(String key) {
    User user =
        User.builder()
            .email(key + "@example.com")
            .roleType(RoleType.USER)
            .username(key)
            .firstName("First")
            .lastName("Last")
            .build();
    entityManager.persist(user);
    MetaModel sourceA = metaModel(user, "A", null);
    MetaModel sourceB = metaModel(user, "B", null);
    Vsum vsum = Vsum.builder().name("vsum").user(user).build();
    entityManager.persist(vsum);
    MetaModel copyA = metaModel(user, "A", sourceA);
    MetaModel copyB = metaModel(user, "B", sourceB);
    for (MetaModel copy : List.of(copyA, copyB)) {
      entityManager.persist(
          VsumMetaModel.builder().vsum(vsum).metaModel(copy).name(copy.getName()).build());
    }
    VsumView brake = view(vsum, user, "Brake", copyA);
    VsumView wheel = view(vsum, user, "Wheel", copyA);
    return new Fixture(
        vsum.getId(),
        user.getId(),
        sourceA.getId(),
        sourceB.getId(),
        brake.getId(),
        brake.getFileStorage().getId(),
        wheel.getId());
  }

  private MetaModel metaModel(User user, String name, MetaModel source) {
    MetaModel metaModel =
        MetaModel.builder()
            .name(name)
            .description(name)
            .domain("test")
            .keyword(List.of())
            .user(user)
            .ecoreFile(file(user, name + ".ecore", FileEnumType.ECORE))
            .genModelFile(file(user, name + ".genmodel", FileEnumType.GEN_MODEL))
            .source(source)
            .build();
    entityManager.persist(metaModel);
    return metaModel;
  }

  private VsumView view(Vsum vsum, User user, String name, MetaModel metaModel) {
    VsumView view =
        VsumView.builder()
            .vsum(vsum)
            .name(name)
            .fileStorage(file(user, name + ".nj", FileEnumType.NEO_JOIN))
            .build();
    entityManager.persist(view);
    entityManager.persist(VsumViewMetaModel.builder().vsumView(view).metaModel(metaModel).build());
    return view;
  }

  private FileStorage file(User user, String name, FileEnumType type) {
    byte[] data = name.getBytes(StandardCharsets.UTF_8);
    FileStorage file = new FileStorage();
    file.setFilename(name);
    file.setType(type);
    file.setContentType("text/plain");
    file.setSizeBytes(data.length);
    file.setSha256(name);
    file.setData(data);
    file.setUser(user);
    entityManager.persist(file);
    return file;
  }

  private record Fixture(
      Long vsumId,
      Long userId,
      Long sourceA,
      Long sourceB,
      Long brakeId,
      Long brakeFileId,
      Long wheelId) {}

  /** Minimal application with only the data source, JPA and the repositories the sync uses. */
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
              classes = {
                VsumRepository.class,
                VsumMetaModelRepository.class,
                MetaModelRepository.class,
                MetaModelRelationRepository.class,
                VsumViewRepository.class,
                VsumViewMetaModelRepository.class,
                FileStorageRepository.class,
                UserRepository.class
              }))
  static class JpaTestApplication {}
}
