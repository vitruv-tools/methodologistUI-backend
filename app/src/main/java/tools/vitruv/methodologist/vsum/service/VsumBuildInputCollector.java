package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.METAMODEL_IDS_NOT_FOUND_IN_THIS_VSUM_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.REACTION_FILE_IDS_ID_NOT_FOUND_ERROR;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.model.ConstraintRuleSet;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.repository.ConstraintRuleSetRepository;

/**
 * Collects the files a {@link Vsum} is built from.
 *
 * <p>The metamodel/genmodel pairs of every relation are deduplicated (first seen wins) and the
 * reaction files are gathered per relation by the {@link ReactionBuildCollector}, which also
 * generates a composite reaction for pairs that carry more than one file.
 */
@Component
@RequiredArgsConstructor
public class VsumBuildInputCollector {

  private static final String OCL_EXTENSION = ".ocl";
  private static final String OCL_CONTENT_TYPE = "text/plain";

  private final ReactionBuildCollector reactionBuildCollector;
  private final ConstraintRuleSetRepository ruleSetRepository;

  /**
   * Collects the build inputs of the given VSUM.
   *
   * @param vsum the VSUM; its relations and their files must be loadable, i.e. the call has to run
   *     inside a transaction
   * @return the inputs in setup-service order
   * @throws NotFoundException if the VSUM has no relations, no metamodel pair or no reaction file
   * @throws tools.vitruv.methodologist.exception.VsumBuildingException if reaction files on a pair
   *     cannot be composed
   */
  public VsumBuildInputs collect(Vsum vsum) {
    if (vsum.getMetaModelRelations() == null || vsum.getMetaModelRelations().isEmpty()) {
      throw new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR);
    }

    Map<String, FileStorage> ecores = new LinkedHashMap<>();
    Map<String, FileStorage> genmodels = new LinkedHashMap<>();
    List<FileStorage> reactions = new ArrayList<>();

    for (MetaModelRelation relation : vsum.getMetaModelRelations()) {
      if (relation == null) {
        throw new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR);
      }

      MetaModel source = relation.getSource();
      MetaModel target = relation.getTarget();

      if (source != null) {
        putPair(ecores, genmodels, source.getEcoreFile(), source.getGenModelFile());
      }
      if (target != null) {
        putPair(ecores, genmodels, target.getEcoreFile(), target.getGenModelFile());
      }

      reactions.addAll(reactionBuildCollector.collectForRelation(relation));
    }

    if (ecores.isEmpty() || genmodels.isEmpty()) {
      throw new NotFoundException(METAMODEL_IDS_NOT_FOUND_IN_THIS_VSUM_NOT_FOUND_ERROR);
    }
    if (reactions.isEmpty()) {
      throw new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR);
    }

    return new VsumBuildInputs(
        new ArrayList<>(ecores.values()),
        new ArrayList<>(genmodels.values()),
        reactions,
        collectConstraints(vsum));
  }

  /**
   * Creates one OCL file per rule set of the VSUM that contains constraints. Each file is named
   * like its rule set, so the constraints keep their grouping in the built project. The files are
   * ordered by rule set name and id, so the fingerprint does not depend on the database order.
   */
  private List<FileStorage> collectConstraints(Vsum vsum) {
    if (vsum.getId() == null) {
      return List.of();
    }
    List<FileStorage> constraints = new ArrayList<>();
    ruleSetRepository.findByVsumId(vsum.getId()).stream()
        .filter(ruleSet -> ruleSet.getOclFile() != null && ruleSet.getOclFile().getData() != null)
        .filter(
            ruleSet ->
                !new String(ruleSet.getOclFile().getData(), StandardCharsets.UTF_8).isBlank())
        .sorted(
            Comparator.comparing(
                    ConstraintRuleSet::getName, Comparator.nullsFirst(String::compareTo))
                .thenComparing(ConstraintRuleSet::getId, Comparator.nullsFirst(Long::compareTo)))
        .forEach(ruleSet -> constraints.add(toConstraintFile(ruleSet)));
    return constraints;
  }

  private static FileStorage toConstraintFile(ConstraintRuleSet ruleSet) {
    byte[] data = ruleSet.getOclFile().getData();
    String name =
        ruleSet.getName() == null || ruleSet.getName().isBlank()
            ? "constraints"
            : ruleSet.getName();
    return FileStorage.builder()
        .filename(name + OCL_EXTENSION)
        .type(FileEnumType.OCL)
        .contentType(OCL_CONTENT_TYPE)
        .sizeBytes(data.length)
        .sha256(VsumBuildInputs.sha256Hex(data))
        .data(data)
        .build();
  }

  /**
   * Associates an Ecore/GenModel pair into the provided maps using stable keys. If either file is
   * {@code null} the pair is ignored; existing entries are preserved (first seen wins).
   */
  private static void putPair(
      Map<String, FileStorage> ecores,
      Map<String, FileStorage> genmodels,
      FileStorage ecore,
      FileStorage genmodel) {
    if (ecore == null || genmodel == null) {
      return;
    }
    ecores.putIfAbsent(key(ecore), ecore);
    genmodels.putIfAbsent(key(genmodel), genmodel);
  }

  /** {@code "id:<id>"} if the storage is persisted, otherwise {@code "name:<filename>"}. */
  private static String key(FileStorage fs) {
    if (fs.getId() != null) {
      return "id:" + fs.getId();
    }
    return "name:" + (fs.getFilename() == null ? "" : fs.getFilename());
  }
}
