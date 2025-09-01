package tools.vitruv.methodologist.builder;

import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.ecore.*;
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
    if (res == null) {
      throw new IllegalArgumentException(label + " resource is null");
    }
    if (!res.getErrors().isEmpty()) {
      throw new IllegalArgumentException(label + " parse error: " + res.getErrors().get(0));
    }
    if (res.getContents().isEmpty()) {
      throw new IllegalArgumentException(label + " has no contents");
    }

    EObject root = res.getContents().get(0);
    if (!(root instanceof EPackage ep)) {
      throw new IllegalArgumentException(label + " root is not an EPackage");
    }

    if (isBlank(ep.getName())) {
      throw new IllegalArgumentException(label + " package has no name");
    }
    if (isBlank(ep.getNsURI())) {
      throw new IllegalArgumentException(label + " package has no nsURI");
    }

    for (EClassifier c : ep.getEClassifiers()) {
      if (isBlank(c.getName())) {
        throw new IllegalArgumentException(label + " has classifier without name");
      }
      if (c instanceof EClass cls) {
        for (EStructuralFeature f : cls.getEStructuralFeatures()) {
          if (isBlank(f.getName())) {
            throw new IllegalArgumentException(
                label + " class " + cls.getName() + " has feature without name");
          }
          if (f instanceof EReference ref && ref.getEType() == null) {
            throw new IllegalArgumentException(
                label + " reference " + cls.getName() + "." + f.getName() + " has no type");
          }
          if (f instanceof EAttribute att && att.getEAttributeType() == null) {
            throw new IllegalArgumentException(
                label + " attribute " + cls.getName() + "." + f.getName() + " has no datatype");
          }
        }
      }
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
