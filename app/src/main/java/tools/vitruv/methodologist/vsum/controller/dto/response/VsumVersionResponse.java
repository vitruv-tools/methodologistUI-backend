package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A stable version of a VSUM as returned by the version endpoints. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VsumVersionResponse {
  private Integer number;

  /** Email of the user who published the version. */
  private String createdBy;

  private Instant createdAt;

  /** The build of the version; its JAR is downloaded through the build's artifact endpoint. */
  private Long buildId;
}
