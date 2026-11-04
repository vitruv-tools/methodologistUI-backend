package tools.vitruv.methodologist.vsum.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import tools.vitruv.methodologist.general.Sha256;
import tools.vitruv.methodologist.general.model.FileStorage;

/**
 * The files a VSUM is built from, in the order the setup-service expects them: genmodels are paired
 * with metamodels by index.
 *
 * @param ecores the metamodel files
 * @param genmodels the genmodel files, one per metamodel, same order
 * @param reactions the reaction files, including generated composites
 */
public record VsumBuildInputs(
    List<FileStorage> ecores, List<FileStorage> genmodels, List<FileStorage> reactions) {

  /**
   * Version of the canonical form below. Bump it to invalidate every cached build, e.g. when the
   * setup-service starts producing different output for the same inputs.
   */
  static final int FINGERPRINT_VERSION = 1;

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
    addLines(lines, "genmodel", genmodels);
    addLines(lines, "reaction", reactions);
    lines.sort(null);

    StringBuilder canonical = new StringBuilder("v" + FINGERPRINT_VERSION + "\n");
    for (String line : lines) {
      canonical.append(line).append('\n');
    }
    return Sha256.hex(canonical.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static void addLines(List<String> lines, String role, List<FileStorage> files) {
    for (FileStorage file : files) {
      byte[] data = file.getData() == null ? new byte[0] : file.getData();
      String name = file.getFilename() == null ? "" : file.getFilename();
      lines.add(role + "\t" + name + "\t" + Sha256.hex(data));
    }
  }
}
