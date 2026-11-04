package tools.vitruv.methodologist.vsum.utilitymethod;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Adds to an {@code .ecore} file the Java names of its EClasses and EEnums, as {@code
 * instanceClassName} attributes.
 *
 * <p>The reactions language derives the Java type of a metaclass from its instance class name,
 * which is only set for metamodels whose generated code is registered. The reactions editor's
 * language server loads the plain {@code .ecore} files instead, so without these attributes every
 * metaclass has an unknown type there. The names are the ones {@link EcoreTypeIndex} and {@link
 * ModelApiSourceGenerator} use.
 */
public final class EcoreInstanceClassNames {

  private static final String INSTANCE_CLASS_NAME = "instanceClassName";

  private EcoreInstanceClassNames() {}

  /**
   * Returns the file with an instance class name on every EClass and EEnum that has none.
   *
   * @param ecore the XMI content of the {@code .ecore} file
   * @return the changed XMI content
   * @throws IOException if the file cannot be parsed or written
   */
  public static byte[] add(byte[] ecore) throws IOException {
    try {
      Document document =
          EcoreTypeIndex.newDocumentBuilder().parse(new ByteArrayInputStream(ecore));
      Element root = document.getDocumentElement();
      List<Element> packages =
          "EPackage".equals(root.getLocalName())
              ? List.of(root)
              : EcoreTypeIndex.childElements(root, "EPackage");
      for (Element pkg : packages) {
        addToPackage(pkg, new ArrayList<>());
      }
      return serialize(document);
    } catch (ParserConfigurationException | SAXException | TransformerException e) {
      throw new IOException(
          "Could not add instance class names to metamodel: " + e.getMessage(), e);
    }
  }

  private static void addToPackage(Element pkg, List<String> parentPath) {
    List<String> path = new ArrayList<>(parentPath);
    path.add(pkg.getAttribute("name"));
    String javaPackage = EcoreTypeIndex.javaPackageOf(path);
    for (Element classifier : EcoreTypeIndex.childElements(pkg, "eClassifiers")) {
      String kind = EcoreTypeIndex.localType(classifier);
      boolean generated = "EClass".equals(kind) || "EEnum".equals(kind);
      if (generated && classifier.getAttribute(INSTANCE_CLASS_NAME).isBlank()) {
        classifier.setAttribute(
            INSTANCE_CLASS_NAME,
            javaPackage + "." + JavaNames.safeName(classifier.getAttribute("name")));
      }
    }
    for (Element subpackage : EcoreTypeIndex.childElements(pkg, "eSubpackages")) {
      addToPackage(subpackage, path);
    }
  }

  private static byte[] serialize(Document document) throws TransformerException {
    TransformerFactory factory = TransformerFactory.newInstance();
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    factory.newTransformer().transform(new DOMSource(document), new StreamResult(out));
    return out.toByteArray();
  }
}
