package tools.vitruv.methodologist.vsum.controller.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.vitruv.methodologist.vsum.VsumRole;

/**
 * Response DTO for the collaboration server describing an active user of a VSUM and the user's role
 * in it. It carries no name or email; the collaboration server compares only the user IDs of its
 * connections with them.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VsumMemberAccessResponse {
  private Long userId;
  private VsumRole role;
}
