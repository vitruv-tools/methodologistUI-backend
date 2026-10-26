package tools.vitruv.methodologist.vsum.helper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A helper as defined by the methodologist: {@code helper def : name(p : T, ...) : R = body;}.
 *
 * @param name the helper name, which becomes the Java method name
 * @param description optional documentation
 * @param parameters the parameters in declaration order
 * @param returnType the OCL return type, e.g. {@code Sequence(simulink::SubSystem)}
 * @param imports metamodel types usable by their simple name, e.g. {@code simulink::SubSystem} or
 *     {@code simulink::*}
 * @param body the OCL expression
 */
public record HelperDefinition(
    String name,
    String description,
    List<Parameter> parameters,
    String returnType,
    List<String> imports,
    String body) {

  /**
   * A helper parameter.
   *
   * @param name the parameter name
   * @param type the OCL type
   */
  public record Parameter(String name, String type) {}

  /** Normalizes {@code null} lists. */
  public HelperDefinition {
    parameters = parameters == null ? List.of() : List.copyOf(parameters);
    imports = imports == null ? List.of() : List.copyOf(imports);
  }

  /** The complete helper definition, as shown to the methodologist. */
  public String oclDefinition() {
    String params =
        parameters.stream().map(p -> p.name() + " : " + p.type()).collect(Collectors.joining(", "));
    String trimmedBody = body == null ? "" : body.strip();
    if (trimmedBody.endsWith(";")) {
      trimmedBody = trimmedBody.substring(0, trimmedBody.length() - 1).stripTrailing();
    }
    String indentedBody =
        trimmedBody.lines().map(line -> "\t" + line).collect(Collectors.joining("\n"));
    return "helper def : "
        + name
        + "("
        + params
        + ") : "
        + returnType
        + " =\n"
        + indentedBody
        + ";";
  }
}
