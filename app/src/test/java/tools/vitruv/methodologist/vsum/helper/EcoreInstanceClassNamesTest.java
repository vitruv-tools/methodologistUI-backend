package tools.vitruv.methodologist.vsum.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex.EcoreFile;

class EcoreInstanceClassNamesTest {

  private static final String MODEL = "tools.vitruv.methodologisttemplate.model.";

  @Test
  void add_namesTheGeneratedInterfacesAndEnums() throws Exception {
    Map<String, String> names =
        instanceClassNames(EcoreInstanceClassNames.add(read("simulink.ecore")));

    assertThat(names)
        .containsEntry("SimulinkModel", MODEL + "simulink.SimulinkModel")
        .containsEntry("Block", MODEL + "simulink.Block")
        .containsEntry("BlockKind", MODEL + "simulink.BlockKind");
  }

  @Test
  void add_usesTheJavaPackagesOfSubpackages() throws Exception {
    Map<String, String> names =
        instanceClassNames(EcoreInstanceClassNames.add(read("library.ecore")));

    assertThat(names)
        .containsEntry("Block", MODEL + "library.Block")
        .containsEntry("Book", MODEL + "library.shelf.Book");
  }

  @Test
  void add_keepsTheRestOfTheMetamodel() throws Exception {
    byte[] changed = EcoreInstanceClassNames.add(read("library.ecore"));

    EcoreTypeIndex index = EcoreTypeIndex.of(List.of(new EcoreFile("library.ecore", changed)));

    assertThat(index.problems()).isEmpty();
    assertThat(index.findClassifier(List.of("library", "shelf", "Shelf")))
        .get()
        .satisfies(
            shelf ->
                assertThat(((EcoreTypeIndex.ClassInfo) shelf).allFeatureNames())
                    .containsExactly("books", "model", "pages"));
  }

  @Test
  void add_keepsExistingInstanceClassNames_andLeavesDataTypesAlone() throws Exception {
    String ecore =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <ecore:EPackage xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore" name="p" nsURI="http://p">
          <eClassifiers xsi:type="ecore:EClass" name="Mapped" instanceClassName="java.util.Map"/>
          <eClassifiers xsi:type="ecore:EDataType" name="Date"/>
        </ecore:EPackage>
        """;

    Map<String, String> names =
        instanceClassNames(EcoreInstanceClassNames.add(ecore.getBytes(StandardCharsets.UTF_8)));

    assertThat(names).containsEntry("Mapped", "java.util.Map").containsEntry("Date", "");
  }

  @Test
  void add_rejectsFilesThatAreNoXml() {
    byte[] invalid = "not xml".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> EcoreInstanceClassNames.add(invalid)).isInstanceOf(IOException.class);
  }

  /** The instance class name of every classifier, by classifier name. */
  private static Map<String, String> instanceClassNames(byte[] ecore) throws Exception {
    NodeList classifiers =
        EcoreTypeIndex.newDocumentBuilder()
            .parse(new ByteArrayInputStream(ecore))
            .getElementsByTagName("eClassifiers");
    Map<String, String> names = new LinkedHashMap<>();
    for (int i = 0; i < classifiers.getLength(); i++) {
      Element classifier = (Element) classifiers.item(i);
      names.put(classifier.getAttribute("name"), classifier.getAttribute("instanceClassName"));
    }
    return names;
  }

  private static byte[] read(String name) throws IOException {
    try (InputStream in =
        EcoreInstanceClassNamesTest.class.getResourceAsStream("/helper/" + name)) {
      return in.readAllBytes();
    }
  }
}
