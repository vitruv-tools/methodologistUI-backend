package tools.vitruv.methodologist.vsum.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.vitruv.methodologist.exception.BuildProblem;
import tools.vitruv.methodologist.vsum.model.VsumBuildStatus;

/** State of one VSUM build as returned by the build endpoints. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VsumBuildResponse {
  private Long id;
  private Long vsumId;
  private VsumBuildStatus status;
  private String fingerprint;
  private boolean forced;

  /** {@code true} if the JAR of this build can still be downloaded. */
  private boolean artifactAvailable;

  private String errorMessage;

  /** Error code of the setup-service if the build failed there; absent otherwise. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String errorCode;

  /** Problems of the failed build, root causes first; absent if none were reported. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<BuildProblem> problems;

  private String requestedBy;
  private Instant createdAt;
  private Instant startedAt;
  private Instant finishedAt;
}
