package tools.vitruv.methodologist.builder;

import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;

public final class SimpleValidators {
  private SimpleValidators() {}

  public static void assertValidEcore(Resource res, String label) {
    if (res == null) throw new IllegalArgumentException(label + " resource is null");
    if (!res.getErrors().isEmpty())
      throw new IllegalArgumentException(label + " parse error: " + res.getErrors().get(0));
    if (res.getContents().isEmpty()) throw new IllegalArgumentException(label + " has no contents");

    EObject root = res.getContents().get(0);
    if (!(root instanceof EPackage ep))
      throw new IllegalArgumentException(label + " root is not an EPackage");

    if (isBlank(ep.getName())) throw new IllegalArgumentException(label + " package has no name");
    if (isBlank(ep.getNsURI())) throw new IllegalArgumentException(label + " package has no nsURI");

    for (EClassifier c : ep.getEClassifiers()) {
      if (isBlank(c.getName()))
        throw new IllegalArgumentException(label + " has classifier without name");
      if (c instanceof EClass cls) {
        for (EStructuralFeature f : cls.getEStructuralFeatures()) {
          if (isBlank(f.getName()))
            throw new IllegalArgumentException(
                label + " class " + cls.getName() + " has feature without name");
          if (f instanceof EReference ref && ref.getEType() == null)
            throw new IllegalArgumentException(
                label + " reference " + cls.getName() + "." + f.getName() + " has no type");
          if (f instanceof EAttribute att && att.getEAttributeType() == null)
            throw new IllegalArgumentException(
                label + " attribute " + cls.getName() + "." + f.getName() + " has no datatype");
        }
      }
    }
  }

  public static void assertValidGenModel(Resource res, String label) {
    if (res == null) throw new IllegalArgumentException(label + " resource is null");
    if (!res.getErrors().isEmpty())
      throw new IllegalArgumentException(label + " parse error: " + res.getErrors().get(0));
    if (res.getContents().isEmpty()) throw new IllegalArgumentException(label + " has no contents");
    if (!(res.getContents().get(0) instanceof GenModel gm))
      throw new IllegalArgumentException(label + " root is not a GenModel");

    if (isBlank(gm.getModelPluginID()))
      throw new IllegalArgumentException(label + " has empty modelPluginID");
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
