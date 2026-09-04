package tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;
import tools.vitruv.methodologist.annotation.ReactionMetadata;

/** Request DTO for creating a corresponding root on insert root reaction. */
@EqualsAndHashCode(callSuper = true)
@Data
@Component
@ReactionMetadata(
    name = "Create Corresponding Root",
    description = "Create a corresponding root when a new root is inserted")
public class CreateCorrespondingRootOnInsertRootRequest extends LowCodeReactionRequestBase {

  @Schema(
      description = "Template discriminator",
      allowableValues = "create_corresponding_root_on_insert_root",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank
  @ReactionMetadata(hide = true)
  private String name = "create_corresponding_root_on_insert_root";

  @ReactionMetadata(defaultStringValue = "${sourceModelUri}")
  @NotNull
  private String model1Uri;

  @ReactionMetadata(defaultStringValue = "${targetModelUri}")
  @NotNull
  private String model2Uri;

  @ReactionMetadata(defaultStringValue = "${sourceModelAlias}")
  @NotNull
  private String model1Alias;

  @ReactionMetadata(defaultStringValue = "${targetModelAlias}")
  @NotNull
  private String model2Alias;

  @ReactionMetadata(
      defaultStringValue =
          "createCorrespondingRoot${capitalizeFirst(targetAlias)}"
              + "OnInsertRoot${capitalizeFirst(sourceAlias)}")
  @NotNull
  private String reactionName;

  @ReactionMetadata(defaultStringValue = "${sourceAlias}")
  @NotNull
  private String model1RootType;

  @ReactionMetadata(defaultStringValue = "${targetAlias}")
  @NotNull
  private String model2RootType;

  @ReactionMetadata(defaultStringValue = "${sourceAlias.toLowerCase()}")
  @NotNull
  private String model1RootVar;
}
