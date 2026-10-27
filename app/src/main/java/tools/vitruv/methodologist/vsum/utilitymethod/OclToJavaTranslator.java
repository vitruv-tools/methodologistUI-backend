package tools.vitruv.methodologist.vsum.utilitymethod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex.ClassInfo;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex.ClassifierInfo;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex.DataTypeInfo;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex.EnumInfo;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex.FeatureInfo;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Binary;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.BoolLit;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Call;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.CollectionLit;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.CollectionPart;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Expr;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.If;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.IntLit;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.IteratorCall;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Let;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Name;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.NamedTypeRef;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.NullLit;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Pos;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Property;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.RealLit;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.StringLit;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Unary;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.VarDecl;
import tools.vitruv.methodologist.vsum.utilitymethod.OclType.AnyType;
import tools.vitruv.methodologist.vsum.utilitymethod.OclType.CollectionKind;
import tools.vitruv.methodologist.vsum.utilitymethod.OclType.CollectionType;
import tools.vitruv.methodologist.vsum.utilitymethod.OclType.EnumType;
import tools.vitruv.methodologist.vsum.utilitymethod.OclType.JavaType;
import tools.vitruv.methodologist.vsum.utilitymethod.OclType.Metaclass;
import tools.vitruv.methodologist.vsum.utilitymethod.OclType.Primitive;
import tools.vitruv.methodologist.vsum.utilitymethod.OclType.PrimitiveKind;
import tools.vitruv.methodologist.vsum.utilitymethod.OclType.VoidType;
import tools.vitruv.methodologist.vsum.utilitymethod.OclTypeResolver.ValueMapping;

/**
 * Type-checks the OCL body of one utility method and translates it into the statements of a Java
 * method. Collection operations become {@link java.util.stream.Stream} pipelines; operations
 * without a concise Java counterpart call the private runtime support methods of the generated
 * class.
 */
final class OclToJavaTranslator {

  /** The signature of a utility method, used to type-check calls between utility methods. */
  record UtilityMethodSignature(
      String name, List<String> parameterNames, List<OclType> parameterTypes, OclType returnType) {}

  /**
   * A translated expression.
   *
   * @param code the Java code
   * @param type the OCL type
   * @param primitive whether the Java expression has a primitive type
   * @param atomic whether the code can be used as a receiver or operand without parentheses
   */
  private record JExpr(String code, OclType type, boolean primitive, boolean atomic) {}

  private record Var(String oclName, String javaName, OclType type, boolean primitive) {}

  private static final Set<String> COLLECTION_OPERATIONS =
      Set.of(
          "size",
          "isEmpty",
          "notEmpty",
          "includes",
          "excludes",
          "includesAll",
          "excludesAll",
          "count",
          "sum",
          "min",
          "max",
          "avg",
          "first",
          "last",
          "at",
          "indexOf",
          "asSet",
          "asOrderedSet",
          "asSequence",
          "asBag",
          "including",
          "excluding",
          "append",
          "prepend",
          "union",
          "intersection",
          "reverse",
          "flatten",
          "subSequence",
          "selectByKind",
          "selectByType");

  private static final String TO_LIST = ".toList()";

  private static final Set<String> OBJECT_OPERATIONS =
      Set.of("oclIsUndefined", "oclIsKindOf", "oclIsTypeOf", "oclAsType", "toString");

  private final OclTypeResolver resolver;
  private final JavaImports imports;
  private final Set<String> runtime;
  private final Map<String, UtilityMethodSignature> utilityMethods;
  private final String className;
  private final Deque<Var> scope = new ArrayDeque<>();
  private final Deque<Var> implicitIterators = new ArrayDeque<>();

  /** Stream pipelines that were collected into a list, so that they can be continued instead. */
  private final Map<JExpr, String> pipelines = new IdentityHashMap<>();

  /** The infix operator at the top of a translated expression, to avoid redundant parentheses. */
  private final Map<JExpr, String> operators = new IdentityHashMap<>();

  private int syntheticCounter;

  OclToJavaTranslator(
      OclTypeResolver resolver,
      JavaImports imports,
      Set<String> runtime,
      Map<String, UtilityMethodSignature> utilityMethods,
      String className) {
    this.resolver = resolver;
    this.imports = imports;
    this.runtime = runtime;
    this.utilityMethods = utilityMethods;
    this.className = className;
  }

  // ── method level ─────────────────────────────────────────────────────────

  /**
   * Translates the body of a utility method.
   *
   * @param signature the utility method's resolved signature
   * @param body the parsed OCL body
   * @return the statements of the Java method body
   */
  List<String> translateBody(UtilityMethodSignature signature, Expr body) {
    for (int i = 0; i < signature.parameterNames().size(); i++) {
      OclType type = signature.parameterTypes().get(i);
      String name = signature.parameterNames().get(i);
      scope.push(new Var(name, name, type, isPrimitiveValue(type)));
    }
    List<String> statements = new ArrayList<>();
    Expr current = body;
    while (current instanceof Let let) {
      for (VarDecl variable : let.variables()) {
        statements.add(declareLocal(variable));
      }
      current = let.body();
    }
    JExpr result = translate(current);
    OclType returnType = signature.returnType();
    if (!OclType.conformsTo(result.type(), returnType)) {
      throw new UtilityMethodTranslationException(
          "The expression has type "
              + result.type().oclName()
              + ", which does not conform to the return type "
              + returnType.oclName()
              + ".",
          current.pos());
    }
    statements.add("return " + coerce(result, returnType).code() + ";");
    return statements;
  }

  /** Declares a top-level {@code let} variable as a final local and returns the statement. */
  private String declareLocal(VarDecl variable) {
    JExpr init = translate(variable.init());
    OclType type = init.type();
    if (variable.type() != null) {
      type = resolver.resolve(variable.type());
      requireConforms(init, type, variable.init().pos(), "The value of '" + variable.name() + "'");
    } else if (type instanceof VoidType) {
      type = OclType.ANY;
    }
    boolean primitive = isPrimitiveValue(type) && !(init.type() instanceof VoidType);
    Var declared = new Var(variable.name(), freshName(variable.name()), type, primitive);
    scope.push(declared);
    return "final "
        + javaType(type, !primitive)
        + " "
        + declared.javaName()
        + " = "
        + coerce(init, type).code()
        + ";";
  }

  // ── expressions ──────────────────────────────────────────────────────────

  private JExpr translate(Expr expr) {
    return switch (expr) {
      case IntLit literal -> integerLiteral(literal);
      case RealLit literal -> new JExpr(literal.text(), OclType.REAL, true, true);
      case StringLit literal ->
          new JExpr(JavaNames.stringLiteral(literal.value()), OclType.STRING, false, true);
      case BoolLit literal ->
          new JExpr(String.valueOf(literal.value()), OclType.BOOLEAN, true, true);
      case NullLit ignored -> new JExpr("null", OclType.VOID, false, true);
      case Name name -> name(name);
      case Property property -> property(property);
      case Call call -> call(call);
      case IteratorCall iterator -> iterator(translateSource(iterator), iterator);
      case Unary unary -> unary(unary);
      case Binary binary -> binary(binary);
      case If conditional -> conditional(conditional);
      case Let let -> nestedLet(let, 0);
      case CollectionLit literal -> collectionLiteral(literal);
    };
  }

  private JExpr integerLiteral(IntLit literal) {
    try {
      return new JExpr(
          String.valueOf(Integer.parseInt(literal.text())), OclType.INTEGER, true, true);
    } catch (NumberFormatException e) {
      throw new UtilityMethodTranslationException(
          "Integer literal " + literal.text() + " is too large.", literal.pos());
    }
  }

  private JExpr name(Name name) {
    if (name.isSimple()) {
      String text = name.segments().get(0);
      Var variable = lookup(text);
      if (variable != null) {
        return variableExpr(variable);
      }
      if (text.equals("self")) {
        throw new UtilityMethodTranslationException(
            "Utility methods have no context object; use a parameter instead of 'self'.",
            name.pos());
      }
      Var implicit = implicitIterators.peek();
      if (implicit != null
          && implicit.type() instanceof Metaclass metaclass
          && metaclass.eClass().findFeature(text).isPresent()) {
        return featureAccess(variableExpr(implicit), text, name.pos());
      }
      String hint =
          resolver.isTypeName(name.segments())
              ? " Types can only be used as arguments of oclIsKindOf, oclIsTypeOf, oclAsType,"
                  + " selectByKind and selectByType."
              : "";
      throw new UtilityMethodTranslationException(
          "Unknown variable '" + text + "'." + hint, name.pos());
    }
    return enumLiteral(name);
  }

