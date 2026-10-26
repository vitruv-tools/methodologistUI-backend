package tools.vitruv.methodologist.vsum.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex.EcoreFile;
import tools.vitruv.methodologist.vsum.helper.HelperDefinition.Parameter;

class ModelApiSourceGeneratorTest {

  private static final String SIMULINK = "tools.vitruv.methodologisttemplate.model.simulink.";
  private static final String OPS = "tools.vitruv.methodologisttemplate.model.ops.";
  private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore#//";

  /** Operations, a read-only and a reserved feature name, data types and Ecore built-ins. */
  private static final String OPS_ECORE =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <ecore:EPackage xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore" name="ops" nsURI="http://ops" nsPrefix="ops">
        <eClassifiers xsi:type="ecore:EClass" name="Counter">
          <eOperations name="increment" eType="ecore:EDataType %1$sEInt">
            <eParameters name="by" eType="ecore:EDataType %1$sEInt"/>
          </eOperations>
          <eOperations name="reset"/>
          <eOperations name="children" upperBound="-1" eType="#//Counter"/>
          <eStructuralFeatures xsi:type="ecore:EAttribute" name="id" changeable="false"
              eType="ecore:EDataType %1$sEString"/>
          <eStructuralFeatures xsi:type="ecore:EAttribute" name="class"
              eType="ecore:EDataType %1$sEString"/>
          <eStructuralFeatures xsi:type="ecore:EAttribute" name="values" upperBound="-1"
              eType="ecore:EDataType %1$sEInt"/>
          <eStructuralFeatures xsi:type="ecore:EAttribute" name="location" eType="#//Location"/>
          <eStructuralFeatures xsi:type="ecore:EReference" name="owner"
              eType="ecore:EClass %1$sEObject"/>
          <eStructuralFeatures xsi:type="ecore:EAttribute" name="type"
              eType="ecore:EDataType %1$sEJavaClass"/>
        </eClassifiers>
        <eClassifiers xsi:type="ecore:EDataType" name="Location" instanceClassName="java.net.URI"/>
      </ecore:EPackage>
      """
          .formatted(ECORE);

  @TempDir Path tempDir;

  private URLClassLoader loader;

  @BeforeEach
  void compileGeneratedApi() throws IOException {
    EcoreTypeIndex index =
        EcoreTypeIndex.of(
            List.of(
                resource("simulink.ecore"),
                resource("library.ecore"),
                new EcoreFile("ops.ecore", OPS_ECORE.getBytes(StandardCharsets.UTF_8))));
    Path stubs = EmfApiStubs.compile(tempDir.resolve("emf"));
    Path classes = tempDir.resolve("classes");

    List<String> errors =
        EmfApiStubs.compile(ModelApiSourceGenerator.generate(index), List.of(stubs), classes);

    assertThat(errors).as("generated API compiles").isEmpty();
    loader =
        new URLClassLoader(
            new URL[] {classes.toUri().toURL(), stubs.toUri().toURL()},
            getClass().getClassLoader());
  }

  @AfterEach
  void closeLoader() throws IOException {
    loader.close();
  }

  @Test
  void generate_createsAnInterfacePerClass_withEmfAccessors() throws Exception {
    Class<?> block = load(SIMULINK + "Block");
    Class<?> gain = load(SIMULINK + "Gain");

    assertThat(block.isInterface()).isTrue();
    assertThat(block.getInterfaces()).extracting(Class::getName).containsExactly(eObject());
    assertThat(gain.getInterfaces()).containsExactly(block);
    assertThat(block.getMethod("getName").getReturnType()).isEqualTo(String.class);
    assertThat(block.getMethod("isEnabled").getReturnType()).isEqualTo(boolean.class);
    assertThat(block.getMethod("setPriority", int.class)).isNotNull();
    assertThat(gain.getMethod("getFactor").getReturnType()).isEqualTo(double.class);
    assertThat(block.getMethod("getKind").getReturnType()).isEqualTo(load(SIMULINK + "BlockKind"));
  }

  @Test
  void generate_typesMultiValuedFeaturesAsELists_withoutSetters() throws Exception {
    Class<?> subSystem = load(SIMULINK + "SubSystem");

    Method getter = subSystem.getMethod("getSubBlocks");

    assertThat(getter.getReturnType().getName()).isEqualTo("org.eclipse.emf.common.util.EList");
    assertThat(((ParameterizedType) getter.getGenericReturnType()).getActualTypeArguments())
        .containsExactly(load(SIMULINK + "Block"));
    assertThat(methodNames(subSystem)).doesNotContain("setSubBlocks");
    assertThat(
            ((ParameterizedType)
                    load(OPS + "Counter").getMethod("getValues").getGenericReturnType())
                .getActualTypeArguments())
        .containsExactly(Integer.class);
  }

  @Test
  void generate_resolvesTypesAcrossFilesAndSubpackages() throws Exception {
    Class<?> book = load("tools.vitruv.methodologisttemplate.model.library.shelf.Book");
    Class<?> libraryBlock = load("tools.vitruv.methodologisttemplate.model.library.Block");

    assertThat(book.getInterfaces()).containsExactly(libraryBlock);
    assertThat(libraryBlock.getMethod("getModel").getReturnType())
        .isEqualTo(load(SIMULINK + "SimulinkModel"));
    assertThat(libraryBlock.getMethod("getPages").getReturnType()).isEqualTo(long.class);
  }

  @Test
  void generate_createsEmfEnums() throws Exception {
    Class<?> kind = load(SIMULINK + "BlockKind");

    assertThat(kind.isEnum()).isTrue();
    assertThat(kind.getEnumConstants())
        .extracting(Object::toString)
        .containsExactly("continuous", "discreteTime");
    assertThat(Arrays.stream(kind.getFields()).map(Field::getName))
        .contains("CONTINUOUS", "DISCRETE_TIME", "VALUES");
    assertThat(kind.getMethod("get", String.class).invoke(null, "discreteTime"))
        .isEqualTo(kind.getField("DISCRETE_TIME").get(null));
    assertThat(kind.getMethod("getByName", String.class)).isNotNull();
    assertThat(kind.getMethod("get", int.class)).isNotNull();
  }

  @Test
  void generate_createsFactoryAndPackagePerEPackage() throws Exception {
    Class<?> factory = load(SIMULINK + "SimulinkFactory");
    Class<?> pkg = load(SIMULINK + "SimulinkPackage");

    assertThat(factory.getField("eINSTANCE").getType()).isEqualTo(factory);
    assertThat(factory.getMethod("createGain").getReturnType()).isEqualTo(load(SIMULINK + "Gain"));
    assertThat(methodNames(factory)).doesNotContain("createBlock");
    assertThat(pkg.getField("eNS_URI").get(null)).isEqualTo("http://simulink");
    assertThat(pkg.getMethod("getGain").getReturnType().getName())
        .isEqualTo("org.eclipse.emf.ecore.EClass");
    assertThat(pkg.getMethod("getBlockKind").getReturnType().getName())
        .isEqualTo("org.eclipse.emf.ecore.EEnum");
    assertThat(pkg.getMethod("getGain_Factor").getReturnType().getName())
        .isEqualTo("org.eclipse.emf.ecore.EAttribute");
    assertThat(pkg.getMethod("getSubSystem_SubBlocks").getReturnType().getName())
        .isEqualTo("org.eclipse.emf.ecore.EReference");
    assertThat(load("tools.vitruv.methodologisttemplate.model.library.shelf.ShelfFactory"))
        .isNotNull();
  }

  @Test
  void generate_declaresOperations() throws Exception {
    Class<?> counter = load(OPS + "Counter");

    assertThat(counter.getMethod("increment", int.class).getReturnType()).isEqualTo(int.class);
    assertThat(counter.getMethod("reset").getReturnType()).isEqualTo(void.class);
    assertThat(counter.getMethod("children").getReturnType().getName())
        .isEqualTo("org.eclipse.emf.common.util.EList");
  }

  @Test
  void generate_mapsSpecialFeaturesLikeEmf() throws Exception {
    Class<?> counter = load(OPS + "Counter");

    assertThat(methodNames(counter)).contains("getId").doesNotContain("setId");
    assertThat(counter.getMethod("getClass_").getReturnType()).isEqualTo(String.class);
    assertThat(counter.getMethod("getLocation").getReturnType()).isEqualTo(java.net.URI.class);
    assertThat(counter.getMethod("getOwner").getReturnType().getName()).isEqualTo(eObject());
    assertThat(counter.getMethod("getType").getReturnType()).isEqualTo(Class.class);
    assertThat(load(OPS + "OpsPackage").getMethod("getCounter_Owner").getReturnType().getName())
        .isEqualTo("org.eclipse.emf.ecore.EReference");
  }

  @Test
  void generate_compilesTogetherWithTheHelperClass() throws IOException {
    EcoreTypeIndex index = EcoreTypeIndex.of(List.of(resource("simulink.ecore")));
    HelperDefinition first =
        new HelperDefinition(
            "first",
            null,
            List.of(new Parameter("model", "SimulinkModel")),
            "Block",
            List.of("simulink::*"),
            "model.contains->first()");
    String helpers = HelperClassGenerator.generate(List.of(first), index).source();
    Path stubs = EmfApiStubs.compile(tempDir.resolve("emf2"));
    Path classes = tempDir.resolve("classes2");
    assertThat(
            EmfApiStubs.compile(ModelApiSourceGenerator.generate(index), List.of(stubs), classes))
        .isEmpty();

    List<String> errors =
        EmfApiStubs.compile(
            Map.of(HelperClassGenerator.QUALIFIED_CLASS_NAME, helpers),
            List.of(stubs, classes),
            classes);

    assertThat(errors).as("helpers compile against the generated API").isEmpty();
  }

  private Class<?> load(String name) throws ClassNotFoundException {
    return loader.loadClass(name);
  }

  private static List<String> methodNames(Class<?> type) {
    return Arrays.stream(type.getMethods()).map(Method::getName).toList();
  }

  private static String eObject() {
    return "org.eclipse.emf.ecore.EObject";
  }

  private static EcoreFile resource(String name) throws IOException {
    try (InputStream in =
        ModelApiSourceGeneratorTest.class.getResourceAsStream("/helper/" + name)) {
      return new EcoreFile(name, in.readAllBytes());
    }
  }
}
