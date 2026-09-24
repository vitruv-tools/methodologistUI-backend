package tools.vitruv.methodologist.general;

/**
 * Enumeration of supported file types within the Methodologist framework. These values represent
 * the different types of modeling and configuration files that can be handled by the system.
 */
public enum FileEnumType {
  ECORE,
  GEN_MODEL,
  REACTION,
  NEO_JOIN,
  OCL,
  /** A fat JAR produced by a VSUM build, see {@code VsumBuild}. */
  VSUM_JAR
}
