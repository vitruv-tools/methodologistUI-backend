package tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;
import tools.vitruv.methodologist.annotation.ReactionMetadata;

/** Base class for low-code reaction requests. */
@Schema(
    description = "Low-code reaction request. The 'name' field selects the request shape.",
    discriminatorProperty = "name",
    subTypes = {
      CreateCorrespondingRootOnInsertRootRequest.class,
      CompositeReactionsRequest.class,
      ExampleRequest.class
    })
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "name",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = CreateCorrespondingRootOnInsertRootRequest.class,
      name = "create_corresponding_root_on_insert_root"),
  @JsonSubTypes.Type(value = CompositeReactionsRequest.class, name = "composite_reactions")
})
@Data
public abstract class LowCodeReactionRequestBase {
  /**
   * Gets the name of the reaction request.
   *
   * @return the name of the reaction
   */
  public abstract String getName();

  @ReactionMetadata(hide = true)
  private boolean regenerate = false;

  /**
   * Convert the typed request into the map your FreeMarker template expects.
   *
   * @return a map containing the template data
   */
  @SuppressWarnings("unchecked")
  public java.util.Map<String, Object> toTemplateData() {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.convertValue(this, Map.class);
  }
}
