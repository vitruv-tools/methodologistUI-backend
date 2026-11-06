package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.vitruv.methodologist.exception.BuildProblem;

/** Latest validation result of a VSUM: whether it is valid, the problems found, and when. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VsumValidationResponse {
  private boolean valid;
  private List<BuildProblem> problems;
  private Instant validatedAt;
}
