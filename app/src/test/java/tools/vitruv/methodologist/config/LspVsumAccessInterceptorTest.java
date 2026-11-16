package tools.vitruv.methodologist.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.socket.WebSocketHandler;
import tools.vitruv.methodologist.lsp.AbstractLspWebSocketHandler;
import tools.vitruv.methodologist.vsum.service.VsumUserService;

/** Unit tests for {@link LspVsumAccessInterceptor}. */
@ExtendWith(MockitoExtension.class)
class LspVsumAccessInterceptorTest {

  private static final String EMAIL = "member@example.com";

  @Mock VsumUserService vsumUserService;
  @Mock ServerHttpResponse response;
  @Mock WebSocketHandler handler;

  private LspVsumAccessInterceptor interceptor;
  private Map<String, Object> attributes;

  @BeforeEach
  void setUp() {
    interceptor = new LspVsumAccessInterceptor(vsumUserService);
    attributes = new HashMap<>();
  }

  @Test
  void memberOfTheVsum_passes_andTheCheckedIdIsStored() {
    when(vsumUserService.isMember(5L, EMAIL)).thenReturn(true);

    boolean allowed =
        interceptor.beforeHandshake(request("vsumId=5", loggedIn()), response, handler, attributes);

    assertThat(allowed).isTrue();
    assertThat(attributes).containsEntry(AbstractLspWebSocketHandler.VSUM_ID_ATTRIBUTE, 5L);
  }

  @Test
  void nonMember_isForbidden_andNothingIsStored() {
    when(vsumUserService.isMember(5L, EMAIL)).thenReturn(false);

    boolean allowed =
        interceptor.beforeHandshake(request("vsumId=5", loggedIn()), response, handler, attributes);

    assertThat(allowed).isFalse();
    verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    assertThat(attributes).isEmpty();
  }

  @Test
  void withoutVsumId_passes_withoutAuthorizedVsum() {
    boolean allowed =
        interceptor.beforeHandshake(request(null, loggedIn()), response, handler, attributes);

    assertThat(allowed).isTrue();
    assertThat(attributes).isEmpty();
    verify(vsumUserService, never())
        .isMember(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"vsumId=abc", "vsumId=0", "vsumId=-3", "vsumId=", "vsumId=1&vsumId=2"})
  void malformedVsumId_isRejectedAsBadRequest(String query) {
    boolean allowed =
        interceptor.beforeHandshake(request(query, loggedIn()), response, handler, attributes);

    assertThat(allowed).isFalse();
    verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
    assertThat(attributes).isEmpty();
  }

  @Test
  void withoutLogin_isUnauthorized_andMembershipIsNotChecked() {
    boolean allowed =
        interceptor.beforeHandshake(request("vsumId=5", null), response, handler, attributes);

    assertThat(allowed).isFalse();
    verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    verify(vsumUserService, never())
        .isMember(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void principalOfAnotherType_isUnauthorized() {
    Principal other = mock(Principal.class);

    boolean allowed =
        interceptor.beforeHandshake(request("vsumId=5", other), response, handler, attributes);

    assertThat(allowed).isFalse();
    verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
  }

  private static ServletServerHttpRequest request(String query, Principal principal) {
    MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/lsp");
    servletRequest.setQueryString(query);
    servletRequest.setUserPrincipal(principal);
    return new ServletServerHttpRequest(servletRequest);
  }

  private static KeycloakAuthentication loggedIn() {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").claim("email", EMAIL).build();
    return new KeycloakAuthentication(jwt, List.of(new SimpleGrantedAuthority("ROLE_user")));
  }
}
