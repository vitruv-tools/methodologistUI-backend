package tools.vitruv.methodologist.builder.configuration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/** The VitruvConfiguration class is used to store the configuration of the Vitruv CLI. */
public class VitruvConfiguration {
  private final List<MetamodelLocation> metamodelLocations = new ArrayList<>();
  private Path localPath;
  private String packageName;
  private File workflow;

  public static String removeLastSegment(String input) {
    int lastDotIndex = input.lastIndexOf('.');
    return lastDotIndex == -1 ? input : input.substring(0, lastDotIndex);
  }

  public static void validateEcoreResource(Resource res) {
    if (res.getContents().isEmpty())
      throw new IllegalArgumentException("Ecore resource has no contents.");
    EObject root = res.getContents().get(0);
    Diagnostic d = Diagnostician.INSTANCE.validate(root);
    if (d.getSeverity() == Diagnostic.ERROR) {
      throw new IllegalArgumentException("Ecore validation failed: " + d.getMessage());
    }
  }

  public Path getLocalPath() {
    return localPath;
  }

  public void setLocalPath(Path localPath) {
    this.localPath = localPath;
  }

  public boolean addMetamodelLocations(MetamodelLocation m) {
    return this.metamodelLocations.add(m);
  }

  public File getWorkflow() {
    return workflow;
  }

  public void setWorkflow(File workflow) {
    this.workflow = workflow;
  }

  public List<MetamodelLocation> getMetaModelLocations() {
    return this.metamodelLocations;
  }

  public void setMetaModelLocations(String paths) {
    // Register resource factories
    Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
    reg.getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
    reg.getExtensionToFactoryMap().put("genmodel", new XMIResourceFactoryImpl());
    GenModelPackage.eINSTANCE.eClass();

    for (String pair : paths.split(";")) {
      if (pair.isBlank() || !pair.contains(",")) continue;
      String metamodelPath = pair.split(",")[0].trim();
      String genmodelPath = pair.split(",")[1].trim();

      File metamodel = new File(metamodelPath);
      File genmodel = new File(genmodelPath);

      ResourceSet rs = new ResourceSetImpl();
      URI ecoreUri = URI.createFileURI(metamodel.getAbsolutePath());
      Resource ecoreRes = rs.getResource(ecoreUri, true);
      validateEcoreResource(ecoreRes); // throws if invalid

      if (!ecoreRes.getContents().isEmpty()
          && ecoreRes.getContents().get(0) instanceof EPackage epkg) {
        this.addMetamodelLocations(new MetamodelLocation(metamodel, genmodel, epkg.getNsURI()));

        // load GenModel to derive package name
        URI genUri = URI.createFileURI(genmodel.getAbsolutePath());
        Resource genRes = rs.getResource(genUri, true);
        if (genRes.getContents().isEmpty()) {
          throw new IllegalArgumentException("GenModel resource has no contents: " + genmodel);
        }
        if (!genRes.getContents().isEmpty() && genRes.getContents().get(0) instanceof GenModel gm) {
          String pkg = removeLastSegment(gm.getModelPluginID());
          this.setPackageName(pkg);
        }
      }
    }
  }

  public String getPackageName() {
    return this.packageName;
  }

  public void setPackageName(String packageName) {
    // BUGFIX: must be replaceAll, not replace
    this.packageName = packageName == null ? null : packageName.replaceAll("\\s", "");
  }
}
