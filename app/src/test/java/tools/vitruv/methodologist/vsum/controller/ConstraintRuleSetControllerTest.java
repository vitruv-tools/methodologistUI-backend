package tools.vitruv.methodologist.vsum.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.vsum.controller.dto.request.RuleSetPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.RuleSetPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.RuleSetResponse;
import tools.vitruv.methodologist.vsum.service.ConstraintRuleSetService;

@ExtendWith(MockitoExtension.class)
class ConstraintRuleSetControllerTest {

  @InjectMocks ConstraintRuleSetController controller;
  @Mock ConstraintRuleSetService service;
  @Mock KeycloakAuthentication authentication;
  @Mock KeycloakAuthentication.ParsedToken parsedToken;

  private RuleSetResponse sampleResponse;

  @BeforeEach
  void setUp() {
    sampleResponse =
        new RuleSetResponse(100L, 10L, "My Rules", "#3b82f6", "desc", "context X inv: true",
            Instant.now(), Instant.now());
  }

  private void stubEmail() {
    when(authentication.getParsedToken()).thenReturn(parsedToken);
    when(parsedToken.getEmail()).thenReturn("test@example.com");
  }

  // ── GET /vsums/{vsumId}/rule-sets ─────────────────────────────────────

  @Test
  void getAll_returnsOkWithList() {
    when(service.findAll(10L)).thenReturn(List.of(sampleResponse));

    ResponseEntity<List<RuleSetResponse>> response = controller.getAll(10L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(1);
    assertThat(response.getBody().get(0).id()).isEqualTo(100L);
  }

  @Test
  void getAll_returnsEmptyList() {
    when(service.findAll(10L)).thenReturn(List.of());

    ResponseEntity<List<RuleSetResponse>> response = controller.getAll(10L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEmpty();
  }

  // ── POST /vsums/{vsumId}/rule-sets ────────────────────────────────────

  @Test
  void create_returnsCreatedWithBody() {
    stubEmail();
    RuleSetPostRequest request = new RuleSetPostRequest("My Rules", "#3b82f6", "desc", "context X inv: true");
    when(service.create("test@example.com", 10L, request)).thenReturn(sampleResponse);

    ResponseEntity<RuleSetResponse> response = controller.create(authentication, 10L, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().id()).isEqualTo(100L);
    assertThat(response.getBody().name()).isEqualTo("My Rules");
  }

  @Test
  void create_passesEmailFromAuthentication() {
    stubEmail();
    RuleSetPostRequest request = new RuleSetPostRequest("X", null, null, null);
    when(service.create(eq("test@example.com"), eq(10L), any())).thenReturn(sampleResponse);

    controller.create(authentication, 10L, request);

    verify(service).create("test@example.com", 10L, request);
  }

  // ── PUT /vsums/{vsumId}/rule-sets/{ruleSetId} ─────────────────────────

  @Test
  void update_returnsOkWithUpdatedBody() {
    stubEmail();
    RuleSetPutRequest request = new RuleSetPutRequest("Updated", "#ff0000", "new desc", "context Y inv: 1=1");
    RuleSetResponse updated = new RuleSetResponse(100L, 10L, "Updated", "#ff0000", "new desc",
        "context Y inv: 1=1", Instant.now(), Instant.now());
    when(service.update("test@example.com", 10L, 100L, request)).thenReturn(updated);

    ResponseEntity<RuleSetResponse> response = controller.update(authentication, 10L, 100L, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().name()).isEqualTo("Updated");
    assertThat(response.getBody().color()).isEqualTo("#ff0000");
  }

  @Test
  void update_passesEmailFromAuthentication() {
    stubEmail();
    RuleSetPutRequest request = new RuleSetPutRequest("X", null, null, null);
    when(service.update(eq("test@example.com"), eq(10L), eq(100L), any())).thenReturn(sampleResponse);

    controller.update(authentication, 10L, 100L, request);

    verify(service).update("test@example.com", 10L, 100L, request);
  }

  @Test
  void update_propagatesNotFoundException() {
    stubEmail();
    RuleSetPutRequest request = new RuleSetPutRequest("X", null, null, null);
    when(service.update(any(), eq(10L), eq(999L), any()))
        .thenThrow(new NotFoundException("RuleSet not found"));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> controller.update(authentication, 10L, 999L, request))
        .isInstanceOf(NotFoundException.class);
  }

  // ── DELETE /vsums/{vsumId}/rule-sets/{ruleSetId} ──────────────────────

  @Test
  void delete_returnsNoContent() {
    ResponseEntity<Void> response = controller.delete(10L, 100L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getBody()).isNull();
  }

  @Test
  void delete_callsServiceWithCorrectIds() {
    controller.delete(10L, 100L);

    verify(service).delete(10L, 100L);
  }

  @Test
  void delete_propagatesNotFoundException() {
    doThrow(new NotFoundException("RuleSet not found")).when(service).delete(10L, 999L);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.delete(10L, 999L))
        .isInstanceOf(NotFoundException.class);
  }
}
