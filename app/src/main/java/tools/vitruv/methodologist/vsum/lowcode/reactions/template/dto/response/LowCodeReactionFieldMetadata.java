package tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Metadata for a field in a low-code reaction. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LowCodeReactionFieldMetadata {
  // TODO: Should have read-only support --> See ReactionMetadata Annotation
  /** The name of the field. */
  private String name;

  /** The type of the field. */
  private String type;

  /** Whether the field is required. */
  private boolean required;

  /** Whether the field is an array. */
  private boolean array;

  /** Whether the field is a map. */
  private boolean map;

  /** The type of the keys in the map. */
  private String mapKeyType;

  /** The type of the values in the map. */
  private String mapValueType;

  /**
   * The display name of the field. \@ReactionMetadata(name=?, description=?, hide=?,
   * defaultStringValue=?)
   */
  private String displayName;

  /** The display description of the field. */
  private String displayDescription;

  /** Whether to hide the field in the display. */
  private boolean displayHide;

  /** The default string value for the display. */
  private String displayDefaultStringValue;

  /** The default integer value for the display. */
  private Integer displayDefaultIntValue;

  /** The default boolean value for the display. */
  private boolean displayDefaultBooleanValue;

  /** The default double value for the display. */
  private Double displayDefaultDoubleValue;

  /**
   * The allowable values for the field. \@Schema(allowableValues=...) as list of enum names, e.g.
   * ["A", "B", "C"]
   */
  private String[] allowableValues;

  /**
   * The minimum size of the field. \@Size(min=?, max=?) Applies to: String length, arrays length,
   * collections size.
   */
  private Integer sizeMin;

  /** The maximum size of the field. */
  private Integer sizeMax;

  /**
   * The minimum length of the field. \@Length(min=?, max=?) Hibernate Validator annotation
   * (primarily for String length).
   */
  private Integer lengthMin;

  /** The maximum length of the field. */
  private Integer lengthMax;

  /** The minimum value of the field. \@Min(value=?) */
  private Long min;

  /** The maximum value of the field. \@Max(value=?) */
  private Long max;

  /** The minimum decimal value of the field. \@DecimalMin(value=?, inclusive=?) */
  private String decimalMin;

  /** Whether the minimum decimal value is inclusive. */
  private Boolean decimalMinInclusive;

  /** The maximum decimal value of the field. \@DecimalMax(value=?, inclusive=?) */
  private String decimalMax;

  /** Whether the maximum decimal value is inclusive. */
  private Boolean decimalMaxInclusive;

  /** The pattern for the field value. \@Pattern(regexp=?) */
  private String pattern;

  /**
   * The flags for the pattern. \@Pattern(flags=...) as list of enum names, e.g.
   * ["CASE_INSENSITIVE"]
   */
  private List<String> patternFlags;
}
