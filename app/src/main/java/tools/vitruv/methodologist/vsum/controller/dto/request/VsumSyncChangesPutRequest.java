package tools.vitruv.methodologist.vsum.controller.dto.request;

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
  private List<Long> metaModelIds;
  private Map<Long, @NotBlank String> metaModelNames;
  @Valid private List<MetaModelRelationRequest> metaModelRelationRequests;
  private List<ViewRequest> viewRequests;
}
