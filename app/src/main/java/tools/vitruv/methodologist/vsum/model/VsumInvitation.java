package tools.vitruv.methodologist.vsum.model;

import jakarta.persistence.Column;
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
import tools.vitruv.methodologist.vsum.VsumInvitationStatus;
import tools.vitruv.methodologist.vsum.VsumRole;

/**
 * Entity representing an invitation for an email address to join a VSUM with a given role.
 *
 * <p>When the invited email already belongs to a registered user, a {@link VsumUser} membership is
 * created directly and the invitation is stored as {@link VsumInvitationStatus#ACCEPTED}. When the
 * email is not yet registered, the invitation is stored as {@link VsumInvitationStatus#PENDING} and
 * is applied automatically once that user registers.
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class VsumInvitation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vsum_id")
  private Vsum vsum;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invited_by")
  private User invitedBy;

  @NotNull
  @Column(name = "invitee_email")
  private String inviteeEmail;

  @NotNull
  @Enumerated(EnumType.STRING)
  private VsumRole role;

  @NotNull
  @Enumerated(EnumType.STRING)
  private VsumInvitationStatus status;

  @CreationTimestamp private Instant createdAt;

  private Instant acceptedAt;
}
