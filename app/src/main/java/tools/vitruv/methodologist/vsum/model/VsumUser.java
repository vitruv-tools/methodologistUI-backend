package tools.vitruv.methodologist.vsum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.VsumRole;

/**
 * Entity representing the relationship between a User and a VSUM (Virtual Single Underlying Model).
 * Maps users to their roles within specific VSUMs and tracks when the relationship was created.
 *
 * Each VsumUser entry associates a user with a VSUM and defines their role (e.g., MEMBER)
 * in that VSUM's context. This enables role-based access control for VSUM operations.
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class VsumUser {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vsum_id")
  private Vsum vsum;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @NotNull
  @Enumerated(EnumType.STRING)
  private VsumRole role; // OWNER, EDITOR, VIEWER, ...

  @CreationTimestamp private Instant createdAt;
}
