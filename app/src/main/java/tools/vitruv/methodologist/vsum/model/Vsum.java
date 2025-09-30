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
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import tools.vitruv.methodologist.user.model.User;

/**
 * Represents a Virtual Single Underlying Model (VSUM) entity. Provides basic information about a
 * VSUM including its name and timestamps.
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Vsum {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private String name;
  private String description;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @CreationTimestamp private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;
  private Instant removedAt;

  @ToString.Exclude
  @OneToMany(mappedBy = "vsum", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
  private List<VsumMetaModel> vsumMetaModels = new ArrayList<>();

  @ToString.Exclude
  @OneToMany(mappedBy = "vsum", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
  private List<MetaModelRelation> metaModelRelations = new ArrayList<>();
}
