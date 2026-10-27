package tools.vitruv.methodologist.vsum.utilitymethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Tracks the imports of the generated utility class. A type is referenced by its simple name if no
 * other type with that simple name is used, otherwise by its fully qualified name.
 */
final class JavaImports {

  private static final List<String> JAVA_LANG =
      List.of("String", "Integer", "Double", "Boolean", "Object", "Math", "Long", "Float");

  private final String ownPackage;
  private final Map<String, String> fqnBySimpleName = new HashMap<>();
  private final TreeSet<String> imports = new TreeSet<>();

  JavaImports(String ownPackage, String ownClassName) {
    this.ownPackage = ownPackage;
    for (String name : JAVA_LANG) {
      fqnBySimpleName.put(name, "java.lang." + name);
    }
    fqnBySimpleName.put(ownClassName, ownPackage + "." + ownClassName);
  }

  private JavaImports(JavaImports other) {
    this.ownPackage = other.ownPackage;
    this.fqnBySimpleName.putAll(other.fqnBySimpleName);
    this.imports.addAll(other.imports);
  }

  /** A copy whose changes do not affect this instance. */
  JavaImports copy() {
    return new JavaImports(this);
  }

  /**
   * Returns the name to use for the type in the generated code and records the import if needed.
   *
   * @param fqn the fully qualified name
   * @return the simple name, or {@code fqn} if the simple name is taken by another type
   */
  String ref(String fqn) {
    int dot = fqn.lastIndexOf('.');
    if (dot < 0) {
      return fqn;
    }
    String simpleName = fqn.substring(dot + 1);
    String existing = fqnBySimpleName.putIfAbsent(simpleName, fqn);
    if (existing != null && !existing.equals(fqn)) {
      return fqn;
    }
    String pkg = fqn.substring(0, dot);
    if (!pkg.equals("java.lang") && !pkg.equals(ownPackage)) {
      imports.add(fqn);
    }
    return simpleName;
  }

  /** The recorded imports, sorted. */
  List<String> imports() {
    return List.copyOf(imports);
  }
}
