package tools.vitruv.methodologist.vsum.controller.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object (DTO) for updating an existing VSUM. Contains validated fields required for
 * VSUM update requests.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VsumSyncChangesPutRequest {
  /**
   * Source IDs of the meta models of the VSUM. {@code null} keeps the VSUM's meta models; an empty
   * list removes them all.
   */
  private List<Long> metaModelIds;

  private Map<Long, @NotBlank String> metaModelNames;

  /**
   * The meta-model relations of the VSUM. {@code null}, like an empty list, removes them all, as
   * the UI sends {@code null} when the last relation was deleted.
   */
  @Valid private List<MetaModelRelationRequest> metaModelRelationRequests;

  /**
   * The views of the VSUM. {@code null} keeps the views, which only lose their links to removed
   * meta models; an empty list deletes them all.
   */
  private List<ViewRequest> viewRequests;

  private JsonNode layout;
}