  private JExpr enumLiteral(Name name) {
    List<String> segments = name.segments();
    List<String> typeSegments = segments.subList(0, segments.size() - 1);
    String literal = segments.get(segments.size() - 1);
    ClassifierInfo classifier;
    try {
      classifier = resolver.resolveClassifier(typeSegments, name.pos());
    } catch (UtilityMethodTranslationException e) {
      if (resolver.isTypeName(segments)) {
        throw new UtilityMethodTranslationException(
            "The type '"
                + name.text()
                + "' cannot be used as a value. Types can only be used as arguments of"
                + " oclIsKindOf, oclIsTypeOf, oclAsType, selectByKind and selectByType.",
            name.pos());
      }
      throw new UtilityMethodTranslationException(
          "Unknown name '" + name.text() + "'.", name.pos());
    }
    if (!(classifier instanceof EnumInfo enumInfo)) {
      throw new UtilityMethodTranslationException(
          "'" + String.join("::", typeSegments) + "' is not an enumeration.", name.pos());
    }
    if (!enumInfo.literals().contains(literal)) {
      throw new UtilityMethodTranslationException(
          "Enumeration '"
              + enumInfo.qualifiedName()
              + "' has no literal '"
              + literal
              + "'. Literals: "
              + String.join(", ", enumInfo.literals())
              + ".",
          name.pos());
    }
    return new JExpr(
        imports.ref(enumInfo.javaQualifiedName()) + "." + JavaNames.enumLiteralName(literal),
        new EnumType(enumInfo),
        false,
        true);
  }

  private JExpr property(Property property) {
    JExpr source = translate(property.source());
    if (source.type() instanceof Metaclass) {
      return featureAccess(source, property.name(), property.pos());
    }
    if (source.type() instanceof CollectionType collection
        && collection.element() instanceof Metaclass metaclass) {
      String variable = syntheticName();
      requireFeature(metaclass.eClass(), property.name(), property.pos());
      IteratorCall collect =
          new IteratorCall(
              property.pos(),
              property.source(),
              "collect",
              List.of(new VarDecl(property.pos(), variable, null, null)),
              null,
              new Property(
                  property.pos(), new Name(property.pos(), List.of(variable)), property.name()),
              true);
      return iterator(source, collect);
    }
    throw new UtilityMethodTranslationException(
        "Cannot navigate '"
            + property.name()
            + "' on a value of type "
            + source.type().oclName()
            + (source.type() instanceof AnyType ? "; cast it with oclAsType(...) first." : "."),
        property.pos());
  }

  private JExpr featureAccess(JExpr source, String featureName, Pos pos) {
    ClassInfo eClass = ((Metaclass) source.type()).eClass();
    FeatureInfo feature = eClass.findFeature(featureName).orElse(null);
    if (feature == null) {
      UtilityMethodSignature utilityMethod = utilityMethodApplicableTo(featureName, source, 1);
      if (utilityMethod != null) {
        return utilityMethodCall(utilityMethod, List.of(source), pos);
      }
      requireFeature(eClass, featureName, pos);
    }
    ValueMapping mapping = valueMapping(feature);
    boolean primitiveBoolean =
        !feature.many() && mapping.primitive() && mapping.type().equals(OclType.BOOLEAN);
    String getter = (primitiveBoolean ? "is" : "get") + JavaNames.capName(feature.name()) + "()";
    String code = paren(source) + "." + getter;
    if (feature.many()) {
      OclType element = mapping.type();
      if (mapping.needsConversion()) {
        element = new JavaType(element.oclName(), boxedJavaClassOf(feature));
      }
      return new JExpr(code, new CollectionType(kindOf(feature), element), false, true);
    }
    if (mapping.needsConversion()) {
      String converted =
          String.format(
              mapping.conversion().replace("Math.", imports.ref("java.lang.Math") + "."), code);
      return new JExpr(converted, mapping.type(), mapping.primitive(), true);
    }
    return new JExpr(code, mapping.type(), mapping.primitive(), true);
  }

  private void requireFeature(ClassInfo eClass, String featureName, Pos pos) {
    if (eClass.findFeature(featureName).isEmpty()) {
      throw new UtilityMethodTranslationException(
          "Type "
              + eClass.qualifiedName()
              + " has no feature '"
              + featureName
              + "'. Features: "
              + String.join(", ", eClass.allFeatureNames())
              + ".",
          pos);
    }
  }

  private ValueMapping valueMapping(FeatureInfo feature) {
    ClassifierInfo type = feature.type();
    if (type instanceof ClassInfo c) {
      return new ValueMapping(new Metaclass(c), false, "%s");
    }
    if (type instanceof EnumInfo e) {
      return new ValueMapping(new EnumType(e), false, "%s");
    }
    if (type instanceof DataTypeInfo d) {
      return OclTypeResolver.mapInstanceClass(d.instanceClassName());
    }
    if (feature.builtinType() != null) {
      return OclTypeResolver.mapBuiltin(feature.builtinType());
    }
    return new ValueMapping(OclType.ANY, false, "%s");
  }

  private static String boxedJavaClassOf(FeatureInfo feature) {
    String instanceClass =
        feature.type() instanceof DataTypeInfo d
            ? d.instanceClassName()
            : OclTypeResolver.builtinInstanceClass(feature.builtinType());
    return OclTypeResolver.boxedInstanceClass(instanceClass);
  }

  private static CollectionKind kindOf(FeatureInfo feature) {
    if (feature.unique()) {
      return feature.ordered() ? CollectionKind.ORDERED_SET : CollectionKind.SET;
    }
    return feature.ordered() ? CollectionKind.SEQUENCE : CollectionKind.BAG;
  }

  // ── calls ────────────────────────────────────────────────────────────────

  private JExpr call(Call call) {
    if (call.source() == null || isThisModule(call.source())) {
      UtilityMethodSignature utilityMethod = utilityMethods.get(call.name());
      if (utilityMethod == null && call.source() == null && !implicitIterators.isEmpty()) {
        Call onIterator =
            new Call(
                call.pos(),
                new Name(call.pos(), List.of(implicitIterators.peek().oclName())),
                call.name(),
                call.args(),
                false);
        return call(onIterator);
      }
      if (utilityMethod == null) {
        throw unknownUtilityMethod(call.name(), call.pos());
      }
      return utilityMethodCall(utilityMethod, translateAll(call.args()), call.pos());
    }
    if (call.name().equals("allInstances")) {
      throw new UtilityMethodTranslationException(
          "allInstances() is not supported because utility methods have no model context. Pass the root"
              + " element as a parameter and navigate from it instead.",
          call.pos());
    }
    JExpr source = translate(call.source());
    if (source.type() instanceof CollectionType) {
      if (call.arrow() || COLLECTION_OPERATIONS.contains(call.name())) {
        return collectionOperation(source, call);
      }
      if (call.name().equals("oclIsUndefined")) {
        return new JExpr(paren(source) + " == null", OclType.BOOLEAN, true, false);
      }
      throw new UtilityMethodTranslationException(
          "Unknown collection operation '" + call.name() + "'.", call.pos());
    }
    if (call.arrow()) {
      return singleValueArrowOperation(source, call);
    }
    return objectOperation(source, call);
  }

  private boolean isThisModule(Expr source) {
    return source instanceof Name name
        && name.isSimple()
        && name.segments().get(0).equals("thisModule")
        && lookup("thisModule") == null;
  }

  private UtilityMethodTranslationException unknownUtilityMethod(String name, Pos pos) {
    String known = utilityMethods.keySet().stream().sorted().collect(Collectors.joining(", "));
    return new UtilityMethodTranslationException(
        "Unknown utility method '"
            + name
            + "'."
            + (known.isEmpty() ? "" : " Utility methods of this project: " + known + "."),
        pos);
  }

