package com.vitruv.methodologist.user.model;

import com.vitruv.methodologist.user.RoleType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

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
