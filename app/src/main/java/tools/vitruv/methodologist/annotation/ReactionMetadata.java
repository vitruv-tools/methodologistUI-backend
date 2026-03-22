package tools.vitruv.methodologist.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface ReactionMetadata {
  String name() default "";

  String description() default "";

  boolean hide() default false;

  // TODO: Add readonly property
  String defaultStringValue() default "";

  int defaultIntValue() default 0;

  boolean defaultBooleanValue() default false;

  double defaultDoubleValue() default 0.0;
}