  private JExpr utilityMethodCall(UtilityMethodSignature utilityMethod, List<JExpr> args, Pos pos) {
    if (args.size() != utilityMethod.parameterTypes().size()) {
      throw new UtilityMethodTranslationException(
          "Utility method '"
              + utilityMethod.name()
              + "' expects "
              + utilityMethod.parameterTypes().size()
              + " argument(s) but got "
              + args.size()
              + ".",
          pos);
    }
    List<String> codes = new ArrayList<>();
    for (int i = 0; i < args.size(); i++) {
      OclType expected = utilityMethod.parameterTypes().get(i);
      requireConforms(
          args.get(i),
          expected,
          pos,
          "Argument '"
              + utilityMethod.parameterNames().get(i)
              + "' of '"
              + utilityMethod.name()
              + "'");
      codes.add(coerce(args.get(i), expected).code());
    }
    return new JExpr(
        utilityMethod.name() + "(" + String.join(", ", codes) + ")",
        utilityMethod.returnType(),
        isPrimitiveValue(utilityMethod.returnType()),
        true);
  }

  /**
   * A utility method that can be called as an operation of {@code source}, e.g. {@code
   * source.utility method()}.
   */
  private UtilityMethodSignature utilityMethodApplicableTo(String name, JExpr source, int arity) {
    UtilityMethodSignature utilityMethod = utilityMethods.get(name);
    if (utilityMethod != null
        && utilityMethod.parameterTypes().size() == arity
        && OclType.conformsTo(source.type(), utilityMethod.parameterTypes().get(0))) {
      return utilityMethod;
    }
    return null;
  }

  private JExpr singleValueArrowOperation(JExpr source, Call call) {
    return switch (call.name()) {
      case "isEmpty" -> new JExpr(paren(source) + " == null", OclType.BOOLEAN, true, false);
      case "notEmpty" -> new JExpr(paren(source) + " != null", OclType.BOOLEAN, true, false);
      case "size" ->
          new JExpr("(" + paren(source) + " == null ? 0 : 1)", OclType.INTEGER, true, true);
      default ->
          throw new UtilityMethodTranslationException(
              "'->"
                  + call.name()
                  + "' requires a collection, but the value has type "
                  + source.type().oclName()
                  + ".",
              call.pos());
    };
  }

  private JExpr objectOperation(JExpr source, Call call) {
    String name = call.name();
    if (OBJECT_OPERATIONS.contains(name)) {
      return typeOperation(source, call);
    }
    UtilityMethodSignature utilityMethod =
        utilityMethodApplicableTo(name, source, call.args().size() + 1);
    if (utilityMethod != null) {
      List<JExpr> args = new ArrayList<>();
      args.add(source);
      args.addAll(translateAll(call.args()));
      return utilityMethodCall(utilityMethod, args, call.pos());
    }
    if (source.type().equals(OclType.STRING)) {
      return stringOperation(source, call);
    }
    if (source.type() instanceof Primitive primitive && primitive.isNumeric()) {
      return numberOperation(source, call);
    }
    if (source.type() instanceof Metaclass metaclass
        && metaclass.eClass().findFeature(name).isPresent()) {
      throw new UtilityMethodTranslationException(
          "'"
              + name
              + "' is a feature of "
              + metaclass.oclName()
              + "; write '."
              + name
              + "' without parentheses.",
          call.pos());
    }
    throw new UtilityMethodTranslationException(
        "Unknown operation '" + name + "' on a value of type " + source.type().oclName() + ".",
        call.pos());
  }

  private JExpr typeOperation(JExpr source, Call call) {
    switch (call.name()) {
      case "oclIsUndefined":
        requireArgs(call, 0);
        return new JExpr(paren(source) + " == null", OclType.BOOLEAN, true, false);
      case "toString":
        requireArgs(call, 0);
        return new JExpr("String.valueOf(" + source.code() + ")", OclType.STRING, false, true);
      default:
        break;
    }
    requireArgs(call, 1);
    if (source.primitive()) {
      throw new UtilityMethodTranslationException(
          "'"
              + call.name()
              + "' cannot be applied to a value of type "
              + source.type().oclName()
              + ".",
          call.pos());
    }
    OclType target = typeArgument(call.args().get(0));
    String targetJava = javaType(target, true);
    return switch (call.name()) {
      case "oclIsKindOf" ->
          new JExpr(paren(source) + " instanceof " + targetJava, OclType.BOOLEAN, true, false);
      case "oclIsTypeOf" -> {
        if (target instanceof Metaclass metaclass) {
          runtime.add(UtilityMethodRuntime.IS_TYPE_OF);
          yield new JExpr(
              UtilityMethodRuntime.IS_TYPE_OF
                  + "("
                  + source.code()
                  + ", "
                  + JavaNames.stringLiteral(metaclass.eClass().pkg().nsUri())
                  + ", "
                  + JavaNames.stringLiteral(metaclass.eClass().name())
                  + ")",
              OclType.BOOLEAN,
              true,
              true);
        }
        yield new JExpr(paren(source) + " instanceof " + targetJava, OclType.BOOLEAN, true, false);
      }
      default -> new JExpr("((" + targetJava + ") " + paren(source) + ")", target, false, true);
    };
  }

  private OclType typeArgument(Expr arg) {
    if (!(arg instanceof Name name)) {
      throw new UtilityMethodTranslationException("Expected a type name.", arg.pos());
    }
    return resolver.resolve(new NamedTypeRef(name.pos(), name.segments()));
  }

  private JExpr stringOperation(JExpr source, Call call) {
    String receiver = paren(source);
    List<JExpr> args = translateAll(call.args());
    return switch (call.name()) {
      case "size", "length" -> {
        requireArgs(call, 0);
        yield new JExpr(receiver + ".length()", OclType.INTEGER, true, true);
      }
      case "concat" -> {
        requireArgTypes(call, args, OclType.STRING);
        yield new JExpr(
            receiver + ".concat(" + args.get(0).code() + ")", OclType.STRING, false, true);
      }
      case "substring" -> {
        requireArgTypes(call, args, OclType.INTEGER, OclType.INTEGER);
        yield new JExpr(
            receiver + ".substring(" + minusOne(args.get(0)) + ", " + args.get(1).code() + ")",
            OclType.STRING,
            false,
            true);
      }
      case "toUpper", "toUpperCase" ->
          stringCall(call, receiver, "toUpperCase()", OclType.STRING, false);
      case "toLower", "toLowerCase" ->
          stringCall(call, receiver, "toLowerCase()", OclType.STRING, false);
      case "trim" -> stringCall(call, receiver, "trim()", OclType.STRING, false);
      case "indexOf" -> {
        requireArgTypes(call, args, OclType.STRING);
        yield new JExpr(
            "(" + receiver + ".indexOf(" + args.get(0).code() + ") + 1)",
            OclType.INTEGER,
            true,
            true);
      }
      case "at" -> {
        requireArgTypes(call, args, OclType.INTEGER);
        yield new JExpr(
            "String.valueOf(" + receiver + ".charAt(" + minusOne(args.get(0)) + "))",
            OclType.STRING,
            false,
            true);
      }
      case "startsWith", "endsWith", "equalsIgnoreCase", "matches" -> {
        requireArgTypes(call, args, OclType.STRING);
        yield new JExpr(
            receiver + "." + call.name() + "(" + args.get(0).code() + ")",
            OclType.BOOLEAN,
            true,
            true);
      }
      case "substituteAll", "replaceAll" -> {
        requireArgTypes(call, args, OclType.STRING, OclType.STRING);
        yield new JExpr(
            receiver + ".replace(" + args.get(0).code() + ", " + args.get(1).code() + ")",
            OclType.STRING,
            false,
            true);
      }
      case "toInteger" -> {
        requireArgs(call, 0);
        yield new JExpr("Integer.parseInt(" + source.code() + ")", OclType.INTEGER, true, true);
      }
      case "toReal" -> {
        requireArgs(call, 0);
        yield new JExpr("Double.parseDouble(" + source.code() + ")", OclType.REAL, true, true);
      }
      default ->
          throw new UtilityMethodTranslationException(
              "Unknown String operation '" + call.name() + "'.", call.pos());
    };
  }

  private JExpr stringCall(
      Call call, String receiver, String method, OclType type, boolean primitive) {
    requireArgs(call, 0);
    return new JExpr(receiver + "." + method, type, primitive, true);
  }

