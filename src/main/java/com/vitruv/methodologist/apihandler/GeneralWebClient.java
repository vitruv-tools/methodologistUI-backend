package com.vitruv.methodologist.apihandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import java.util.function.Consumer;
/**
 * Component that provides configured WebClient instances for making HTTP requests.
 * Supports custom header configuration and automatic request/response logging.
 */
@Slf4j
@Component
public class GeneralWebClient {
  private final ReactorClientHttpConnector reactorClientHttpConnector;
  private final int payloadSize;

  /**
   * Constructs a new GeneralWebClient with specified payload size.
   *
   * @param payloadSize maximum size in bytes for request/response payloads
   */
  public GeneralWebClient(@Value("${http.client.payload_size}") int payloadSize) {
    this.reactorClientHttpConnector = new ReactorClientHttpConnector(HttpClient.create());
    this.payloadSize = payloadSize;
  }

  /**
   * Creates a configured WebClient instance with the given base URL and header customizations.
   * Automatically configures payload size limits and request/response logging.
   *
   * @param baseUrl base URL for the WebClient
   * @param headersConsumer consumer for customizing HTTP headers
   * @return configured WebClient instance
   */
  public WebClient get(String baseUrl, Consumer<HttpHeaders> headersConsumer) {
    Class<?> loggerClazz = GeneralWebClient.class;
    try {
      loggerClazz = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
    } catch (ClassNotFoundException ignored) {
      log.warn("Ignored exception", e);
    }

    return WebClient.builder()
        .exchangeStrategies(
            ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(payloadSize))
                .build())
        .baseUrl(baseUrl)
        .defaultHeaders(headersConsumer)
        .clientConnector(reactorClientHttpConnector)
        .filter(new WebClientLogger(loggerClazz))
        .build();
  }
}
