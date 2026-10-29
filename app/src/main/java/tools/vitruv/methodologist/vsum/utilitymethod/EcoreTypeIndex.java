package tools.vitruv.methodologist.vsum.utilitymethod;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Read-only index of the classifiers declared in a set of {@code .ecore} files.
 *
 * <p>The index parses the XMI directly, so the backend does not need EMF on its classpath. It knows
 * the Java names EMF generates for the classifiers, which follow the genmodels the setup-service
 * creates for every VSUM build: the base package is {@value #MODEL_BASE_PACKAGE}, followed by the
 * (sub)package names.
 */
public final class EcoreTypeIndex {

  /** Base package of the Java code EMF generates for the metamodels of a VSUM build. */
  public static final String MODEL_BASE_PACKAGE = "tools.vitruv.methodologisttemplate.model";

  static final String ECORE_NS_URI = "http://www.eclipse.org/emf/2002/Ecore";
  private static final String XSI_NS_URI = "http://www.w3.org/2001/XMLSchema-instance";

  /**
   * An {@code .ecore} file to index.
   *
   * @param filename the file name, used to resolve cross-file references
   * @param data the XMI content
   */
  public record EcoreFile(String filename, byte[] data) {}

  /** An EPackage. */
  public static final class PackageInfo {
    private final String name;
    private final String nsUri;
    private final PackageInfo parent;
    private final Map<String, ClassifierInfo> classifiers = new LinkedHashMap<>();
    private final Map<String, PackageInfo> subpackages = new LinkedHashMap<>();

    PackageInfo(String name, String nsUri, PackageInfo parent) {
      this.name = name;
      this.nsUri = nsUri;
      this.parent = parent;
    }

    /** The name as declared in the metamodel. */
    public String name() {
      return name;
    }

    /** The namespace URI of the package. */
    public String nsUri() {
      return nsUri;
    }

    /** Segments of the qualified OCL name, e.g. {@code [simulink, blocks]}. */
    public List<String> path() {
      List<String> path = new ArrayList<>();
      for (PackageInfo current = this; current != null; current = current.parent) {
        path.add(0, current.name);
      }
      return path;
    }

    /** The Java package EMF generates the classifiers of this package into. */
    public String javaPackage() {
      return javaPackageOf(path());
    }

    /** The classifiers declared directly in this package. */
    public List<ClassifierInfo> classifiers() {
      return List.copyOf(classifiers.values());
    }

    /** The direct subpackages. */
    public List<PackageInfo> subpackages() {
      return List.copyOf(subpackages.values());
    }
  }

  /**
   * The Java package EMF generates the classifiers of a package into.
   *
   * @param path segments of the qualified OCL name of the package, e.g. {@code [simulink, blocks]}
   * @return the Java package name
   */
  public static String javaPackageOf(List<String> path) {
    StringBuilder builder = new StringBuilder(MODEL_BASE_PACKAGE);
    for (String segment : path) {
      builder.append('.').append(JavaNames.safeName(segment));
    }
    return builder.toString();
  }

  /** Common view of EClasses, EEnums and EDataTypes. */
  public abstract static sealed class ClassifierInfo permits ClassInfo, EnumInfo, DataTypeInfo {
    private final String name;
    private final PackageInfo pkg;

    ClassifierInfo(String name, PackageInfo pkg) {
      this.name = name;
      this.pkg = pkg;
    }

    /** The name as declared in the metamodel. */
    public String name() {
      return name;
    }

    /** The package declaring this classifier. */
    public PackageInfo pkg() {
      return pkg;
    }

    /** The OCL name, e.g. {@code simulink::SubSystem}. */
    public String qualifiedName() {
      return String.join("::", pkg.path()) + "::" + name;
    }

    /** The fully qualified name of the generated Java interface or enum. */
    public String javaQualifiedName() {
      return pkg.javaPackage() + "." + JavaNames.safeName(name);
    }
  }

  /** An EClass. */
  public static final class ClassInfo extends ClassifierInfo {
    private final boolean isAbstract;
    private final List<String> superTypeRefs;
    private final List<ClassInfo> superTypes = new ArrayList<>();
    private final Map<String, FeatureInfo> features = new LinkedHashMap<>();
    private final List<OperationInfo> operations = new ArrayList<>();

    ClassInfo(String name, PackageInfo pkg, boolean isAbstract, List<String> superTypeRefs) {
      super(name, pkg);
      this.isAbstract = isAbstract;
      this.superTypeRefs = superTypeRefs;
    }

    /** Whether the class is abstract. */
    public boolean isAbstract() {
      return isAbstract;
    }

    /** The direct supertypes. */
    public List<ClassInfo> superTypes() {
      return Collections.unmodifiableList(superTypes);
    }

    /** This class and all of its direct and indirect supertypes, nearest first. */
    public List<ClassInfo> selfAndAllSuperTypes() {
      Set<ClassInfo> seen = new LinkedHashSet<>();
      Deque<ClassInfo> pending = new ArrayDeque<>();
      pending.add(this);
      while (!pending.isEmpty()) {
        ClassInfo current = pending.poll();
        if (seen.add(current)) {
          pending.addAll(current.superTypes);
        }
      }
      return List.copyOf(seen);
    }

    /** Whether instances of this class are instances of {@code other}. */
    public boolean conformsTo(ClassInfo other) {
      return selfAndAllSuperTypes().contains(other);
    }

    /** Finds a feature declared by this class or inherited from a supertype. */
    public Optional<FeatureInfo> findFeature(String featureName) {
      for (ClassInfo type : selfAndAllSuperTypes()) {
        FeatureInfo feature = type.features.get(featureName);
        if (feature != null) {
          return Optional.of(feature);
        }
      }
      return Optional.empty();
    }

    /** The features declared by this class, in declaration order. */
    public List<FeatureInfo> features() {
      return List.copyOf(features.values());
    }

    /** The operations declared by this class, in declaration order. */
    public List<OperationInfo> operations() {
      return Collections.unmodifiableList(operations);
    }

    /** Names of all features, including inherited ones. */
    public List<String> allFeatureNames() {
      Set<String> names = new LinkedHashSet<>();
      for (ClassInfo type : selfAndAllSuperTypes()) {
        names.addAll(type.features.keySet());
      }
      return List.copyOf(names);
    }
  }

  /** An EEnum. */
  public static final class EnumInfo extends ClassifierInfo {
    private final List<String> literals;

    EnumInfo(String name, PackageInfo pkg, List<String> literals) {
      super(name, pkg);
      this.literals = List.copyOf(literals);
    }

    /** The names of the literals, in declaration order. */
    public List<String> literals() {
      return literals;
    }
  }

  /** A user-defined EDataType. */
  public static final class DataTypeInfo extends ClassifierInfo {
    private final String instanceClassName;

    DataTypeInfo(String name, PackageInfo pkg, String instanceClassName) {
      super(name, pkg);
      this.instanceClassName = instanceClassName;
    }

    /** The Java class of the values, e.g. {@code java.util.Date}. */
    public String instanceClassName() {
      return instanceClassName;
    }
  }

  /**
   * An EAttribute or EReference, or another typed element: the parameters and the result of an
   * EOperation are represented the same way.
   */
  public static final class FeatureInfo {
    private final String name;
    private final String typeRef;
    private final boolean many;
    private final boolean unique;
    private final boolean ordered;
    private final boolean changeable;
    private ClassifierInfo type;
    private String builtinType;

    FeatureInfo(
        String name,
        String typeRef,
        boolean many,
        boolean unique,
        boolean ordered,
        boolean changeable) {
      this.name = name;
      this.typeRef = typeRef;
      this.many = many;
      this.unique = unique;
      this.ordered = ordered;
      this.changeable = changeable;
    }

    /** The name as declared in the metamodel. */
    public String name() {
      return name;
    }

    /** Whether the feature is multi-valued. */
    public boolean many() {
      return many;
    }

    /** Whether the values of a multi-valued feature are unique. */
    public boolean unique() {
      return unique;
    }

    /** Whether the values of a multi-valued feature are ordered. */
    public boolean ordered() {
      return ordered;
    }

    /** Whether the value can be set, i.e. EMF generates a setter for a single-valued feature. */
    public boolean changeable() {
      return changeable;
    }

    /** Whether a type is declared; an EOperation without one returns {@code void}. */
    public boolean typed() {
      return !typeRef.isBlank();
    }

    /** The classifier of a user-defined type, or {@code null} for Ecore built-ins. */
    public ClassifierInfo type() {
      return type;
    }

    /** The name of the Ecore built-in data type, e.g. {@code EString}, or {@code null}. */
    public String builtinType() {
      return builtinType;
    }
  }

  /**
   * An EOperation.
   *
   * @param name the name as declared in the metamodel
   * @param result the type of the result; not {@link FeatureInfo#typed() typed} for {@code void}
   * @param parameters the parameters, in declaration order
   */
  public record OperationInfo(String name, FeatureInfo result, List<FeatureInfo> parameters) {}

  /** Where a reference was written, to resolve same-file fragments. */
  private record URIContext(String filename, PackageInfo rootPackage) {}

  private final List<PackageInfo> rootPackages = new ArrayList<>();
  private final Map<String, PackageInfo> rootPackageByFilename = new LinkedHashMap<>();
  private final List<ClassInfo> classes = new ArrayList<>();
  private final List<FeatureInfo> features = new ArrayList<>();
  private final List<String> problems = new ArrayList<>();

  private EcoreTypeIndex() {}

  /**
   * Indexes the given files. Files that cannot be parsed are skipped and reported in {@link
   * #problems()}.
   *
   * @param files the {@code .ecore} files
   * @return the index
   */
  public static EcoreTypeIndex of(List<EcoreFile> files) {
    EcoreTypeIndex index = new EcoreTypeIndex();
    for (EcoreFile file : files) {
      index.read(file);
    }
    index.resolve();
    return index;
  }

  /** Root packages in file order. */
  public List<PackageInfo> rootPackages() {
    return List.copyOf(rootPackages);
  }

  /** Problems found while reading the files. */
  public List<String> problems() {
    return List.copyOf(problems);
  }

  /** All classifiers of all packages, including subpackages. */
  public List<ClassifierInfo> allClassifiers() {
    List<ClassifierInfo> result = new ArrayList<>();
    Deque<PackageInfo> pending = new ArrayDeque<>(rootPackages);
    while (!pending.isEmpty()) {
      PackageInfo pkg = pending.poll();
      result.addAll(pkg.classifiers.values());
      pending.addAll(pkg.subpackages.values());
    }
    return result;
  }

  /**
   * Finds a package by its qualified OCL path, e.g. {@code [simulink]} or {@code [simulink,
   * blocks]}.
   */
  public Optional<PackageInfo> findPackage(List<String> path) {
    if (path.isEmpty()) {
      return Optional.empty();
    }
    PackageInfo current =
        rootPackages.stream().filter(p -> p.name.equals(path.get(0))).findFirst().orElse(null);
    for (int i = 1; current != null && i < path.size(); i++) {
      current = current.subpackages.get(path.get(i));
    }
    return Optional.ofNullable(current);
  }

  /** Finds a classifier by its qualified OCL name segments, e.g. {@code [simulink, SubSystem]}. */
  public Optional<ClassifierInfo> findClassifier(List<String> segments) {
    if (segments.size() < 2) {
      return Optional.empty();
    }
    return findPackage(segments.subList(0, segments.size() - 1))
        .map(pkg -> pkg.classifiers.get(segments.get(segments.size() - 1)));
  }

  /** All classifiers with the given simple name. */
  public List<ClassifierInfo> findBySimpleName(String name) {
    return allClassifiers().stream().filter(c -> c.name().equals(name)).toList();
  }

  // ── reading ──────────────────────────────────────────────────────────────

  private void read(EcoreFile file) {
    Document document;
    try {
      document = newDocumentBuilder().parse(new ByteArrayInputStream(file.data()));
    } catch (ParserConfigurationException | SAXException | IOException e) {
      problems.add("Could not read metamodel '" + file.filename() + "': " + e.getMessage());
      return;
    }
    Element root = document.getDocumentElement();
    List<Element> packageElements = new ArrayList<>();
    if ("EPackage".equals(root.getLocalName())) {
      packageElements.add(root);
    } else {
      packageElements.addAll(childElements(root, "EPackage"));
    }
    for (Element packageElement : packageElements) {
      PackageInfo pkg = readPackage(packageElement, null, file.filename());
      rootPackages.add(pkg);
      rootPackageByFilename.putIfAbsent(baseName(file.filename()), pkg);
    }
  }

  private PackageInfo readPackage(Element element, PackageInfo parent, String filename) {
    PackageInfo pkg =
        new PackageInfo(element.getAttribute("name"), element.getAttribute("nsURI"), parent);
    PackageInfo root = parent == null ? pkg : rootOf(parent);
    URIContext context = new URIContext(filename, root);
    for (Element classifier : childElements(element, "eClassifiers")) {
      readClassifier(classifier, pkg, context);
    }
    for (Element sub : childElements(element, "eSubpackages")) {
      PackageInfo subpackage = readPackage(sub, pkg, filename);
      pkg.subpackages.put(subpackage.name, subpackage);
    }
    return pkg;
  }

  private void readClassifier(Element element, PackageInfo pkg, URIContext context) {
    String name = element.getAttribute("name");
    String kind = localType(element);
    switch (kind) {
      case "EEnum" -> {
        List<String> literals =
            childElements(element, "eLiterals").stream().map(l -> l.getAttribute("name")).toList();
        pkg.classifiers.put(name, new EnumInfo(name, pkg, literals));
      }
      case "EDataType" ->
          pkg.classifiers.put(
              name, new DataTypeInfo(name, pkg, element.getAttribute("instanceClassName")));
      default -> readClass(element, pkg, context, name);
    }
  }

  private void readClass(Element element, PackageInfo pkg, URIContext context, String name) {
    List<String> superTypeRefs = new ArrayList<>();
    for (String ref : element.getAttribute("eSuperTypes").trim().split("\\s+")) {
      if (!ref.isBlank()) {
        superTypeRefs.add(qualify(ref, context));
      }
    }
    for (Element superType : childElements(element, "eSuperTypes")) {
      String ref = superType.getAttribute("href");
      if (!ref.isBlank()) {
        superTypeRefs.add(qualify(ref, context));
      }
    }
    for (Element generic : childElements(element, "eGenericSuperTypes")) {
      String ref = generic.getAttribute("eClassifier");
      if (!ref.isBlank()) {
        superTypeRefs.add(qualify(ref, context));
      }
    }
    ClassInfo info =
        new ClassInfo(
            name, pkg, Boolean.parseBoolean(element.getAttribute("abstract")), superTypeRefs);
    for (Element featureElement : childElements(element, "eStructuralFeatures")) {
      FeatureInfo feature = readFeature(featureElement, context);
      info.features.put(feature.name, feature);
      features.add(feature);
    }
    for (Element operationElement : childElements(element, "eOperations")) {
      info.operations.add(readOperation(operationElement, context));
    }
    pkg.classifiers.put(name, info);
    classes.add(info);
  }

  private OperationInfo readOperation(Element element, URIContext context) {
    FeatureInfo result = readFeature(element, context);
    features.add(result);
    List<FeatureInfo> parameters = new ArrayList<>();
    for (Element parameterElement : childElements(element, "eParameters")) {
      FeatureInfo parameter = readFeature(parameterElement, context);
      features.add(parameter);
      parameters.add(parameter);
    }
    return new OperationInfo(element.getAttribute("name"), result, List.copyOf(parameters));
  }

  private FeatureInfo readFeature(Element element, URIContext context) {
    String typeRef = element.getAttribute("eType");
    if (typeRef.isBlank()) {
      typeRef =
          childElements(element, "eType").stream()
              .map(t -> t.getAttribute("href"))
              .findFirst()
              .orElse("");
    }
    if (typeRef.isBlank()) {
      typeRef =
          childElements(element, "eGenericType").stream()
              .map(g -> g.getAttribute("eClassifier"))
              .findFirst()
              .orElse("");
    }
    String upper = element.getAttribute("upperBound");
    boolean many = !upper.isBlank() && !"1".equals(upper.trim()) && !"0".equals(upper.trim());
    return new FeatureInfo(
        element.getAttribute("name"),
        qualify(typeRef, context),
        many,
        !"false".equals(element.getAttribute("unique")),
        !"false".equals(element.getAttribute("ordered")),
        !"false".equals(element.getAttribute("changeable")));
  }

  // ── resolving ────────────────────────────────────────────────────────────

  private void resolve() {
    for (ClassInfo info : classes) {
      for (String ref : info.superTypeRefs) {
        ClassifierInfo resolved = resolveRef(ref);
        if (resolved instanceof ClassInfo superType && superType != info) {
          info.superTypes.add(superType);
        }
      }
    }
    for (FeatureInfo feature : features) {
      String ref = feature.typeRef;
      if (ref.isBlank()) {
        continue;
      }
      if (ref.startsWith(ECORE_NS_URI + "#//")) {
        feature.builtinType = ref.substring((ECORE_NS_URI + "#//").length());
      } else {
        feature.type = resolveRef(ref);
      }
    }
  }

  /**
   * Resolves a qualified reference of the form {@code <file or nsURI>#//path/To/Classifier}.
   * Unresolvable references fall back to a unique classifier with the same simple name.
   */
  private ClassifierInfo resolveRef(String ref) {
    int hash = ref.indexOf('#');
    if (hash < 0) {
      return null;
    }
    String location = ref.substring(0, hash);
    String fragment = ref.substring(hash + 1);
    if (!fragment.startsWith("//")) {
      return null;
    }
    List<String> segments = List.of(fragment.substring(2).split("/"));
    PackageInfo root = findRootByLocation(location);
    if (root != null) {
      PackageInfo pkg = root;
      for (int i = 0; pkg != null && i < segments.size() - 1; i++) {
        pkg = pkg.subpackages.get(segments.get(i));
      }
      if (pkg != null && pkg.classifiers.containsKey(segments.get(segments.size() - 1))) {
        return pkg.classifiers.get(segments.get(segments.size() - 1));
      }
    }
    List<ClassifierInfo> candidates = findBySimpleName(segments.get(segments.size() - 1));
    return candidates.size() == 1 ? candidates.get(0) : null;
  }

  private PackageInfo findRootByLocation(String location) {
    for (PackageInfo root : rootPackages) {
      if (location.equals(root.nsUri)) {
        return root;
      }
    }
    return rootPackageByFilename.get(baseName(location));
  }

  /**
   * Turns a reference as written in the XMI into {@code <location>#//path}. Same-file references
   * ({@code #//X}) get the file name of their file; a leading type token ({@code ecore:EClass
   * other.ecore#//X}) is dropped.
   */
  private static String qualify(String ref, URIContext context) {
    String trimmed = ref.trim();
    int space = trimmed.lastIndexOf(' ');
    if (space >= 0) {
      trimmed = trimmed.substring(space + 1);
    }
    if (trimmed.startsWith("#")) {
      String rootUri = context.rootPackage().nsUri;
      return (rootUri.isBlank() ? context.filename() : rootUri) + trimmed;
    }
    return trimmed;
  }

  // ── xml utility methods ──────────────────────────────────────────────────────────

  static DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory.newDocumentBuilder();
  }

  static List<Element> childElements(Element parent, String localName) {
    List<Element> result = new ArrayList<>();
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element element && localName.equals(localNameOf(element))) {
        result.add(element);
      }
    }
    return result;
  }

  private static String localNameOf(Element element) {
    return element.getLocalName() != null ? element.getLocalName() : element.getTagName();
  }

  static String localType(Element element) {
    String type = element.getAttributeNS(XSI_NS_URI, "type");
    int colon = type.indexOf(':');
    return colon >= 0 ? type.substring(colon + 1) : type;
  }

  private static PackageInfo rootOf(PackageInfo pkg) {
    PackageInfo current = pkg;
    while (current.parent != null) {
      current = current.parent;
    }
    return current;
  }

  private static String baseName(String location) {
    String normalized = location.replace('\\', '/');
    int slash = normalized.lastIndexOf('/');
    return (slash >= 0 ? normalized.substring(slash + 1) : normalized).toLowerCase(Locale.ROOT);
  }
}
