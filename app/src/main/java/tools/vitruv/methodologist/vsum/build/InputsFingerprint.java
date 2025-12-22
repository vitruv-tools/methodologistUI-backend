package tools.vitruv.methodologist.vsum.build;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import tools.vitruv.methodologist.general.model.FileStorage;

/**
 * Utility for computing a deterministic fingerprint of build inputs.
 *
 * <p>The fingerprint is a hex-encoded SHA-256 hash computed from a normalized payload that includes
 * the list of Ecore files, the list of GenModel files, and a single reaction file. Normalization
 * sorts files by filename, filters {@code null} entries, and represents each file as {@code
 * filename@sha256}. If a file has no SHA-256 recorded, the fingerprint of its data is used; if data
 * is absent, the literal {@code "no-data"} is used for that file.
 *
 * <p>This class is stateless, thread-safe and not instantiable.
 */
public final class InputsFingerprint {

  private InputsFingerprint() {}

  /**
   * Compute a deterministic fingerprint for the provided inputs.
   *
   * <p>The payload used for hashing has the form: {@code
   * "ECORES=<normalized-ecores>|GENS=<normalized-genmodels>|REACTION=<normalized-reaction>"}. Each
   * normalized list is a semicolon-separated sequence of {@code filename@sha256} entries.
   *
   * @param ecores the list of Ecore file descriptors; must not be {@code null}. {@code null}
   *     elements are ignored.
   * @param genmodels the list of GenModel file descriptors; must not be {@code null}. {@code null}
   *     elements are ignored.
   * @param reaction the reaction file descriptor; must not be {@code null}
   * @return a hex-encoded SHA-256 fingerprint of the normalized inputs
   * @throws NullPointerException if {@code ecores}, {@code genmodels} or {@code reaction} is {@code
   *     null}
   */
  public static String fingerprint(
      List<FileStorage> ecores, List<FileStorage> genmodels, FileStorage reaction) {
    Objects.requireNonNull(ecores);
    Objects.requireNonNull(genmodels);
    Objects.requireNonNull(reaction);

    String payload =
        "ECORES="
            + normalize(ecores)
            + "|GENS="
            + normalize(genmodels)
            + "|REACTION="
            + normalizeOne(reaction);

    return sha256Hex(payload.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Normalize a list of file descriptors into a deterministic semicolon-separated string.
   *
   * <p>Files are filtered for non-null, sorted by filename (nulls last), converted to their {@code
   * filename@sha256} representation, and joined with {@code ';'}. Returns an empty string for an
   * empty or all-null input list.
   *
   * @param files the list to normalize; may contain {@code null} elements but must not be {@code
   *     null}
   * @return semicolon-separated normalized representation or empty string if none
   */
  private static String normalize(List<FileStorage> files) {
    return files.stream()
        .filter(Objects::nonNull)
        .sorted(
            Comparator.comparing(FileStorage::getFilename, Comparator.nullsLast(String::compareTo)))
        .map(InputsFingerprint::normalizeOne)
        .reduce((a, b) -> a + ";" + b)
        .orElse("");
  }

  /**
   * Normalize a single file descriptor to the form {@code filename@sha256}.
   *
   * <p>If the descriptor has no recorded SHA-256, the SHA-256 of its data is computed. If data is
   * also missing, the literal {@code "no-data"} is used as the hash component.
   *
   * @param f the file descriptor; must not be {@code null}
   * @return normalized representation
   */
  private static String normalizeOne(FileStorage f) {
    String name = f.getFilename() == null ? "" : f.getFilename();
    String sha = f.getSha256();
    if (sha == null || sha.isBlank()) {
      byte[] data = f.getData();
      sha = data == null ? "no-data" : sha256Hex(data);
    }
    return name + "@" + sha;
  }

  /**
   * Compute the SHA-256 hex string of the given bytes.
   *
   * @param bytes input bytes; must not be {@code null}
   * @return hex-encoded SHA-256 digest
   * @throws IllegalStateException if the SHA-256 MessageDigest implementation is not available
   */
  private static String sha256Hex(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
