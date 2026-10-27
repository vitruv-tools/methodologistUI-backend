package tools.vitruv.methodologist.vsum.utilitymethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Binary;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Call;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.CollectionTypeRef;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.IteratorCall;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Name;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.NamedTypeRef;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Pos;
import tools.vitruv.methodologist.vsum.utilitymethod.OclType.CollectionKind;

class OclParserTest {

  @Test
  void parseExpression_respectsOperatorPrecedence() {
    Binary or = (Binary) OclParser.parseExpression("a and b or not c = d + 1 * 2");

    assertThat(or.op()).isEqualTo("or");
    assertThat(((Binary) or.left()).op()).isEqualTo("and");
    Binary equals = (Binary) or.right();
    assertThat(equals.op()).isEqualTo("=");
    assertThat(((Binary) equals.right()).op()).isEqualTo("+");
  }

  @Test
  void parseExpression_distinguishesExplicitAndImplicitIterators() {
    IteratorCall explicit =
        (IteratorCall) OclParser.parseExpression("blocks->select(b : Block | b.enabled)");
    IteratorCall implicit = (IteratorCall) OclParser.parseExpression("blocks->select(enabled)");

    assertThat(explicit.iterators()).extracting(OclAst.VarDecl::name).containsExactly("b");
    assertThat(explicit.iterators().get(0).type()).isInstanceOf(NamedTypeRef.class);
    assertThat(implicit.implicitIterator()).isTrue();
  }

  @Test
  void parseExpression_acceptsDotCollectionCalls_bangQualifiedNames_andTrailingSemicolon() {
    IteratorCall dotted =
        (IteratorCall)
            OclParser.parseExpression("model.contains.select(b | b.oclIsKindOf(mm!SubSystem));");

    assertThat(dotted.arrow()).isFalse();
    Call kindOf = (Call) dotted.body();
    assertThat(((Name) kindOf.args().get(0)).segments()).containsExactly("mm", "SubSystem");
  }

  @Test
  void parseExpression_parsesIterateWithAccumulator() {
    IteratorCall iterate =
        (IteratorCall) OclParser.parseExpression("xs->iterate(x; sum : Integer = 0 | sum + x)");

    assertThat(iterate.iterators()).extracting(OclAst.VarDecl::name).containsExactly("x");
    assertThat(iterate.accumulator().name()).isEqualTo("sum");
  }

  @Test
  void parseExpression_reportsPositionOfUnexpectedToken() {
    assertThatThrownBy(() -> OclParser.parseExpression("a +\n  )"))
        .isInstanceOf(UtilityMethodTranslationException.class)
        .hasMessageContaining("Unexpected ')'")
        .satisfies(
            e ->
                assertThat(((UtilityMethodTranslationException) e).pos()).isEqualTo(new Pos(2, 3)));
    assertThatThrownBy(() -> OclParser.parseExpression("   ")).hasMessageContaining("empty");
    assertThatThrownBy(() -> OclParser.parseExpression("'open"))
        .hasMessageContaining("Unterminated string");
  }

  @Test
  void parseType_parsesNestedCollectionTypes() {
    CollectionTypeRef type =
        (CollectionTypeRef) OclParser.parseType("Sequence(Set(simulink::Block))");

    assertThat(type.kind()).isEqualTo(CollectionKind.SEQUENCE);
    CollectionTypeRef inner = (CollectionTypeRef) type.element();
    assertThat(inner.kind()).isEqualTo(CollectionKind.SET);
    assertThat(((NamedTypeRef) inner.element()).segments()).isEqualTo(List.of("simulink", "Block"));
  }
}
