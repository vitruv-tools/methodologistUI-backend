package tools.vitruv.methodologist.vsum.helper;

import tools.vitruv.methodologist.vsum.helper.OclAst.Pos;

/**
 * Thrown when a helper cannot be parsed or translated to Java. The position, if known, refers to
 * the OCL body of the helper.
 */
public class HelperTranslationException extends RuntimeException {

  private final transient Pos pos;

  /**
   * Creates an exception for a problem at a position of the OCL body.
   *
   * @param message human-readable description of the problem
   * @param pos 1-based position in the OCL body, or {@code null} if the problem is not in the body
   */
  public HelperTranslationException(String message, Pos pos) {
    super(message);
    this.pos = pos;
  }

  /**
   * Creates an exception for a problem outside of the OCL body, e.g. in a parameter type.
   *
   * @param message human-readable description of the problem
   */
  public HelperTranslationException(String message) {
    this(message, null);
  }

  /** The 1-based position in the OCL body, or {@code null}. */
  public Pos pos() {
    return pos;
  }
}
