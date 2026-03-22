package tools.vitruv.methodologist.vsum.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
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

  /** The VSUM this relation belongs to. */
  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vsum_id")
  private Vsum vsum;

  /** The source meta-model. */
  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id")
  private MetaModel source;

  /** The target meta-model. */
  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "target_id")
  private MetaModel target;

  /** The file storage for the reaction. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reaction_file_id")
  private FileStorage reactionFileStorage;

  /** The set of fine-granular meta-model relations. */
  @OneToMany(mappedBy = "metaModelRelation", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
  private Set<FineGranularMetaModelRelation> fineGranularMetaModelRelationSet = new HashSet<>();

  /** The timestamp when the relation was created. */
  @CreationTimestamp private Instant createdAt;
}