  private JExpr numberOperation(JExpr source, Call call) {
    String math = imports.ref("java.lang.Math");
    List<JExpr> args = translateAll(call.args());
    return switch (call.name()) {
      case "abs" -> {
        requireArgs(call, 0);
        yield new JExpr(math + ".abs(" + source.code() + ")", source.type(), true, true);
      }
      case "floor", "round", "ceil", "ceiling" -> {
        requireArgs(call, 0);
        String method =
            switch (call.name()) {
              case "floor" -> "floor";
              case "round" -> "round";
              default -> "ceil";
            };
        yield new JExpr(
            "(int) " + math + "." + method + "(" + source.code() + ")",
            OclType.INTEGER,
            true,
            false);
      }
      case "max", "min" -> {
        requireArgs(call, 1);
        JExpr other = args.get(0);
        requireNumeric(other, call.pos());
        yield new JExpr(
            math + "." + call.name() + "(" + source.code() + ", " + other.code() + ")",
            OclType.commonSuperType(source.type(), other.type()),
            true,
            true);
      }
      case "div", "mod" -> {
        requireArgTypes(call, args, OclType.INTEGER);
        requireType(source, OclType.INTEGER, call.pos());
        String op = call.name().equals("div") ? " / " : " % ";
        yield new JExpr(paren(source) + op + paren(args.get(0)), OclType.INTEGER, true, false);
      }
      default ->
          throw new UtilityMethodTranslationException(
              "Unknown operation '" + call.name() + "' on a number.", call.pos());
    };
  }

  // ── collection operations ────────────────────────────────────────────────

  private JExpr collectionOperation(JExpr source, Call call) {
    CollectionType type = (CollectionType) source.type();
    OclType element = type.element();
    String receiver = paren(source);
    String name = call.name();
    if (name.equals("selectByKind") || name.equals("selectByType")) {
      requireArgs(call, 1);
      return selectByType(source, typeArgument(call.args().get(0)), name.equals("selectByType"));
    }
    List<JExpr> args = translateAll(call.args());
    switch (name) {
      case "size":
        requireArgs(call, 0);
        return new JExpr(receiver + ".size()", OclType.INTEGER, true, true);
      case "isEmpty":
        requireArgs(call, 0);
        return new JExpr(receiver + ".isEmpty()", OclType.BOOLEAN, true, true);
      case "notEmpty":
        requireArgs(call, 0);
        return new JExpr("!" + receiver + ".isEmpty()", OclType.BOOLEAN, true, false);
      case "includes", "excludes":
        requireArgs(call, 1);
        String contains = receiver + ".contains(" + args.get(0).code() + ")";
        return name.equals("includes")
            ? new JExpr(contains, OclType.BOOLEAN, true, true)
            : new JExpr("!" + contains, OclType.BOOLEAN, true, false);
      case "includesAll":
        requireCollectionArg(call, args);
        return new JExpr(
            receiver + ".containsAll(" + args.get(0).code() + ")", OclType.BOOLEAN, true, true);
      case "excludesAll":
        requireCollectionArg(call, args);
        return new JExpr(
            imports.ref("java.util.Collections")
                + ".disjoint("
                + source.code()
                + ", "
                + args.get(0).code()
                + ")",
            OclType.BOOLEAN,
            true,
            true);
      case "count":
        requireArgs(call, 1);
        return new JExpr(
            imports.ref("java.util.Collections")
                + ".frequency("
                + source.code()
                + ", "
                + args.get(0).code()
                + ")",
            OclType.INTEGER,
            true,
            true);
      case "sum":
        requireArgs(call, 0);
        return sum(source, element, call.pos());
      case "avg":
        requireArgs(call, 0);
        requireNumericElements(element, call.pos());
        return new JExpr(
            stream(source) + ".mapToDouble(Number::doubleValue).average().orElse(Double.NaN)",
            OclType.REAL,
            true,
            true);
      case "min", "max":
        requireArgs(call, 0);
        if (!(element instanceof Primitive)) {
          throw new UtilityMethodTranslationException(
              "'" + name + "' requires a collection of numbers or strings.", call.pos());
        }
        return new JExpr(
            imports.ref("java.util.Collections") + "." + name + "(" + source.code() + ")",
            element,
            false,
            true);
      case "first", "last":
        requireArgs(call, 0);
        String function =
            name.equals("first") ? UtilityMethodRuntime.FIRST : UtilityMethodRuntime.LAST;
        runtime.add(function);
        return new JExpr(function + "(" + source.code() + ")", element, false, true);
      case "at":
        requireOrdered(type, call);
        requireArgTypes(call, args, OclType.INTEGER);
        return new JExpr(receiver + ".get(" + minusOne(args.get(0)) + ")", element, false, true);
      case "indexOf":
        requireOrdered(type, call);
        requireArgs(call, 1);
        return new JExpr(
            "(" + receiver + ".indexOf(" + args.get(0).code() + ") + 1)",
            OclType.INTEGER,
            true,
            true);
      case "subSequence":
        requireOrdered(type, call);
        requireArgTypes(call, args, OclType.INTEGER, OclType.INTEGER);
        return new JExpr(
            receiver + ".subList(" + minusOne(args.get(0)) + ", " + args.get(1).code() + ")",
            type.withKind(CollectionKind.SEQUENCE),
            false,
            true);
      case "asSet":
        requireArgs(call, 0);
        return convertKind(source, CollectionKind.SET);
      case "asOrderedSet":
        requireArgs(call, 0);
        return convertKind(source, CollectionKind.ORDERED_SET);
      case "asSequence":
        requireArgs(call, 0);
        return convertKind(source, CollectionKind.SEQUENCE);
      case "asBag":
        requireArgs(call, 0);
        return convertKind(source, CollectionKind.BAG);
      case "including", "append", "prepend":
        return addElement(source, call, args);
      case "excluding":
        requireArgs(call, 1);
        String predicate = imports.ref("java.util.function.Predicate");
        return finish(
            stream(source)
                + ".filter("
                + predicate
                + ".not("
                + predicate
                + ".isEqual("
                + args.get(0).code()
                + ")))",
            type,
            type.kind().unique());
      case "union":
        return union(source, call, args);
      case "intersection":
        requireCollectionArg(call, args);
        CollectionKind kind =
            type.kind().isJavaSet() ? CollectionKind.SET : CollectionKind.ORDERED_SET;
        return finish(
            stream(source) + ".filter(" + paren(args.get(0)) + "::contains)",
            type.withKind(kind),
            type.kind().unique());
      case "reverse":
        requireArgs(call, 0);
        requireOrdered(type, call);
        runtime.add(UtilityMethodRuntime.REVERSE);
        return new JExpr(
            UtilityMethodRuntime.REVERSE + "(" + source.code() + ")", type, false, true);
      case "flatten":
        requireArgs(call, 0);
        if (!(element instanceof CollectionType inner)) {
          return source;
        }
        CollectionKind flatKind =
            type.kind().ordered() ? CollectionKind.SEQUENCE : CollectionKind.BAG;
        return finish(
            stream(source) + ".flatMap(" + imports.ref("java.util.Collection") + "::stream)",
            new CollectionType(flatKind, inner.element()));
      default:
        throw new UtilityMethodTranslationException(
            "Unknown collection operation '" + name + "'.", call.pos());
    }
  }

  private JExpr sum(JExpr source, OclType element, Pos pos) {
    requireNumericElements(element, pos);
    if (element.equals(OclType.INTEGER)) {
      return new JExpr(
          stream(source) + ".mapToInt(Integer::intValue).sum()", OclType.INTEGER, true, true);
    }
    return new JExpr(
        stream(source) + ".mapToDouble(Number::doubleValue).sum()", OclType.REAL, true, true);
  }

  private JExpr selectByType(JExpr source, OclType target, boolean exact) {
    CollectionType type = (CollectionType) source.type();
    String targetJava = javaType(target, true);
    String filter;
    if (exact && target instanceof Metaclass metaclass) {
      runtime.add(UtilityMethodRuntime.IS_TYPE_OF);
      String variable = freshName("element");
      filter =
          variable
              + " -> "
              + UtilityMethodRuntime.IS_TYPE_OF
              + "("
              + variable
              + ", "
              + JavaNames.stringLiteral(metaclass.eClass().pkg().nsUri())
              + ", "
              + JavaNames.stringLiteral(metaclass.eClass().name())
              + ")";
    } else {
      filter = targetJava + ".class::isInstance";
    }
    return finish(
        stream(source) + ".filter(" + filter + ").map(" + targetJava + ".class::cast)",
        new CollectionType(type.kind(), target),
        type.kind().unique());
  }

