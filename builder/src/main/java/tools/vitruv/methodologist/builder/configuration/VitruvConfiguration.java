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
import org.eclipse.emf.ecore.resource.*;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

public class VitruvConfiguration {
  private final List<MetamodelLocation> metamodels = new ArrayList<>();
  private Path localPath;
  private String packageName;

  public static String removeLastSegment(String s) {
    int i = (s == null ? -1 : s.lastIndexOf('.'));
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

  /** pairs format: "path/to.a.ecore,path/to.a.genmodel;path/to.b.ecore,path/to.b.genmodel" */
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

      // load & validate ecore
      Resource eRes = rs.getResource(URI.createFileURI(ecore.getAbsolutePath()), true);
      assertValidEcore(eRes, "Ecore: " + ecore.getName());
      EPackage ep = (EPackage) eRes.getContents().get(0);

      // load & validate genmodel
      Resource gRes = rs.getResource(URI.createFileURI(gen.getAbsolutePath()), true);
      assertValidGenModel(gRes, "GenModel: " + gen.getName());
      GenModel gm = (GenModel) gRes.getContents().get(0);

      // collect
      this.metamodels.add(new MetamodelLocation(ecore, gen, ep.getNsURI()));
      this.setPackageName(removeLastSegment(gm.getModelPluginID()));
    }
  }

  public String getPackageName() {
    return packageName;
  }

  public void setPackageName(String s) {
    this.packageName = (s == null ? null : s.replaceAll("\\s", ""));
  }
}
