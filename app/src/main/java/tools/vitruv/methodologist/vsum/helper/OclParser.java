package tools.vitruv.methodologist.vsum.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.vitruv.methodologist.vsum.helper.OclAst.Binary;
import tools.vitruv.methodologist.vsum.helper.OclAst.BoolLit;
import tools.vitruv.methodologist.vsum.helper.OclAst.Call;
import tools.vitruv.methodologist.vsum.helper.OclAst.CollectionLit;
import tools.vitruv.methodologist.vsum.helper.OclAst.CollectionPart;
import tools.vitruv.methodologist.vsum.helper.OclAst.CollectionTypeRef;
import tools.vitruv.methodologist.vsum.helper.OclAst.Expr;
import tools.vitruv.methodologist.vsum.helper.OclAst.If;
import tools.vitruv.methodologist.vsum.helper.OclAst.IntLit;
import tools.vitruv.methodologist.vsum.helper.OclAst.IteratorCall;
import tools.vitruv.methodologist.vsum.helper.OclAst.Let;
import tools.vitruv.methodologist.vsum.helper.OclAst.Name;
import tools.vitruv.methodologist.vsum.helper.OclAst.NamedTypeRef;
import tools.vitruv.methodologist.vsum.helper.OclAst.NullLit;
import tools.vitruv.methodologist.vsum.helper.OclAst.Pos;
import tools.vitruv.methodologist.vsum.helper.OclAst.Property;
import tools.vitruv.methodologist.vsum.helper.OclAst.RealLit;
import tools.vitruv.methodologist.vsum.helper.OclAst.StringLit;
import tools.vitruv.methodologist.vsum.helper.OclAst.TypeRef;
import tools.vitruv.methodologist.vsum.helper.OclAst.Unary;
import tools.vitruv.methodologist.vsum.helper.OclAst.VarDecl;
import tools.vitruv.methodologist.vsum.helper.OclType.CollectionKind;

/**
 * Recursive-descent parser for the OCL expressions of helpers.
 *
 * <p>Collection operations may be written with {@code ->} (OCL) or {@code .} (VitruvOCL); qualified
 * names may use {@code ::} (VitruvOCL) or {@code !}.
 */
public final class OclParser {

  /** Operations that take an iterator body. */
  static final Set<String> ITERATORS =
      Set.of(
          "select",
          "reject",
          "collect",
          "collectNested",
          "forAll",
          "exists",
          "any",
          "one",
          "isUnique",
          "sortedBy",
          "closure",
          "iterate");

  private static final Set<String> RESERVED =
      Set.of(
          "and", "or", "xor", "not", "implies", "if", "then", "else", "endif", "let", "in", "true",
          "false", "null", "div", "mod");

  private enum Kind {
    IDENT,
    INT,
    REAL,
    STRING,
    SYMBOL,
    EOF
  }

  private record Token(Kind kind, String text, Pos pos) {
    boolean is(String symbolOrKeyword) {
      return (kind == Kind.SYMBOL || kind == Kind.IDENT) && text.equals(symbolOrKeyword);
    }

    String describe() {
      return kind == Kind.EOF ? "the end of the expression" : "'" + text + "'";
    }
  }

  private static final String[] SYMBOLS = {
    "->", "::", "..", "<>", "<=", ">=", "==", "!=", "=", "<", ">", "+", "-", "*", "/", "(", ")",
    "{", "}", ",", ".", "|", ":", ";", "!"
  };

  private final List<Token> tokens;
  private int index;

  private OclParser(String text) {
    this.tokens = tokenize(text == null ? "" : text);
  }

  /**
   * Parses an OCL expression. A trailing {@code ;} as written at the end of helpers is allowed.
   *
   * @param text the expression
   * @return the syntax tree
   * @throws HelperTranslationException if the text is not a valid expression
   */
  public static Expr parseExpression(String text) {
    OclParser parser = new OclParser(text);
    if (parser.peek().kind == Kind.EOF) {
      throw new HelperTranslationException("The OCL definition is empty.", parser.peek().pos);
    }
    Expr expr = parser.expression();
    parser.accept(";");
    parser.expectEnd();
    return expr;
  }

