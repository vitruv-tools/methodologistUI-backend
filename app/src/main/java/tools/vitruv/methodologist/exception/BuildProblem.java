package tools.vitruv.methodologist.exception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One problem of a failed VSUM build or project generation as reported by the setup-service,
 * attributed to the platform file it comes from where possible.
 *
 * <p>Unknown properties are ignored so that additions on the setup-service side do not break
 * parsing; properties that are {@code null} are left out of the JSON.
 *
 * @param source the kind of input, or the party, responsible for the problem, e.g. {@code
 *     REACTIONS} or {@code INFRASTRUCTURE}
 * @param file name of the file as it was sent to the setup-service, {@code null} if the problem is
 *     not tied to a file
 * @param fileId id of the platform file the problem refers to, {@code null} if it cannot be
 *     resolved
 * @param line 1-based line in the file, {@code null} if unknown
 * @param column 1-based column in the line, {@code null} if unknown
 * @param message what is wrong
 * @param excerpt the affected line of the file, with a caret below the column if known
 * @param hint what to change, {@code null} if there is no specific advice
 * @param hiddenFollowUpErrors number of further errors that only follow from this one, {@code null}
 *     if none
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record BuildProblem(
    String source,
    String file,
    Long fileId,
    Integer line,
    Integer column,
    String message,
    String excerpt,
    String hint,
    Integer hiddenFollowUpErrors) {

  /**
   * Returns this problem attributed to a platform file.
   *
   * @param newFileId the id of the platform file, may be {@code null}
   * @return the problem with the file id
   */
  public BuildProblem withFileId(Long newFileId) {
    return new BuildProblem(
        source, file, newFileId, line, column, message, excerpt, hint, hiddenFollowUpErrors);
  }
}
