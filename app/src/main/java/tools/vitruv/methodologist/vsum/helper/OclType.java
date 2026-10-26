package tools.vitruv.methodologist.vsum.helper;

import java.util.List;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex.ClassInfo;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex.EnumInfo;

/** The static types of OCL expressions in helpers. */
public sealed interface OclType {

  OclType BOOLEAN = new Primitive(PrimitiveKind.BOOLEAN);
  OclType INTEGER = new Primitive(PrimitiveKind.INTEGER);
  OclType REAL = new Primitive(PrimitiveKind.REAL);
  OclType STRING = new Primitive(PrimitiveKind.STRING);
  OclType ANY = new AnyType();
  OclType VOID = new VoidType();

  /** The OCL spelling of the type, e.g. {@code Sequence(simulink::Block)}. */
  String oclName();

  /** The OCL primitive types. */
  enum PrimitiveKind {
    BOOLEAN("Boolean"),
    INTEGER("Integer"),
    REAL("Real"),
    STRING("String");

    private final String oclName;

    PrimitiveKind(String oclName) {
      this.oclName = oclName;
    }
  }

  /** The OCL collection kinds. */
  enum CollectionKind {
    COLLECTION("Collection", false, false),
    SET("Set", false, true),
    ORDERED_SET("OrderedSet", true, true),
    BAG("Bag", false, false),
    SEQUENCE("Sequence", true, false);

    private final String oclName;
    private final boolean ordered;
    private final boolean unique;

    CollectionKind(String oclName, boolean ordered, boolean unique) {
      this.oclName = oclName;
      this.ordered = ordered;
      this.unique = unique;
    }

    public String oclName() {
      return oclName;
    }

    public boolean ordered() {
      return ordered;
    }

    public boolean unique() {
      return unique;
    }

    /**
     * Whether the kind is represented by a {@link java.util.Set} in Java. Ordered sets are lists,
     * so that they keep EMF's {@code EList} semantics and support positional access.
     */
    public boolean isJavaSet() {
      return this == SET;
    }

    /** Finds a kind by its OCL name. */
    public static CollectionKind fromOclName(String name) {
      for (CollectionKind kind : values()) {
        if (kind.oclName.equals(name)) {
          return kind;
        }
      }
      return null;
    }
  }

  /** Boolean, Integer, Real or String. */
  record Primitive(PrimitiveKind kind) implements OclType {
    @Override
    public String oclName() {
      return kind.oclName;
    }

    public boolean isNumeric() {
      return kind == PrimitiveKind.INTEGER || kind == PrimitiveKind.REAL;
    }
  }

  /** An EClass of one of the VSUM's metamodels. */
  record Metaclass(ClassInfo eClass) implements OclType {
    @Override
    public String oclName() {
      return eClass.qualifiedName();
    }
  }

  /** An EEnum of one of the VSUM's metamodels. */
  record EnumType(EnumInfo eEnum) implements OclType {
    @Override
    public String oclName() {
      return eEnum.qualifiedName();
    }
  }

  /** An EDataType without an OCL counterpart, handled as an opaque Java type. */
  record JavaType(String oclName, String javaName) implements OclType {}

  /** A collection of elements of one type. */
  record CollectionType(CollectionKind kind, OclType element) implements OclType {
    @Override
    public String oclName() {
      return kind.oclName + "(" + element.oclName() + ")";
    }

    /** The same element type in another kind of collection. */
    public CollectionType withKind(CollectionKind newKind) {
      return new CollectionType(newKind, element);
    }
  }

  /** OclAny, the supertype of all types. */
  record AnyType() implements OclType {
    @Override
    public String oclName() {
      return "OclAny";
    }
  }

  /** OclVoid, the type of {@code null}, conforming to all types. */
  record VoidType() implements OclType {
    @Override
    public String oclName() {
      return "OclVoid";
    }
  }

  /** Whether values of {@code type} can be used where {@code expected} is required. */
  static boolean conformsTo(OclType type, OclType expected) {
    if (type.equals(expected) || expected instanceof AnyType || type instanceof VoidType) {
      return true;
    }
    if (type instanceof Primitive p && expected instanceof Primitive e) {
      return p.kind() == PrimitiveKind.INTEGER && e.kind() == PrimitiveKind.REAL;
    }
    if (type instanceof Metaclass m && expected instanceof Metaclass e) {
      return m.eClass().conformsTo(e.eClass());
    }
    if (type instanceof CollectionType c && expected instanceof CollectionType e) {
      return kindConforms(c.kind(), e.kind()) && conformsTo(c.element(), e.element());
    }
    return false;
  }

  /**
   * Collection kinds conform if they are represented by the same Java collection; any kind conforms
   * to {@code Collection}.
   */
  private static boolean kindConforms(CollectionKind kind, CollectionKind expected) {
    return expected == CollectionKind.COLLECTION
        || kind == expected
        || (!kind.isJavaSet() && !expected.isJavaSet() && kind != CollectionKind.COLLECTION);
  }

  /** The most specific type both arguments conform to. */
  static OclType commonSuperType(OclType a, OclType b) {
    if (conformsTo(a, b)) {
      return b;
    }
    if (conformsTo(b, a)) {
      return a;
    }
    if (a instanceof Metaclass ma && b instanceof Metaclass mb) {
      List<ClassInfo> others = mb.eClass().selfAndAllSuperTypes();
      for (ClassInfo candidate : ma.eClass().selfAndAllSuperTypes()) {
        if (others.contains(candidate)) {
          return new Metaclass(candidate);
        }
      }
      return ANY;
    }
    if (a instanceof CollectionType ca && b instanceof CollectionType cb) {
      CollectionKind kind = ca.kind() == cb.kind() ? ca.kind() : CollectionKind.COLLECTION;
      return new CollectionType(kind, commonSuperType(ca.element(), cb.element()));
    }
    return ANY;
  }
}
