package tools.vitruv.methodologist.apihandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.vitruv.methodologist.exception.BuildProblem;
import tools.vitruv.methodologist.general.model.FileStorage;

/**
 * Remembers which platform file was sent to the setup-service under which name, so that the
 * problems the setup-service reports for a file name can be traced back to the platform file.
 *
 * <p>File names are unique within a multipart part, but the same name may be used in two parts (a
 * view file may be named like a metamodel). A problem is therefore resolved within the part that
 * matches its {@code source} first, and by its name alone only if that name is unambiguous.
 */
final class UploadedFiles {

  private final Map<String, Map<String, FileStorage>> filesByPart = new HashMap<>();
  private final Map<String, FileStorage> filesByName = new HashMap<>();
  private final Map<String, Integer> nameCounts = new HashMap<>();

  /**
   * Records that a file was sent in the given part under the given name.
   *
   * @param partName the multipart part, e.g. {@code reactionFiles}
   * @param filename the name the file was sent with
   * @param file the platform file
   */
  void add(String partName, String filename, FileStorage file) {
    filesByPart.computeIfAbsent(partName, part -> new HashMap<>()).put(filename, file);
    filesByName.put(filename, file);
    nameCounts.merge(filename, 1, Integer::sum);
  }

  /**
   * Sets the id of the platform file on each problem whose file can be resolved and clears it on
   * the others.
   *
   * @param problems the problems reported by the setup-service
   * @return the problems with their file ids, in the same order
   */
  List<BuildProblem> resolve(List<BuildProblem> problems) {
    return problems.stream().map(this::resolve).toList();
  }

  private BuildProblem resolve(BuildProblem problem) {
    FileStorage file =
        problem.file() == null ? null : find(partOf(problem.source()), problem.file());
    return problem.withFileId(file == null ? null : file.getId());
  }

  private FileStorage find(String partName, String filename) {
    if (partName != null) {
      FileStorage file = filesByPart.getOrDefault(partName, Map.of()).get(filename);
      if (file != null) {
        return file;
      }
    }
    return nameCounts.getOrDefault(filename, 0) == 1 ? filesByName.get(filename) : null;
  }

  /** The multipart part holding the files of a problem source, {@code null} for other sources. */
  private static String partOf(String source) {
    if (source == null) {
      return null;
    }
    return switch (source) {
      case "METAMODEL" -> SetupServiceApiHandler.METAMODEL_FILES_PART;
      case "REACTIONS" -> SetupServiceApiHandler.REACTION_FILES_PART;
      case "CONSTRAINTS" -> SetupServiceApiHandler.CONSTRAINT_FILES_PART;
      case "VIEWS" -> SetupServiceApiHandler.VIEW_FILES_PART;
      default -> null;
    };
  }
}