  private JExpr convertKind(JExpr source, CollectionKind kind) {
    CollectionType type = (CollectionType) source.type();
    CollectionType target = type.withKind(kind);
    if (kind == CollectionKind.SET) {
      if (type.kind().isJavaSet()) {
        return retype(source, target);
      }
      return new JExpr(
          "new " + imports.ref("java.util.LinkedHashSet") + "<>(" + source.code() + ")",
          target,
          false,
          true);
    }
    if (kind == CollectionKind.ORDERED_SET && !type.kind().unique()) {
      return new JExpr(stream(source) + ".distinct().toList()", target, false, true);
    }
    if (type.kind().isJavaSet()) {
      return new JExpr(
          "new " + imports.ref("java.util.ArrayList") + "<>(" + source.code() + ")",
          target,
          false,
          true);
    }
    return retype(source, target);
  }

  private JExpr addElement(JExpr source, Call call, List<JExpr> args) {
    requireArgs(call, 1);
    CollectionType type = (CollectionType) source.type();
    JExpr value = args.get(0);
    OclType element = type.element() instanceof VoidType ? value.type() : type.element();
    if (!OclType.conformsTo(value.type(), element)) {
      throw new UtilityMethodTranslationException(
          "Cannot add a value of type "
              + value.type().oclName()
              + " to a collection of "
              + element.oclName()
              + ".",
          call.pos());
    }
    if (!call.name().equals("including")) {
      requireOrdered(type, call);
    }
    String streamClass = imports.ref("java.util.stream.Stream");
    String single = streamClass + ".of(" + coerce(value, element).code() + ")";
    String concatenated =
        call.name().equals("prepend")
            ? streamClass + ".concat(" + single + ", " + stream(source) + ")"
            : streamClass + ".concat(" + stream(source) + ", " + single + ")";
    return finish(concatenated, new CollectionType(type.kind(), element));
  }

  private JExpr union(JExpr source, Call call, List<JExpr> args) {
    requireCollectionArg(call, args);
    CollectionType type = (CollectionType) source.type();
    CollectionType other = (CollectionType) args.get(0).type();
    OclType element = OclType.commonSuperType(type.element(), other.element());
    CollectionType result = new CollectionType(type.kind(), element);
    return finish(
        imports.ref("java.util.stream.Stream")
            + ".concat("
            + stream(source)
            + ", "
            + stream(args.get(0))
            + ")",
        result);
  }

  // ── iterators ────────────────────────────────────────────────────────────

  private JExpr translateSource(IteratorCall iterator) {
    return translate(iterator.source());
  }

  private JExpr iterator(JExpr source, IteratorCall call) {
    if (!(source.type() instanceof CollectionType type)) {
      throw new UtilityMethodTranslationException(
          "'"
              + call.name()
              + "' requires a collection, but the value has type "
              + source.type().oclName()
              + ".",
          call.pos());
    }
    if (call.iterators().size() > 1 && !Set.of("forAll", "exists").contains(call.name())) {
      throw new UtilityMethodTranslationException(
          "'" + call.name() + "' supports only one iterator variable.", call.pos());
    }
    if (call.name().equals("iterate")) {
      return iterate(source, call);
    }
    if (call.name().equals("select") && isKindOfTest(call)) {
      Call test = (Call) call.body();
      return selectByType(source, typeArgument(test.args().get(0)), false);
    }
    if (call.iterators().size() == 2) {
      return nestedQuantifier(source, call);
    }
    Var variable = declareIterator(call, type.element());
    String lambdaVariable = variable.javaName();
    JExpr body;
    try {
      body = translate(call.body());
    } finally {
      popIterator(call);
    }
    String lambda = lambdaVariable + " -> " + body.code();
    return switch (call.name()) {
      case "select" -> {
        requireBoolean(body, call.body().pos());
        yield finish(stream(source) + ".filter(" + lambda + ")", type, type.kind().unique());
      }
      case "reject" -> {
        requireBoolean(body, call.body().pos());
        yield finish(
            stream(source) + ".filter(" + lambdaVariable + " -> !" + paren(body) + ")",
            type,
            type.kind().unique());
      }
      case "collect" -> collect(source, type, lambdaVariable, body);
      case "collectNested" ->
          finish(
              stream(source) + ".map(" + lambda + ")",
              new CollectionType(orderedOrBag(type), elementOf(body.type())));
      case "forAll", "exists" -> {
        requireBoolean(body, call.body().pos());
        String match = call.name().equals("forAll") ? "allMatch" : "anyMatch";
        yield new JExpr(
            stream(source) + "." + match + "(" + lambda + ")", OclType.BOOLEAN, true, true);
      }
      case "one" -> {
        requireBoolean(body, call.body().pos());
        yield new JExpr(
            stream(source) + ".filter(" + lambda + ").count() == 1", OclType.BOOLEAN, true, false);
      }
      case "any" -> {
        requireBoolean(body, call.body().pos());
        yield new JExpr(
            stream(source) + ".filter(" + lambda + ").findFirst().orElse(null)",
            type.element(),
            false,
            true);
      }
      case "isUnique" -> {
        runtime.add(UtilityMethodRuntime.IS_UNIQUE);
        yield new JExpr(
            UtilityMethodRuntime.IS_UNIQUE + "(" + source.code() + ", " + lambda + ")",
            OclType.BOOLEAN,
            true,
            true);
      }
      case "sortedBy" -> sortedBy(source, type, variable, body, call);
      case "closure" -> closure(source, type, lambdaVariable, body, call);
      default ->
          throw new UtilityMethodTranslationException(
              "Unknown iterator '" + call.name() + "'.", call.pos());
    };
  }

  private boolean isKindOfTest(IteratorCall call) {
    if (!(call.body() instanceof Call test)
        || !test.name().equals("oclIsKindOf")
        || test.args().size() != 1) {
      return false;
    }
    if (call.implicitIterator()) {
      return test.source() == null;
    }
    return test.source() instanceof Name name
        && name.isSimple()
        && name.segments().get(0).equals(call.iterators().get(0).name());
  }

  private Var declareIterator(IteratorCall call, OclType element) {
    if (call.implicitIterator()) {
      Var implicit = new Var(syntheticName(), freshName(baseNameFor(element)), element, false);
      scope.push(implicit);
      implicitIterators.push(implicit);
      return implicit;
    }
    VarDecl declaration = call.iterators().get(0);
    OclType type = element;
    if (declaration.type() != null) {
      type = resolver.resolve(declaration.type());
      if (!OclType.conformsTo(element, type)) {
        throw new UtilityMethodTranslationException(
            "The iterator '"
                + declaration.name()
                + "' is declared as "
                + type.oclName()
                + " but the elements have type "
                + element.oclName()
                + ".",
            declaration.pos());
      }
    }
    String base = isSynthetic(declaration.name()) ? baseNameFor(type) : declaration.name();
    Var variable = new Var(declaration.name(), freshName(base), type, false);
    scope.push(variable);
    return variable;
  }

  private void popIterator(IteratorCall call) {
    scope.pop();
    if (call.implicitIterator()) {
      implicitIterators.pop();
    }
  }

  private JExpr nestedQuantifier(JExpr source, IteratorCall call) {
    CollectionType type = (CollectionType) source.type();
    List<Var> variables = new ArrayList<>();
    for (VarDecl declaration : call.iterators()) {
      Var variable =
          new Var(declaration.name(), freshName(declaration.name()), type.element(), false);
      scope.push(variable);
      variables.add(variable);
    }
    JExpr body;
    try {
      body = translate(call.body());
    } finally {
      variables.forEach(v -> scope.pop());
    }
    requireBoolean(body, call.body().pos());
    String match = call.name().equals("forAll") ? "allMatch" : "anyMatch";
    String inner =
        stream(source)
            + "."
            + match
            + "("
            + variables.get(1).javaName()
            + " -> "
            + body.code()
            + ")";
    return new JExpr(
        stream(source) + "." + match + "(" + variables.get(0).javaName() + " -> " + inner + ")",
        OclType.BOOLEAN,
        true,
        true);
  }

