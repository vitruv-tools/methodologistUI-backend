package com.vitruv.methodologist.user.model;

import com.vitruv.methodologist.user.RoleType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Entity representing a user in the system.
 * Maps to the `usr` table in the database.
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "usr")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Email private String email;

  @NotNull
  @Enumerated(EnumType.STRING)
  private RoleType roleType;

  private String username;
  private String firstName;
  private String lastName;

  @CreationTimestamp private Instant createdAt;
  private Instant removedAt;
}
