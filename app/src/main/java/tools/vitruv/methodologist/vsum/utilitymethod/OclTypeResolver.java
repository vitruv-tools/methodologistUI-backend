package tools.vitruv.methodologist.vsum.utilitymethod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex.ClassInfo;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex.ClassifierInfo;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex.DataTypeInfo;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex.EnumInfo;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex.PackageInfo;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.CollectionTypeRef;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.NamedTypeRef;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Pos;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.TypeRef;

/**
 * Resolves type names of a utility method against the VSUM's metamodels.
 *
 * <p>Qualified names ({@code simulink::SubSystem}) always work. Simple names ({@code SubSystem})
 * are looked up in the utility method's imports first ({@code simulink::SubSystem} or {@code
 * simulink::*}) and otherwise accepted if exactly one metamodel declares the name.
 */
public final class OclTypeResolver {

  /**
   * How a value of an Ecore data type is represented in OCL and Java.
   *
   * @param type the OCL type
   * @param primitive whether the Java getter returns a primitive
   * @param conversion a format string turning the Java getter result into the OCL type's Java
   *     representation, {@code %s} if none is needed
   */
  public record ValueMapping(OclType type, boolean primitive, String conversion) {
    boolean needsConversion() {
      return !"%s".equals(conversion);
    }
  }

  private static final Map<String, String> ECORE_INSTANCE_CLASSES =
      Map.ofEntries(
          Map.entry("EString", "java.lang.String"),
          Map.entry("EBoolean", "boolean"),
          Map.entry("EBooleanObject", "java.lang.Boolean"),
          Map.entry("EInt", "int"),
          Map.entry("EIntegerObject", "java.lang.Integer"),
          Map.entry("EShort", "short"),
          Map.entry("EShortObject", "java.lang.Short"),
          Map.entry("EByte", "byte"),
          Map.entry("EByteObject", "java.lang.Byte"),
          Map.entry("ELong", "long"),
          Map.entry("ELongObject", "java.lang.Long"),
          Map.entry("EDouble", "double"),
          Map.entry("EDoubleObject", "java.lang.Double"),
          Map.entry("EFloat", "float"),
          Map.entry("EFloatObject", "java.lang.Float"),
          Map.entry("EChar", "char"),
          Map.entry("ECharacterObject", "java.lang.Character"),
          Map.entry("EBigInteger", "java.math.BigInteger"),
          Map.entry("EBigDecimal", "java.math.BigDecimal"),
          Map.entry("EDate", "java.util.Date"),
          Map.entry("EJavaObject", "java.lang.Object"));

  private static final Map<String, ValueMapping> INSTANCE_CLASS_MAPPINGS =
      Map.ofEntries(
          Map.entry("java.lang.String", new ValueMapping(OclType.STRING, false, "%s")),
          Map.entry("boolean", new ValueMapping(OclType.BOOLEAN, true, "%s")),
          Map.entry("java.lang.Boolean", new ValueMapping(OclType.BOOLEAN, false, "%s")),
          Map.entry("int", new ValueMapping(OclType.INTEGER, true, "%s")),
          Map.entry("short", new ValueMapping(OclType.INTEGER, true, "%s")),
          Map.entry("byte", new ValueMapping(OclType.INTEGER, true, "%s")),
          Map.entry("java.lang.Integer", new ValueMapping(OclType.INTEGER, false, "%s")),
          Map.entry("java.lang.Short", new ValueMapping(OclType.INTEGER, true, "%s.intValue()")),
          Map.entry("java.lang.Byte", new ValueMapping(OclType.INTEGER, true, "%s.intValue()")),
          Map.entry("long", new ValueMapping(OclType.INTEGER, true, "Math.toIntExact(%s)")),
          Map.entry(
              "java.lang.Long", new ValueMapping(OclType.INTEGER, true, "Math.toIntExact(%s)")),
          Map.entry(
              "java.math.BigInteger",
              new ValueMapping(OclType.INTEGER, true, "%s.intValueExact()")),
          Map.entry("double", new ValueMapping(OclType.REAL, true, "%s")),
          Map.entry("float", new ValueMapping(OclType.REAL, true, "%s")),
          Map.entry("java.lang.Double", new ValueMapping(OclType.REAL, false, "%s")),
          Map.entry("java.lang.Float", new ValueMapping(OclType.REAL, true, "%s.doubleValue()")),
          Map.entry(
              "java.math.BigDecimal", new ValueMapping(OclType.REAL, true, "%s.doubleValue()")),
          Map.entry("char", new ValueMapping(OclType.STRING, false, "String.valueOf(%s)")),
          Map.entry(
              "java.lang.Character", new ValueMapping(OclType.STRING, false, "String.valueOf(%s)")),
          Map.entry("java.lang.Object", new ValueMapping(OclType.ANY, false, "%s")));

