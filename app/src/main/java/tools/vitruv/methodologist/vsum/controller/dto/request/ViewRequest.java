package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO describing one view of a VSUM in a sync request.
 *
 * <p>A request updates the existing view with the given {@code id}. Without an id, or with an id
 * that is not one of the VSUM's views, it updates the view with the given {@code name}; without a
 * name, it updates the view with the same file and meta models. If no view matches, a view is
 * created. Views that no request describes are deleted.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ViewRequest {
  /** ID of the existing view this request describes, if any. */
  private Long id;

  /**
   * Name of the view, unique within the VSUM and at most 255 characters long (checked when the
   * changes are applied). If {@code null} or blank, an existing view keeps its name and a new view
   * is named after its file.
   */
  @Size(max = 255)
  private String name;

  /**
   * Description of the view. If {@code null}, an existing view keeps its description; an empty or
   * blank description removes it.
   */
  private String description;

  /**
   * Source IDs of the meta models the view uses; IDs of meta models the VSUM does not contain are
   * ignored. A request without id and name, as sent before views had names, is ignored if this is
   * empty.
   */
  @NotNull @NotEmpty private List<Long> metaModelIds;

  /** ID of the NeoJoin file defining the view. */
  @NotNull private Long fileStorageId;
}
