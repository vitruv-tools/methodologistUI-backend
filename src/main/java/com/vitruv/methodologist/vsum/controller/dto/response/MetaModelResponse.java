package com.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;
import lombok.*;

/**
 * Data Transfer Object (DTO) for representing metamodel information in API responses.
 * Contains all relevant metamodel attributes including timestamps for creation, updates, and deletion.
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
}