  private JExpr collect(JExpr source, CollectionType type, String variable, JExpr body) {
    CollectionKind kind = orderedOrBag(type);
    if (body.type() instanceof CollectionType inner) {
      return finish(
          stream(source) + ".flatMap(" + variable + " -> " + paren(body) + ".stream())",
          new CollectionType(kind, elementOf(inner.element())));
    }
    return finish(
        stream(source) + ".map(" + variable + " -> " + body.code() + ")",
        new CollectionType(kind, elementOf(body.type())));
  }

  private JExpr sortedBy(
      JExpr source, CollectionType type, Var variable, JExpr body, IteratorCall call) {
    if (!(body.type() instanceof Primitive) && !(body.type() instanceof EnumType)) {
      throw new UtilityMethodTranslationException(
          "'sortedBy' requires a Boolean, Integer, Real, String or enumeration key, but the key"
              + " has type "
              + body.type().oclName()
              + ".",
          call.body().pos());
    }
    CollectionKind kind =
        type.kind().unique() ? CollectionKind.ORDERED_SET : CollectionKind.SEQUENCE;
    String comparator =
        imports.ref("java.util.Comparator")
            + ".comparing(("
            + javaType(variable.type(), true)
            + " "
            + variable.javaName()
            + ") -> "
            + body.code()
            + ")";
    return new JExpr(
        stream(source) + ".sorted(" + comparator + ").toList()", type.withKind(kind), false, true);
  }

  private JExpr closure(
      JExpr source, CollectionType type, String variable, JExpr body, IteratorCall call) {
    OclType element = type.element();
    OclType produced = elementOf(body.type());
    if (!OclType.conformsTo(produced, element)) {
      throw new UtilityMethodTranslationException(
          "The body of 'closure' must yield elements of type "
              + element.oclName()
              + ", but yields "
              + produced.oclName()
              + ".",
          call.body().pos());
    }
    String step = variable + " -> " + body.code();
    if (!(body.type() instanceof CollectionType)) {
      step =
          variable
              + " -> "
              + imports.ref("java.util.Collections")
              + ".singletonList("
              + body.code()
              + ")";
    }
    runtime.add(UtilityMethodRuntime.CLOSURE);
    String code =
        className
            + ".<"
            + javaType(element, true)
            + ">"
            + UtilityMethodRuntime.CLOSURE
            + "("
            + source.code()
            + ", "
            + step
            + ")";
    if (type.kind().ordered()) {
      return new JExpr(code, type.withKind(CollectionKind.ORDERED_SET), false, true);
    }
    return new JExpr(
        "new " + imports.ref("java.util.LinkedHashSet") + "<>(" + code + ")",
        type.withKind(CollectionKind.SET),
        false,
        true);
  }

  private JExpr iterate(JExpr source, IteratorCall call) {
    CollectionType type = (CollectionType) source.type();
    VarDecl accumulatorDecl = call.accumulator();
    JExpr init = translate(accumulatorDecl.init());
    OclType accumulatorType =
        accumulatorDecl.type() != null ? resolver.resolve(accumulatorDecl.type()) : init.type();
    requireConforms(
        init,
        accumulatorType,
        accumulatorDecl.init().pos(),
        "The initial value of the accumulator");
    VarDecl iteratorDecl = call.iterators().get(0);
    OclType element =
        iteratorDecl.type() != null ? resolver.resolve(iteratorDecl.type()) : type.element();
    Var iterator = new Var(iteratorDecl.name(), freshName(iteratorDecl.name()), element, false);
    scope.push(iterator);
    Var accumulator =
        new Var(accumulatorDecl.name(), freshName(accumulatorDecl.name()), accumulatorType, false);
    scope.push(accumulator);
    JExpr body;
    try {
      body = translate(call.body());
    } finally {
      scope.pop();
      scope.pop();
    }
    requireConforms(body, accumulatorType, call.body().pos(), "The body of 'iterate'");
    runtime.add(UtilityMethodRuntime.ITERATE);
    String elementJava = javaType(element, true);
    String accumulatorJava = javaType(accumulatorType, true);
    String code =
        className
            + ".<"
            + elementJava
            + ", "
            + accumulatorJava
            + ">"
            + UtilityMethodRuntime.ITERATE
            + "("
            + source.code()
            + ", "
            + coerce(init, accumulatorType).code()
            + ", ("
            + elementJava
            + " "
            + iterator.javaName()
            + ", "
            + accumulatorJava
            + " "
            + accumulator.javaName()
            + ") -> "
            + coerce(body, accumulatorType).code()
            + ")";
    return new JExpr(code, accumulatorType, false, true);
  }

  private static CollectionKind orderedOrBag(CollectionType type) {
    return type.kind().ordered() ? CollectionKind.SEQUENCE : CollectionKind.BAG;
  }

  private static OclType elementOf(OclType type) {
    if (type instanceof CollectionType collection) {
      return elementOf(collection.element());
    }
    return type instanceof VoidType ? OclType.ANY : type;
  }

  // ── operators ────────────────────────────────────────────────────────────

  private JExpr unary(Unary unary) {
    JExpr operand = translate(unary.operand());
    if (unary.op().equals("not")) {
      requireBoolean(operand, unary.operand().pos());
      return new JExpr("!" + paren(operand), OclType.BOOLEAN, true, false);
    }
    requireNumeric(operand, unary.operand().pos());
    return new JExpr("-" + paren(operand), operand.type(), true, false);
  }

  private JExpr binary(Binary binary) {
    JExpr left = translate(binary.left());
    JExpr right = translate(binary.right());
    String op = binary.op();
    return switch (op) {
      case "and", "or", "xor", "implies" -> logical(op, left, right, binary);
      case "=", "==" -> equality(left, right, false, binary.pos());
      case "<>", "!=" -> equality(left, right, true, binary.pos());
      case "<", ">", "<=", ">=" -> comparison(op, left, right, binary.pos());
      case "+" -> plus(left, right, binary.pos());
      case "-", "*" -> arithmetic(op, left, right, binary.pos());
      case "/" -> {
        requireNumeric(left, binary.left().pos());
        requireNumeric(right, binary.right().pos());
        boolean integerDivision =
            left.type().equals(OclType.INTEGER) && right.type().equals(OclType.INTEGER);
        yield new JExpr(
            (integerDivision ? "(double) " : "") + paren(left) + " / " + paren(right),
            OclType.REAL,
            true,
            false);
      }
      case "div", "mod" -> {
        requireType(left, OclType.INTEGER, binary.left().pos());
        requireType(right, OclType.INTEGER, binary.right().pos());
        String javaOp = op.equals("div") ? " / " : " % ";
        yield new JExpr(paren(left) + javaOp + paren(right), OclType.INTEGER, true, false);
      }
      default ->
          throw new UtilityMethodTranslationException(
              "Unknown operator '" + op + "'.", binary.pos());
    };
  }

  private JExpr logical(String op, JExpr left, JExpr right, Binary binary) {
    requireBoolean(left, binary.left().pos());
    requireBoolean(right, binary.right().pos());
    return switch (op) {
      case "and" -> infix(left, "&&", right, OclType.BOOLEAN);
      case "or" -> infix(left, "||", right, OclType.BOOLEAN);
      case "xor" -> infix(left, "^", right, OclType.BOOLEAN);
      default -> new JExpr("!" + paren(left) + " || " + paren(right), OclType.BOOLEAN, true, false);
    };
  }

  /**
   * {@code left op right}. The left operand is not parenthesized if it has the same operator, since
   * all generated infix operators are left-associative.
   */
  private JExpr infix(JExpr left, String op, JExpr right, OclType type) {
    String leftCode = op.equals(operators.get(left)) ? left.code() : paren(left);
    JExpr result = new JExpr(leftCode + " " + op + " " + paren(right), type, true, false);
    operators.put(result, op);
    return result;
  }

