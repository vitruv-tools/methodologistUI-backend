package tools.vitruv.methodologist.vsum.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.Type;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.VsumRepresentation;

/**
 * JSON snapshot of the VSUM state at the time this history record was created.
 *
 * <p>Persisted in a JSON column using Hibernate Types and mapped via the configured {@code "json"}
 * type. Serialized from and deserialized to {@link VsumRepresentation}. May be {@code null}.
 */
@Builder
@Setter
@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class VsumHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * When the snapshot was taken. Set by {@code VsumHistoryService} from the clock it also measures
   * the writeback interval with, rather than by Hibernate.
   */
  private Instant createdAt;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "creator_id")
  private User creator;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vsum_id")
  private Vsum vsum;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", nullable = false)
  private VsumRepresentation representation;

  /**
   * Whether the snapshot was taken before a writeback of the collaboration server, rather than
   * before a user's change or revert.
   */
  @Column(nullable = false)
  private boolean collaborationWriteback;
}
