package tools.vitruv.methodologist.vsum.model;

/** Lifecycle of a {@link VsumBuild}. */
public enum VsumBuildStatus {
  /** Accepted and waiting for a free build worker. */
  QUEUED,
  /** The setup-service is building the project right now. */
  RUNNING,
  /**
   * The build finished and produced an artifact (which may since have been removed by retention).
   */
  SUCCEEDED,
  /** The build failed; see {@link VsumBuild#getErrorMessage()}. */
  FAILED;

  /** Whether the build will not change state anymore. */
  public boolean isTerminal() {
    return this == SUCCEEDED || this == FAILED;
  }
}
