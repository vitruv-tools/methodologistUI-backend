package tools.vitruv.methodologist.builder;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelFactory;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.impl.EObjectImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.junit.jupiter.api.Test;

class SimpleValidatorsTest {

  private Resource newResource(String uri) {
    XMIResourceImpl resource = new XMIResourceImpl(URI.createURI(uri));
    ResourceSetImpl resourceSet = new ResourceSetImpl();
    resourceSet.getResources().add(resource);
    return resource;
  }

  private EPackage createMinimalEPackage(String name, String nsURI) {
    EcoreFactory factory = EcoreFactory.eINSTANCE;
    EPackage ePackage = factory.createEPackage();
    ePackage.setName(name);
    ePackage.setNsURI(nsURI);
    ePackage.setNsPrefix("test");
    return ePackage;
  }

  private GenModel createMinimalGenModel(String modelPluginId) {
    GenModel genModel = GenModelFactory.eINSTANCE.createGenModel();
    genModel.setModelPluginID(modelPluginId);
    return genModel;
  }

  @Test
  void assertValidEcore_acceptsMinimalValidPackage() {
    Resource resource = newResource("mem:/valid.ecore");

    EPackage ePackage = createMinimalEPackage("pkg", "http://example/pkg");
    EcoreFactory factory = EcoreFactory.eINSTANCE;

    EClass classA = factory.createEClass();
    classA.setName("A");

    EAttribute attributeName = factory.createEAttribute();
    attributeName.setName("name");
    attributeName.setEType(EcorePackage.Literals.ESTRING);

    EClass classB = factory.createEClass();
    classB.setName("B");

    org.eclipse.emf.ecore.EReference referenceToB = factory.createEReference();
    referenceToB.setName("toB");
    referenceToB.setEType(classB);

    classA.getEStructuralFeatures().add(attributeName);
    classA.getEStructuralFeatures().add(referenceToB);

    ePackage.getEClassifiers().add(classA);
    ePackage.getEClassifiers().add(classB);

    resource.getContents().add(ePackage);

    assertDoesNotThrow(() -> SimpleValidators.assertValidEcore(resource, "Ecore"));
  }

  @Test
  void assertValidEcore_throws_whenResourceNull() {
    assertThrows(
        IllegalArgumentException.class, () -> SimpleValidators.assertValidEcore(null, "Ecore"));
  }

  @Test
  void assertValidEcore_throws_whenEmptyContents() {
    Resource resource = newResource("mem:/empty.ecore");
    assertThrows(
        IllegalArgumentException.class, () -> SimpleValidators.assertValidEcore(resource, "Ecore"));
  }

  @Test
  void assertValidEcore_throws_whenRootNotEPackage() {
    Resource resource = newResource("mem:/notpkg.ecore");
    EObject someObject = new EObjectImpl() {};
    resource.getContents().add(someObject);

    assertThrows(
        IllegalArgumentException.class, () -> SimpleValidators.assertValidEcore(resource, "Ecore"));
  }

  @Test
  void assertValidEcore_throws_whenPackageNameBlank() {
    Resource resource = newResource("mem:/pkgblank.ecore");
    EPackage ePackage = createMinimalEPackage("", "http://example/x");
    resource.getContents().add(ePackage);

    assertThrows(
        IllegalArgumentException.class, () -> SimpleValidators.assertValidEcore(resource, "Ecore"));
  }

  @Test
  void assertValidEcore_throws_whenPackageNsURIBlank() {
    Resource resource = newResource("mem:/nsblank.ecore");
    EPackage ePackage = createMinimalEPackage("pkg", "");
    resource.getContents().add(ePackage);

    assertThrows(
        IllegalArgumentException.class, () -> SimpleValidators.assertValidEcore(resource, "Ecore"));
  }

  @Test
  void assertValidEcore_throws_whenClassifierWithoutName() {
    Resource resource = newResource("mem:/clsnoname.ecore");
    EPackage ePackage = createMinimalEPackage("pkg", "http://example/x");
    EClass unnamedClass = EcoreFactory.eINSTANCE.createEClass(); // no name set
    ePackage.getEClassifiers().add(unnamedClass);
    resource.getContents().add(ePackage);

    assertThrows(
        IllegalArgumentException.class, () -> SimpleValidators.assertValidEcore(resource, "Ecore"));
  }

