package tools.vitruv.methodologist.vsum.build;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import tools.vitruv.methodologist.general.model.FileStorage;

public final class InputsFingerprint {

  private InputsFingerprint() {}

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

  private static String normalize(List<FileStorage> files) {
    return files.stream()
        .filter(Objects::nonNull)
        .sorted(
            Comparator.comparing(FileStorage::getFilename, Comparator.nullsLast(String::compareTo)))
        .map(InputsFingerprint::normalizeOne)
        .reduce((a, b) -> a + ";" + b)
        .orElse("");
  }

  private static String normalizeOne(FileStorage f) {
    String name = f.getFilename() == null ? "" : f.getFilename();
    String sha = f.getSha256();
    if (sha == null || sha.isBlank()) {
      byte[] data = f.getData();
      sha = data == null ? "no-data" : sha256Hex(data);
    }
    return name + "@" + sha;
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
