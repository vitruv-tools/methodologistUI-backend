package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data transfer object representing a user associated with a VSUM. Contains user identification,
 * contact information, role, and creation timestamp.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VsumUserResponse {
  private Long id;
  private Long vsumId;
  private String firstName;
  private String lastName;
  private String email;
  private String roleEn;
  private Instant createdAt;
}
