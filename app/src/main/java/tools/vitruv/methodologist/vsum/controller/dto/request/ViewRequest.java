package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request DTO used to submit meta model IDs for view type operations. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ViewRequest {
  @NotNull @NotEmpty private List<Long> metaModelIds;
  @NotNull private Long fileStorageId;
}
