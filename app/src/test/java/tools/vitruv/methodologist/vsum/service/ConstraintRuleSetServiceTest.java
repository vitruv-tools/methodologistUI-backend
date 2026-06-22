package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.RuleSetPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.RuleSetPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.RuleSetResponse;
import tools.vitruv.methodologist.vsum.model.ConstraintRuleSet;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.repository.ConstraintRuleSetRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;

@ExtendWith(MockitoExtension.class)
class ConstraintRuleSetServiceTest {

  @InjectMocks ConstraintRuleSetService service;
  @Mock ConstraintRuleSetRepository ruleSetRepository;
  @Mock VsumRepository vsumRepository;
  @Mock UserRepository userRepository;

  private User user;
  private Vsum vsum;
  private ConstraintRuleSet ruleSet;

  @BeforeEach
  void setUp() {
    user = User.builder().id(1L).email("test@example.com").build();
    vsum = Vsum.builder().id(10L).name("TestVsum").build();

    FileStorage oclFile =
        FileStorage.builder()
            .filename("my_rules.ocl")
            .data("context Foo inv: true".getBytes(StandardCharsets.UTF_8))
            .build();

    ruleSet =
        ConstraintRuleSet.builder()
            .id(100L)
            .vsum(vsum)
            .name("My Rules")
            .color("#3b82f6")
            .description("desc")
            .oclFile(oclFile)
            .build();
  }

  // ── findAll ──────────────────────────────────────────────────────────────

  @Test
  void findAll_returnsMappedResponses() {
    when(ruleSetRepository.findByVsumId(10L)).thenReturn(List.of(ruleSet));

    List<RuleSetResponse> result = service.findAll(10L);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(100L);
    assertThat(result.get(0).vsumId()).isEqualTo(10L);
    assertThat(result.get(0).name()).isEqualTo("My Rules");
    assertThat(result.get(0).color()).isEqualTo("#3b82f6");
    assertThat(result.get(0).oclContent()).isEqualTo("context Foo inv: true");
  }

  @Test
  void findAll_emptyList_whenNoRuleSets() {
    when(ruleSetRepository.findByVsumId(99L)).thenReturn(List.of());

    List<RuleSetResponse> result = service.findAll(99L);

    assertThat(result).isEmpty();
  }

  @Test
  void findAll_handlesNullOclFile() {
    ConstraintRuleSet noFile =
        ConstraintRuleSet.builder().id(200L).vsum(vsum).name("Empty").color("#fff").build();
    when(ruleSetRepository.findByVsumId(10L)).thenReturn(List.of(noFile));

    List<RuleSetResponse> result = service.findAll(10L);

    assertThat(result.get(0).oclContent()).isEmpty();
  }

  @Test
  void findAll_handlesNullOclFileData() {
    FileStorage emptyFile = FileStorage.builder().filename("x.ocl").data(null).build();
    ConstraintRuleSet rs =
        ConstraintRuleSet.builder()
            .id(201L)
            .vsum(vsum)
            .name("NullData")
            .color("#fff")
            .oclFile(emptyFile)
            .build();
    when(ruleSetRepository.findByVsumId(10L)).thenReturn(List.of(rs));

    List<RuleSetResponse> result = service.findAll(10L);

    assertThat(result.get(0).oclContent()).isEmpty();
  }

  // ── create ───────────────────────────────────────────────────────────────

  @Test
  void create_savesAndReturnsResponse() {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull("test@example.com"))
        .thenReturn(Optional.of(user));
    when(vsumRepository.findById(10L)).thenReturn(Optional.of(vsum));
    when(ruleSetRepository.save(any()))
        .thenAnswer(
            inv -> {
              ConstraintRuleSet rs = inv.getArgument(0);
              rs.getClass(); // ensure non-null
              // assign id via reflection-free builder workaround: return a new instance with id
              return ConstraintRuleSet.builder()
                  .id(200L)
                  .vsum(rs.getVsum())
                  .name(rs.getName())
                  .color(rs.getColor())
                  .description(rs.getDescription())
                  .oclFile(rs.getOclFile())
                  .build();
            });

    RuleSetPostRequest request =
        new RuleSetPostRequest("New Set", "#ff0000", "my desc", "context X inv: 1=1");

