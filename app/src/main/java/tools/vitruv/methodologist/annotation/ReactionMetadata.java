package tools.vitruv.methodologist.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Annotation to provide metadata for reactions. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface ReactionMetadata {
  /**
   * The name of the reaction or field.
   *
   * @return the name
   */
  String name() default "";

  /**
   * The description of the reaction or field.
   *
   * @return the description
   */
  String description() default "";

  /**
   * Whether the reaction or field should be hidden.
   *
   * @return true if hidden, false otherwise
   */
  boolean hide() default false;

  // TODO: Add readonly property
  /**
   * The default string value.
   *
   * @return the default string value
   */
  String defaultStringValue() default "";

  /**
   * The default integer value.
   *
   * @return the default integer value
   */
  int defaultIntValue() default 0;

  /**
   * The default boolean value.
   *
   * @return the default boolean value
   */
  boolean defaultBooleanValue() default false;

  /**
   * The default double value.
   *
   * @return the default double value
   */
  double defaultDoubleValue() default 0.0;
}
