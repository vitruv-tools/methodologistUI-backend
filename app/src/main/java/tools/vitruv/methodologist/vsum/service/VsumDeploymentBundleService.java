package tools.vitruv.methodologist.vsum.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.exception.BuildArtifactCreationException;

/**
 * Service that packages a built VSUM into a deployment bundle: a ZIP archive holding the fat JAR
 * together with the launcher scripts and documentation of the {@code deployment} resource package.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VsumDeploymentBundleService {

  /** Classpath package whose whole content is copied into every deployment bundle. */
  static final String BUNDLE_RESOURCE_ROOT = "deployment";

  /** Name under which the built fat JAR is stored in a deployment bundle. */
  static final String JAR_ENTRY_NAME = "vsum.jar";

  private static final String BUNDLE_ROOT_PATTERN =
      ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + BUNDLE_RESOURCE_ROOT + "/";
  private static final String BUNDLE_CONTENT_PATTERN = BUNDLE_ROOT_PATTERN + "**";

  /**
   * Permissions of the launcher scripts inside the bundle: executable for the user who extracts the
   * archive, no access at all for group and others.
   */
  private static final Set<PosixFilePermission> EXECUTABLE_PERMISSIONS =
      PosixFilePermissions.fromString("rwx------");

  private static final Set<String> EXECUTABLE_SUFFIXES = Set.of(".sh", ".command", ".bash");

  private static final Map<String, String> ZIP_FILE_SYSTEM_ENV =
      Map.of("create", "true", "enablePosixFileAttributes", "true");

  private static final String POSIX_FILE_ATTRIBUTE_VIEW = "posix";
  private static final String BUNDLE_FILE_NAME = "bundle.zip";
  private static final String WORK_DIRECTORY_PREFIX = "vsum-deployment-";

  /**
   * Attributes making the temporary work directory accessible to the owner only, so that no other
   * local user can read the artifact or plant entries in it while it is being written. Empty on
   * file systems without POSIX support, which then apply their own default permissions.
   */
  private static final FileAttribute<?>[] OWNER_ONLY_DIRECTORY = ownerOnlyDirectoryAttributes();

  VsumBuildService vsumBuildService;
  ResourceLoader resourceLoader;

  /**
   * Builds the VSUM and packages the resulting fat JAR together with the {@code deployment}
   * resource package into a single ZIP archive that the user can run locally.
   *
   * <p>The {@code deployment} package holds the launcher scripts and documentation needed to start
   * the application; adding a file there is enough to have it shipped in the bundle, no code change
   * is required. Launcher scripts are stored with the POSIX executable bit set so that they can be
   * started directly after extraction on macOS and Linux.
   *
   * <p>Access to the VSUM is validated by {@link VsumBuildService#buildAndWait(String, Long)},
   * whose exceptions are propagated unchanged.
   *
   * @param callerEmail email address of the requesting user
   * @param id the VSUM identifier
   * @return the deployment bundle as ZIP bytes
   * @throws org.springframework.security.access.AccessDeniedException if the user is not authorized
   *     for this VSUM
   * @throws BuildArtifactCreationException if the archive cannot be assembled
   */
  public byte[] createDeploymentBundle(String callerEmail, Long id) {
    return createDeploymentBundle(vsumBuildService.buildAndWait(callerEmail, id));
  }

  /**
   * Packages an already built fat JAR together with the {@code deployment} resource package into
   * the deployment bundle, see {@link #createDeploymentBundle(String, Long)}.
   *
   * @param jar the built fat JAR bytes
   * @return the deployment bundle as ZIP bytes
   * @throws BuildArtifactCreationException if the archive cannot be assembled
   */
  public byte[] createDeploymentBundle(byte[] jar) {
    Path workDirectory = null;
    try {
      // The archive is assembled in a private directory instead of directly in the shared
      // temporary directory, so that no other local user can read or replace it meanwhile.
      workDirectory = Files.createTempDirectory(WORK_DIRECTORY_PREFIX, OWNER_ONLY_DIRECTORY);

      Path bundle = workDirectory.resolve(BUNDLE_FILE_NAME);
      writeBundle(bundle, jar);
      return Files.readAllBytes(bundle);
    } catch (IOException e) {
      log.error("Failed to assemble the deployment bundle", e);
      throw new BuildArtifactCreationException(e.getMessage());
    } finally {
      deleteQuietly(workDirectory);
    }
  }

  /**
   * Builds the attributes restricting the temporary work directory to its owner.
   *
   * @return the owner-only attributes, or an empty array if the default file system has no POSIX
   *     support
   */
  private static FileAttribute<?>[] ownerOnlyDirectoryAttributes() {
    if (!FileSystems.getDefault()
        .supportedFileAttributeViews()
        .contains(POSIX_FILE_ATTRIBUTE_VIEW)) {
      return new FileAttribute<?>[0];
    }
    return new FileAttribute<?>[] {
      PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
    };
  }

  /**
   * Writes the fat JAR and the {@code deployment} resources into the given archive.
   *
   * @param bundle path of the archive to create
   * @param jar the built fat JAR bytes
   * @throws IOException if the archive cannot be written
   */
  private void writeBundle(Path bundle, byte[] jar) throws IOException {
    try (FileSystem zip = FileSystems.newFileSystem(bundle, ZIP_FILE_SYSTEM_ENV)) {
      Files.write(zip.getPath(JAR_ENTRY_NAME), jar);
      for (BundleEntry entry : readBundleResources()) {
        addBundleEntry(zip, entry);
      }
    }
  }

  /**
   * Copies a single resource into the archive, preserving its path inside the {@code deployment}
   * package and marking launcher scripts as executable.
   *
   * @param zip the archive file system to write into
   * @param entry the resource and the name it gets in the archive
   * @throws IOException if the entry cannot be written
   */
  private void addBundleEntry(FileSystem zip, BundleEntry entry) throws IOException {
    Path target = zip.getPath(entry.name());
    Path parent = target.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    try (InputStream source = entry.resource().getInputStream()) {
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    if (isExecutableBundleEntry(entry.name())) {
      Files.setPosixFilePermissions(target, EXECUTABLE_PERMISSIONS);
    }
  }

  /**
   * Collects every readable file of the {@code deployment} resource package.
   *
   * <p>Entries are sorted by name so that the produced archive is reproducible.
   *
   * @return the resources to add, each paired with its name inside the archive
   * @throws IOException if the classpath cannot be scanned
   */
  private List<BundleEntry> readBundleResources() throws IOException {
    ResourcePatternResolver resolver =
        ResourcePatternUtils.getResourcePatternResolver(resourceLoader);

    List<String> roots = new ArrayList<>();
    for (Resource root : resolver.getResources(BUNDLE_ROOT_PATTERN)) {
      String url = root.getURL().toString();
      roots.add(url.endsWith("/") ? url : url + "/");
    }

    if (roots.isEmpty()) {
      log.warn(
          "No '{}' resource package found on the classpath, the bundle will contain only {}",
          BUNDLE_RESOURCE_ROOT,
          JAR_ENTRY_NAME);
      return List.of();
    }

    List<BundleEntry> entries = new ArrayList<>();
    for (Resource resource : resolver.getResources(BUNDLE_CONTENT_PATTERN)) {
      String name = resource.isReadable() ? relativeBundleName(resource, roots) : null;

      if (name == null || name.isEmpty()) {
        log.debug(
            "Skipping non-file resource of the '{}' package: {}", BUNDLE_RESOURCE_ROOT, resource);
      } else {
        entries.add(new BundleEntry(name, resource));
      }
    }

    entries.sort(Comparator.comparing(BundleEntry::name));
    return entries;
  }

  /**
   * Determines the archive name of a resource relative to the {@code deployment} package.
   *
   * @param resource the resource to name
   * @param roots the URLs of the {@code deployment} package roots, each ending with a slash
   * @return the relative name, or {@code null} if the resource belongs to no known root
   * @throws IOException if the resource URL cannot be resolved
   */
  private String relativeBundleName(Resource resource, List<String> roots) throws IOException {
    String url = resource.getURL().toString();
    for (String root : roots) {
      if (url.startsWith(root)) {
        return url.substring(root.length());
      }
    }
    return null;
  }

  /**
   * Tells whether an archive entry is a launcher script that must stay executable.
   *
   * @param name the entry name inside the archive
   * @return {@code true} if the executable bit has to be set
   */
  private boolean isExecutableBundleEntry(String name) {
    String lowerCaseName = name.toLowerCase(Locale.ROOT);
    return EXECUTABLE_SUFFIXES.stream().anyMatch(lowerCaseName::endsWith);
  }

  /**
   * Removes the temporary work directory and the archive it holds, logging instead of failing when
   * they cannot be deleted.
   *
   * @param workDirectory the directory to delete; may be {@code null}
   */
  private void deleteQuietly(Path workDirectory) {
    if (workDirectory == null) {
      return;
    }
    try {
      Files.deleteIfExists(workDirectory.resolve(BUNDLE_FILE_NAME));
      Files.deleteIfExists(workDirectory);
    } catch (IOException e) {
      log.warn("Could not delete the temporary deployment work directory {}", workDirectory, e);
    }
  }

  /**
   * A single file of the {@code deployment} package together with its name inside the archive.
   *
   * @param name the entry name inside the archive
   * @param resource the classpath resource holding the content
   */
  private record BundleEntry(String name, Resource resource) {}
}
