package tools.vitruv.methodologist.vsum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Join entity linking a VsumView to its associated metamodels.
 *
 * <p>Represents the relationship between a NeoJoin configuration and the metamodels it connects.
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class VsumViewMetaModel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vsum_view_id", nullable = false)
  private VsumView vsumView;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "meta_model_id", nullable = false)
  private MetaModel metaModel;
}
