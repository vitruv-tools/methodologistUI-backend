package tools.vitruv.methodologist.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Runtime exception indicating that a call to the external setup-service failed.
 *
 * <p>Thrown by the setup-service API handler when the upstream service is unreachable, returns an
 * error status, or responds with an empty artifact. The provided reason is used as the exception
 * message.
 */
public class SetupServiceException extends RuntimeException {
  public static final String ERROR_TEMPLATE = "SETUP_SERVICE_ERROR";

  private final transient HttpStatusCode upstreamStatus;

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
    super(reason);
    this.upstreamStatus = upstreamStatus;
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
}
