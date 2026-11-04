package tools.vitruv.methodologist.vsum.utilitymethod;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JavaMethodExtractorTest {

  private static final String SOURCE =
      """
      package p;

      public final class Utility {
        /**
         * Says hello.
         */
        public static String hello(final String name) {
          final String s = "} { \\" }";
          final char c = '}';
          // } not a brace
          /* } { */ if (true) {
            return s + c + name;
          }
          return "";
        }

        public static int answer() {
          return 42;
        }
      }
      """;

  @Test
  void extract_returnsTheMethodWithJavadoc_ignoringBracesInLiteralsAndComments() {
    String method = JavaMethodExtractor.extract(SOURCE, "hello");

    assertThat(method)
        .startsWith("/**\n * Says hello.\n */\npublic static String hello(")
        .endsWith("  return \"\";\n}\n")
        .doesNotContain("answer");
  }

  @Test
  void extract_returnsAMethodWithoutJavadoc() {
    assertThat(JavaMethodExtractor.extract(SOURCE, "answer"))
        .isEqualTo("public static int answer() {\n  return 42;\n}\n");
  }

  @Test
  void extract_returnsNull_forAnUnknownMethod() {
    assertThat(JavaMethodExtractor.extract(SOURCE, "missing")).isNull();
  }
}
