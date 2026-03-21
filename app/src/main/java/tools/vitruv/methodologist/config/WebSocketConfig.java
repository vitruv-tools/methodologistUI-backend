package tools.vitruv.methodologist.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Configuration class for setting up WebSocket endpoints. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  /** The WebSocket handler for managing LSP (Language Server Protocol) connections. */
  private final LspWebSocketHandler lspWebSocketHandler;

  /**
   * Constructor for WebSocketConfig.
   *
   * @param lspWebSocketHandler the handler for LSP WebSocket connections
   */
  public WebSocketConfig(LspWebSocketHandler lspWebSocketHandler) {
    this.lspWebSocketHandler = lspWebSocketHandler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(lspWebSocketHandler, "/lsp").setAllowedOrigins("*");
  }
}
