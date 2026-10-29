package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.METAMODEL_IDS_NOT_FOUND_IN_THIS_VSUM_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.REACTION_FILE_IDS_ID_NOT_FOUND_ERROR;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.model.ConstraintRuleSet;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.VsumView;
import tools.vitruv.methodologist.vsum.model.repository.ConstraintRuleSetRepository;

/**
 * Collects the files a {@link Vsum} is built from.
 *
 * <p>The metamodels are those of the VSUM and of its relations, deduplicated by file (first seen
 * wins); genmodels are not needed, the setup-service generates them. The reaction files are
 * gathered per relation by the {@link ReactionBuildCollector}, which also generates a composite
 * reaction for pairs that carry more than one file. Constraint rule sets and views are added as
 * they are.
 */
@Component
@RequiredArgsConstructor
public class VsumBuildInputCollector {

  private final ReactionBuildCollector reactionBuildCollector;
  private final ConstraintRuleSetRepository constraintRuleSetRepository;

  /**
   * Collects the build inputs of the given VSUM.
   *
   * @param vsum the VSUM; its metamodels, relations, views, and their files must be loadable, i.e.
   *     the call has to run inside a transaction
   * @return the inputs of the setup-service
   * @throws NotFoundException if the VSUM has no metamodel or a relation is missing
   * @throws tools.vitruv.methodologist.exception.VsumBuildingException if reaction files on a pair
   *     cannot be composed
   */
  public VsumBuildInputs collect(Vsum vsum) {
    Map<String, FileStorage> ecores = new LinkedHashMap<>();
    List<FileStorage> reactions = new ArrayList<>();

    if (vsum.getVsumMetaModels() != null) {
      for (VsumMetaModel vsumMetaModel : vsum.getVsumMetaModels()) {
        if (vsumMetaModel != null && vsumMetaModel.getRemovedAt() == null) {
          putEcore(ecores, vsumMetaModel.getMetaModel());
        }
      }
    }

    if (vsum.getMetaModelRelations() != null) {
      for (MetaModelRelation relation : vsum.getMetaModelRelations()) {
        if (relation == null) {
          throw new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR);
        }
        putEcore(ecores, relation.getSource());
        putEcore(ecores, relation.getTarget());
        reactions.addAll(reactionBuildCollector.collectForRelation(relation));
      }
    }

    if (ecores.isEmpty()) {
      throw new NotFoundException(METAMODEL_IDS_NOT_FOUND_IN_THIS_VSUM_NOT_FOUND_ERROR);
    }

    return new VsumBuildInputs(
        new ArrayList<>(ecores.values()), reactions, collectConstraints(vsum), collectViews(vsum));
  }

  private List<FileStorage> collectConstraints(Vsum vsum) {
    if (vsum.getId() == null) {
      return List.of();
    }
    return constraintRuleSetRepository.findByVsumId(vsum.getId()).stream()
        .map(ConstraintRuleSet::getOclFile)
        .filter(Objects::nonNull)
        .toList();
  }

  private static List<FileStorage> collectViews(Vsum vsum) {
    if (vsum.getViews() == null) {
      return List.of();
    }
    return vsum.getViews().stream()
        .filter(Objects::nonNull)
        .map(VsumView::getFileStorage)
        .filter(Objects::nonNull)
        .toList();
  }

  /** Adds the metamodel's ecore file unless it is missing or already present. */
  private static void putEcore(Map<String, FileStorage> ecores, MetaModel metaModel) {
    if (metaModel == null || metaModel.getEcoreFile() == null) {
      return;
    }
    ecores.putIfAbsent(key(metaModel.getEcoreFile()), metaModel.getEcoreFile());
  }

  /** {@code "id:<id>"} if the storage is persisted, otherwise {@code "name:<filename>"}. */
  private static String key(FileStorage fs) {
    if (fs.getId() != null) {
      return "id:" + fs.getId();
    }
    return "name:" + (fs.getFilename() == null ? "" : fs.getFilename());
  }
}
