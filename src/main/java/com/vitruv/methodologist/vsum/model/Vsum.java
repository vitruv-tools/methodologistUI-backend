package com.vitruv.methodologist.vsum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
