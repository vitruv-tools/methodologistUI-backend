package tools.vitruv.methodologist.vsum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import tools.vitruv.methodologist.general.model.FileStorage;

/**
 * Entity representing a named relation between two {@link MetaModel} instances within a {@link
 * Vsum}. Each relation is associated with a reaction file and records its creation timestamp.
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_vsum_source_target_file",
          columnNames = {"vsum_id", "source_id", "target_id", "reaction_file_id"})
    })
public class MetaModelRelation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vsum_id")
  private Vsum vsum;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id")
  private MetaModel source;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "target_id")
  private MetaModel target;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reaction_file_id")
  private FileStorage reactionFileStorage;

  @CreationTimestamp private Instant createdAt;
}
