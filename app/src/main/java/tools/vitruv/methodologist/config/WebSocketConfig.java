package tools.vitruv.methodologist.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final LspWebSocketHandler lspWebSocketHandler;

  public WebSocketConfig(LspWebSocketHandler lspWebSocketHandler) {
    this.lspWebSocketHandler = lspWebSocketHandler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(lspWebSocketHandler, "/lsp")
        .setAllowedOrigins("*");
  }
}
