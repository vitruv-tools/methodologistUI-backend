package tools.vitruv.methodologist.vsum.build;

import java.util.Objects;
import lombok.Getter;

/**
 * Construct a {@link BuildKey} that uniquely identifies a build request for the given VSUM.
 *
 * <p>This helper collects a deterministic set of Ecore/GenModel pairs and the first reaction file
 * referenced by {@code vsum}, computes an inputs fingerprint (via {@link
 * InputsFingerprint#fingerprint(java.util.List, java.util.List,
 * tools.vitruv.methodologist.general.model.FileStorage)}), and returns a {@link BuildKey} composed
 * of the requesting user's email, the VSUM id and the computed fingerprint. Pairs are deduplicated
 * and ordered to ensure deterministic fingerprinting.
 *
 * @param callerEmail the email of the caller that will scope the build key; must not be {@code
 *     null}
 * @param vsumId the identifier of the VSUM for which the build is requested; must not be {@code
 *     null}
 * @param vsum the VSUM aggregate containing meta-model relations and reaction references; must not
 *     be {@code null} and must contain at least one valid meta-model pair and one reaction file
 *     reference
 * @return a new {@link BuildKey} composed of {@code callerEmail}, {@code vsumId} and the inputs
 *     fingerprint
 * @throws NullPointerException if {@code callerEmail}, {@code vsumId} or {@code vsum} is {@code
 *     null}
 * @throws tools.vitruv.methodologist.exception.NotFoundException if {@code vsum} has no meta-model
 *     relations or no valid reaction file references required to compute the fingerprint
 */
@Getter
public final class BuildKey {
  private final String userKey;
  private final Long vsumId;
  private final String inputsFingerprint;

  /**
   * Create a new {@link BuildKey} instance.
   *
   * <p>The constructor requires all components to be non-null and will throw {@link
   * NullPointerException} when any argument is {@code null}. The provided values are stored as
   * immutable fields used by {@code equals}, {@code hashCode} and {@code toString}.
   *
   * @param userKey a non-null user-scoped key (e.g. caller email) that scopes the build
   * @param vsumId a non-null identifier of the VSUM for which the build is requested
   * @param inputsFingerprint a non-null fingerprint computed from the build inputs
   * @throws NullPointerException if {@code userKey}, {@code vsumId} or {@code inputsFingerprint} is
   *     {@code null}
   */
  public BuildKey(String userKey, Long vsumId, String inputsFingerprint) {
    this.userKey = Objects.requireNonNull(userKey);
    this.vsumId = Objects.requireNonNull(vsumId);
    this.inputsFingerprint = Objects.requireNonNull(inputsFingerprint);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BuildKey)) {
      return false;
    }
    BuildKey that = (BuildKey) o;
    return userKey.equals(that.userKey)
        && vsumId.equals(that.vsumId)
        && inputsFingerprint.equals(that.inputsFingerprint);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userKey, vsumId, inputsFingerprint);
  }

  @Override
  public String toString() {
    return "BuildKey{userKey='"
        + userKey
        + "', vsumId="
        + vsumId
        + ", inputsFingerprint='"
        + inputsFingerprint
        + "'}";
  }
}