  private JExpr equality(JExpr left, JExpr right, boolean negated, Pos pos) {
    String equals = negated ? " != " : " == ";
    if (left.type() instanceof VoidType || right.type() instanceof VoidType) {
      JExpr value = left.type() instanceof VoidType ? right : left;
      if (value.primitive()) {
        throw new UtilityMethodTranslationException(
            "A value of type " + value.type().oclName() + " is never null.", pos);
      }
      return new JExpr(paren(left) + equals + paren(right), OclType.BOOLEAN, true, false);
    }
    String code;
    if (left.type() instanceof Primitive l
        && right.type() instanceof Primitive r
        && l.kind() != PrimitiveKind.STRING
        && r.kind() != PrimitiveKind.STRING) {
      if (l.isNumeric() != r.isNumeric()) {
        throw new UtilityMethodTranslationException(
            "Cannot compare " + l.oclName() + " with " + r.oclName() + ".", pos);
      }
      String leftCode =
          left.primitive() || right.primitive()
              ? paren(left)
              : "(" + javaType(left.type(), false) + ") " + paren(left);
      code = leftCode + equals + paren(right);
    } else if (isIdentityComparable(left.type(), right.type())) {
      code = paren(left) + equals + paren(right);
    } else {
      String objects = imports.ref("java.util.Objects");
      code = (negated ? "!" : "") + objects + ".equals(" + left.code() + ", " + right.code() + ")";
      return new JExpr(code, OclType.BOOLEAN, true, !negated);
    }
    return new JExpr(code, OclType.BOOLEAN, true, false);
  }

  private static boolean isIdentityComparable(OclType left, OclType right) {
    if (left instanceof Metaclass && right instanceof Metaclass) {
      return true;
    }
    return left instanceof EnumType && left.equals(right);
  }

  private JExpr comparison(String op, JExpr left, JExpr right, Pos pos) {
    if (left.type().equals(OclType.STRING) && right.type().equals(OclType.STRING)) {
      return new JExpr(
          paren(left) + ".compareTo(" + right.code() + ") " + op + " 0",
          OclType.BOOLEAN,
          true,
          false);
    }
    requireNumeric(left, pos);
    requireNumeric(right, pos);
    return new JExpr(paren(left) + " " + op + " " + paren(right), OclType.BOOLEAN, true, false);
  }

  private JExpr plus(JExpr left, JExpr right, Pos pos) {
    if (left.type().equals(OclType.STRING) || right.type().equals(OclType.STRING)) {
      JExpr stringLeft =
          left.type().equals(OclType.STRING)
              ? left
              : new JExpr("String.valueOf(" + left.code() + ")", OclType.STRING, false, true);
      return retypeInfix(infix(stringLeft, "+", right, OclType.STRING), false);
    }
    return arithmetic("+", left, right, pos);
  }

  private JExpr arithmetic(String op, JExpr left, JExpr right, Pos pos) {
    requireNumeric(left, pos);
    requireNumeric(right, pos);
    OclType type =
        left.type().equals(OclType.INTEGER) && right.type().equals(OclType.INTEGER)
            ? OclType.INTEGER
            : OclType.REAL;
    return infix(left, op, right, type);
  }

  /** The same infix expression with another primitive flag, keeping its operator. */
  private JExpr retypeInfix(JExpr expr, boolean primitive) {
    JExpr result = new JExpr(expr.code(), expr.type(), primitive, expr.atomic());
    operators.put(result, operators.get(expr));
    return result;
  }

  private JExpr conditional(If conditional) {
    JExpr condition = translate(conditional.condition());
    requireBoolean(condition, conditional.condition().pos());
    JExpr thenExpr = translate(conditional.thenExpr());
    JExpr elseExpr = translate(conditional.elseExpr());
    OclType type = OclType.commonSuperType(thenExpr.type(), elseExpr.type());
    JExpr thenCoerced = coerce(thenExpr, type);
    JExpr elseCoerced = coerce(elseExpr, type);
    return new JExpr(
        paren(condition) + " ? " + paren(thenCoerced) + " : " + paren(elseCoerced),
        type,
        thenExpr.primitive() && elseExpr.primitive(),
        false);
  }

  private JExpr nestedLet(Let let, int index) {
    if (index == let.variables().size()) {
      return translate(let.body());
    }
    VarDecl variable = let.variables().get(index);
    JExpr init = translate(variable.init());
    OclType type = init.type();
    if (variable.type() != null) {
      type = resolver.resolve(variable.type());
      requireConforms(init, type, variable.init().pos(), "The value of '" + variable.name() + "'");
    } else if (type instanceof VoidType) {
      type = OclType.ANY;
    }
    Var declared = new Var(variable.name(), freshName(variable.name()), type, false);
    scope.push(declared);
    JExpr body;
    try {
      body = nestedLet(let, index + 1);
    } finally {
      scope.pop();
    }
    runtime.add(UtilityMethodRuntime.LET);
    String valueJava = javaType(type, true);
    String code =
        className
            + ".<"
            + valueJava
            + ", "
            + javaType(body.type(), true)
            + ">"
            + UtilityMethodRuntime.LET
            + "("
            + coerce(init, type).code()
            + ", ("
            + valueJava
            + " "
            + declared.javaName()
            + ") -> "
            + body.code()
            + ")";
    return new JExpr(code, body.type(), false, true);
  }

  private JExpr collectionLiteral(CollectionLit literal) {
    List<CollectionPart> parts = literal.parts();
    if (parts.size() == 1 && parts.get(0).last() != null) {
      return range(literal, parts.get(0));
    }
    List<JExpr> items = new ArrayList<>();
    OclType element = OclType.VOID;
    for (CollectionPart part : parts) {
      if (part.last() != null) {
        throw new UtilityMethodTranslationException(
            "A range must be the only part of a collection literal.", part.first().pos());
      }
      JExpr item = translate(part.first());
      items.add(item);
      element = OclType.commonSuperType(element, item.type());
    }
    CollectionType type = new CollectionType(literal.kind(), element);
    if (items.isEmpty()) {
      String empty =
          literal.kind().isJavaSet()
              ? "new " + imports.ref("java.util.LinkedHashSet") + "<>()"
              : imports.ref("java.util.List") + ".of()";
      return new JExpr(empty, type, false, true);
    }
    final OclType itemType = element;
    String list =
        imports.ref("java.util.Arrays")
            + ".<"
            + javaType(element, true)
            + ">asList("
            + items.stream().map(i -> coerce(i, itemType).code()).collect(Collectors.joining(", "))
            + ")";
    return switch (literal.kind()) {
      case SET ->
          new JExpr(
              "new " + imports.ref("java.util.LinkedHashSet") + "<>(" + list + ")",
              type,
              false,
              true);
      case ORDERED_SET -> new JExpr(list + ".stream().distinct().toList()", type, false, true);
      default -> new JExpr(list, type, false, true);
    };
  }

  private JExpr range(CollectionLit literal, CollectionPart part) {
    JExpr first = translate(part.first());
    JExpr last = translate(part.last());
    requireType(first, OclType.INTEGER, part.first().pos());
    requireType(last, OclType.INTEGER, part.last().pos());
    CollectionType type = new CollectionType(literal.kind(), OclType.INTEGER);
    String stream =
        imports.ref("java.util.stream.IntStream")
            + ".rangeClosed("
            + first.code()
            + ", "
            + last.code()
            + ").boxed()";
    return finish(stream, type);
  }

  // ── code utility methods ─────────────────────────────────────────────────────────

  /**
   * A stream over the elements of {@code source}. A pipeline that would only be collected into a
   * list is continued instead.
   */
  private String stream(JExpr source) {
    String pipeline = pipelines.get(source);
    return pipeline != null ? pipeline : paren(source) + ".stream()";
  }

  /** Collects a stream into the Java representation of {@code type}. */
  private JExpr finish(String stream, CollectionType type) {
    return finish(stream, type, false);
  }

  /**
   * Collects a stream into the Java representation of {@code type}; {@code distinct} tells whether
   * the stream is known to contain no duplicates, e.g. because it filters a unique collection.
   */
  private JExpr finish(String stream, CollectionType type, boolean distinct) {
    if (type.kind() == CollectionKind.SET) {
      String code =
          stream
              + ".collect("
              + imports.ref("java.util.stream.Collectors")
              + ".toCollection("
              + imports.ref("java.util.LinkedHashSet")
              + "::new))";
      return new JExpr(code, type, false, true);
    }
    String pipeline =
        type.kind() == CollectionKind.ORDERED_SET && !distinct ? stream + ".distinct()" : stream;
    JExpr result = new JExpr(pipeline + TO_LIST, type, false, true);
    pipelines.put(result, pipeline);
    return result;
  }

