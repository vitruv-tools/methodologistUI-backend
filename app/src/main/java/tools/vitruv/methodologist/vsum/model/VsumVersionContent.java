package tools.vitruv.methodologist.vsum.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMetaModelResponse;

/**
 * What the canvas of a {@link Vsum} showed when a {@link VsumVersion} was published. Every file id
 * in it refers to one of the version's own copies ({@link VsumVersion#getContentFiles()}), so later
 * edits of the working copy do not change it. It is stored as JSON in {@link
 * VsumVersion#getContent()}; {@link #toJson} and {@link #fromJson} convert it.
 *
 * @param project the project as {@code GET /api/v1/vsums/{id}/details} returned it, without role
 *     and user ID
 * @param ruleSets the project's constraint rule sets
 */
public record VsumVersionContent(VsumMetaModelResponse project, List<RuleSet> ruleSets) {

  /**
   * Converts this content into the JSON that is stored.
   *
   * @param mapper the application's object mapper
   * @return the content as a JSON tree
   */
  public JsonNode toJson(ObjectMapper mapper) {
    return mapper.valueToTree(this);
  }

  /**
   * Reads stored content. Fields that no longer exist in the response classes are ignored, as far
   * as the mapper ignores unknown properties, which the application's mapper does.
   *
   * @param mapper the application's object mapper
   * @param json the stored content
   * @return a new content object; changing it does not change the stored JSON
   * @throws IllegalStateException if the JSON cannot be read as content
   */
  public static VsumVersionContent fromJson(ObjectMapper mapper, JsonNode json) {
    try {
      return mapper.treeToValue(json, VsumVersionContent.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("The content of a published version cannot be read", e);
    }
  }

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
