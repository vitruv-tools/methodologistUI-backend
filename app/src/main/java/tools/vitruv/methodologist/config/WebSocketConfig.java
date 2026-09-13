package tools.vitruv.methodologist.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/** Configuration class for setting up WebSocket endpoints. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final LspWebSocketHandler lspWebSocketHandler;
  private final OclLspWebSocketHandler oclLspWebSocketHandler;

  /**
   * Constructs a new {@code WebSocketConfig} with the given WebSocket handlers.
   *
   * @param lspWebSocketHandler the handler for LSP WebSocket connections
   * @param oclLspWebSocketHandler the handler for OCL LSP WebSocket connections
   */
  public WebSocketConfig(
      LspWebSocketHandler lspWebSocketHandler, OclLspWebSocketHandler oclLspWebSocketHandler) {
    this.lspWebSocketHandler = lspWebSocketHandler;
    this.oclLspWebSocketHandler = oclLspWebSocketHandler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(lspWebSocketHandler, "/lsp").setAllowedOrigins("*");
    registry.addHandler(oclLspWebSocketHandler, "/ocl-lsp").setAllowedOrigins("*");
  }

  /**
   * Configures the servlet WebSocket container's text and binary message buffer sizes, so large
   * LSP payloads (e.g. Reactions DSL, VitruvOCL) don't exceed Tomcat's 8 KB default.
   *
   * @param maxBufferSize the maximum message buffer size in bytes
   * @return the configured container factory bean
   */
  @Bean
  public ServletServerContainerFactoryBean createWebSocketContainer(
      @Value("${websocket.max-buffer-size:262144}") int maxBufferSize) {
    ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
    container.setMaxTextMessageBufferSize(maxBufferSize);
    container.setMaxBinaryMessageBufferSize(maxBufferSize);
    return container;
  }
}
