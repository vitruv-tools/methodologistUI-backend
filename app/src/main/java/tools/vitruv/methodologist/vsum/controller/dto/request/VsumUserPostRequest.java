package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request DTO for adding a user to a VSUM. Contains the VSUM ID and the user ID to associate. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VsumUserPostRequest {
  @NotNull private Long vsumId;
  @NotNull private Long userId;
}
