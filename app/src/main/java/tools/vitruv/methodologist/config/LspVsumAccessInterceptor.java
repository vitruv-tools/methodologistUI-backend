package tools.vitruv.methodologist.config;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import tools.vitruv.methodologist.lsp.AbstractLspWebSocketHandler;
import tools.vitruv.methodologist.vsum.service.VsumUserService;

/**
 * Checks, before an LSP WebSocket is opened, that the logged-in user may read the VSUM named in the
 * {@code vsumId} query parameter. A handshake that fails the check is rejected, so no language
 * server process and no workspace with the project's metamodels is ever created for it.
 *
 * <p>The checked ID is stored as session attribute {@link
 * AbstractLspWebSocketHandler#VSUM_ID_ATTRIBUTE}; handlers read it from there and never from the
 * URL. Viewers pass, since the language servers only read the project. A request without {@code
 * vsumId} passes too and gets a workspace without metamodels.
 */
@Component
public class LspVsumAccessInterceptor implements HandshakeInterceptor {

  private static final String VSUM_ID_PARAMETER = "vsumId";

  private final VsumUserService vsumUserService;

  /**
   * Creates the interceptor.
   *
   * @param vsumUserService used to check VSUM membership
   */
  public LspVsumAccessInterceptor(VsumUserService vsumUserService) {
    this.vsumUserService = vsumUserService;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    if (!(request.getPrincipal() instanceof KeycloakAuthentication authentication)
        || !authentication.isAuthenticated()) {
      return reject(response, HttpStatus.UNAUTHORIZED);
    }

    List<String> values =
        UriComponentsBuilder.fromUri(request.getURI())
            .build()
            .getQueryParams()
            .getOrDefault(VSUM_ID_PARAMETER, List.of());
    if (values.isEmpty()) {
      return true;
    }
    Long vsumId = parseVsumId(values);
    if (vsumId == null) {
      return reject(response, HttpStatus.BAD_REQUEST);
    }
    if (!vsumUserService.isMember(vsumId, authentication.getParsedToken().getEmail())) {
      return reject(response, HttpStatus.FORBIDDEN);
    }
    attributes.put(AbstractLspWebSocketHandler.VSUM_ID_ATTRIBUTE, vsumId);
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {
    // Nothing to clean up.
  }

  private static Long parseVsumId(List<String> values) {
    if (values.size() != 1) {
      return null;
    }
    try {
      long vsumId = Long.parseLong(values.get(0));
      return vsumId > 0 ? vsumId : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static boolean reject(ServerHttpResponse response, HttpStatus status) {
    response.setStatusCode(status);
    return false;
  }
}
