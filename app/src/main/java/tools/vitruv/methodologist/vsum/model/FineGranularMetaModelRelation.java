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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import tools.vitruv.methodologist.general.model.FileStorage;

/** Entity representing a fine-granular meta-model relation. */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class FineGranularMetaModelRelation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull @NotBlank private String sourceId;
  @NotNull @NotBlank private String targetId;

  private String lowCodeReactionTemplate;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  @EqualsAndHashCode.Exclude
  private Map<String, Object> lowCodeReactionTemplateParams;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "meta_model_relation_id")
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private MetaModelRelation metaModelRelation;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reaction_file_id")
  @EqualsAndHashCode.Exclude
  private FileStorage reactionFileStorage;

  @CreationTimestamp @EqualsAndHashCode.Exclude private Instant createdAt;
}
