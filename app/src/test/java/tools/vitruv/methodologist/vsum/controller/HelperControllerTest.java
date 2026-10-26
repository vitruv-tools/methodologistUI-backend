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
import tools.vitruv.methodologist.vsum.controller.dto.request.HelperRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperClassResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperNameAvailabilityResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperPreviewResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperResponse;
import tools.vitruv.methodologist.vsum.service.HelperService;

@ExtendWith(MockitoExtension.class)
class HelperControllerTest {

  private static final String EMAIL = "test@example.com";

  @InjectMocks HelperController controller;
  @Mock HelperService service;
  @Mock KeycloakAuthentication authentication;
  @Mock KeycloakAuthentication.ParsedToken parsedToken;

  private final HelperRequest request =
      new HelperRequest("answer", null, "Integer", List.of(), List.of(), "42");
  private final HelperResponse response =
      new HelperResponse(
          1L,
          10L,
          "answer",
          null,
          "Integer",
          List.of(),
          List.of(),
          "42",
          "helper def : answer() : Integer =\n\t42;",
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

    ResponseEntity<List<HelperResponse>> result = controller.getAll(authentication, 10L);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).containsExactly(response);
  }

  @Test
  void create_returnsCreated() {
    when(service.create(EMAIL, 10L, request)).thenReturn(response);

    ResponseEntity<HelperResponse> result = controller.create(authentication, 10L, request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getBody()).isSameAs(response);
  }

  @Test
  void update_returnsOk() {
    when(service.update(EMAIL, 10L, 1L, request)).thenReturn(response);

    ResponseEntity<HelperResponse> result = controller.update(authentication, 10L, 1L, request);

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
  void preview_delegatesWithHelperId() {
    HelperPreviewResponse preview = new HelperPreviewResponse("helper", "java", List.of());
    when(service.preview(EMAIL, 10L, 1L, request)).thenReturn(preview);

    assertThat(controller.preview(authentication, 10L, 1L, request).getBody()).isSameAs(preview);
  }

  @Test
  void checkName_delegates() {
    HelperNameAvailabilityResponse availability = new HelperNameAvailabilityResponse(true, null);
    when(service.checkName(EMAIL, 10L, "answer", null)).thenReturn(availability);

    assertThat(controller.checkName(authentication, 10L, "answer", null).getBody())
        .isSameAs(availability);
  }

  @Test
  void generatedClass_delegates() {
    HelperClassResponse generated =
        new HelperClassResponse("a.Utility", "Utility.java", "class", List.of());
    when(service.generatedClass(EMAIL, 10L)).thenReturn(generated);

    assertThat(controller.generatedClass(authentication, 10L).getBody()).isSameAs(generated);
  }
}
