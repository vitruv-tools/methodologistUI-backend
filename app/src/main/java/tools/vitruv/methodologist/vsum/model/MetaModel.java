package tools.vitruv.methodologist.vsum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import tools.vitruv.methodologist.general.model.FileStorage;
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
public class MetaModel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull @NotBlank private String name;
  @NotNull @NotBlank private String description;
  @NotNull @NotBlank private String domain;
  @NotNull private List<String> keyword;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ecore_file_id")
  private FileStorage ecoreFile;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "gen_model_file_id")
  private FileStorage genModelFile;

  @NotNull @Builder.Default private Boolean isClone = false;

  @CreationTimestamp private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;
  private Instant removedAt;
}
