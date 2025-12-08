package tools.vitruv.methodologist.builder;

import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * Utility class providing static validation methods for Ecore and GenModel resources. Ensures that
 * basic structural and naming rules are met before further processing.
 */
public final class SimpleValidators {
  private SimpleValidators() {}

  /**
   * Validates that the given Ecore resource is well-formed. The resource must not be null, contain
   * parsing errors, or be empty. The root element must be an EPackage with a non-empty name and
   * nsURI. Each classifier must have a name, and all classes must have properly named and typed
   * structural features. Attributes must have datatypes and references must have target types.
   * Throws IllegalArgumentException if any validation check fails.
   */
  public static void assertValidEcore(Resource res, String label) {
    validateResourceBasics(res, label);
    EPackage ep = extractRootPackage(res, label);
    validatePackageBasics(ep, label);
    validateClassifiers(ep, label);
  }

  private static void validateResourceBasics(Resource res, String label) {
    if (res == null) {
      throw new IllegalArgumentException(label + " resource is null");
    }
    if (!res.getErrors().isEmpty()) {
      throw new IllegalArgumentException(label + " parse error: " + res.getErrors().get(0));
    }
    if (res.getContents().isEmpty()) {
      throw new IllegalArgumentException(label + " has no contents");
    }
  }

  private static EPackage extractRootPackage(Resource res, String label) {
    EObject root = res.getContents().get(0);
    if (!(root instanceof EPackage ep)) {
      throw new IllegalArgumentException(label + " root is not an EPackage");
    }
    return ep;
  }

  private static void validatePackageBasics(EPackage ep, String label) {
    if (isBlank(ep.getName())) {
      throw new IllegalArgumentException(label + " package has no name");
    }
    if (isBlank(ep.getNsURI())) {
      throw new IllegalArgumentException(label + " package has no nsURI");
    }
  }

  private static void validateClassifiers(EPackage ep, String label) {
    for (EClassifier classifier : ep.getEClassifiers()) {
      validateClassifierName(classifier, label);
      if (classifier instanceof EClass cls) {
        validateClassFeatures(cls, label);
      }
    }
  }

  private static void validateClassifierName(EClassifier classifier, String label) {
    if (isBlank(classifier.getName())) {
      throw new IllegalArgumentException(label + " has classifier without name");
    }
  }

  private static void validateClassFeatures(EClass cls, String label) {
    for (EStructuralFeature feature : cls.getEStructuralFeatures()) {
      validateFeatureBasics(cls, feature, label);
      validateFeatureType(cls, feature, label);
    }
  }

  private static void validateFeatureBasics(EClass cls, EStructuralFeature feature, String label) {
    if (isBlank(feature.getName())) {
      throw new IllegalArgumentException(
          label + " class " + cls.getName() + " has feature without name");
    }
  }

  private static void validateFeatureType(EClass cls, EStructuralFeature feature, String label) {
    if (feature instanceof EReference ref && ref.getEType() == null) {
      throw new IllegalArgumentException(
          label + " reference " + cls.getName() + "." + feature.getName() + " has no type");
    }
    if (feature instanceof EAttribute att && att.getEAttributeType() == null) {
      throw new IllegalArgumentException(
          label + " attribute " + cls.getName() + "." + feature.getName() + " has no datatype");
    }
  }

  /**
   * Validates that the given GenModel resource is well-formed. The resource must not be null,
   * contain parsing errors, or be empty. The root element must be a GenModel with a non-empty
   * modelPluginID. Throws IllegalArgumentException if any validation check fails.
   */
  public static void assertValidGenModel(Resource res, String label) {
    if (res == null) {
      throw new IllegalArgumentException(label + " resource is null");
    }
    if (!res.getErrors().isEmpty()) {
      throw new IllegalArgumentException(label + " parse error: " + res.getErrors().get(0));
    }
    if (res.getContents().isEmpty()) {
      throw new IllegalArgumentException(label + " has no contents");
    }
    if (!(res.getContents().get(0) instanceof GenModel gm)) {
      throw new IllegalArgumentException(label + " root is not a GenModel");
    }

    if (isBlank(gm.getModelPluginID())) {
      throw new IllegalArgumentException(label + " has empty modelPluginID");
    }
  }

  /** Returns true if the given string is null or consists only of whitespace. */
  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