    RuleSetResponse response = service.create("test@example.com", 10L, request);

    assertThat(response.id()).isEqualTo(200L);
    assertThat(response.vsumId()).isEqualTo(10L);
    assertThat(response.name()).isEqualTo("New Set");
    assertThat(response.color()).isEqualTo("#ff0000");
    assertThat(response.description()).isEqualTo("my desc");
    assertThat(response.oclContent()).isEqualTo("context X inv: 1=1");
  }

  @Test
  void create_usesDefaultColorWhenNull() {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull("test@example.com"))
        .thenReturn(Optional.of(user));
    when(vsumRepository.findById(10L)).thenReturn(Optional.of(vsum));

    ArgumentCaptor<ConstraintRuleSet> captor = ArgumentCaptor.forClass(ConstraintRuleSet.class);
    when(ruleSetRepository.save(captor.capture()))
        .thenAnswer(
            inv -> {
              ConstraintRuleSet rs = captor.getValue();
              return ConstraintRuleSet.builder()
                  .id(201L)
                  .vsum(rs.getVsum())
                  .name(rs.getName())
                  .color(rs.getColor())
                  .oclFile(rs.getOclFile())
                  .build();
            });

    RuleSetPostRequest request = new RuleSetPostRequest("Set", null, null, null);
    service.create("test@example.com", 10L, request);

    assertThat(captor.getValue().getColor()).isEqualTo("#3b82f6");
  }

  @Test
  void create_usesEmptyContentWhenOclContentNull() {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull("test@example.com"))
        .thenReturn(Optional.of(user));
    when(vsumRepository.findById(10L)).thenReturn(Optional.of(vsum));

    ArgumentCaptor<ConstraintRuleSet> captor = ArgumentCaptor.forClass(ConstraintRuleSet.class);
    when(ruleSetRepository.save(captor.capture()))
        .thenAnswer(
            inv -> {
              ConstraintRuleSet rs = captor.getValue();
              return ConstraintRuleSet.builder()
                  .id(202L)
                  .vsum(rs.getVsum())
                  .name(rs.getName())
                  .color(rs.getColor())
                  .oclFile(rs.getOclFile())
                  .build();
            });

    service.create("test@example.com", 10L, new RuleSetPostRequest("Set", null, null, null));

    assertThat(new String(captor.getValue().getOclFile().getData(), StandardCharsets.UTF_8))
        .isEmpty();
  }

  @Test
  void create_throwsUnauthorized_whenUserNotFound() {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull("unknown@example.com"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.create(
                    "unknown@example.com", 10L, new RuleSetPostRequest("X", null, null, null)))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void create_throwsNotFound_whenVsumNotFound() {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull("test@example.com"))
        .thenReturn(Optional.of(user));
    when(vsumRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.create(
                    "test@example.com", 99L, new RuleSetPostRequest("X", null, null, null)))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("VSUM not found");
  }

  @Test
  void create_oclFileHasCorrectFilename() {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull("test@example.com"))
        .thenReturn(Optional.of(user));
    when(vsumRepository.findById(10L)).thenReturn(Optional.of(vsum));

    ArgumentCaptor<ConstraintRuleSet> captor = ArgumentCaptor.forClass(ConstraintRuleSet.class);
    when(ruleSetRepository.save(captor.capture()))
        .thenAnswer(
            inv -> {
              ConstraintRuleSet rs = captor.getValue();
              return ConstraintRuleSet.builder()
                  .id(203L)
                  .vsum(rs.getVsum())
                  .name(rs.getName())
                  .color(rs.getColor())
                  .oclFile(rs.getOclFile())
                  .build();
            });

    service.create(
        "test@example.com", 10L, new RuleSetPostRequest("My Special Rules!", null, null, ""));

    assertThat(captor.getValue().getOclFile().getFilename()).isEqualTo("My_Special_Rules_.ocl");
  }

  @Test
  void create_oclFileHasSha256() {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull("test@example.com"))
        .thenReturn(Optional.of(user));
    when(vsumRepository.findById(10L)).thenReturn(Optional.of(vsum));

    ArgumentCaptor<ConstraintRuleSet> captor = ArgumentCaptor.forClass(ConstraintRuleSet.class);
    when(ruleSetRepository.save(captor.capture()))
        .thenAnswer(
            inv -> {
              ConstraintRuleSet rs = captor.getValue();
              return ConstraintRuleSet.builder()
                  .id(204L)
                  .vsum(rs.getVsum())
                  .name(rs.getName())
                  .color(rs.getColor())
                  .oclFile(rs.getOclFile())
                  .build();
            });

    service.create("test@example.com", 10L, new RuleSetPostRequest("X", null, null, "content"));

    assertThat(captor.getValue().getOclFile().getSha256()).isNotBlank().hasSize(64);
  }

  // ── update ───────────────────────────────────────────────────────────────

  @Test
  void update_updatesFieldsAndReturnsResponse() {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull("test@example.com"))
        .thenReturn(Optional.of(user));
    when(ruleSetRepository.findByIdAndVsumId(100L, 10L)).thenReturn(Optional.of(ruleSet));
    when(ruleSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    RuleSetPutRequest request =
        new RuleSetPutRequest("Updated Name", "#00ff00", "new desc", "context Y inv: 2=2");

    RuleSetResponse response = service.update("test@example.com", 10L, 100L, request);

    assertThat(response.name()).isEqualTo("Updated Name");
    assertThat(response.color()).isEqualTo("#00ff00");
    assertThat(response.description()).isEqualTo("new desc");
    assertThat(response.oclContent()).isEqualTo("context Y inv: 2=2");
  }

  @Test
  void update_keepsOldColor_whenRequestColorNull() {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull("test@example.com"))
        .thenReturn(Optional.of(user));
    when(ruleSetRepository.findByIdAndVsumId(100L, 10L)).thenReturn(Optional.of(ruleSet));
    when(ruleSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    RuleSetPutRequest request = new RuleSetPutRequest("Name", null, null, "");

    RuleSetResponse response = service.update("test@example.com", 10L, 100L, request);

    assertThat(response.color()).isEqualTo("#3b82f6");
  }

  @Test
  void update_throwsNotFound_whenRuleSetNotFound() {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull("test@example.com"))
        .thenReturn(Optional.of(user));
    when(ruleSetRepository.findByIdAndVsumId(999L, 10L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.update(
                    "test@example.com", 10L, 999L, new RuleSetPutRequest("X", null, null, null)))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("RuleSet not found");
  }

  @Test
  void update_throwsUnauthorized_whenUserNotFound() {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull("unknown@example.com"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.update(
                    "unknown@example.com", 10L, 100L, new RuleSetPutRequest("X", null, null, null)))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void update_usesEmptyContentWhenOclContentNull() {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull("test@example.com"))
        .thenReturn(Optional.of(user));
    when(ruleSetRepository.findByIdAndVsumId(100L, 10L)).thenReturn(Optional.of(ruleSet));

    ArgumentCaptor<ConstraintRuleSet> captor = ArgumentCaptor.forClass(ConstraintRuleSet.class);
    when(ruleSetRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    service.update("test@example.com", 10L, 100L, new RuleSetPutRequest("X", null, null, null));

    assertThat(new String(captor.getValue().getOclFile().getData(), StandardCharsets.UTF_8))
        .isEmpty();
  }

  // ── delete ───────────────────────────────────────────────────────────────

  @Test
  void delete_removesRuleSet() {
    when(ruleSetRepository.findByIdAndVsumId(100L, 10L)).thenReturn(Optional.of(ruleSet));

    service.delete(10L, 100L);

    verify(ruleSetRepository).delete(ruleSet);
  }

  @Test
  void delete_throwsNotFound_whenRuleSetNotFound() {
    when(ruleSetRepository.findByIdAndVsumId(999L, 10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(10L, 999L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("RuleSet not found");
  }

  @Test
  void delete_doesNotDeleteOtherRuleSets() {
    when(ruleSetRepository.findByIdAndVsumId(100L, 10L)).thenReturn(Optional.of(ruleSet));

    service.delete(10L, 100L);

    verify(ruleSetRepository).delete(eq(ruleSet));
  }
}
