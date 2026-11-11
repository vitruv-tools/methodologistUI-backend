package tools.vitruv.methodologist.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import tools.vitruv.methodologist.lsp.LspWebSocketHandler;
import tools.vitruv.methodologist.lsp.NeoJoinLspWebSocketHandler;
import tools.vitruv.methodologist.lsp.OclLspWebSocketHandler;

/** Configuration class for setting up WebSocket endpoints. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final LspWebSocketHandler lspWebSocketHandler;
  private final OclLspWebSocketHandler oclLspWebSocketHandler;
  private final NeoJoinLspWebSocketHandler neoJoinLspWebSocketHandler;
  private final LspVsumAccessInterceptor lspVsumAccessInterceptor;

  @Value("${allowed.origins:*}")
  private String allowedOrigins;

  /**
   * Constructs a new {@code WebSocketConfig} with the given WebSocket handlers.
   *
   * @param lspWebSocketHandler the handler for LSP WebSocket connections
   * @param oclLspWebSocketHandler the handler for OCL LSP WebSocket connections
   * @param neoJoinLspWebSocketHandler the handler for NeoJoin LSP WebSocket connections
   * @param lspVsumAccessInterceptor checks VSUM membership during the handshake
   */
  public WebSocketConfig(
      LspWebSocketHandler lspWebSocketHandler,
      OclLspWebSocketHandler oclLspWebSocketHandler,
      NeoJoinLspWebSocketHandler neoJoinLspWebSocketHandler,
      LspVsumAccessInterceptor lspVsumAccessInterceptor) {
    this.lspWebSocketHandler = lspWebSocketHandler;
    this.oclLspWebSocketHandler = oclLspWebSocketHandler;
    this.neoJoinLspWebSocketHandler = neoJoinLspWebSocketHandler;
    this.lspVsumAccessInterceptor = lspVsumAccessInterceptor;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    // Browsers reject the handshake unless the server selects the "bearer" subprotocol they sent.
    DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler();
    handshakeHandler.setSupportedProtocols(WebSocketBearerTokenResolver.BEARER_PROTOCOL);

    registerLsp(
        registry, lspWebSocketHandler, WebSocketBearerTokenResolver.LSP_PATH, handshakeHandler);
    registerLsp(
        registry,
        oclLspWebSocketHandler,
        WebSocketBearerTokenResolver.OCL_LSP_PATH,
        handshakeHandler);
    registerLsp(
        registry,
        neoJoinLspWebSocketHandler,
        WebSocketBearerTokenResolver.NEOJOIN_LSP_PATH,
        handshakeHandler);
  }

  private void registerLsp(
      WebSocketHandlerRegistry registry,
      WebSocketHandler handler,
      String path,
      DefaultHandshakeHandler handshakeHandler) {
    registry
        .addHandler(handler, path)
        .setHandshakeHandler(handshakeHandler)
        .addInterceptors(lspVsumAccessInterceptor)
        .setAllowedOriginPatterns(allowedOrigins.split(","));
  }

  /**
   * Configures the servlet WebSocket container's text and binary message buffer sizes, so large LSP
   * payloads (e.g. Reactions DSL, VitruvOCL) don't exceed Tomcat's 8 KB default.
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
