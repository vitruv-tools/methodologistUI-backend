package tools.vitruv.methodologist.vsum.helper;

import java.util.List;
import tools.vitruv.methodologist.vsum.helper.OclType.CollectionKind;

/** Syntax tree of the OCL expressions and type expressions used in helpers. */
public final class OclAst {

  private OclAst() {}

  /** A 1-based source position. */
  public record Pos(int line, int column) {}

  /** An expression. */
  public sealed interface Expr
      permits IntLit,
          RealLit,
          StringLit,
          BoolLit,
          NullLit,
          Name,
          Property,
          Call,
          IteratorCall,
          Unary,
          Binary,
          If,
          Let,
          CollectionLit {
    /** Where the expression starts in the OCL source. */
    Pos pos();
  }

  /** An Integer literal. */
  public record IntLit(Pos pos, String text) implements Expr {}

  /** A Real literal. */
  public record RealLit(Pos pos, String text) implements Expr {}

  /** A String literal. */
  public record StringLit(Pos pos, String value) implements Expr {}

  /** {@code true} or {@code false}. */
  public record BoolLit(Pos pos, boolean value) implements Expr {}

  /** {@code null}. */
  public record NullLit(Pos pos) implements Expr {}

  /**
   * A possibly qualified name: a variable, a type ({@code mm::Class}) or an enum literal ({@code
   * mm::Enum::LITERAL}).
   */
  public record Name(Pos pos, List<String> segments) implements Expr {
    /** Whether the name has a single segment. */
    public boolean isSimple() {
      return segments.size() == 1;
    }

    /** The name as written, with {@code ::} separators. */
    public String text() {
      return String.join("::", segments);
    }
  }

  /** {@code source.name} or {@code source->name} without parentheses. */
  public record Property(Pos pos, Expr source, String name) implements Expr {}

  /**
   * {@code source.name(args)}, {@code source->name(args)} or, with a {@code null} source, a call of
   * another helper.
   */
  public record Call(Pos pos, Expr source, String name, List<Expr> args, boolean arrow)
      implements Expr {}

  /** {@code source->name(v | body)} and {@code source->iterate(v; acc : T = init | body)}. */
  public record IteratorCall(
      Pos pos,
      Expr source,
      String name,
      List<VarDecl> iterators,
      VarDecl accumulator,
      Expr body,
      boolean arrow)
      implements Expr {
    /** Whether the iterator variable was omitted, e.g. {@code ->select(isActive)}. */
    public boolean implicitIterator() {
      return iterators.isEmpty();
    }
  }

  /** {@code not e} or {@code -e}. */
  public record Unary(Pos pos, String op, Expr operand) implements Expr {}

  /** An infix operation. */
  public record Binary(Pos pos, String op, Expr left, Expr right) implements Expr {}

  /** {@code if c then a else b endif}. */
  public record If(Pos pos, Expr condition, Expr thenExpr, Expr elseExpr) implements Expr {}

  /** {@code let x : T = e, ... in body}. */
  public record Let(Pos pos, List<VarDecl> variables, Expr body) implements Expr {}

  /** {@code Sequence{a, b, 1..n}}. */
  public record CollectionLit(Pos pos, CollectionKind kind, List<CollectionPart> parts)
      implements Expr {}

  /** An item or range of a collection literal; {@code last} is {@code null} for single items. */
  public record CollectionPart(Expr first, Expr last) {}

  /** A variable declaration; {@code type} and {@code init} may be {@code null}. */
  public record VarDecl(Pos pos, String name, TypeRef type, Expr init) {}

  /** A type expression. */
  public sealed interface TypeRef permits NamedTypeRef, CollectionTypeRef {
    /** Where the type starts in the OCL source. */
    Pos pos();
  }

  /** A primitive or metamodel type, e.g. {@code Integer} or {@code simulink::Block}. */
  public record NamedTypeRef(Pos pos, List<String> segments) implements TypeRef {
    /** The name as written, with {@code ::} separators. */
    public String text() {
      return String.join("::", segments);
    }
  }

  /** A collection type, e.g. {@code Sequence(simulink::Block)}. */
  public record CollectionTypeRef(Pos pos, CollectionKind kind, TypeRef element)
      implements TypeRef {}
}
