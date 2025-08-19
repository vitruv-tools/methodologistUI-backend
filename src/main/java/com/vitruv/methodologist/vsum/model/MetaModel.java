package com.vitruv.methodologist.vsum.model;

import com.vitruv.methodologist.general.model.FileStorage;
import com.vitruv.methodologist.user.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import lombok.*;
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
public class MetaModel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private String name;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @NotNull
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "file_storage_id")
  private FileStorage fileStorage;

  @CreationTimestamp private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;
  private Instant removedAt;
}
