package tools.vitruv.methodologist.exception;

/**
 * Runtime exception indicating that a VSUM build operation failed.
 *
 * <p>Thrown by service layers when a build does not complete successfully. The provided reason is
 * used as the exception message.
 */
public class VsumBuildingException extends RuntimeException {
  public static final String ERROR_TEMPLATE =
      "Error occurred during maven build! Please fix your setup accordingly!.";

  /**
   * Constructs a new {@code VsumBuilingException} with the specified detail message.
   *
   * @param reason the detail message describing why the build failed
   */
  public VsumBuildingException(String reason) {
    super(reason);
  }
}