  @Test
  void assertValidEcore_throws_whenFeatureWithoutName() {

    EClass classC = EcoreFactory.eINSTANCE.createEClass();
    classC.setName("C");

    EAttribute unnamedAttribute = EcoreFactory.eINSTANCE.createEAttribute();
    unnamedAttribute.setEType(EcorePackage.Literals.ESTRING);

    Resource resource = newResource("mem:/featurenoname.ecore");
    EPackage ePackage = createMinimalEPackage("pkg", "http://example/x");

    classC.getEStructuralFeatures().add(unnamedAttribute);
    ePackage.getEClassifiers().add(classC);
    resource.getContents().add(ePackage);

    assertThrows(
        IllegalArgumentException.class, () -> SimpleValidators.assertValidEcore(resource, "Ecore"));
  }

  @Test
  void assertValidEcore_throws_whenReferenceWithoutType() {
    EClass classC = EcoreFactory.eINSTANCE.createEClass();
    classC.setName("C");

    org.eclipse.emf.ecore.EReference referenceWithoutType =
        EcoreFactory.eINSTANCE.createEReference();
    referenceWithoutType.setName("toX");
    Resource resource = newResource("mem:/reftypemissing.ecore");
    EPackage ePackage = createMinimalEPackage("pkg", "http://example/x");

    classC.getEStructuralFeatures().add(referenceWithoutType);
    ePackage.getEClassifiers().add(classC);
    resource.getContents().add(ePackage);

    assertThrows(
        IllegalArgumentException.class, () -> SimpleValidators.assertValidEcore(resource, "Ecore"));
  }

  @Test
  void assertValidEcore_throws_whenAttributeWithoutDatatype() {
    EClass classC = EcoreFactory.eINSTANCE.createEClass();
    classC.setName("C");

    EAttribute attributeWithoutDatatype = EcoreFactory.eINSTANCE.createEAttribute();
    attributeWithoutDatatype.setName("n");

    classC.getEStructuralFeatures().add(attributeWithoutDatatype);
    EPackage ePackage = createMinimalEPackage("pkg", "http://example/x");
    ePackage.getEClassifiers().add(classC);

    Resource resource = newResource("mem:/attrtypemissing.ecore");
    resource.getContents().add(ePackage);

    assertThrows(
        IllegalArgumentException.class, () -> SimpleValidators.assertValidEcore(resource, "Ecore"));
  }

  @Test
  void assertValidGenModel_acceptsMinimalValidGenModel() {
    Resource resource = newResource("mem:/valid.genmodel");
    GenModel genModel = createMinimalGenModel("com.example.model");
    resource.getContents().add(genModel);

    assertDoesNotThrow(() -> SimpleValidators.assertValidGenModel(resource, "GenModel"));
  }

  @Test
  void assertValidGenModel_throws_whenResourceNull() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SimpleValidators.assertValidGenModel(null, "GenModel"));
  }

  @Test
  void assertValidGenModel_throws_whenEmptyContents() {
    Resource resource = newResource("mem:/empty.genmodel");
    assertThrows(
        IllegalArgumentException.class,
        () -> SimpleValidators.assertValidGenModel(resource, "GenModel"));
  }

  @Test
  void assertValidGenModel_throws_whenRootNotGenModel() {
    Resource resource = newResource("mem:/notgm.genmodel");
    EObject notAGenModel = new EObjectImpl() {};
    resource.getContents().add(notAGenModel);

    assertThrows(
        IllegalArgumentException.class,
        () -> SimpleValidators.assertValidGenModel(resource, "GenModel"));
  }

  @Test
  void assertValidGenModel_throws_whenModelPluginIdBlank() {
    Resource resource = newResource("mem:/blankid.genmodel");
    GenModel genModel = createMinimalGenModel("");
    resource.getContents().add(genModel);

    assertThrows(
        IllegalArgumentException.class,
        () -> SimpleValidators.assertValidGenModel(resource, "GenModel"));
  }
}