  /**
   * Parses a type expression such as {@code Integer} or {@code Sequence(simulink::Block)}.
   *
   * @param text the type
   * @return the syntax tree
   * @throws HelperTranslationException if the text is not a valid type
   */
  public static TypeRef parseType(String text) {
    OclParser parser = new OclParser(text);
    TypeRef type = parser.type();
    parser.expectEnd();
    return type;
  }

  // ── expressions ──────────────────────────────────────────────────────────

  private Expr expression() {
    return binaryLevel(0);
  }

  private static final List<Set<String>> BINARY_LEVELS =
      List.of(
          Set.of("implies"),
          Set.of("or"),
          Set.of("xor"),
          Set.of("and"),
          Set.of("=", "<>", "==", "!="),
          Set.of("<", ">", "<=", ">="),
          Set.of("+", "-"),
          Set.of("*", "/", "div", "mod"));

  private Expr binaryLevel(int level) {
    if (level == BINARY_LEVELS.size()) {
      return unary();
    }
    Expr left = binaryLevel(level + 1);
    while (peek().kind != Kind.STRING && BINARY_LEVELS.get(level).contains(peek().text)) {
      Token op = next();
      Expr right = binaryLevel(level + 1);
      left = new Binary(op.pos, op.text, left, right);
    }
    return left;
  }

  private Expr unary() {
    Token token = peek();
    if (token.is("not") || token.is("-")) {
      next();
      return new Unary(token.pos, token.text, unary());
    }
    return postfix(primary());
  }

  private Expr postfix(Expr source) {
    Expr current = source;
    while (true) {
      Token token = peek();
      if (token.is(".") || token.is("->")) {
        next();
        boolean arrow = token.is("->");
        Token name = expectIdentifier("an operation or property name");
        if (peek().is("(")) {
          current = callOrIterator(current, name, arrow);
        } else if (arrow) {
          throw error("Expected '(' after '->" + name.text + "'", peek());
        } else {
          current = new Property(name.pos, current, name.text);
        }
      } else {
        return current;
      }
    }
  }

  private Expr callOrIterator(Expr source, Token name, boolean arrow) {
    expect("(");
    if (ITERATORS.contains(name.text)) {
      return iteratorCall(source, name, arrow);
    }
    List<Expr> args = arguments();
    return new Call(name.pos, source, name.text, args, arrow);
  }

  private List<Expr> arguments() {
    List<Expr> args = new ArrayList<>();
    if (!peek().is(")")) {
      do {
        args.add(expression());
      } while (accept(","));
    }
    expect(")");
    return args;
  }

  private Expr iteratorCall(Expr source, Token name, boolean arrow) {
    if (name.text.equals("iterate")) {
      final VarDecl iterator = variable(false);
      expect(";");
      VarDecl accumulator = variable(true);
      expect("|");
      Expr body = expression();
      expect(")");
      return new IteratorCall(
          name.pos, source, name.text, List.of(iterator), accumulator, body, arrow);
    }
    List<VarDecl> iterators = tryIteratorVariables();
    Expr body = expression();
    expect(")");
    return new IteratorCall(name.pos, source, name.text, iterators, null, body, arrow);
  }

  /** Parses {@code a, b : T |} if present; otherwise rewinds and returns an empty list. */
  private List<VarDecl> tryIteratorVariables() {
    int start = index;
    List<VarDecl> iterators = new ArrayList<>();
    try {
      do {
        Token name = peek();
        if (name.kind != Kind.IDENT || RESERVED.contains(name.text)) {
          index = start;
          return List.of();
        }
        next();
        TypeRef type = accept(":") ? type() : null;
        iterators.add(new VarDecl(name.pos, name.text, type, null));
      } while (accept(","));
      if (accept("|")) {
        return iterators;
      }
    } catch (HelperTranslationException e) {
      // not a variable list; the body starts right after '('
    }
    index = start;
    return List.of();
  }

  private VarDecl variable(boolean withInit) {
    Token name = expectIdentifier("a variable name");
    TypeRef type = accept(":") ? type() : null;
    Expr init = null;
    if (withInit) {
      expect("=");
      init = expression();
    }
    return new VarDecl(name.pos, name.text, type, init);
  }

