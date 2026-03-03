package tools.vitruv.methodologist.vsum.lowcode.reactions.template.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import org.hibernate.validator.constraints.Length;
import org.jspecify.annotations.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.annotation.ReactionMetadata;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.LowCodeReactionRequestBase;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response.LowCodeReactionFieldMetadata;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response.LowCodeReactionMetadata;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response.LowCodeReactionMetadataResponse;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import static tools.vitruv.methodologist.messages.Message.LOWCODE_REACTIONS_METADATA_LOADED_SUCCESSFULLY;

@RestController
@RequestMapping("/api/")
@Validated
@AllArgsConstructor
public class LowCodeReactionMetadataController {
    private final List<LowCodeReactionRequestBase> lowCodeReactionRequestBaseList;

    private static String simpleTypeName(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz.getSimpleName();
        }
        if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> rawClazz) {
            return rawClazz.getSimpleName();
        }
        return "Object";
    }

    private @NonNull List<LowCodeReactionFieldMetadata> getLowCodeReactionFieldMetadata(Class<? extends LowCodeReactionRequestBase> dtoClass) {
        List<LowCodeReactionFieldMetadata> fields = new ArrayList<>();
        for (Field field : dtoClass.getDeclaredFields()) {
            LowCodeReactionFieldMetadata meta = new LowCodeReactionFieldMetadata();
            extractTypeInformation(field, meta);

            ReactionMetadata reactionMetadata = field.getAnnotation(ReactionMetadata.class);
            if (reactionMetadata != null) {
                meta.setDisplayName(reactionMetadata.name());
                meta.setDisplayDescription(reactionMetadata.description());
                meta.setDisplayHide(reactionMetadata.hide());
            }

            Size size = field.getAnnotation(Size.class);
            if (size != null) {
                meta.setSizeMin(size.min());
                meta.setSizeMax(size.max());
            }

            Length length = field.getAnnotation(Length.class);
            if (length != null) {
                meta.setLengthMin(length.min());
                meta.setLengthMax(length.max());
            }

            Min min = field.getAnnotation(Min.class);
            if (min != null) {
                meta.setMin(min.value());
            }

            Max max = field.getAnnotation(Max.class);
            if (max != null) {
                meta.setMax(max.value());
            }

            DecimalMin decimalMin = field.getAnnotation(DecimalMin.class);
            if (decimalMin != null) {
                meta.setDecimalMin(decimalMin.value());
                meta.setDecimalMinInclusive(decimalMin.inclusive());
            }

            DecimalMax decimalMax = field.getAnnotation(DecimalMax.class);
            if (decimalMax != null) {
                meta.setDecimalMax(decimalMax.value());
                meta.setDecimalMaxInclusive(decimalMax.inclusive());
            }

            Schema schema = field.getAnnotation(Schema.class);
            if (schema != null) {
                meta.setAllowableValues(schema.allowableValues());
            }

            Pattern pattern = field.getAnnotation(Pattern.class);
            if (pattern != null) {
                meta.setPattern(pattern.regexp());
                if (pattern.flags() != null && pattern.flags().length > 0) {
                    meta.setPatternFlags(Arrays.stream(pattern.flags()).map(Enum::name).toList());
                }
            }
            fields.add(meta);
        }
        return fields;
    }

    private void extractHeaderInformation(Class<? extends LowCodeReactionRequestBase> dtoClass, LowCodeReactionMetadata.LowCodeReactionMetadataBuilder builder) {
        ReactionMetadata reactionMetadata = dtoClass.getAnnotation(ReactionMetadata.class);
        if (reactionMetadata != null) {
            builder.name(reactionMetadata.name());
            builder.description(reactionMetadata.description());
            builder.hide(reactionMetadata.hide());
        }
    }

    private void extractTypeInformation(Field field, LowCodeReactionFieldMetadata meta) {
        meta.setName(field.getName());
        meta.setType(field.getType().getSimpleName());
        meta.setRequired(field.isAnnotationPresent(NotNull.class) || field.isAnnotationPresent(NotBlank.class));

        if (field.getType().isArray()) {
            meta.setArray(true);
            meta.setType(field.getType().getComponentType().getSimpleName());
        } else if (Collection.class.isAssignableFrom(field.getType())) {
            meta.setArray(true);

            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length == 1) {
                    Type elementType = args[0];
                    meta.setType(simpleTypeName(elementType));
                }
            } else {
                meta.setType("Object");
            }
        } else if (Map.class.isAssignableFrom(field.getType())) {
            meta.setMap(true);

            // Best-effort extraction of K/V generic types. We don't try to interpret nested maps.
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length == 2) {
                    meta.setMapKeyType(simpleTypeName(args[0]));
                    meta.setMapValueType(simpleTypeName(args[1]));
                    meta.setType(meta.getMapValueType());
                } else {
                    meta.setMapKeyType("Object");
                    meta.setMapValueType("Object");
                    meta.setType("Object");
                }
            } else {
                meta.setMapKeyType("Object");
                meta.setMapValueType("Object");
                meta.setType("Object");
            }
        }
    }

    @Operation(summary = "Get metadata for low-code reactions", description = "Gets the configuration metadata for a low-code reactions")
    @GetMapping("/lowcode-metadata")
    @PreAuthorize("hasRole('user')")
    public ResponseTemplateDto<LowCodeReactionMetadataResponse> getDtoMetadata() {
        var data = lowCodeReactionRequestBaseList.stream().map(dto -> new AbstractMap.SimpleEntry<>(dto.getName(), getDtoMetadata(dto.getClass()))).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return ResponseTemplateDto.<LowCodeReactionMetadataResponse>builder().data(LowCodeReactionMetadataResponse.builder().reactionMetadataMap(data).build()).message(LOWCODE_REACTIONS_METADATA_LOADED_SUCCESSFULLY).build();
    }

    private LowCodeReactionMetadata getDtoMetadata(Class<? extends LowCodeReactionRequestBase> dtoClass) {
        var builder = LowCodeReactionMetadata.builder();
        getDtoMetadata(dtoClass, builder);
        return builder.build();
    }

    private List<LowCodeReactionFieldMetadata> getDtoMetadata(Class<? extends LowCodeReactionRequestBase> dtoClass, LowCodeReactionMetadata.LowCodeReactionMetadataBuilder builder) {
        var superClass = dtoClass.getSuperclass();
        List<LowCodeReactionFieldMetadata> fields = new ArrayList<>();
        if (superClass != null && LowCodeReactionRequestBase.class.isAssignableFrom(superClass)) {
            fields.addAll(getDtoMetadata(superClass.asSubclass(LowCodeReactionRequestBase.class), builder));
        }
        fields.addAll(getLowCodeReactionFieldMetadata(dtoClass));
        builder.fields(fields);
        extractHeaderInformation(dtoClass, builder);
        return fields;
    }
}