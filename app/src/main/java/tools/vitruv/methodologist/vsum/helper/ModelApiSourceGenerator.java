package tools.vitruv.methodologist.vsum.helper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex.ClassInfo;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex.ClassifierInfo;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex.DataTypeInfo;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex.EnumInfo;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex.FeatureInfo;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex.OperationInfo;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex.PackageInfo;

/**
 * Generates Java source for the API EMF generates from the metamodels of a VSUM: an interface per
 * EClass, an enum per EEnum and the factory and package interface of every EPackage.
 *
 * <p>The sources only declare the API, they carry no implementation. They are compiled for the
 * reactions editor's language server, which resolves Java types from its classpath only, so that
 * metaclasses and the generated helper class get real Java types in the editor. Names follow EMF's
 * defaults, which the genmodels the setup-service creates for every VSUM build use as well. All
 * types are referenced by their qualified names, so the sources need no imports.
 */
public final class ModelApiSourceGenerator {

  private static final String EMF_ECORE = "org.eclipse.emf.ecore.";
  private static final String E_OBJECT = EMF_ECORE + "EObject";
  private static final String E_LIST = "org.eclipse.emf.common.util.EList";
  private static final String ENUMERATOR = "org.eclipse.emf.common.util.Enumerator";

  /**
   * EClasses of the Ecore metamodel, whose generated interfaces live in {@code
   * org.eclipse.emf.ecore}.
   */
  private static final Set<String> ECORE_CLASSES =
      Set.of(
          "EObject",
          "EModelElement",
          "ENamedElement",
          "ETypedElement",
          "EClassifier",
          "EClass",
          "EDataType",
          "EEnum",
          "EEnumLiteral",
          "EStructuralFeature",
          "EAttribute",
          "EReference",
          "EOperation",
          "EParameter",
          "EPackage",
          "EFactory",
          "EAnnotation",
          "EGenericType",
          "ETypeParameter");

  /** Ecore data types whose instance class {@link OclTypeResolver} does not know. */
  private static final Map<String, String> OTHER_ECORE_DATA_TYPES =
      Map.of(
          "EJavaClass", "java.lang.Class<?>",
          "EByteArray", "byte[]",
          "EEnumerator", ENUMERATOR,
          "EEList", E_LIST + "<?>",
          "EResource", "org.eclipse.emf.ecore.resource.Resource",
          "EResourceSet", "org.eclipse.emf.ecore.resource.ResourceSet",
          "EFeatureMap", "org.eclipse.emf.ecore.util.FeatureMap",
          "EFeatureMapEntry", "org.eclipse.emf.ecore.util.FeatureMap.Entry",
          "EDiagnosticChain", "org.eclipse.emf.common.util.DiagnosticChain");

  private ModelApiSourceGenerator() {}

  /**
   * Generates the API sources of all packages in the index, including subpackages.
   *
   * @param index the VSUM's metamodels
   * @return the source of each type, keyed by its fully qualified name, in a stable order
   */
  public static Map<String, String> generate(EcoreTypeIndex index) {
    Map<String, String> sources = new LinkedHashMap<>();
    Deque<PackageInfo> pending = new ArrayDeque<>(index.rootPackages());
    while (!pending.isEmpty()) {
      PackageInfo pkg = pending.poll();
      generatePackage(pkg, sources);
      pending.addAll(pkg.subpackages());
    }
    return sources;
  }

  private static void generatePackage(PackageInfo pkg, Map<String, String> sources) {
    String javaPackage = pkg.javaPackage();
    for (ClassifierInfo classifier : pkg.classifiers()) {
      switch (classifier) {
        case ClassInfo eClass -> sources.put(eClass.javaQualifiedName(), classSource(eClass));
        case EnumInfo eEnum -> sources.put(eEnum.javaQualifiedName(), enumSource(eEnum));
        case DataTypeInfo ignored -> {
          // EMF generates no type for a data type; its values use the instance class.
        }
      }
    }
    sources.put(javaPackage + "." + factoryName(pkg), factorySource(pkg));
    sources.put(javaPackage + "." + packageName(pkg), packageSource(pkg));
  }

  // ── classifiers ──────────────────────────────────────────────────────────

