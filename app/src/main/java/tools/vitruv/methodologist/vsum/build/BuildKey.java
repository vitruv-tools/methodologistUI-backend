package tools.vitruv.methodologist.vsum.build;

import java.util.Objects;

public final class BuildKey {
  private final String userKey;
  private final Long vsumId;
  private final String inputsFingerprint;

  public BuildKey(String userKey, Long vsumId, String inputsFingerprint) {
    this.userKey = Objects.requireNonNull(userKey);
    this.vsumId = Objects.requireNonNull(vsumId);
    this.inputsFingerprint = Objects.requireNonNull(inputsFingerprint);
  }

  public String getUserKey() {
    return userKey;
  }

  public Long getVsumId() {
    return vsumId;
  }

  public String getInputsFingerprint() {
    return inputsFingerprint;
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
