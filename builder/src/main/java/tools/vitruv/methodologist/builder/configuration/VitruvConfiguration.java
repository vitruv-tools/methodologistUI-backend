package tools.vitruv.methodologist.builder.configuration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.*;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

public class VitruvConfiguration {
  private final List<MetamodelLocation> metamodels = new ArrayList<>();
  private Path localPath;
  private String packageName;

  public static String removeLastSegment(String s) {
    int i = s == null ? -1 : s.lastIndexOf('.');
    return i < 0 ? s : s.substring(0, i);
  }

  public Path getLocalPath() {
    return localPath;
  }

  public void setLocalPath(Path p) {
    this.localPath = p;
  }

  public List<MetamodelLocation> getMetaModelLocations() {
    return metamodels;
  }

  public void setMetaModelLocations(String pairs) {
    // Factories
    Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
    reg.getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
    reg.getExtensionToFactoryMap().put("genmodel", new XMIResourceFactoryImpl());
    GenModelPackage.eINSTANCE.eClass();

    for (String pair : pairs.split(";")) {
      if (pair.isBlank() || !pair.contains(",")) continue;
      String e = pair.split(",")[0].trim();
      String g = pair.split(",")[1].trim();
      File ecore = new File(e), gen = new File(g);

      if (!ecore.isFile() || ecore.length() == 0)
        throw new IllegalArgumentException("Ecore missing/empty: " + ecore);
      if (!gen.isFile() || gen.length() == 0)
        throw new IllegalArgumentException("GenModel missing/empty: " + gen);

      ResourceSet rs = new ResourceSetImpl();
      Resource eRes = rs.getResource(URI.createFileURI(ecore.getAbsolutePath()), true);
      if (!eRes.getErrors().isEmpty())
        throw new IllegalArgumentException("Ecore parse error: " + eRes.getErrors().get(0));
      if (eRes.getContents().isEmpty() || !(eRes.getContents().get(0) instanceof EPackage ep))
        throw new IllegalArgumentException("Ecore root is not an EPackage: " + ecore);

      Resource gRes = rs.getResource(URI.createFileURI(gen.getAbsolutePath()), true);
      if (!gRes.getErrors().isEmpty())
        throw new IllegalArgumentException("GenModel parse error: " + gRes.getErrors().get(0));
      if (gRes.getContents().isEmpty() || !(gRes.getContents().get(0) instanceof GenModel gm))
        throw new IllegalArgumentException("GenModel root is not a GenModel: " + gen);

      this.metamodels.add(new MetamodelLocation(ecore, gen, ep.getNsURI()));
      this.setPackageName(
          removeLastSegment(((GenModel) gRes.getContents().get(0)).getModelPluginID()));
    }
  }

  public String getPackageName() {
    return packageName;
  }

  public void setPackageName(String s) {
    this.packageName = (s == null ? null : s.replaceAll("\\s", ""));
  }
}
