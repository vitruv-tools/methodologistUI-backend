package tools.vitruv.methodologist.general;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.UUID;

/**
 * Creates temporary directories that only the current user can read, write and enter. The system
 * temporary directory is shared by all users, so every directory created there must be private.
 */
public final class PrivateTempDirectories {

  private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY =
      PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));

  private PrivateTempDirectories() {}

  /**
   * Creates a new directory in the system temporary directory that only the current user can
   * access.
   *
   * @param prefix the prefix of the directory name
   * @return the new directory
   * @throws IOException if the directory cannot be created or made private
   */
  public static Path create(String prefix) throws IOException {
    if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
      return Files.createTempDirectory(prefix, OWNER_ONLY);
    }
    // Non-POSIX file systems (Windows): create the directory, then restrict it to its owner
    Path directory =
        Files.createDirectory(
            Path.of(System.getProperty("java.io.tmpdir"), prefix + UUID.randomUUID()));
    File file = directory.toFile();
    boolean restricted =
        file.setReadable(true, true)
            && file.setWritable(true, true)
            && file.setExecutable(true, true);
    if (!restricted) {
      Files.delete(directory);
      throw new IOException("Could not restrict access to " + directory);
    }
    return directory;
  }
}
