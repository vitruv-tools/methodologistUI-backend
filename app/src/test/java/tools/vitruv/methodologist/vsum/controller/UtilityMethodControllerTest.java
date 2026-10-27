package tools.vitruv.methodologist.vsum.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import tools.vitruv.methodologist.vsum.controller.dto.request.UtilityMethodRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodClassResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodNameAvailabilityResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodPreviewResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodResponse;
import tools.vitruv.methodologist.vsum.service.UtilityMethodService;

@ExtendWith(MockitoExtension.class)
class UtilityMethodControllerTest {

  private static final String EMAIL = "test@example.com";

  @InjectMocks UtilityMethodController controller;
  @Mock UtilityMethodService service;
  @Mock KeycloakAuthentication authentication;
  @Mock KeycloakAuthentication.ParsedToken parsedToken;

  private final UtilityMethodRequest request =
      new UtilityMethodRequest("answer", null, "Integer", List.of(), List.of(), "42");
  private final UtilityMethodResponse response =
      new UtilityMethodResponse(
          1L,
          10L,
          "answer",
          null,
          "Integer",
          List.of(),
          List.of(),
          "42",
          "def : answer() : Integer =\n\t42;",
          "public static int answer() {}",
          List.of(),
          Instant.now(),
          Instant.now());

  @BeforeEach
  void stubEmail() {
    when(authentication.getParsedToken()).thenReturn(parsedToken);
    when(parsedToken.getEmail()).thenReturn(EMAIL);
  }

  @Test
  void getAll_returnsOk() {
    when(service.findAll(EMAIL, 10L)).thenReturn(List.of(response));

    ResponseEntity<List<UtilityMethodResponse>> result = controller.getAll(authentication, 10L);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).containsExactly(response);
  }

  @Test
  void create_returnsCreated() {
    when(service.create(EMAIL, 10L, request)).thenReturn(response);

    ResponseEntity<UtilityMethodResponse> result = controller.create(authentication, 10L, request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getBody()).isSameAs(response);
  }

  @Test
  void update_returnsOk() {
    when(service.update(EMAIL, 10L, 1L, request)).thenReturn(response);

    ResponseEntity<UtilityMethodResponse> result =
        controller.update(authentication, 10L, 1L, request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isSameAs(response);
  }

  @Test
  void delete_returnsNoContent() {
    ResponseEntity<Void> result = controller.delete(authentication, 10L, 1L);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(service).delete(EMAIL, 10L, 1L);
  }

  @Test
  void readOnlyEndpoints_delegateToTheService() {
    UtilityMethodPreviewResponse preview =
        new UtilityMethodPreviewResponse("def", "java", List.of());
    UtilityMethodNameAvailabilityResponse availability =
        new UtilityMethodNameAvailabilityResponse(true, null);
    UtilityMethodClassResponse generated =
        new UtilityMethodClassResponse("a.Utility", "Utility.java", "class", List.of());
    when(service.preview(EMAIL, 10L, 1L, request)).thenReturn(preview);
    when(service.checkName(EMAIL, 10L, "answer", null)).thenReturn(availability);
    when(service.generatedClass(EMAIL, 10L)).thenReturn(generated);

    assertThat(controller.preview(authentication, 10L, 1L, request).getBody()).isSameAs(preview);
    assertThat(controller.checkName(authentication, 10L, "answer", null).getBody())
        .isSameAs(availability);
    assertThat(controller.generatedClass(authentication, 10L).getBody()).isSameAs(generated);
  }
}
