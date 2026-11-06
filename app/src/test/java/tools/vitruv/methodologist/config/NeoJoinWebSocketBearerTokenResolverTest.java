package tools.vitruv.methodologist.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Unit tests for {@link NeoJoinWebSocketBearerTokenResolver}. */
class NeoJoinWebSocketBearerTokenResolverTest {

  private final NeoJoinWebSocketBearerTokenResolver resolver =
      new NeoJoinWebSocketBearerTokenResolver();

  @Test
  void resolve_readsTokenFromSubprotocol_onNeoJoinLsp() {
    assertThat(resolver.resolve(request("/neojoin-lsp", "bearer, abc.def.ghi")))
        .isEqualTo("abc.def.ghi");
  }

  @Test
  void resolve_respectsContextPath() {
    MockHttpServletRequest request = request("/api/neojoin-lsp", "bearer, abc");
    request.setContextPath("/api");

    assertThat(resolver.resolve(request)).isEqualTo("abc");
  }

  @Test
  void resolve_prefersAuthorizationHeader() {
    MockHttpServletRequest request = request("/neojoin-lsp", "bearer, fromProtocol");
    request.addHeader("Authorization", "Bearer fromHeader");

    assertThat(resolver.resolve(request)).isEqualTo("fromHeader");
  }

  @Test
  void resolve_ignoresSubprotocol_onOtherPaths() {
    assertThat(resolver.resolve(request("/ocl-lsp", "bearer, abc"))).isNull();
    assertThat(resolver.resolve(request("/neojoin-lsp/other", "bearer, abc"))).isNull();
  }

  @Test
  void resolve_ignoresMalformedSubprotocols() {
    assertThat(resolver.resolve(request("/neojoin-lsp", null))).isNull();
    assertThat(resolver.resolve(request("/neojoin-lsp", "bearer"))).isNull();
    assertThat(resolver.resolve(request("/neojoin-lsp", "bearer, "))).isNull();
    assertThat(resolver.resolve(request("/neojoin-lsp", "basic, abc"))).isNull();
    assertThat(resolver.resolve(request("/neojoin-lsp", "bearer, abc, extra"))).isNull();
  }

  private static MockHttpServletRequest request(String uri, String protocols) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    if (protocols != null) {
      request.addHeader("Sec-WebSocket-Protocol", protocols);
    }
    return request;
  }
}
