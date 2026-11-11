package tools.vitruv.methodologist.exception;

import java.util.List;

/**
 * The failure of a VSUM build that has already finished, read back to answer a request. The build
 * logged the failure when it happened, at the level that fits its cause, so it is not logged again
 * as a failure of the request.
 */
public class StoredBuildFailureException extends SetupServiceException {

  /**
   * Creates the exception for a stored build failure.
   *
   * @param reason the stored failure description
   * @param errorCode the stored error code of the setup-service, or {@code null}
   * @param problems the stored problems of the build, or {@code null}
   */
  public StoredBuildFailureException(String reason, String errorCode, List<BuildProblem> problems) {
    super(reason, null, errorCode, problems);
  }
}
