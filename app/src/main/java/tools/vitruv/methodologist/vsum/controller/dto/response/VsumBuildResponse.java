package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
  private String requestedBy;
  private Instant createdAt;
  private Instant startedAt;
  private Instant finishedAt;
}
