package tools.vitruv.methodologist.exception;

/**
 * Runtime exception indicating that creation of a build artifact (ZIP, JAR bundle, etc.) failed.
 *
 * <p>Thrown when the system is unable to package build outputs such as fat JARs or Dockerfiles into
 * a distributable artifact.
 */
public class BuildArtifactCreationException extends RuntimeException {

  public static final String MESSAGE_TEMPLATE = "Failed to create artifact zip: %s";

  /**
   * Constructs a new {@code BuildArtifactCreationException} with the specified detail message.
   *
   * @param cause the detail message describing why artifact creation failed
   */
  public BuildArtifactCreationException(String cause) {
    super(String.format(MESSAGE_TEMPLATE, cause));
  }

  /**
   * Constructs a new {@code BuildArtifactCreationException} with a detail message and the exception
   * that caused the failure.
   *
   * @param cause the detail message describing why artifact creation failed
   * @param throwable the exception that caused the failure
   */
  public BuildArtifactCreationException(String cause, Throwable throwable) {
    super(String.format(MESSAGE_TEMPLATE, cause), throwable);
  }
}
