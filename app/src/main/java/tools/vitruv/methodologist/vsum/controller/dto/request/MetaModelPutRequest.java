package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for creating or updating a metamodel.
 *
 * <p>All fields are required and validated with Jakarta Bean Validation annotations.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MetaModelPutRequest {
  @NotNull @NotBlank private String name;
  @NotNull @NotBlank private String description;
  @NotNull @NotBlank private String domain;
  @NotNull private List<String> keyword;
  @NotNull private Long ecoreFileId;
  @NotNull private Long genModelFileId;
}
