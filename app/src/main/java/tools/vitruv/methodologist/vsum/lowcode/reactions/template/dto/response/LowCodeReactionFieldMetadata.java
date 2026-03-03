package tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response;

import lombok.*;

import java.util.List;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LowCodeReactionFieldMetadata {
    private String name;
    private String type;
    private boolean required;
    private boolean array;

    private boolean map;
    private String mapKeyType;
    private String mapValueType;

    /**
     * \@ReactionMetadata(name=?, description=?, hide=?, defaultStringValue=?)
     */
    private String displayName;
    private String displayDescription;
    private boolean displayHide;
    private String displayDefaultStringValue;
    private Integer displayDefaultIntValue;
    private boolean displayDefaultBooleanValue;
    private Double displayDefaultDoubleValue;

    /**
     * \@Schema(allowableValues=...) as list of enum names, e.g. ["A", "B", "C"]
     */
    private String[] allowableValues;

    /**
     * \@Size(min=?, max=?)
     * Applies to: String length, arrays length, collections size (depending on field type).
     */
    private Integer sizeMin;
    private Integer sizeMax;

    /**
     * \@Length(min=?, max=?)
     * Hibernate Validator annotation (primarily for String length).
     */
    private Integer lengthMin;
    private Integer lengthMax;

    /** \@Min(value=?) */
    private Long min;

    /** \@Max(value=?) */
    private Long max;

    /** \@DecimalMin(value=?, inclusive=?) */
    private String decimalMin;
    private Boolean decimalMinInclusive;

    /** \@DecimalMax(value=?, inclusive=?) */
    private String decimalMax;
    private Boolean decimalMaxInclusive;

    /** \@Pattern(regexp=?) */
    private String pattern;

    /** \@Pattern(flags=...) as list of enum names, e.g. ["CASE_INSENSITIVE"] */
    private List<String> patternFlags;
}
