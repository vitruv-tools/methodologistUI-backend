package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What a published version of a VSUM looks like on the canvas. Its file ids refer to the version's
 * own copies, which members can download like any other file of the VSUM.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VsumVersionContentResponse {
  /** Version number, counted per VSUM: 1, 2, 3, ... */
  private Integer number;

  /** Email of the user who published it. */
  private String createdBy;

  private Instant createdAt;

  /** The project as {@code GET /api/v1/vsums/{id}/details} returned it at publish time. */
  private VsumMetaModelResponse project;

  /** The project's rule sets at publish time, as {@code GET /api/v1/vsums/{id}/rule-sets}. */
  private List<RuleSetResponse> ruleSets;
}
