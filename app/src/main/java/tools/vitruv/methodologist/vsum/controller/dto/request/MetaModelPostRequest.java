package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object (DTO) for creating a new metamodel. Represents the request payload for POST
 * operations on metamodels.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MetaModelPostRequest {
  @NotNull
  @NotBlank
  @Size(max = 255)
  private String name;

  @Builder.Default
  @NotNull
  @NotBlank
  @Size(max = 255)
  private String version = "1.0";

  @NotNull
  @NotBlank
  @Size(max = 255)
  private String description;

  @NotNull
  @NotBlank
  @Size(max = 255)
  private String domain;

  @NotNull private List<String> keyword;
  @NotNull private Long ecoreFileId;
}
