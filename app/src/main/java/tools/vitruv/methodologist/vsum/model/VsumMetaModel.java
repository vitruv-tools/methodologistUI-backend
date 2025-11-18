package tools.vitruv.methodologist.vsum.model;

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
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing the association between a {@link Vsum} and a {@link MetaModel}.
 *
 * <p>This entity tracks which metamodels are linked to a given VSUM (Versioned System Under
 * Management) instance. It stores metadata about the relationship, including creation, update, and
 * optional removal timestamps.
 *
 * <p>Fields:
 *
 * <ul>
 *   <li><b>id</b> — unique identifier for the link entry.
 *   <li><b>vsum</b> — the owning VSUM to which the metamodel belongs (cannot be null).
 *   <li><b>metaModel</b> — the linked metamodel entity (cannot be null).
 *   <li><b>createdAt</b> — timestamp automatically set when the link is created.
 *   <li><b>updatedAt</b> — timestamp automatically updated when the link is modified.
 *   <li><b>removedAt</b> — optional timestamp indicating when the link was removed.
 * </ul>
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class VsumMetaModel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vsum_id")
  private Vsum vsum;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "meta_model_id")
  private MetaModel metaModel;

  @CreationTimestamp private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;
  private Instant removedAt;
}
