package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.vsum.service.VsumDeploymentBundleService.JAR_ENTRY_NAME;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.vitruv.methodologist.exception.NotFoundException;

@ExtendWith(MockitoExtension.class)
class VsumDeploymentBundleServiceTest {

  private static final String BUILD_EMAIL = "x@y.com";
  private static final Long BUILD_ID = 1L;

  @TempDir private Path tempDir;

  @Mock private VsumBuildService vsumBuildService;

  private VsumDeploymentBundleService service;

  @BeforeEach
  void setUp() {
    service =
        new VsumDeploymentBundleService(
            vsumBuildService, new PathMatchingResourcePatternResolver());
  }

  @Test
  void createDeploymentBundle_shouldContainTheBuiltJar() throws IOException {
    byte[] jarBytes = authorizeBuildableVsum();

    Map<String, byte[]> entries = entriesOf(service.createDeploymentBundle(BUILD_EMAIL, BUILD_ID));

    assertThat(entries).containsKey(JAR_ENTRY_NAME);
    assertThat(entries.get(JAR_ENTRY_NAME)).isEqualTo(jarBytes);
  }

  @Test
  void createDeploymentBundle_shouldContainEveryFileOfTheDeploymentPackage()
      throws IOException, URISyntaxException {
    authorizeBuildableVsum();

    Map<String, byte[]> entries = entriesOf(service.createDeploymentBundle(BUILD_EMAIL, BUILD_ID));

    assertThat(entries).containsOnlyKeys(expectedBundleEntryNames());
  }

  @Test
  void createDeploymentBundle_shouldKeepTheLauncherScriptsExecutable() throws IOException {
    authorizeBuildableVsum();

    byte[] bundle = service.createDeploymentBundle(BUILD_EMAIL, BUILD_ID);

    Path archive = writeToTempFile(bundle);
    Map<String, String> env = new HashMap<>();
    env.put("enablePosixFileAttributes", "true");
    Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rwx------");
    try (FileSystem zip = FileSystems.newFileSystem(archive, env)) {
      assertThat(Files.getPosixFilePermissions(zip.getPath("run.sh")))
          .containsExactlyInAnyOrderElementsOf(ownerOnly);
      assertThat(Files.getPosixFilePermissions(zip.getPath("run.command")))
          .containsExactlyInAnyOrderElementsOf(ownerOnly);
    }
  }

  @Test
  void createDeploymentBundle_shouldShipDockerFilesThatRunTheJarEntry() throws IOException {
    authorizeBuildableVsum();

    Map<String, byte[]> entries = entriesOf(service.createDeploymentBundle(BUILD_EMAIL, BUILD_ID));

    String dockerfile = new String(entries.get("Dockerfile"), StandardCharsets.UTF_8);
    String compose = new String(entries.get("docker-compose.yaml"), StandardCharsets.UTF_8);

    // `docker build` fails if COPY names a file that is not in the archive next to the Dockerfile.
    assertThat(dockerfile).contains("COPY " + JAR_ENTRY_NAME + " /app/" + JAR_ENTRY_NAME);
    assertThat(dockerfile)
        .contains("ENTRYPOINT [\"java\", \"-jar\", \"/app/" + JAR_ENTRY_NAME + "\"]");
    // Without this the server binds to localhost inside the container and the port mapping is dead.
    assertThat(dockerfile).contains("VITRUV_SERVER_HOST=0.0.0.0");
    assertThat(compose).contains("build: .");
    assertThat(compose).contains(":8080\"");
    // Both files are consumed on Linux; a CRLF checkout must not leak into them.
    assertThat(dockerfile).doesNotContain("\r");
    assertThat(compose).doesNotContain("\r");
  }

  @Test
  void createDeploymentBundle_shouldThrowAccessDenied_whenUserNotMember() {
    when(vsumBuildService.buildAndWait(BUILD_EMAIL, BUILD_ID))
        .thenThrow(new org.springframework.security.access.AccessDeniedException("no"));

    assertThatThrownBy(() -> service.createDeploymentBundle(BUILD_EMAIL, BUILD_ID))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void createDeploymentBundle_shouldThrowNotFound_whenNoReactions() {
    when(vsumBuildService.buildAndWait(BUILD_EMAIL, BUILD_ID))
        .thenThrow(new NotFoundException("Reaction files"));

    assertThatThrownBy(() -> service.createDeploymentBundle(BUILD_EMAIL, BUILD_ID))
        .isInstanceOf(NotFoundException.class);
  }

  /**
   * Stubs the build pipeline to return a fixed JAR for the test VSUM.
   *
   * @return the JAR bytes the pipeline is stubbed to return
   */
  private byte[] authorizeBuildableVsum() {
    byte[] jarBytes = "FAKEJAR".getBytes(StandardCharsets.UTF_8);
    when(vsumBuildService.buildAndWait(BUILD_EMAIL, BUILD_ID)).thenReturn(jarBytes);
    return jarBytes;
  }

  /**
   * Reads all entries of a ZIP archive held in memory.
   *
   * @param bundle the archive bytes
   * @return the entry names mapped to their content
   * @throws IOException if the archive cannot be read
   */
  private Map<String, byte[]> entriesOf(byte[] bundle) throws IOException {
    Path archive = writeToTempFile(bundle);
    Map<String, byte[]> entries = new HashMap<>();

    try (FileSystem zip = FileSystems.newFileSystem(archive, Map.of())) {
      for (Path root : zip.getRootDirectories()) {
        try (Stream<Path> paths = Files.walk(root)) {
          for (Path path : paths.filter(Files::isRegularFile).toList()) {
            entries.put(root.relativize(path).toString(), Files.readAllBytes(path));
          }
        }
      }
    }

    return entries;
  }

  /**
   * Lists the names the bundle is expected to hold: the JAR plus every file that currently lives in
   * the {@code deployment} resource package.
   *
   * @return the expected entry names
   * @throws IOException if the resource package cannot be read
   * @throws URISyntaxException if the resource package location is not a valid URI
   */
  private String[] expectedBundleEntryNames() throws IOException, URISyntaxException {
    Path deploymentPackage =
        Path.of(
            getClass().getResource("/" + VsumDeploymentBundleService.BUNDLE_RESOURCE_ROOT).toURI());

    try (Stream<Path> paths = Files.walk(deploymentPackage)) {
      Set<String> names =
          paths
              .filter(Files::isRegularFile)
              .map(path -> deploymentPackage.relativize(path).toString())
              .collect(Collectors.toSet());
      names.add(JAR_ENTRY_NAME);
      return names.toArray(String[]::new);
    }
  }

  /**
   * Stores archive bytes in a temporary file so that they can be opened as a ZIP file system.
   *
   * @param bundle the archive bytes
   * @return the path of the written file
   * @throws IOException if the file cannot be written
   */
  private Path writeToTempFile(byte[] bundle) throws IOException {
    Path archive = tempDir.resolve("bundle-" + bundle.length + ".zip");
    Files.write(archive, bundle);
    return archive;
  }
}