  private Expr primary() {
    Token token = next();
    switch (token.kind) {
      case INT:
        return new IntLit(token.pos, token.text);
      case REAL:
        return new RealLit(token.pos, token.text);
      case STRING:
        return new StringLit(token.pos, token.text);
      case IDENT:
        return identifierExpression(token);
      case SYMBOL:
        if (token.is("(")) {
          Expr inner = expression();
          expect(")");
          return inner;
        }
        throw error("Unexpected " + token.describe(), token);
      default:
        throw error("Unexpected end of the expression", token);
    }
  }

  private Expr identifierExpression(Token token) {
    switch (token.text) {
      case "true", "false":
        return new BoolLit(token.pos, Boolean.parseBoolean(token.text));
      case "null":
        return new NullLit(token.pos);
      case "if":
        return ifExpression(token);
      case "let":
        return letExpression(token);
      default:
        break;
    }
    if (RESERVED.contains(token.text)) {
      throw error("Unexpected '" + token.text + "'", token);
    }
    CollectionKind kind = CollectionKind.fromOclName(token.text);
    if (kind != null && peek().is("{")) {
      return collectionLiteral(token, kind);
    }
    List<String> segments = new ArrayList<>(List.of(token.text));
    while (peek().is("::") || (peek().is("!") && peekAt(1).kind == Kind.IDENT)) {
      next();
      segments.add(expectIdentifier("a name after '::'").text);
    }
    if (segments.size() == 1 && peek().is("(")) {
      next();
      return new Call(token.pos, null, token.text, arguments(), false);
    }
    return new Name(token.pos, List.copyOf(segments));
  }

  private Expr ifExpression(Token start) {
    final Expr condition = expression();
    expect("then");
    Expr thenExpr = expression();
    expect("else");
    Expr elseExpr = expression();
    expect("endif");
    return new If(start.pos, condition, thenExpr, elseExpr);
  }

  private Expr letExpression(Token start) {
    List<VarDecl> variables = new ArrayList<>();
    do {
      variables.add(variable(true));
    } while (accept(","));
    expect("in");
    return new Let(start.pos, variables, expression());
  }

  private Expr collectionLiteral(Token start, CollectionKind kind) {
    expect("{");
    List<CollectionPart> parts = new ArrayList<>();
    if (!peek().is("}")) {
      do {
        Expr first = expression();
        Expr last = accept("..") ? expression() : null;
        parts.add(new CollectionPart(first, last));
      } while (accept(","));
    }
    expect("}");
    return new CollectionLit(start.pos, kind, parts);
  }

  // ── types ────────────────────────────────────────────────────────────────

  private TypeRef type() {
    Token token = expectIdentifier("a type");
    CollectionKind kind = CollectionKind.fromOclName(token.text);
    if (kind != null && peek().is("(")) {
      next();
      TypeRef element = type();
      expect(")");
      return new CollectionTypeRef(token.pos, kind, element);
    }
    List<String> segments = new ArrayList<>(List.of(token.text));
    while (peek().is("::") || peek().is("!")) {
      next();
      segments.add(expectIdentifier("a type name").text);
    }
    return new NamedTypeRef(token.pos, List.copyOf(segments));
  }

  // ── token helpers ────────────────────────────────────────────────────────

  private Token peek() {
    return tokens.get(index);
  }

  private Token peekAt(int offset) {
    return tokens.get(Math.min(index + offset, tokens.size() - 1));
  }

  private Token next() {
    Token token = tokens.get(index);
    if (token.kind != Kind.EOF) {
      index++;
    }
    return token;
  }

  private boolean accept(String symbol) {
    if (peek().is(symbol)) {
      next();
      return true;
    }
    return false;
  }

  private void expect(String symbol) {
    if (!accept(symbol)) {
      throw error("Expected '" + symbol + "' but found " + peek().describe(), peek());
    }
  }

  private Token expectIdentifier(String what) {
    Token token = peek();
    if (token.kind != Kind.IDENT || RESERVED.contains(token.text)) {
      throw error("Expected " + what + " but found " + token.describe(), token);
    }
    return next();
  }

