package tools.vitruv.methodologist.vsum.lowcode.reactions.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.exception.LowCodeTemplateException;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.CompositeReactionsRequest;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.CreateCorrespondingRootOnInsertRootRequest;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.ExampleRequest;

/** Unit tests for {@link LowCodeReactionService} template rendering. */
class LowCodeReactionServiceTest {

  private final LowCodeReactionService service = new LowCodeReactionService(null);

  @Test
  void applyTemplate_rendersCreateCorrespondingRootTemplate() {
    CreateCorrespondingRootOnInsertRootRequest request =
        new CreateCorrespondingRootOnInsertRootRequest();
    request.setModel1Uri("http://pcm");
    request.setModel2Uri("http://uml");
    request.setModel1Alias("pcm");
    request.setModel2Alias("uml");
    request.setReactionName("createCorrespondingRoot");
    request.setModel1RootType("Component");
    request.setModel2RootType("Class");
    request.setModel1RootVar("component");

    String rendered = service.applyTemplate(request);

    assertThat(rendered).contains("import \"http://pcm\" as pcm");
    assertThat(rendered).contains("import \"http://uml\" as uml");
    assertThat(rendered).contains("reactions: createCorrespondingRoot");
    assertThat(rendered).contains("after element pcm::Component inserted as root");
    assertThat(rendered).contains("call createAndRegisterClass(newValue)");
  }

  @Test
  void applyTemplate_rendersCompositeImports() {
    CompositeReactionsRequest request = new CompositeReactionsRequest();
    request.setModel1Uri("http://pcm");
    request.setModel2Uri("http://uml");
    request.setModel1Alias("pcm");
    request.setModel2Alias("uml");
    request.setReactionName("compositeReaction");
    request.setImports(new String[] {"firstReaction", "secondReaction"});

    String rendered = service.applyTemplate(request);

    assertThat(rendered).contains("import firstReaction");
    assertThat(rendered).contains("import secondReaction");
    assertThat(rendered).contains("reactions: compositeReaction");
  }

  @Test
  void applyTemplate_unknownTemplate_throwsLowCodeTemplateException() {
    ExampleRequest request = new ExampleRequest();
    request.setStringField("x");

    assertThatThrownBy(() -> service.applyTemplate(request))
        .isInstanceOf(LowCodeTemplateException.class);
  }
}
