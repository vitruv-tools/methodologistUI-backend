package tools.vitruv.methodologist.exception;

/**
 * Unchecked exception indicating a failure to execute the external Vitruv-CLI tool.
 *
 * <p>The exception message is produced by formatting {@link #MESSAGE_TEMPLATE} with a
 * human-readable reason provided by the caller.
 */
public class CLIExecuteException extends RuntimeException {
  public static final String MESSAGE_TEMPLATE = "Failed to execute Vitruv-CLI: %s";

  /**
   * Constructs a new {@code CLIExecuteException} with a formatted detail message.
   *
   * @param reason a short description of why the CLI execution failed
   */
  public CLIExecuteException(String reason) {
    super(String.format(MESSAGE_TEMPLATE, reason));
  }
}
