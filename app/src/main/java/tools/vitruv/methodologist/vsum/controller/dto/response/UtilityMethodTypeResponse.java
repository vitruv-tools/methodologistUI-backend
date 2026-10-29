package tools.vitruv.methodologist.vsum.controller.dto.response;

/**
 * A metamodel type that utility methods can use.
 *
 * @param metamodel the qualified name of the metamodel package, e.g. {@code simulink}
 * @param name the simple name, e.g. {@code SubSystem}
 * @param qualifiedName the OCL name, e.g. {@code simulink::SubSystem}
 * @param kind {@code CLASS}, {@code ENUM} or {@code DATATYPE}
 * @param javaName the fully qualified name of the generated Java type
 */
public record UtilityMethodTypeResponse(
    String metamodel, String name, String qualifiedName, String kind, String javaName) {}