  private void expectEnd() {
    if (peek().kind != Kind.EOF) {
      throw error("Unexpected " + peek().describe(), peek());
    }
  }

  private static HelperTranslationException error(String message, Token token) {
    return new HelperTranslationException(message, token.pos);
  }

  // ── lexer ────────────────────────────────────────────────────────────────

  private static List<Token> tokenize(String text) {
    List<Token> result = new ArrayList<>();
    int i = 0;
    int line = 1;
    int lineStart = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      Pos pos = new Pos(line, i - lineStart + 1);
      if (c == '\n') {
        line++;
        lineStart = i + 1;
        i++;
      } else if (Character.isWhitespace(c)) {
        i++;
      } else if (text.startsWith("--", i)) {
        while (i < text.length() && text.charAt(i) != '\n') {
          i++;
        }
      } else if (text.startsWith("/*", i)) {
        int end = text.indexOf("*/", i + 2);
        if (end < 0) {
          throw new HelperTranslationException("Unterminated comment", pos);
        }
        for (int j = i; j < end; j++) {
          if (text.charAt(j) == '\n') {
            line++;
            lineStart = j + 1;
          }
        }
        i = end + 2;
      } else if (Character.isJavaIdentifierStart(c) && c != '$') {
        int start = i;
        while (i < text.length()
            && Character.isJavaIdentifierPart(text.charAt(i))
            && text.charAt(i) != '$') {
          i++;
        }
        result.add(new Token(Kind.IDENT, text.substring(start, i), pos));
      } else if (Character.isDigit(c)) {
        i = number(text, i, pos, result);
      } else if (c == '\'' || c == '"') {
        i = string(text, i, pos, result);
      } else {
        String symbol = symbolAt(text, i);
        if (symbol == null) {
          throw new HelperTranslationException("Unexpected character '" + c + "'", pos);
        }
        result.add(new Token(Kind.SYMBOL, symbol, pos));
        i += symbol.length();
      }
    }
    result.add(new Token(Kind.EOF, "", new Pos(line, text.length() - lineStart + 1)));
    return result;
  }

  private static int number(String text, int start, Pos pos, List<Token> result) {
    int i = start;
    while (i < text.length() && Character.isDigit(text.charAt(i))) {
      i++;
    }
    boolean real = false;
    if (i + 1 < text.length() && text.charAt(i) == '.' && Character.isDigit(text.charAt(i + 1))) {
      real = true;
      i++;
      while (i < text.length() && Character.isDigit(text.charAt(i))) {
        i++;
      }
    }
    if (i < text.length() && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
      int exponent = i + 1;
      if (exponent < text.length()
          && (text.charAt(exponent) == '+' || text.charAt(exponent) == '-')) {
        exponent++;
      }
      if (exponent < text.length() && Character.isDigit(text.charAt(exponent))) {
        real = true;
        i = exponent;
        while (i < text.length() && Character.isDigit(text.charAt(i))) {
          i++;
        }
      }
    }
    result.add(new Token(real ? Kind.REAL : Kind.INT, text.substring(start, i), pos));
    return i;
  }

  private static int string(String text, int start, Pos pos, List<Token> result) {
    char quote = text.charAt(start);
    StringBuilder value = new StringBuilder();
    int i = start + 1;
    while (i < text.length() && text.charAt(i) != quote) {
      char c = text.charAt(i);
      if (c == '\n') {
        break;
      }
      if (c == '\\' && i + 1 < text.length()) {
        char escaped = text.charAt(i + 1);
        value.append(
            switch (escaped) {
              case 'n' -> '\n';
              case 't' -> '\t';
              case 'r' -> '\r';
              default -> escaped;
            });
        i += 2;
      } else {
        value.append(c);
        i++;
      }
    }
    if (i >= text.length() || text.charAt(i) != quote) {
      throw new HelperTranslationException("Unterminated string literal", pos);
    }
    result.add(new Token(Kind.STRING, value.toString(), pos));
    return i + 1;
  }

  private static String symbolAt(String text, int i) {
    for (String symbol : SYMBOLS) {
      if (text.startsWith(symbol, i)) {
        return symbol;
      }
    }
    return null;
  }
}
