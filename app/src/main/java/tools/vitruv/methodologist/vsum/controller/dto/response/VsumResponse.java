package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.vitruv.methodologist.vsum.VsumRole;

/**
 * Data Transfer Object (DTO) for VSUM response data. Contains fields representing a VSUM's state
 * when returned by the API.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VsumResponse {
  private Long id;
  private String name;
  private VsumRole role;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant removedAt;
}
