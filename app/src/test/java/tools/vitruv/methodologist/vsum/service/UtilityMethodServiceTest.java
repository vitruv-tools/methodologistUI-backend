package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UtilityMethodValidationException;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.controller.dto.request.UtilityMethodParameterDto;
import tools.vitruv.methodologist.vsum.controller.dto.request.UtilityMethodRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodClassResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodPreviewResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodTypeResponse;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.UtilityMethod;
import tools.vitruv.methodologist.vsum.model.UtilityMethodParameter;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.UtilityMethodRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodClassGenerator;

@ExtendWith(MockitoExtension.class)
class UtilityMethodServiceTest {

  private static final String EMAIL = "test@example.com";
  private static final long VSUM_ID = 10L;

  @InjectMocks UtilityMethodService service;
  @Mock UtilityMethodRepository utilityMethodRepository;
  @Mock VsumUserRepository vsumUserRepository;
  @Mock VsumMetaModelRepository vsumMetaModelRepository;

  private Vsum vsum;
  private FileStorage simulinkEcore;
  private List<UtilityMethod> stored;

  @BeforeEach
  void setUp() throws IOException {
    vsum = Vsum.builder().id(VSUM_ID).name("TestVsum").build();
    try (InputStream in = getClass().getResourceAsStream("/utilitymethod/simulink.ecore")) {
      simulinkEcore =
          FileStorage.builder().id(1L).filename("simulink.ecore").data(in.readAllBytes()).build();
    }
    stored = new ArrayList<>();
    lenient()
        .when(
            vsumUserRepository
                .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                    VSUM_ID, EMAIL))
        .thenReturn(Optional.of(VsumUser.builder().vsum(vsum).build()));
    lenient()
        .when(vsumMetaModelRepository.findByVsumId(VSUM_ID))
        .thenReturn(
            List.of(
                VsumMetaModel.builder()
                    .vsum(vsum)
                    .metaModel(MetaModel.builder().ecoreFile(simulinkEcore).build())
                    .build()));
    lenient().when(utilityMethodRepository.findByVsumIdOrderByNameAsc(VSUM_ID)).thenReturn(stored);
    lenient()
        .when(utilityMethodRepository.save(any(UtilityMethod.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  // ── create ───────────────────────────────────────────────────────────────

  @Test
  void create_savesUtilityMethodAndReturnsGeneratedJava() {
    UtilityMethodResponse response = service.create(EMAIL, VSUM_ID, subSystemsRequest());

    ArgumentCaptor<UtilityMethod> saved = ArgumentCaptor.forClass(UtilityMethod.class);
    verify(utilityMethodRepository).save(saved.capture());
    assertThat(saved.getValue().getVsum()).isSameAs(vsum);
    assertThat(saved.getValue().getParameters())
        .extracting(UtilityMethodParameter::getName, UtilityMethodParameter::getType)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("model", "simulink::SimulinkModel"));
    assertThat(saved.getValue().getImports()).containsExactly("simulink::*");
    assertThat(response.problems()).isEmpty();
    assertThat(response.javaMethod())
        .contains("public static List<SubSystem> getAllSubSystems(SimulinkModel model)");
    assertThat(response.oclDefinition())
        .startsWith(
            "def : getAllSubSystems(model : simulink::SimulinkModel) :"
                + " Sequence(simulink::SubSystem) =");
  }

  @Test
  void create_rejectsNameUsedByAnotherUtilityMethodIgnoringCase() {
    stored.add(utilityMethod(1L, "GetAllSubSystems", "Integer", "1"));

    assertThatThrownBy(() -> service.create(EMAIL, VSUM_ID, subSystemsRequest()))
        .isInstanceOf(UtilityMethodValidationException.class)
        .hasMessageContaining("already exists");
    verify(utilityMethodRepository, never()).save(any());
  }

  @Test
  void create_rejectsUtilityMethodThatDoesNotTranslate_withPosition() {
    UtilityMethodRequest request =
        new UtilityMethodRequest(
            "broken",
            null,
            "Integer",
            List.of(new UtilityMethodParameterDto("model", "simulink::SimulinkModel")),
            List.of(),
            "model.contians->size()");

    assertThatThrownBy(() -> service.create(EMAIL, VSUM_ID, request))
        .isInstanceOf(UtilityMethodValidationException.class)
        .hasMessageContaining("Line 1, column 7")
        .hasMessageContaining("no feature 'contians'");
    verify(utilityMethodRepository, never()).save(any());
  }

  @Test
  void create_throwsAccessDenied_whenCallerIsNoMember() {
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                VSUM_ID, "other@example.com"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create("other@example.com", VSUM_ID, subSystemsRequest()))
        .isInstanceOf(AccessDeniedException.class);
  }

  // ── update ───────────────────────────────────────────────────────────────

  @Test
  void update_rejectsRenameThatBreaksCallers() {
    UtilityMethod callee = utilityMethod(1L, "base", "Integer", "1");
    stored.add(callee);
    stored.add(utilityMethod(2L, "caller", "Integer", "base() + 1"));
    when(utilityMethodRepository.findByIdAndVsumId(1L, VSUM_ID)).thenReturn(Optional.of(callee));
    UtilityMethodRequest rename =
        new UtilityMethodRequest("renamed", null, "Integer", List.of(), List.of(), "1");

    assertThatThrownBy(() -> service.update(EMAIL, VSUM_ID, 1L, rename))
        .isInstanceOf(UtilityMethodValidationException.class)
        .hasMessageContaining("caller");
    assertThat(callee.getName()).isEqualTo("base");
  }

  @Test
  void update_appliesChanges_whenValid() {
    UtilityMethod existing = utilityMethod(1L, "answer", "Integer", "1");
    stored.add(existing);
    when(utilityMethodRepository.findByIdAndVsumId(1L, VSUM_ID)).thenReturn(Optional.of(existing));
    UtilityMethodRequest request =
        new UtilityMethodRequest("answer", "The answer.", "Integer", List.of(), List.of(), " 42 ");

    UtilityMethodResponse response = service.update(EMAIL, VSUM_ID, 1L, request);

    assertThat(existing.getBody()).isEqualTo("42");
    assertThat(existing.getDescription()).isEqualTo("The answer.");
    assertThat(response.javaMethod()).contains("return 42;");
  }

  @Test
  void update_throwsNotFound_forUtilityMethodOfAnotherVsum() {
    when(utilityMethodRepository.findByIdAndVsumId(99L, VSUM_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(EMAIL, VSUM_ID, 99L, subSystemsRequest()))
        .isInstanceOf(NotFoundException.class);
  }

  // ── delete ───────────────────────────────────────────────────────────────

  @Test
  void delete_removesUnusedUtilityMethod() {
    UtilityMethod utilityMethod = utilityMethod(1L, "answer", "Integer", "42");
    stored.add(utilityMethod);
    when(utilityMethodRepository.findByIdAndVsumId(1L, VSUM_ID))
        .thenReturn(Optional.of(utilityMethod));

    service.delete(EMAIL, VSUM_ID, 1L);

    verify(utilityMethodRepository).delete(utilityMethod);
  }

  @Test
  void delete_rejectsUtilityMethodUsedByAnotherUtilityMethod() {
    UtilityMethod callee = utilityMethod(1L, "base", "Integer", "1");
    stored.add(callee);
    stored.add(utilityMethod(2L, "caller", "Integer", "thisModule.base() + 1"));
    when(utilityMethodRepository.findByIdAndVsumId(1L, VSUM_ID)).thenReturn(Optional.of(callee));

    assertThatThrownBy(() -> service.delete(EMAIL, VSUM_ID, 1L))
        .isInstanceOf(UtilityMethodValidationException.class)
        .hasMessageContaining("used by: caller");
    verify(utilityMethodRepository, never()).delete(any(UtilityMethod.class));
  }

  // ── preview, name check, class, types ────────────────────────────────────

  @Test
  void preview_returnsProblemsInsteadOfThrowing_andIgnoresTheEditedMethodsName() {
    stored.add(utilityMethod(1L, "getAllSubSystems", "Integer", "1"));

    UtilityMethodPreviewResponse preview =
        service.preview(EMAIL, VSUM_ID, null, subSystemsRequest());

    assertThat(preview.problems())
        .extracting(p -> p.message())
        .containsExactly(
            "A utility method named 'getAllSubSystems' already exists in this project.");
    assertThat(preview.javaMethod()).isNull();
    assertThat(preview.oclDefinition()).contains("closure");

    UtilityMethodPreviewResponse ofEdited =
        service.preview(EMAIL, VSUM_ID, 1L, subSystemsRequest());

    assertThat(ofEdited.problems()).as("the edited method's own name is free").isEmpty();
    assertThat(ofEdited.javaMethod()).contains("oclClosure");
  }

  @Test
  void checkName_reportsInvalidAndTakenNames() {
    stored.add(utilityMethod(1L, "taken", "Integer", "1"));

    assertThat(service.checkName(EMAIL, VSUM_ID, "free", null).available()).isTrue();
    assertThat(service.checkName(EMAIL, VSUM_ID, "Taken", null).available()).isFalse();
    assertThat(service.checkName(EMAIL, VSUM_ID, "taken", 1L).available()).isTrue();
    assertThat(service.checkName(EMAIL, VSUM_ID, "not valid", null).message())
        .contains("not a valid Java method name");
  }

  @Test
  void generatedClass_leavesOutBrokenUtilityMethodsAndReportsThem() {
    stored.add(utilityMethod(1L, "answer", "Integer", "42"));
    stored.add(utilityMethod(2L, "broken", "Integer", "nope"));

    UtilityMethodClassResponse response = service.generatedClass(EMAIL, VSUM_ID);

    assertThat(response.qualifiedClassName())
        .isEqualTo("tools.vitruv.methodologisttemplate.consistency.utils.Utility");
    assertThat(response.source()).contains("public static int answer()").doesNotContain("broken");
    assertThat(response.problems())
        .extracting(p -> p.utilityMethodName())
        .containsExactly("broken");
  }

  @Test
  void availableTypes_listsTheVsumsMetamodelTypes() {
    List<UtilityMethodTypeResponse> types = service.availableTypes(EMAIL, VSUM_ID);

    assertThat(types)
        .extracting(UtilityMethodTypeResponse::qualifiedName)
        .contains("simulink::SubSystem", "simulink::BlockKind");
    assertThat(types)
        .filteredOn(t -> t.name().equals("SubSystem"))
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.kind()).isEqualTo("CLASS");
              assertThat(t.javaName())
                  .isEqualTo("tools.vitruv.methodologisttemplate.model.simulink.SubSystem");
            });
  }

  // ── build ────────────────────────────────────────────────────────────────

  @Test
  void generatedSources_areEmpty_withoutUtilityMethods() {
    assertThat(service.generateBuildFile(vsum, List.of(simulinkEcore))).isEmpty();
    assertThat(service.generateEditorSource(VSUM_ID, List.of(simulinkEcore))).isEmpty();
  }

  @Test
  void generateBuildFile_returnsJavaSource() {
    stored.add(utilityMethod(1L, "answer", "Integer", "42"));

    FileStorage file = service.generateBuildFile(vsum, List.of(simulinkEcore)).orElseThrow();

    assertThat(file.getFilename()).isEqualTo(UtilityMethodClassGenerator.FILE_NAME);
    assertThat(new String(file.getData(), StandardCharsets.UTF_8))
        .contains("public final class Utility")
        .contains("public static int answer()");
  }

  @Test
  void generateBuildFile_failsTheBuild_whenAUtilityMethodDoesNotTranslate() {
    stored.add(firstBlock());

    assertThatThrownBy(() -> service.generateBuildFile(vsum, List.of()))
        .isInstanceOf(VsumBuildingException.class)
        .hasMessageContaining("first:")
        .hasMessageContaining("Unknown metamodel 'simulink'");
  }

  // ── editor ───────────────────────────────────────────────────────────────

  @Test
  void generateEditorSource_leavesOutUtilityMethodsThatDoNotTranslate() {
    stored.add(firstBlock());
    stored.add(utilityMethod(2L, "answer", "Integer", "42"));

    String source = service.generateEditorSource(VSUM_ID, List.of()).orElseThrow();

    assertThat(source)
        .contains("public final class Utility")
        .contains("public static int answer()")
        .doesNotContain(" first(");
  }

  // ── fixtures ─────────────────────────────────────────────────────────────

  private UtilityMethodRequest subSystemsRequest() {
    return new UtilityMethodRequest(
        "getAllSubSystems",
        "All subsystems, including nested ones.",
        "Sequence(simulink::SubSystem)",
        List.of(new UtilityMethodParameterDto("model", "simulink::SimulinkModel")),
        List.of("simulink::*", " "),
        "model.contains->selectByKind(SubSystem)\n"
            + "  ->closure(s | s.subBlocks->selectByKind(SubSystem))");
  }

  /** Needs the simulink metamodel, so it does not translate when no metamodel is passed. */
  private UtilityMethod firstBlock() {
    UtilityMethod first = utilityMethod(1L, "first", "simulink::Block", "model.contains->first()");
    first.getParameters().add(new UtilityMethodParameter("model", "simulink::SimulinkModel"));
    return first;
  }

  private UtilityMethod utilityMethod(Long id, String name, String returnType, String body) {
    return UtilityMethod.builder()
        .id(id)
        .vsum(vsum)
        .name(name)
        .returnType(returnType)
        .body(body)
        .build();
  }
}
