package tools.vitruv.methodologist.vsum.model;

import java.time.Instant;
import java.util.List;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMetaModelResponse;

/**
 * What the canvas of a {@link Vsum} showed when a {@link VsumVersion} was published. Every file id
 * in it refers to one of the version's own copies ({@link VsumVersion#getContentFiles()}), so later
 * edits of the working copy do not change it.
 *
 * @param project the project as {@code GET /api/v1/vsums/{id}/details} returned it, without role
 * @param ruleSets the project's constraint rule sets
 */
public record VsumVersionContent(VsumMetaModelResponse project, List<RuleSet> ruleSets) {

  /**
   * A constraint rule set of the published project.
   *
   * @param id the rule set's id in the working copy
   * @param name the rule set's name
   * @param color the rule set's color
   * @param description the rule set's description
   * @param oclFileId the copy of the rule set's OCL file; {@code null} if it had none
   * @param createdAt when the rule set was created
   * @param updatedAt when the rule set was last changed
   */
  public record RuleSet(
      Long id,
      String name,
      String color,
      String description,
      Long oclFileId,
      Instant createdAt,
      Instant updatedAt) {}
}
