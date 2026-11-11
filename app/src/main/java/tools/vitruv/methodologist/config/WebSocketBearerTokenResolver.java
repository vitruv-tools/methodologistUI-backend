package tools.vitruv.methodologist.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Resolves the bearer token from the {@code Authorization} header and, for the LSP WebSocket
 * handshakes only ({@link #LSP_PATHS}), from the {@code Sec-WebSocket-Protocol} header.
 *
 * <p>Browsers cannot set headers on a WebSocket handshake, so the client sends the token as a
 * subprotocol: {@code new WebSocket(url, ["bearer", token])}, which arrives as {@code
 * Sec-WebSocket-Protocol: bearer, <token>}. Unlike a query parameter, this keeps the token out of
 * URLs and access logs.
 */
public class WebSocketBearerTokenResolver implements BearerTokenResolver {

  /** Path of the Reactions LSP WebSocket endpoint. */
  public static final String LSP_PATH = "/lsp";

  /** Path of the VitruvOCL LSP WebSocket endpoint. */
  public static final String OCL_LSP_PATH = "/ocl-lsp";

  /** Path of the NeoJoin LSP WebSocket endpoint. */
  public static final String NEOJOIN_LSP_PATH = "/neojoin-lsp";

  /** All LSP WebSocket endpoints; their handshakes require a Keycloak token. */
  public static final List<String> LSP_PATHS = List.of(LSP_PATH, OCL_LSP_PATH, NEOJOIN_LSP_PATH);

  /** Subprotocol name that precedes the token. */
  public static final String BEARER_PROTOCOL = "bearer";

  private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

  @Override
  public String resolve(HttpServletRequest request) {
    String token = delegate.resolve(request);
    if (token != null || !isLspRequest(request)) {
      return token;
    }

    String protocols = request.getHeader("Sec-WebSocket-Protocol");
    if (protocols == null) {
      return null;
    }
    String[] parts = protocols.split(",", -1);
    if (parts.length != 2 || !BEARER_PROTOCOL.equals(parts[0].trim())) {
      return null;
    }
    String subprotocolToken = parts[1].trim();
    return subprotocolToken.isEmpty() ? null : subprotocolToken;
  }

  private static boolean isLspRequest(HttpServletRequest request) {
    return LSP_PATHS.stream()
        .anyMatch(path -> (request.getContextPath() + path).equals(request.getRequestURI()));
  }
}