  private static String classSource(ClassInfo eClass) {
    String superTypes =
        eClass.superTypes().isEmpty()
            ? E_OBJECT
            : eClass.superTypes().stream()
                .map(ClassifierInfo::javaQualifiedName)
                .collect(Collectors.joining(", "));
    StringBuilder body = new StringBuilder();
    for (FeatureInfo feature : eClass.features()) {
      String type = typeOf(feature);
      body.append("  ").append(type).append(' ').append(getterName(feature)).append("();\n");
      if (!feature.many() && feature.changeable()) {
        body.append("  void set")
            .append(JavaNames.capName(feature.name()))
            .append('(')
            .append(type)
            .append(" value);\n");
      }
    }
    for (OperationInfo operation : eClass.operations()) {
      body.append("  ")
          .append(operation.result().typed() ? typeOf(operation.result()) : "void")
          .append(' ')
          .append(JavaNames.safeName(operation.name()))
          .append('(')
          .append(
              operation.parameters().stream()
                  .map(p -> typeOf(p) + " " + JavaNames.safeName(p.name()))
                  .collect(Collectors.joining(", ")))
          .append(");\n");
    }
    return compilationUnit(
        eClass.pkg(),
        "public interface "
            + JavaNames.safeName(eClass.name())
            + " extends "
            + superTypes
            + " {\n"
            + body
            + "}\n");
  }

  private static String enumSource(EnumInfo eEnum) {
    String name = JavaNames.safeName(eEnum.name());
    String constants =
        eEnum.literals().stream()
            .map(
                literal ->
                    "  "
                        + JavaNames.enumLiteralName(literal)
                        + "("
                        + JavaNames.stringLiteral(literal)
                        + ")")
            .collect(Collectors.joining(",\n"));
    String body =
        "public enum "
            + name
            + " implements "
            + ENUMERATOR
            + " {\n"
            + constants
            + ";\n\n"
            + "  public static final java.util.List<"
            + name
            + "> VALUES = java.util.List.of(values());\n\n"
            + "  private final String literal;\n\n"
            + "  "
            + name
            + "(String literal) {\n"
            + "    this.literal = literal;\n"
            + "  }\n\n"
            + lookup(name, "get(String literal)", "getLiteral().equals(literal)")
            + lookup(name, "getByName(String name)", "getName().equals(name)")
            + lookup(name, "get(int value)", "getValue() == value")
            + "  @Override\n  public int getValue() {\n    return ordinal();\n  }\n\n"
            + "  @Override\n  public String getName() {\n    return literal;\n  }\n\n"
            + "  @Override\n  public String getLiteral() {\n    return literal;\n  }\n\n"
            + "  @Override\n  public String toString() {\n    return literal;\n  }\n"
            + "}\n";
    return compilationUnit(eEnum.pkg(), body);
  }

  private static String lookup(String enumName, String signature, String condition) {
    return "  public static "
        + enumName
        + " "
        + signature
        + " {\n"
        + "    for ("
        + enumName
        + " candidate : VALUES) {\n"
        + "      if (candidate."
        + condition
        + ") {\n"
        + "        return candidate;\n"
        + "      }\n"
        + "    }\n"
        + "    return null;\n"
        + "  }\n\n";
  }

  // ── factory and package ──────────────────────────────────────────────────

  private static String factorySource(PackageInfo pkg) {
    String name = factoryName(pkg);
    StringBuilder body = new StringBuilder();
    body.append("public interface ")
        .append(name)
        .append(" extends ")
        .append(EMF_ECORE)
        .append("EFactory {\n")
        .append("  ")
        .append(name)
        .append(" eINSTANCE = null;\n");
    for (ClassifierInfo classifier : pkg.classifiers()) {
      if (classifier instanceof ClassInfo eClass && !eClass.isAbstract()) {
        body.append("  ")
            .append(eClass.javaQualifiedName())
            .append(" create")
            .append(eClass.name())
            .append("();\n");
      }
    }
    body.append("  ").append(packageName(pkg)).append(" get").append(packageName(pkg));
    body.append("();\n}\n");
    return compilationUnit(pkg, body.toString());
  }

