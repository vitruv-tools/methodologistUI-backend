package tools.vitruv.methodologist.vsum.controller.dto.request;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object (DTO) for creating a new metamodel. Represents the request payload for POST
 * operations on metamodels.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MetaModelFilterRequest {
  private String name;
  private String description;
  private String domain;
  private List<String> keyword;
  private Long ecoreFileId;
  private Long genModelFileId;

  @Builder.Default private Boolean ownedByUser = false;
  private Instant createdFrom;
  private Instant createdTo;
}
