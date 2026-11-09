package tools.vitruv.methodologist.apihandler;

import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * Logs one entry per HTTP call of a Spring WebClient: method, path and status.
 *
 * <p>Bodies and query strings are not logged, because calls to third-party services such as
 * Keycloak carry credentials, tokens and personal data. Successful calls are logged at DEBUG, calls
 * answered with an error status at WARN; the caller decides whether the call failed.
 *
 * @see org.springframework.web.reactive.function.client.ExchangeFilterFunction
 */
public class WebClientLogger implements ExchangeFilterFunction {
  final Logger log;

  /**
   * Constructs a new WebClientLogger for the specified class.
   *
   * @param clazz the class to create the logger for
   */
  public WebClientLogger(Class<?> clazz) {
    log = LoggerFactory.getLogger(clazz);
  }

  /**
   * Logs the method, path and status of an HTTP exchange.
   *
   * @param request the original client request
   * @param next the next exchange function in the chain
   * @return Mono containing the client response
   */
  @Override
  public Mono<ClientResponse> filter(
      @NotNull ClientRequest request, @NotNull ExchangeFunction next) {
    String method = request.method().name();
    String path = request.url().getPath();
    return next.exchange(request)
        .doOnNext(
            response -> {
              int status = response.statusCode().value();
              if (response.statusCode().isError()) {
                log.warn("Third-party call {} {} -> {}", method, path, status);
              } else {
                log.debug("Third-party call {} {} -> {}", method, path, status);
              }
            });
  }
}
