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
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import tools.vitruv.methodologist.general.model.FileStorage;

/**
 * Entity representing a named relation between two {@link MetaModel} instances within a {@link
 * Vsum}. The coarse reaction file is optional when the relation is defined only by {@link
 * FineGranularMetaModelRelation} children.
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_vsum_source_target",
          columnNames = {"vsum_id", "source_id", "target_id"})
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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reaction_file_id")
  private FileStorage reactionFileStorage;

  @Builder.Default
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  @OneToMany(
      mappedBy = "metaModelRelation",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  private Set<FineGranularMetaModelRelation> fineGranularMetaModelRelationSet = new HashSet<>();

  @CreationTimestamp private Instant createdAt;
}
