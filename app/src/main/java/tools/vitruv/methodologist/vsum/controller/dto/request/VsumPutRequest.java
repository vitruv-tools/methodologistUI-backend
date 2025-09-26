package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO for updating an existing VSUM.
 *
 * <p>Contains the mutable fields of a VSUM. Jakarta Bean Validation annotations are enforced during
 * request binding. Lombok generates accessors, constructors, and a builder.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VsumPutRequest {
  @NotNull @NotBlank private String name;
}
