package tools.vitruv.methodologist.builder.configuration;

import static tools.vitruv.methodologist.builder.SimpleValidators.*;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * Configuration object for Vitruv metamodel builds. Stores references to validated metamodels
 * (Ecore and GenModel pairs), the local output path, and the base package name. Responsible for
 * parsing input pairs, loading resources with EMF, and validating their correctness before they are
 * used in code generation.
 */
public class VitruvConfiguration {
  private final List<MetamodelLocation> metamodels = new ArrayList<>();
  private Path localPath;
  private String packageName;

  /**
   * Removes the last segment after a dot from a string, often used to trim a plugin identifier into
   * a package name.
   */
  public static String removeLastSegment(String s) {
    int i = (s == null ? -1 : s.lastIndexOf('.'));
    return i < 0 ? s : s.substring(0, i);
  }

  /** Returns the configured local path for generated artifacts. */
  public Path getLocalPath() {
    return localPath;
  }

  /** Sets the local path for generated artifacts. */
  public void setLocalPath(Path p) {
    this.localPath = p;
  }

  /** Returns the list of loaded and validated metamodel locations. */
  public List<MetamodelLocation> getMetaModelLocations() {
    return metamodels;
  }

  /**
   * Parses a string of pairs describing Ecore and GenModel files, loads them with EMF, validates
   * them, and stores the results. The expected format is: {@code
   * "path/to/model.ecore,path/to/model.genmodel;path/to/other.ecore,path/to/other.genmodel"}
   * Invalid or missing files will cause exceptions. On success, the namespace URI and package name
   * are derived from the resources.
   */
  public void setMetaModelLocations(String pairs) {
    // EMF factories
    Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
    reg.getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
    reg.getExtensionToFactoryMap().put("genmodel", new XMIResourceFactoryImpl());
    GenModelPackage.eINSTANCE.eClass();

    if (pairs == null || pairs.isBlank()) return;

    for (String pair : pairs.split(";")) {
      if (pair.isBlank() || !pair.contains(",")) continue;

      String e = pair.split(",")[0].trim();
      String g = pair.split(",")[1].trim();

      File ecore = new File(e);
      File gen = new File(g);

      if (!ecore.isFile() || ecore.length() == 0L)
        throw new IllegalArgumentException("Ecore missing/empty: " + ecore);
      if (!gen.isFile() || gen.length() == 0L)
        throw new IllegalArgumentException("GenModel missing/empty: " + gen);

      ResourceSet rs = new ResourceSetImpl();

      Resource eRes = rs.getResource(URI.createFileURI(ecore.getAbsolutePath()), true);
      assertValidEcore(eRes, "Ecore: " + ecore.getName());
      EPackage ep = (EPackage) eRes.getContents().get(0);

      Resource gRes = rs.getResource(URI.createFileURI(gen.getAbsolutePath()), true);
      assertValidGenModel(gRes, "GenModel: " + gen.getName());
      GenModel gm = (GenModel) gRes.getContents().get(0);

      this.metamodels.add(new MetamodelLocation(ecore, gen, ep.getNsURI()));
      this.setPackageName(removeLastSegment(gm.getModelPluginID()));
    }
  }

  /** Returns the current package name, stripped of whitespace. */
  public String getPackageName() {
    return packageName;
  }

  /** Sets the package name, removing all whitespace if present. */
  public void setPackageName(String s) {
    this.packageName = (s == null ? null : s.replaceAll("\\s", ""));
  }
}
