package tools.vitruv.methodologist.vitruvcli;

/** Machine-readable status reported by Vitruv-CLI after a GenModel precheck run. */
public enum GenModelPrecheckStatus {
  CLEAN,
  ISSUES_FOUND,
  FIXES_APPLIED,
  ABORTED,
  UNKNOWN
}
