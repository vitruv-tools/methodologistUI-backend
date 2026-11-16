package tools.vitruv.methodologist.general;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

/** Helpers for building file paths from user-supplied names. */
public final class SafePaths {

  private SafePaths() {}

  /**
   * Resolves an uploaded file name against a directory without letting it leave that directory.
   * File names are supplied by users, so only the last name element is used and the result must be
   * a direct child of {@code directory}; {@code ../x}, absolute paths, {@code ..} and blank names
   * never resolve outside it.
   *
   * @param directory the directory the file must be written into
   * @param fileName the stored, user-supplied file name; may be {@code null}
   * @return the target path inside {@code directory}, or empty if the name cannot be used safely
   */
  public static Optional<Path> resolveInside(Path directory, String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return Optional.empty();
    }
    try {
      Path name = Path.of(fileName).getFileName();
      if (name == null) {
        return Optional.empty();
      }
      Path base = directory.toAbsolutePath().normalize();
      Path target = base.resolve(name).normalize();
      return base.equals(target.getParent()) ? Optional.of(target) : Optional.empty();
    } catch (InvalidPathException e) {
      return Optional.empty();
    }
  }
}
