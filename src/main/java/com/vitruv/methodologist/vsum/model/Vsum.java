package com.vitruv.methodologist.vsum.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Represents a Virtual Single Underlying Model (VSUM) entity.
 * Provides basic information about a VSUM including its name and timestamps.
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Vsum {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private String name;

  @CreationTimestamp private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;
  private Instant removedAt;
}
