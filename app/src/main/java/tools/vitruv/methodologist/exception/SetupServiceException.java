package tools.vitruv.methodologist.exception;

import java.util.List;
import org.springframework.http.HttpStatusCode;

/**
 * Runtime exception indicating that a call to the external setup-service failed.
 *
 * <p>Thrown by the setup-service API handler when the upstream service is unreachable, returns an
 * error status, or responds with an empty artifact. The provided reason is used as the exception
 * message. Error responses of the setup-service additionally carry its error code and, for failed
 * builds, the problems it found in the uploaded files.
 */
public class SetupServiceException extends RuntimeException {
  public static final String ERROR_TEMPLATE = "SETUP_SERVICE_ERROR";

  private final transient HttpStatusCode upstreamStatus;
  private final String errorCode;
  private final transient List<BuildProblem> problems;

  /**
   * Constructs a new {@code SetupServiceException} with the specified detail message.
   *
   * @param reason the detail message describing why the setup-service call failed
   */
  public SetupServiceException(String reason) {
    this(reason, null);
  }

  /**
   * Constructs a new {@code SetupServiceException} for an error response of the setup-service.
   *
   * @param reason the detail message describing why the setup-service call failed
   * @param upstreamStatus the status returned by the setup-service, or {@code null} if it did not
   *     respond
   */
  public SetupServiceException(String reason, HttpStatusCode upstreamStatus) {
    this(reason, upstreamStatus, null, null);
  }

  /**
   * Constructs a new {@code SetupServiceException} for a structured error response of the
   * setup-service.
   *
   * @param reason the detail message describing why the setup-service call failed
   * @param upstreamStatus the status returned by the setup-service, or {@code null} if it did not
   *     respond
   * @param errorCode the error code of the setup-service, e.g. {@code VSUM_BUILD_FAILED}, or {@code
   *     null} if unknown
   * @param problems the problems the setup-service reported, or {@code null} if there are none
   */
  public SetupServiceException(
      String reason, HttpStatusCode upstreamStatus, String errorCode, List<BuildProblem> problems) {
    super(reason);
    this.upstreamStatus = upstreamStatus;
    this.errorCode = errorCode;
    this.problems = problems == null || problems.isEmpty() ? null : List.copyOf(problems);
  }

  /**
   * Returns whether the setup-service rejected the request itself, e.g. because an uploaded file is
   * invalid, as opposed to failing or being unreachable.
   *
   * @return {@code true} if the setup-service answered with a 4xx status
   */
  public boolean isRejectedBySetupService() {
    return upstreamStatus != null && upstreamStatus.is4xxClientError();
  }

  /**
   * Returns the error code of the setup-service.
   *
   * @return the error code, e.g. {@code VSUM_REACTIONS_ERROR}, or {@code null} if the setup-service
   *     did not report one
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * Returns the problems the setup-service reported, root causes first.
   *
   * @return the problems, or {@code null} if there are none
   */
  public List<BuildProblem> getProblems() {
    return problems;
  }
}
