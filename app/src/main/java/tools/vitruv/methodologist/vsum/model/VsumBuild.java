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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.user.model.User;

/**
 * One build of a {@link Vsum} through the setup-service.
 *
 * <p>A build records which inputs it was made from ({@link #fingerprint}), who asked for it, how it
 * went and, once it succeeded, the resulting fat JAR as a {@link FileStorage}. Builds are kept as
 * history; retention only clears the {@link #artifact} of old successful builds, it never deletes
 * the row.
 */
@Builder
@Setter
@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class VsumBuild {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vsum_id")
  private Vsum vsum;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requested_by_id")
  private User requestedBy;

  /** SHA-256 over the canonical form of the build inputs, see {@code VsumBuildInputs}. */
  @NotNull
  @Column(length = 64)
  private String fingerprint;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(length = 32)
  private VsumBuildStatus status;

  /** {@code true} if the requester asked to build even though an up-to-date artifact existed. */
  @Builder.Default private boolean forced = false;

  @Column(columnDefinition = "text")
  private String errorMessage;

  /** The built fat JAR; {@code null} until the build succeeds or after retention removed it. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "artifact_id")
  private FileStorage artifact;

  @CreationTimestamp private Instant createdAt;
  private Instant startedAt;
  private Instant finishedAt;
}
