package tools.vitruv.methodologist.vsum.lowcode.reactions.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.exception.LowCodeTemplateException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.controller.responsedto.FileStorageResponse;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.service.FileStorageService;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.CompositeReactionsRequest;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.CreateCorrespondingRootOnInsertRootRequest;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.ExampleRequest;

/** Unit tests for {@link LowCodeReactionService} template rendering and persistence. */
@ExtendWith(MockitoExtension.class)
class LowCodeReactionServiceTest {

  @Mock FileStorageService fileStorageService;

  private LowCodeReactionService service;

  @BeforeEach
  void setup() {
    service = new LowCodeReactionService(fileStorageService);
  }

  @Test
  void applyTemplate_rendersCreateCorrespondingRootTemplate() {
    CreateCorrespondingRootOnInsertRootRequest request = createCorrespondingRootRequest();

    String rendered = service.applyTemplate(request);

    assertThat(rendered).contains("import \"http://pcm\" as pcm");
    assertThat(rendered).contains("import \"http://uml\" as uml");
    assertThat(rendered).contains("reactions: createCorrespondingRoot");
    assertThat(rendered).contains("after element pcm::Component inserted as root");
    assertThat(rendered).contains("call createAndRegisterClass(newValue)");
  }

  @Test
  void applyTemplate_snapshotCreateCorrespondingRoot() {
    String rendered = service.applyTemplate(createCorrespondingRootRequest());

    assertThat(rendered.stripTrailing())
        .isEqualToNormalizingNewlines(
            """
            import java.io.File;

            import "http://pcm" as pcm
            import "http://uml" as uml

            reactions: createCorrespondingRoot
            in reaction to changes in pcm
            execute actions in uml

            reaction RootObjectInsertedInpcm {
                after element pcm::Component inserted as root
                call createAndRegisterClass(newValue)
            }

            routine createAndRegisterClass(
                pcm::Component component
            ) {
                match {
                    require absence of uml::Class
                    corresponding to component
                }

                create {
                    val umlRoot =
                    new uml::Class
                }

                update {
                    addCorrespondenceBetween(component, umlRoot)
                }
            }
            """
                .stripTrailing());
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

  @Test
  void generateAndSaveReaction_storesNewFile_whenExistingFileMissing() {
    CreateCorrespondingRootOnInsertRootRequest request = createCorrespondingRootRequest();
    FileStorageResponse stored = FileStorageResponse.builder().id(42L).build();
    FileStorage file = new FileStorage();
    file.setId(42L);
    when(fileStorageService.storeFile(
            eq("u@ex.com"),
            any(byte[].class),
            eq("create_corresponding_root_on_insert_root.reactions"),
            eq("text/plain"),
            eq(FileEnumType.REACTION)))
        .thenReturn(stored);
    when(fileStorageService.getFile(42L)).thenReturn(file);

    FileStorage result = service.generateAndSaveReaction("u@ex.com", request, null);

    assertThat(result).isSameAs(file);
    verify(fileStorageService)
        .storeFile(
            eq("u@ex.com"),
            any(byte[].class),
            eq("create_corresponding_root_on_insert_root.reactions"),
            eq("text/plain"),
            eq(FileEnumType.REACTION));
  }

  @Test
  void generateAndSaveReaction_updatesExistingFile() {
    CreateCorrespondingRootOnInsertRootRequest request = createCorrespondingRootRequest();
    FileStorage existing = new FileStorage();
    existing.setId(13L);
    existing.setFilename("existing.reactions");
    existing.setContentType("text/plain");
    FileStorageResponse updated = FileStorageResponse.builder().id(13L).build();
    when(fileStorageService.updateFile(
            eq("u@ex.com"),
            eq(13L),
            any(byte[].class),
            eq("existing.reactions"),
            eq("text/plain")))
        .thenReturn(updated);
    when(fileStorageService.getFile(13L)).thenReturn(existing);

    FileStorage result = service.generateAndSaveReaction("u@ex.com", request, existing);

    assertThat(result).isSameAs(existing);
    verify(fileStorageService)
        .updateFile(
            eq("u@ex.com"),
            eq(13L),
            any(byte[].class),
            eq("existing.reactions"),
            eq("text/plain"));
  }

  private CreateCorrespondingRootOnInsertRootRequest createCorrespondingRootRequest() {
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
    return request;
  }
}
