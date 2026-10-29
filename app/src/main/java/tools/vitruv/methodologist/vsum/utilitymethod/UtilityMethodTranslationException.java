package tools.vitruv.methodologist.vsum.utilitymethod;

import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Pos;

/**
 * Thrown when a utility method cannot be parsed or translated to Java. The position, if known,
 * refers to the OCL body of the utility method.
 */
public class UtilityMethodTranslationException extends RuntimeException {

  private final transient Pos pos;

  /**
   * Creates an exception for a problem at a position of the OCL body.
   *
   * @param message human-readable description of the problem
   * @param pos 1-based position in the OCL body, or {@code null} if the problem is not in the body
   */
  public UtilityMethodTranslationException(String message, Pos pos) {
    super(message);
    this.pos = pos;
  }

  /**
   * Creates an exception for a problem outside of the OCL body, e.g. in a parameter type.
   *
   * @param message human-readable description of the problem
   */
  public UtilityMethodTranslationException(String message) {
    this(message, null);
  }

  /** The 1-based position in the OCL body, or {@code null}. */
  public Pos pos() {
    return pos;
  }
}
