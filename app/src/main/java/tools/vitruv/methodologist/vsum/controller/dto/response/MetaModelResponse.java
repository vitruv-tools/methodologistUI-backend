package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object (DTO) for representing metamodel information in API responses. Contains all
 * relevant metamodel attributes including timestamps for creation, updates, and deletion.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MetaModelResponse {
  private Long id;
  private String name;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant removedAt;
  private Long ecoreFileId;
  private Long genModelFileId;
}
