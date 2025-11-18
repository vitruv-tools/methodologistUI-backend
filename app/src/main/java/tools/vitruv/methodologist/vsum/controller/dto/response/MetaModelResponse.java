package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response DTO representing a metamodel entry exposed through API responses. Contains metadata such
 * as descriptive information, domain, keywords, timestamps, and references to related Ecore and
 * GenModel files.
 *
 * <p>Used by {@code MetaModelController} to return metamodel details to clients.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MetaModelResponse {
  private Long id;
  private String name;
  private String description;
  private String domain;
  private Long sourceId;
  private List<String> keyword;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant removedAt;
  private Long ecoreFileId;
  private Long genModelFileId;
}
