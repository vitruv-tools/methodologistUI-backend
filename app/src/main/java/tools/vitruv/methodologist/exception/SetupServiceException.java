package tools.vitruv.methodologist.exception;

/**
 * Runtime exception indicating that a call to the external setup-service failed.
 *
 * <p>Thrown by the setup-service API handler when the upstream service is unreachable, returns an
 * error status, or responds with an empty artifact. The provided reason is used as the exception
 * message.
 */
public class SetupServiceException extends RuntimeException {
  public static final String ERROR_TEMPLATE = "SETUP_SERVICE_ERROR";

  /**
   * Constructs a new {@code SetupServiceException} with the specified detail message.
   *
   * @param reason the detail message describing why the setup-service call failed
   */
  public SetupServiceException(String reason) {
    super(reason);
  }
}
