package tools.vitruv.methodologist.vsum.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.vitruv.methodologist.general.model.FileStorage;

@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class FineGranularMetaModelRelation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull @NotBlank
    private String sourceId;
    @NotNull @NotBlank
    private String targetId;

    @NotNull @NotBlank
    private String lowCodeReactionTemplate;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meta_model_relation_id")
    private MetaModelRelation metaModelRelation;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reaction_file_id")
    private FileStorage reactionFileStorage;
}