  private final EcoreTypeIndex index;
  private final Map<String, ClassifierInfo> importedByName = new LinkedHashMap<>();
  private final List<PackageInfo> importedPackages = new ArrayList<>();

  /**
   * Creates a resolver for a utility method with the given imports.
   *
   * @param index the VSUM's metamodels
   * @param imports entries of the form {@code mm::Class} or {@code mm::*}
   * @throws UtilityMethodTranslationException if an import cannot be resolved
   */
  public OclTypeResolver(EcoreTypeIndex index, List<String> imports) {
    this.index = index;
    for (String entry : imports == null ? List.<String>of() : imports) {
      addImport(entry);
    }
  }

  private void addImport(String entry) {
    String trimmed = entry == null ? "" : entry.trim().replace("!", "::");
    if (trimmed.isEmpty()) {
      return;
    }
    List<String> segments = List.of(trimmed.split("::"));
    if (segments.get(segments.size() - 1).equals("*")) {
      List<String> path = segments.subList(0, segments.size() - 1);
      PackageInfo pkg =
          index
              .findPackage(path)
              .orElseThrow(
                  () ->
                      new UtilityMethodTranslationException(
                          "Import '" + trimmed + "': " + unknownPackageMessage(path)));
      importedPackages.add(pkg);
      return;
    }
    ClassifierInfo classifier =
        index
            .findClassifier(segments)
            .orElseThrow(
                () ->
                    new UtilityMethodTranslationException(
                        "Import '" + trimmed + "': unknown metamodel type."));
    ClassifierInfo previous = importedByName.putIfAbsent(classifier.name(), classifier);
    if (previous != null && previous != classifier) {
      throw new UtilityMethodTranslationException(
          "Imports '"
              + previous.qualifiedName()
              + "' and '"
              + classifier.qualifiedName()
              + "' have the same simple name.");
    }
  }

  /** Resolves a type expression. */
  public OclType resolve(TypeRef ref) {
    if (ref instanceof CollectionTypeRef collection) {
      return new OclType.CollectionType(collection.kind(), resolve(collection.element()));
    }
    NamedTypeRef named = (NamedTypeRef) ref;
    if (named.segments().size() == 1) {
      OclType primitive = primitive(named.segments().get(0));
      if (primitive != null) {
        return primitive;
      }
    }
    return typeOf(resolveClassifier(named.segments(), named.pos()));
  }

  /** Resolves a (possibly simple) classifier name. */
  public ClassifierInfo resolveClassifier(List<String> segments, Pos pos) {
    if (segments.size() > 1) {
      return index
          .findClassifier(segments)
          .orElseThrow(
              () ->
                  new UtilityMethodTranslationException(
                      unknownQualifiedTypeMessage(segments), pos));
    }
    String name = segments.get(0);
    ClassifierInfo imported = importedByName.get(name);
    if (imported != null) {
      return imported;
    }
    List<ClassifierInfo> fromPackages =
        importedPackages.stream()
            .flatMap(pkg -> pkg.classifiers().stream())
            .filter(c -> c.name().equals(name))
            .toList();
    if (fromPackages.size() == 1) {
      return fromPackages.get(0);
    }
    List<ClassifierInfo> candidates =
        fromPackages.isEmpty() ? index.findBySimpleName(name) : fromPackages;
    if (candidates.size() == 1) {
      return candidates.get(0);
    }
    if (candidates.isEmpty()) {
      throw new UtilityMethodTranslationException("Unknown type '" + name + "'.", pos);
    }
    throw new UtilityMethodTranslationException(
        "Ambiguous type '"
            + name
            + "': "
            + candidates.stream()
                .map(ClassifierInfo::qualifiedName)
                .collect(Collectors.joining(", "))
            + ". Import it or use the qualified name.",
        pos);
  }

