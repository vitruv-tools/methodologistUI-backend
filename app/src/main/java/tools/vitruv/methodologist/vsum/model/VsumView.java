package tools.vitruv.methodologist.vsum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import tools.vitruv.methodologist.general.model.FileStorage;

/**
 * Represents a NeoJoin configuration (view) within a VSUM.
 *
 * <p>Each view is backed by a NeoJoin file and can be associated with multiple metamodels.
 */
@Getter
@Setter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class VsumView {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @EqualsAndHashCode.Include
  @ToString.Include
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vsum_id", nullable = false)
  @ToString.Exclude
  private Vsum vsum;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "file_storage_id", nullable = false)
  @ToString.Exclude
  private FileStorage fileStorage;

  @OneToMany(mappedBy = "vsumView", fetch = FetchType.LAZY)
  @ToString.Exclude
  private Set<VsumViewMetaModel> viewMetaModels = new HashSet<>();

  @CreationTimestamp private Instant createdAt;
}
