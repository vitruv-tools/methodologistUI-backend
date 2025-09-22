package tools.vitruv.methodologist.exception;

/**
 * Unchecked exception indicating a failure while computing or preparing a file hash.
 *
 * <p>Typically wraps lower-level issues such as missing algorithms or I/O problems encountered when
 * obtaining bytes to hash. Use this to signal a domain-specific hashing failure instead of throwing
 * generic exceptions.
 *
 * @see java.security.MessageDigest
 */
public class FileHashingException extends RuntimeException {
  /**
   * Creates a new {@code FileHashingException} with a detail message and root cause.
   *
   * @param message human-readable description of the error
   * @param cause underlying cause of the hashing failure
   */
  public FileHashingException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a new {@code FileHashingException} with a detail message.
   *
   * @param message human-readable description of the error
   */
  public FileHashingException(String message) {
    super(message);
  }
}