  /** Whether a classifier with the given (possibly simple) name can be resolved. */
  public boolean isTypeName(List<String> segments) {
    if (segments.size() == 1 && primitive(segments.get(0)) != null) {
      return true;
    }
    try {
      resolveClassifier(segments, null);
      return true;
    } catch (UtilityMethodTranslationException e) {
      return false;
    }
  }

  /** The OCL type of a classifier. */
  public static OclType typeOf(ClassifierInfo classifier) {
    return switch (classifier) {
      case ClassInfo c -> new OclType.Metaclass(c);
      case EnumInfo e -> new OclType.EnumType(e);
      case DataTypeInfo d -> mapInstanceClass(d.instanceClassName()).type();
    };
  }

  /** How values of the Ecore built-in data type with the given name are represented. */
  public static ValueMapping mapBuiltin(String ecoreName) {
    return mapInstanceClass(ECORE_INSTANCE_CLASSES.get(ecoreName));
  }

  /** How values of a data type with the given instance class are represented. */
  public static ValueMapping mapInstanceClass(String instanceClassName) {
    if (instanceClassName == null || instanceClassName.isBlank()) {
      return new ValueMapping(OclType.ANY, false, "%s");
    }
    ValueMapping mapping = INSTANCE_CLASS_MAPPINGS.get(instanceClassName);
    if (mapping != null) {
      return mapping;
    }
    return new ValueMapping(
        new OclType.JavaType(instanceClassName, instanceClassName), false, "%s");
  }

  /** The boxed Java class of a data type that needs a conversion, e.g. {@code java.lang.Long}. */
  public static String boxedInstanceClass(String instanceClassName) {
    return switch (instanceClassName) {
      case "long" -> "java.lang.Long";
      case "float" -> "java.lang.Float";
      case "char" -> "java.lang.Character";
      case "short" -> "java.lang.Short";
      case "byte" -> "java.lang.Byte";
      default -> instanceClassName;
    };
  }

  /** The instance class of an Ecore built-in data type, or {@code null}. */
  public static String builtinInstanceClass(String ecoreName) {
    return ECORE_INSTANCE_CLASSES.get(ecoreName);
  }

  private static OclType primitive(String name) {
    return switch (name) {
      case "Boolean" -> OclType.BOOLEAN;
      case "Integer", "UnlimitedNatural" -> OclType.INTEGER;
      case "Real" -> OclType.REAL;
      case "String" -> OclType.STRING;
      case "OclAny" -> OclType.ANY;
      default -> null;
    };
  }

  private String unknownQualifiedTypeMessage(List<String> segments) {
    List<String> path = segments.subList(0, segments.size() - 1);
    if (index.findPackage(path).isEmpty()) {
      return unknownPackageMessage(path);
    }
    return "Metamodel '"
        + String.join("::", path)
        + "' has no type '"
        + segments.get(segments.size() - 1)
        + "'.";
  }

  private String unknownPackageMessage(List<String> path) {
    String available =
        index.rootPackages().stream().map(PackageInfo::name).collect(Collectors.joining(", "));
    return "Unknown metamodel '"
        + String.join("::", path)
        + "'."
        + (available.isEmpty()
            ? " The project has no metamodels."
            : " Available metamodels: " + available + ".");
  }
}
