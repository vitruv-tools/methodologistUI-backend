package tools.vitruv.methodologist.vsum.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.LowCodeReactionRequestBase;

/** Mapper for low-code reaction requests. */
@Component
public class LowCodeReactionRequestMapper {

  private final ObjectMapper objectMapper;

  /**
   * Constructs a new LowCodeReactionRequestMapper.
   *
   * @param objectMapper the object mapper to use
   */
  public LowCodeReactionRequestMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Maps a template and its parameters to a LowCodeReactionRequestBase.
   *
   * @param template the template name
   * @param params the parameters for the template
   * @return the mapped low-code reaction request
   */
  public LowCodeReactionRequestBase map(String template, Map<String, Object> params) {
    if (template == null) {
      return null;
    }

    // Combine template + params
    Map<String, Object> combined = new HashMap<>(params != null ? params : Map.of());
    combined.put("name", template);

    // Deserialize using polymorphic Jackson (@JsonTypeInfo on LowCodeReactionRequestBase)
    return objectMapper.convertValue(combined, LowCodeReactionRequestBase.class);
  }
}
