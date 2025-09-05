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
 * Data Transfer Object (DTO) for updating an existing VSUM. Contains validated fields required for
 * VSUM update requests.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VsumPutRequest {
  @NotNull @NotBlank private String name;
  @NotNull private List<Long> metaModelIds;
}
