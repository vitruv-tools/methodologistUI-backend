package tools.vitruv.methodologist.vsum.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import tools.vitruv.methodologist.general.model.FileStorage;

/**
 * The files a VSUM is built from. The setup-service generates the genmodels from the metamodels.
 *
 * @param ecores the metamodel files
 * @param reactions the reaction files, including generated composites
 * @param constraints the VitruvOCL constraint files
 * @param views the NeoJoin view definition files
 */
public record VsumBuildInputs(
    List<FileStorage> ecores,
    List<FileStorage> reactions,
    List<FileStorage> constraints,
    List<FileStorage> views) {

  private static final String SHA_256 = "SHA-256";

  /**
   * Version of the canonical form below. Bump it to invalidate every cached build, e.g. when the
   * setup-service starts producing different output for the same inputs.
   */
  static final int FINGERPRINT_VERSION = 2;

  /**
   * A SHA-256 hex digest identifying these inputs by content.
   *
   * <p>Only the role, name and bytes of each file count. The order in which the VSUM's relations
   * were traversed does not, so two requests for the same VSUM yield the same fingerprint even
   * though {@code Vsum#getMetaModelRelations()} is an unordered set.
   *
   * @return 64 lowercase hex characters
   */
  public String fingerprint() {
    List<String> lines = new ArrayList<>();
    addLines(lines, "ecore", ecores);
    addLines(lines, "reaction", reactions);
    addLines(lines, "constraint", constraints);
    addLines(lines, "view", views);
    lines.sort(null);

    MessageDigest digest = sha256();
    digest.update(("v" + FINGERPRINT_VERSION + "\n").getBytes(StandardCharsets.UTF_8));
    for (String line : lines) {
      digest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static void addLines(List<String> lines, String role, List<FileStorage> files) {
    for (FileStorage file : files) {
      byte[] data = file.getData() == null ? new byte[0] : file.getData();
      String name = file.getFilename() == null ? "" : file.getFilename();
      lines.add(role + "\t" + name + "\t" + sha256Hex(data));
    }
  }

  /** Lowercase hex SHA-256 of the given bytes. */
  static String sha256Hex(byte[] data) {
    return HexFormat.of().formatHex(sha256().digest(data));
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance(SHA_256);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(SHA_256 + " is not available", e);
    }
  }
}
