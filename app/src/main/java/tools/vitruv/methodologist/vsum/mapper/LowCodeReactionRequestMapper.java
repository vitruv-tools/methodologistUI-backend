package tools.vitruv.methodologist.vsum.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.LowCodeReactionRequestBase;

import java.util.HashMap;
import java.util.Map;

@Component
public class LowCodeReactionRequestMapper {

    private final ObjectMapper objectMapper;

    public LowCodeReactionRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LowCodeReactionRequestBase map(String template, Map<String, Object> params) {
        if (template == null) return null;

        // Combine template + params
        Map<String, Object> combined = new HashMap<>(params != null ? params : Map.of());
        combined.put("name", template);

        // Deserialize using polymorphic Jackson (@JsonTypeInfo on LowCodeReactionRequestBase)
        return objectMapper.convertValue(combined, LowCodeReactionRequestBase.class);
    }
}