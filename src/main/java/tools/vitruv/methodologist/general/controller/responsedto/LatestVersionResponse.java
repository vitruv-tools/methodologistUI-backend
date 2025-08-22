package tools.vitruv.methodologist.general.controller.responsedto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LatestVersionResponse {
  private Boolean forceUpdate;
  private String version;
}
