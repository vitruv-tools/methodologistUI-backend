package com.vitruv.methodologist.vsum.controller.dto.response;

import lombok.*;

import java.time.Instant;

/**
 * Data Transfer Object (DTO) for VSUM response data.
 * Contains fields representing a VSUM's state when returned by the API.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VsumResponse {
  private Long id;
  private String name;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant removedAt;
}