  private static String packageSource(PackageInfo pkg) {
    String name = packageName(pkg);
    StringBuilder body = new StringBuilder();
    body.append("public interface ")
        .append(name)
        .append(" extends ")
        .append(EMF_ECORE)
        .append("EPackage {\n")
        .append("  String eNAME = ")
        .append(JavaNames.stringLiteral(pkg.name()))
        .append(";\n")
        .append("  String eNS_URI = ")
        .append(JavaNames.stringLiteral(pkg.nsUri()))
        .append(";\n")
        .append("  ")
        .append(name)
        .append(" eINSTANCE = null;\n");
    for (ClassifierInfo classifier : pkg.classifiers()) {
      body.append("  ")
          .append(EMF_ECORE)
          .append(metaTypeOf(classifier))
          .append(" get")
          .append(classifier.name())
          .append("();\n");
      if (classifier instanceof ClassInfo eClass) {
        for (FeatureInfo feature : eClass.features()) {
          body.append("  ")
              .append(EMF_ECORE)
              .append(isReference(feature) ? "EReference" : "EAttribute")
              .append(" get")
              .append(eClass.name())
              .append('_')
              .append(JavaNames.capName(feature.name()))
              .append("();\n");
        }
      }
    }
    body.append("  ").append(factoryName(pkg)).append(" get").append(factoryName(pkg));
    body.append("();\n}\n");
    return compilationUnit(pkg, body.toString());
  }

  private static String metaTypeOf(ClassifierInfo classifier) {
    return switch (classifier) {
      case ClassInfo ignored -> "EClass";
      case EnumInfo ignored -> "EEnum";
      case DataTypeInfo ignored -> "EDataType";
    };
  }

  private static boolean isReference(FeatureInfo feature) {
    return feature.type() instanceof ClassInfo
        || (feature.builtinType() != null && ECORE_CLASSES.contains(feature.builtinType()));
  }

  /** The prefix EMF derives from the package name by default, e.g. {@code Simulink}. */
  private static String prefix(PackageInfo pkg) {
    return JavaNames.capName(pkg.name());
  }

  private static String factoryName(PackageInfo pkg) {
    return prefix(pkg) + "Factory";
  }

  private static String packageName(PackageInfo pkg) {
    return prefix(pkg) + "Package";
  }

  // ── types ────────────────────────────────────────────────────────────────

  private static String getterName(FeatureInfo feature) {
    String accessor =
        ("boolean".equals(typeOf(feature)) ? "is" : "get") + JavaNames.capName(feature.name());
    // Object#getClass() is final, so EMF renames the accessor of a feature named "class".
    return "getClass".equals(accessor) ? "getClass_" : accessor;
  }

  /** The Java type of a typed element; multi-valued elements are {@code EList}s. */
  private static String typeOf(FeatureInfo element) {
    String type = singleTypeOf(element);
    return element.many() ? E_LIST + "<" + boxed(type) + ">" : type;
  }

  private static String singleTypeOf(FeatureInfo element) {
    ClassifierInfo type = element.type();
    if (type instanceof ClassInfo || type instanceof EnumInfo) {
      return type.javaQualifiedName();
    }
    if (type instanceof DataTypeInfo dataType) {
      String instanceClass = dataType.instanceClassName();
      return instanceClass == null || instanceClass.isBlank() ? "java.lang.Object" : instanceClass;
    }
    String builtin = element.builtinType();
    if (builtin == null) {
      return "java.lang.Object";
    }
    String instanceClass = OclTypeResolver.builtinInstanceClass(builtin);
    if (instanceClass != null) {
      return instanceClass;
    }
    if (ECORE_CLASSES.contains(builtin)) {
      return EMF_ECORE + builtin;
    }
    return OTHER_ECORE_DATA_TYPES.getOrDefault(builtin, "java.lang.Object");
  }

  private static String boxed(String type) {
    return switch (type) {
      case "boolean" -> "java.lang.Boolean";
      case "int" -> "java.lang.Integer";
      case "long" -> "java.lang.Long";
      case "double" -> "java.lang.Double";
      case "float" -> "java.lang.Float";
      case "char" -> "java.lang.Character";
      case "short" -> "java.lang.Short";
      case "byte" -> "java.lang.Byte";
      default -> type;
    };
  }

  private static String compilationUnit(PackageInfo pkg, String body) {
    return "package " + pkg.javaPackage() + ";\n\n" + body;
  }
}
