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
import tools.vitruv.methodologist.exception.HelperValidationException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.controller.dto.request.HelperParameterDto;
import tools.vitruv.methodologist.vsum.controller.dto.request.HelperRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperClassResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperPreviewResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperTypeResponse;
import tools.vitruv.methodologist.vsum.helper.HelperClassGenerator;
import tools.vitruv.methodologist.vsum.model.Helper;
import tools.vitruv.methodologist.vsum.model.HelperParameter;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.HelperRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;

@ExtendWith(MockitoExtension.class)
class HelperServiceTest {

  private static final String EMAIL = "test@example.com";
  private static final long VSUM_ID = 10L;

  @InjectMocks HelperService service;
  @Mock HelperRepository helperRepository;
  @Mock VsumUserRepository vsumUserRepository;
  @Mock VsumMetaModelRepository vsumMetaModelRepository;

  private Vsum vsum;
  private FileStorage simulinkEcore;
  private List<Helper> stored;

  @BeforeEach
  void setUp() throws IOException {
    vsum = Vsum.builder().id(VSUM_ID).name("TestVsum").build();
    try (InputStream in = getClass().getResourceAsStream("/helper/simulink.ecore")) {
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
    lenient().when(helperRepository.findByVsumIdOrderByNameAsc(VSUM_ID)).thenReturn(stored);
    lenient()
        .when(helperRepository.save(any(Helper.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  // ── create ───────────────────────────────────────────────────────────────

  @Test
  void create_savesHelperAndReturnsGeneratedJava() {
    HelperResponse response = service.create(EMAIL, VSUM_ID, subSystemsRequest());

    ArgumentCaptor<Helper> saved = ArgumentCaptor.forClass(Helper.class);
    verify(helperRepository).save(saved.capture());
    assertThat(saved.getValue().getVsum()).isSameAs(vsum);
    assertThat(saved.getValue().getParameters())
        .extracting(HelperParameter::getName, HelperParameter::getType)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("model", "simulink::SimulinkModel"));
    assertThat(saved.getValue().getImports()).containsExactly("simulink::*");
    assertThat(response.problems()).isEmpty();
    assertThat(response.javaMethod())
        .contains("public static List<SubSystem> getAllSubSystems(SimulinkModel model)");
    assertThat(response.oclDefinition())
        .startsWith(
            "helper def : getAllSubSystems(model : simulink::SimulinkModel) :"
                + " Sequence(simulink::SubSystem) =");
  }

  @Test
  void create_rejectsNameUsedByAnotherHelperIgnoringCase() {
    stored.add(helper(1L, "GetAllSubSystems", "Integer", "1"));

    assertThatThrownBy(() -> service.create(EMAIL, VSUM_ID, subSystemsRequest()))
        .isInstanceOf(HelperValidationException.class)
        .hasMessageContaining("already exists");
    verify(helperRepository, never()).save(any());
  }

  @Test
  void create_rejectsHelperThatDoesNotTranslate_withPosition() {
    HelperRequest request =
        new HelperRequest(
            "broken",
            null,
            "Integer",
            List.of(new HelperParameterDto("model", "simulink::SimulinkModel")),
            List.of(),
            "model.contians->size()");

    assertThatThrownBy(() -> service.create(EMAIL, VSUM_ID, request))
        .isInstanceOf(HelperValidationException.class)
        .hasMessageContaining("Line 1, column 7")
        .hasMessageContaining("no feature 'contians'");
    verify(helperRepository, never()).save(any());
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
    Helper callee = helper(1L, "base", "Integer", "1");
    stored.add(callee);
    stored.add(helper(2L, "caller", "Integer", "base() + 1"));
    when(helperRepository.findByIdAndVsumId(1L, VSUM_ID)).thenReturn(Optional.of(callee));
    HelperRequest rename = new HelperRequest("renamed", null, "Integer", List.of(), List.of(), "1");

    assertThatThrownBy(() -> service.update(EMAIL, VSUM_ID, 1L, rename))
        .isInstanceOf(HelperValidationException.class)
        .hasMessageContaining("caller");
    assertThat(callee.getName()).isEqualTo("base");
  }

  @Test
  void update_appliesChanges_whenValid() {
    Helper existing = helper(1L, "answer", "Integer", "1");
    stored.add(existing);
    when(helperRepository.findByIdAndVsumId(1L, VSUM_ID)).thenReturn(Optional.of(existing));
    HelperRequest request =
        new HelperRequest("answer", "The answer.", "Integer", List.of(), List.of(), " 42 ");

    HelperResponse response = service.update(EMAIL, VSUM_ID, 1L, request);

    assertThat(existing.getBody()).isEqualTo("42");
    assertThat(existing.getDescription()).isEqualTo("The answer.");
    assertThat(response.javaMethod()).contains("return 42;");
  }

  @Test
  void update_throwsNotFound_forHelperOfAnotherVsum() {
    when(helperRepository.findByIdAndVsumId(99L, VSUM_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(EMAIL, VSUM_ID, 99L, subSystemsRequest()))
        .isInstanceOf(NotFoundException.class);
  }

  // ── delete ───────────────────────────────────────────────────────────────

  @Test
  void delete_removesUnusedHelper() {
    Helper helper = helper(1L, "answer", "Integer", "42");
    stored.add(helper);
    when(helperRepository.findByIdAndVsumId(1L, VSUM_ID)).thenReturn(Optional.of(helper));

    service.delete(EMAIL, VSUM_ID, 1L);

    verify(helperRepository).delete(helper);
  }

  @Test
  void delete_rejectsHelperUsedByAnotherHelper() {
    Helper callee = helper(1L, "base", "Integer", "1");
    stored.add(callee);
    stored.add(helper(2L, "caller", "Integer", "thisModule.base() + 1"));
    when(helperRepository.findByIdAndVsumId(1L, VSUM_ID)).thenReturn(Optional.of(callee));

    assertThatThrownBy(() -> service.delete(EMAIL, VSUM_ID, 1L))
        .isInstanceOf(HelperValidationException.class)
        .hasMessageContaining("used by: caller");
    verify(helperRepository, never()).delete(any(Helper.class));
  }

  // ── preview, name check, class, types ────────────────────────────────────

  @Test
  void preview_returnsProblemsInsteadOfThrowing() {
    stored.add(helper(1L, "getAllSubSystems", "Integer", "1"));

    HelperPreviewResponse preview = service.preview(EMAIL, VSUM_ID, null, subSystemsRequest());

    assertThat(preview.problems())
        .extracting(p -> p.message())
        .containsExactly(
            "A utility method named 'getAllSubSystems' already exists in this project.");
    assertThat(preview.javaMethod()).isNull();
    assertThat(preview.oclDefinition()).contains("closure");
  }

  @Test
  void preview_ofEditedHelper_ignoresItsOwnName() {
    stored.add(helper(1L, "getAllSubSystems", "Integer", "1"));

    HelperPreviewResponse preview = service.preview(EMAIL, VSUM_ID, 1L, subSystemsRequest());

    assertThat(preview.problems()).isEmpty();
    assertThat(preview.javaMethod()).contains("oclClosure");
  }

  @Test
  void checkName_reportsInvalidAndTakenNames() {
    stored.add(helper(1L, "taken", "Integer", "1"));

    assertThat(service.checkName(EMAIL, VSUM_ID, "free", null).available()).isTrue();
    assertThat(service.checkName(EMAIL, VSUM_ID, "Taken", null).available()).isFalse();
    assertThat(service.checkName(EMAIL, VSUM_ID, "taken", 1L).available()).isTrue();
    assertThat(service.checkName(EMAIL, VSUM_ID, "not valid", null).message())
        .contains("not a valid Java method name");
  }

  @Test
  void generatedClass_leavesOutBrokenHelpersAndReportsThem() {
    stored.add(helper(1L, "answer", "Integer", "42"));
    stored.add(helper(2L, "broken", "Integer", "nope"));

    HelperClassResponse response = service.generatedClass(EMAIL, VSUM_ID);

    assertThat(response.qualifiedClassName())
        .isEqualTo("tools.vitruv.methodologisttemplate.consistency.utils.Utility");
    assertThat(response.source()).contains("public static int answer()").doesNotContain("broken");
    assertThat(response.problems()).extracting(p -> p.helperName()).containsExactly("broken");
  }

  @Test
  void availableTypes_listsTheVsumsMetamodelTypes() {
    List<HelperTypeResponse> types = service.availableTypes(EMAIL, VSUM_ID);

    assertThat(types)
        .extracting(HelperTypeResponse::qualifiedName)
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
  void generateBuildFile_isEmpty_withoutHelpers() {
    assertThat(service.generateBuildFile(vsum, List.of(simulinkEcore))).isEmpty();
  }

  @Test
  void generateBuildFile_returnsJavaSource() {
    stored.add(helper(1L, "answer", "Integer", "42"));

    FileStorage file = service.generateBuildFile(vsum, List.of(simulinkEcore)).orElseThrow();

    assertThat(file.getFilename()).isEqualTo(HelperClassGenerator.FILE_NAME);
    assertThat(new String(file.getData(), StandardCharsets.UTF_8))
        .contains("public final class Utility")
        .contains("public static int answer()");
  }

  @Test
  void generateBuildFile_failsTheBuild_whenAHelperDoesNotTranslate() {
    Helper helper = helper(1L, "first", "simulink::Block", "model.contains->first()");
    helper.getParameters().add(new HelperParameter("model", "simulink::SimulinkModel"));
    stored.add(helper);

    assertThatThrownBy(() -> service.generateBuildFile(vsum, List.of()))
        .isInstanceOf(VsumBuildingException.class)
        .hasMessageContaining("first:")
        .hasMessageContaining("Unknown metamodel 'simulink'");
  }

  // ── editor ───────────────────────────────────────────────────────────────

  @Test
  void generateEditorSource_isEmpty_withoutHelpers() {
    assertThat(service.generateEditorSource(VSUM_ID, List.of(simulinkEcore))).isEmpty();
  }

  @Test
  void generateEditorSource_leavesOutHelpersThatDoNotTranslate() {
    Helper broken = helper(1L, "first", "simulink::Block", "model.contains->first()");
    broken.getParameters().add(new HelperParameter("model", "simulink::SimulinkModel"));
    stored.add(broken);
    stored.add(helper(2L, "answer", "Integer", "42"));

    String source = service.generateEditorSource(VSUM_ID, List.of()).orElseThrow();

    assertThat(source)
        .contains("public final class Utility")
        .contains("public static int answer()")
        .doesNotContain(" first(");
  }

  // ── fixtures ─────────────────────────────────────────────────────────────

  private HelperRequest subSystemsRequest() {
    return new HelperRequest(
        "getAllSubSystems",
        "All subsystems, including nested ones.",
        "Sequence(simulink::SubSystem)",
        List.of(new HelperParameterDto("model", "simulink::SimulinkModel")),
        List.of("simulink::*", " "),
        "model.contains->selectByKind(SubSystem)\n"
            + "  ->closure(s | s.subBlocks->selectByKind(SubSystem))");
  }

  private Helper helper(Long id, String name, String returnType, String body) {
    return Helper.builder().id(id).vsum(vsum).name(name).returnType(returnType).body(body).build();
  }
}
