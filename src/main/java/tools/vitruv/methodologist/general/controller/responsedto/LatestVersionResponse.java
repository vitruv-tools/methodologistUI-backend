package tools.vitruv.methodologist.general.controller.responsedto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object (DTO) representing the latest version information of the application.
 * This response is typically returned by version-check endpoints to inform clients
 * about the current version and whether a forced update is required.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LatestVersionResponse {
  private Boolean forceUpdate;
  private String version;
}
