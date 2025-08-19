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

@Slf4j
@Component
public class GeneralWebClient {
  private final ReactorClientHttpConnector reactorClientHttpConnector;
  private final int payloadSize;

  public GeneralWebClient(@Value("${http.client.payload_size}") int payloadSize) {
    this.reactorClientHttpConnector = new ReactorClientHttpConnector(HttpClient.create());
    this.payloadSize = payloadSize;
  }

  public WebClient get(String baseUrl, Consumer<HttpHeaders> headersConsumer) {
    Class<?> loggerClazz = GeneralWebClient.class;
    try {
      loggerClazz = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());
    } catch (ClassNotFoundException ignored) {
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