  /** Adapts the Java representation of {@code expr} to {@code target}, e.g. for return values. */
  private JExpr coerce(JExpr expr, OclType target) {
    if (target.equals(OclType.REAL) && expr.type().equals(OclType.INTEGER)) {
      if (expr.code().matches("\\d+")) {
        return new JExpr(expr.code() + ".0", OclType.REAL, true, true);
      }
      return new JExpr("(double) " + paren(expr), OclType.REAL, true, false);
    }
    if (!(expr.type() instanceof CollectionType source)
        || !(target instanceof CollectionType expected)) {
      return expr;
    }
    if (source.element() instanceof VoidType) {
      if (expected.kind().isJavaSet() && !source.kind().isJavaSet()) {
        return new JExpr(
            "new " + imports.ref("java.util.LinkedHashSet") + "<>()", target, false, true);
      }
      return retype(expr, target);
    }
    if (source.element().equals(OclType.INTEGER) && expected.element().equals(OclType.REAL)) {
      return finish(stream(expr) + ".map(Integer::doubleValue)", expected);
    }
    boolean sameElement =
        javaType(source.element(), true).equals(javaType(expected.element(), true));
    boolean sameCategory =
        expected.kind() == CollectionKind.COLLECTION
            || expected.kind().isJavaSet() == source.kind().isJavaSet();
    if (sameElement && sameCategory) {
      return retype(expr, target);
    }
    String copy =
        expected.kind().isJavaSet()
            ? imports.ref("java.util.LinkedHashSet")
            : imports.ref("java.util.ArrayList");
    return new JExpr(
        "new " + copy + "<" + javaType(expected.element(), true) + ">(" + expr.code() + ")",
        target,
        false,
        true);
  }

  /** Converts a 1-based OCL index into a 0-based Java index. */
  private static String minusOne(JExpr index) {
    if (index.code().matches("\\d+")) {
      return String.valueOf(Integer.parseInt(index.code()) - 1);
    }
    return paren(index) + " - 1";
  }

  private static JExpr retype(JExpr expr, OclType type) {
    return new JExpr(expr.code(), type, expr.primitive(), expr.atomic());
  }

  private static String paren(JExpr expr) {
    return expr.atomic() ? expr.code() : "(" + expr.code() + ")";
  }

  /** The Java type of values of {@code type}; {@code boxed} for use as a type argument. */
  String javaType(OclType type, boolean boxed) {
    return switch (type) {
      case Primitive primitive -> primitiveJavaType(primitive, boxed);
      case Metaclass metaclass -> imports.ref(metaclass.eClass().javaQualifiedName());
      case EnumType enumType -> imports.ref(enumType.eEnum().javaQualifiedName());
      case JavaType javaType -> imports.ref(javaType.javaName());
      case CollectionType collection -> {
        String container =
            switch (collection.kind()) {
              case SET -> "java.util.Set";
              case COLLECTION -> "java.util.Collection";
              default -> "java.util.List";
            };
        yield imports.ref(container) + "<" + javaType(collection.element(), true) + ">";
      }
      case AnyType ignored -> "Object";
      case VoidType ignored -> "Object";
    };
  }

  private static String primitiveJavaType(Primitive primitive, boolean boxed) {
    return switch (primitive.kind()) {
      case BOOLEAN -> boxed ? "Boolean" : "boolean";
      case INTEGER -> boxed ? "Integer" : "int";
      case REAL -> boxed ? "Double" : "double";
      case STRING -> "String";
    };
  }

  private static boolean isPrimitiveValue(OclType type) {
    return type instanceof Primitive primitive && primitive.kind() != PrimitiveKind.STRING;
  }

  // ── scope ────────────────────────────────────────────────────────────────

  private Var lookup(String oclName) {
    for (Var variable : scope) {
      if (variable.oclName().equals(oclName)) {
        return variable;
      }
    }
    return null;
  }

  private JExpr variableExpr(Var variable) {
    return new JExpr(variable.javaName(), variable.type(), variable.primitive(), true);
  }

  /**
   * A Java name for a new variable. Lambda parameters must not shadow any variable in scope, so
   * names already in use get a numeric suffix.
   */
  private String freshName(String base) {
    StringBuilder builder = new StringBuilder();
    for (char c : base.toCharArray()) {
      builder.append(Character.isJavaIdentifierPart(c) && c != '$' ? c : '_');
    }
    String candidate = JavaNames.safeName(builder.toString());
    if (candidate.isEmpty() || !Character.isJavaIdentifierStart(candidate.charAt(0))) {
      candidate = "v" + candidate;
    }
    Set<String> used = new HashSet<>();
    scope.forEach(v -> used.add(v.javaName()));
    String name = candidate;
    for (int i = 1; used.contains(name); i++) {
      name = candidate + i;
    }
    return name;
  }

  private String syntheticName() {
    return "$" + syntheticCounter++;
  }

  private static boolean isSynthetic(String oclName) {
    return oclName.startsWith("$");
  }

  /** A readable variable name for elements of {@code type}, e.g. {@code block}. */
  private static String baseNameFor(OclType type) {
    return type instanceof Metaclass metaclass
        ? decapitalize(metaclass.eClass().name())
        : "element";
  }

  private static String decapitalize(String name) {
    return name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }

  // ── checks ───────────────────────────────────────────────────────────────

  private List<JExpr> translateAll(List<Expr> exprs) {
    return exprs.stream().map(this::translate).toList();
  }

  private static void requireArgs(Call call, int count) {
    if (call.args().size() != count) {
      throw new UtilityMethodTranslationException(
          "'"
              + call.name()
              + "' expects "
              + count
              + " argument(s) but got "
              + call.args().size()
              + ".",
          call.pos());
    }
  }

  private static void requireArgTypes(Call call, List<JExpr> args, OclType... types) {
    requireArgs(call, types.length);
    for (int i = 0; i < types.length; i++) {
      if (!OclType.conformsTo(args.get(i).type(), types[i])) {
        throw new UtilityMethodTranslationException(
            "Argument "
                + (i + 1)
                + " of '"
                + call.name()
                + "' must be of type "
                + types[i].oclName()
                + " but has type "
                + args.get(i).type().oclName()
                + ".",
            call.args().get(i).pos());
      }
    }
  }

  private static void requireCollectionArg(Call call, List<JExpr> args) {
    requireArgs(call, 1);
    if (!(args.get(0).type() instanceof CollectionType)) {
      throw new UtilityMethodTranslationException(
          "The argument of '" + call.name() + "' must be a collection.", call.args().get(0).pos());
    }
  }

  private static void requireOrdered(CollectionType type, Call call) {
    if (!type.kind().ordered()) {
      throw new UtilityMethodTranslationException(
          "'"
              + call.name()
              + "' requires an ordered collection (Sequence or OrderedSet), but the"
              + " value has type "
              + type.oclName()
              + ".",
          call.pos());
    }
  }

  private static void requireBoolean(JExpr expr, Pos pos) {
    requireType(expr, OclType.BOOLEAN, pos);
  }

  private static void requireType(JExpr expr, OclType type, Pos pos) {
    if (!OclType.conformsTo(expr.type(), type) || expr.type() instanceof VoidType) {
      throw new UtilityMethodTranslationException(
          "Expected a value of type "
              + type.oclName()
              + " but found "
              + expr.type().oclName()
              + ".",
          pos);
    }
  }

  private static void requireNumeric(JExpr expr, Pos pos) {
    if (!(expr.type() instanceof Primitive primitive) || !primitive.isNumeric()) {
      throw new UtilityMethodTranslationException(
          "Expected a number but found a value of type " + expr.type().oclName() + ".", pos);
    }
  }

  private static void requireNumericElements(OclType element, Pos pos) {
    if (!(element instanceof Primitive primitive) || !primitive.isNumeric()) {
      throw new UtilityMethodTranslationException(
          "Expected a collection of numbers but the elements have type " + element.oclName() + ".",
          pos);
    }
  }

  private static void requireConforms(JExpr expr, OclType expected, Pos pos, String what) {
    if (!OclType.conformsTo(expr.type(), expected)) {
      throw new UtilityMethodTranslationException(
          what
              + " has type "
              + expr.type().oclName()
              + " but "
              + expected.oclName()
              + " is expected.",
          pos);
    }
  }
}
