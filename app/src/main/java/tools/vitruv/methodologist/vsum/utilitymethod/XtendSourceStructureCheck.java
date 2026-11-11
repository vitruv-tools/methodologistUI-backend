package tools.vitruv.methodologist.vsum.utilitymethod;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtend.core.xtend.XtendClass;
import org.eclipse.xtend.core.xtend.XtendFile;
import org.eclipse.xtend.core.xtend.XtendFunction;
import org.eclipse.xtend.core.xtend.XtendMember;
import org.eclipse.xtend.core.xtend.XtendParameter;
import org.eclipse.xtend.core.xtend.XtendTypeDeclaration;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodClassGenerator.BodyRange;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodClassGenerator.GeneratedXtend;
import tools.vitruv.methodologist.vsum.utilitymethod.XtendUtilityCompiler.XtendIssue;

/**
 * Checks that the parsed utility class has exactly the shape {@link UtilityMethodClassGenerator}
 * generated, so that a body cannot end its method and declare members or annotations of its own.
 * Annotations matter because active annotations run code while compiling.
 */
final class XtendSourceStructureCheck {

  /** Message of the problem reported for a body that is not a single method body. */
  static final String NOT_A_SINGLE_BODY = "The body must be a single method body.";

  private XtendSourceStructureCheck() {}

  /**
   * Checks the parsed source.
   *
   * @param file the parsed source
   * @param generated the source as generated
   * @return the problems found, empty if the source has the generated shape
   */
  static List<XtendIssue> check(XtendFile file, GeneratedXtend generated) {
    List<BodyRange> bodies = generated.bodies();
    List<XtendTypeDeclaration> types = file.getXtendTypes();
    if (types.size() != 1
        || !(types.get(0) instanceof XtendClass utilityClass)
        || !UtilityMethodClassGenerator.CLASS_NAME.equals(utilityClass.getName())
        || !utilityClass.getAnnotations().isEmpty()) {
      return problemsForAll(bodies);
    }
    List<XtendMember> members = utilityClass.getMembers();
    if (members.size() != bodies.size()) {
      return problemsForAll(bodies);
    }
    List<XtendIssue> problems = new ArrayList<>();
    for (int i = 0; i < bodies.size(); i++) {
      if (!hasGeneratedShape(members.get(i), bodies.get(i))) {
        problems.add(problem(bodies.get(i).utilityMethodName()));
      }
    }
    return problems;
  }

  private static boolean hasGeneratedShape(XtendMember member, BodyRange body) {
    if (!(member instanceof XtendFunction function)
        || !body.utilityMethodName().equals(function.getName())
        || !function.isStatic()
        || !function.getAnnotations().isEmpty()
        || function.getExpression() == null) {
      return false;
    }
    for (XtendParameter parameter : function.getParameters()) {
      if (!parameter.getAnnotations().isEmpty()) {
        return false;
      }
    }
    ICompositeNode header = node(function);
    ICompositeNode expression = node(function.getExpression());
    return header != null
        && expression != null
        && header.getStartLine() == body.headerLine()
        // The block expression spans the braces: from the header line to the line after the body.
        && expression.getStartLine() == body.headerLine()
        && expression.getEndLine() == body.lastBodyLine() + 1;
  }

  private static ICompositeNode node(EObject object) {
    return NodeModelUtils.findActualNodeFor(object);
  }

  private static List<XtendIssue> problemsForAll(List<BodyRange> bodies) {
    return bodies.stream().map(body -> problem(body.utilityMethodName())).toList();
  }

  private static XtendIssue problem(String utilityMethodName) {
    return new XtendIssue(
        UtilityMethodClassGenerator.ERROR, NOT_A_SINGLE_BODY, null, null, utilityMethodName);
  }
}
