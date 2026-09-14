package tools.vitruv.methodologist.vsum.reaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ReactionParserUtil}. */
class ReactionParserUtilTest {

  @Test
  void parse_extractsReactionNameAliasesAndUris() {
    String content =
        """
        import "http://pcm" as pcm
        import "http://uml" as uml

        reactions: createCorrespondingRoot
        in reaction to changes in pcm
        execute actions in uml
        """;

    ReactionParserUtil.ReactionFileInfo info = ReactionParserUtil.parse(content);

    assertThat(info.reactionName()).isEqualTo("createCorrespondingRoot");
    assertThat(info.modelAlias1()).isEqualTo("pcm");
    assertThat(info.modelAlias2()).isEqualTo("uml");
    assertThat(info.modelUri1()).isEqualTo("http://pcm");
    assertThat(info.modelUri2()).isEqualTo("http://uml");
  }
}
