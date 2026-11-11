package tools.vitruv.methodologist.vsum.utilitymethod;

import java.util.List;

/**
 * A utility method as defined by the methodologist: a name, typed parameters, a return type and an
 * Xtend body, which become a {@code def static} method of the generated utility class.
 *
 * @param name the utility method name, which becomes the Java method name
 * @param description optional documentation
 * @param parameters the parameters in declaration order
 * @param returnType the Xtend/Java return type, e.g. {@code List<SubSystem>}
 * @param imports Java imports without keyword and semicolon, e.g. {@code
 *     tools.vitruv.methodologisttemplate.model.simulink.*}
 * @param body the Xtend method body without the surrounding braces
 */
public record UtilityMethodDefinition(
    String name,
    String description,
    List<Parameter> parameters,
    String returnType,
    List<String> imports,
    String body) {

  /**
   * A utility method parameter.
   *
   * @param name the parameter name
   * @param type the Xtend/Java type
   */
  public record Parameter(String name, String type) {}

  /** Normalizes {@code null} lists. */
  public UtilityMethodDefinition {
    parameters = parameters == null ? List.of() : List.copyOf(parameters);
    imports = imports == null ? List.of() : List.copyOf(imports);
  }
}
