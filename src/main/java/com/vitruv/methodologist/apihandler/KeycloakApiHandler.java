package com.vitruv.methodologist.apihandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import com.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken;
import com.vitruv.methodologist.exception.ParseException;
import com.vitruv.methodologist.exception.UnauthorizedException;
import com.vitruv.methodologist.exception.UncaughtRuntimeException;
import org.springframework.web.reactive.function.BodyInserters;
import java.time.Duration;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Component responsible for handling authentication-related API calls to Keycloak.
 * Provides functionality for token exchange, access token retrieval, and refresh token operations.
 */
@Component
public class KeycloakApiHandler {
  private final WebClient webClient;
  private final ObjectMapper mapper = new ObjectMapper();

  @Value("${investino.keycloak.client-id}")
  private String clientId;

  public static final int RESPONSE_TIMEOUT_IN_SECONDS = 5;

  public static final String POST_TOKEN_URL = "/realms/methodologist/protocol/openid-connect/token";

  public KeycloakApiHandler(@Value("${third_api.keycloak.base_url}") String baseUrl) {
    HttpClient httpClient =
        HttpClient.create().responseTimeout(Duration.ofSeconds(RESPONSE_TIMEOUT_IN_SECONDS));

    this.webClient =
        WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
  }

  public KeycloakWebToken getExchangeTokenOrThrow(String token, String userId) {
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    try {
      Map<String, String> fieldMap =
          mapper.convertValue(
              new ExchangeTokenPostBody(token, userId),
              new TypeReference<Map<String, String>>() {});
      formData.setAll(fieldMap);
    } catch (Exception e) {
      throw new ParseException(e.getMessage());
    }

    return webClient
        .post()
        .uri(POST_TOKEN_URL)
        .body(BodyInserters.fromFormData(formData))
        .retrieve()
        .bodyToMono(KeycloakWebToken.class)
        .doOnError(
            (exception) -> {
              throw new UncaughtRuntimeException(exception.getMessage());
            })
        .block();
  }

  public KeycloakWebToken getAccessTokenOrThrow(String username, String password) {
    var formData = new LinkedMultiValueMap<String, String>();
    formData.add("client_id", clientId);
    formData.add("grant_type", "password");
    formData.add("username", username);
    formData.add("password", password);

    return webClient
        .post()
        .uri(POST_TOKEN_URL)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(BodyInserters.fromFormData(formData))
        .retrieve()
        .onStatus(
            HttpStatusCode::isError,
            response ->
                response
                    .bodyToMono(String.class)
                    .handle(
                        (body, handler) -> {
                          if (response.statusCode().equals(HttpStatus.UNAUTHORIZED))
                            throw new UnauthorizedException();
                          throw new UncaughtRuntimeException(body);
                        }))
        .bodyToMono(KeycloakWebToken.class)
        .doOnError(
            exception -> {
              throw new UncaughtRuntimeException(exception.getMessage());
            })
        .block();
  }

  public KeycloakWebToken getAccessTokenByRefreshToken(String refreshToken) {
    var formData = new LinkedMultiValueMap<String, String>();
    formData.add("client_id", clientId);
    formData.add("grant_type", "refresh_token");
    formData.add("refresh_token", refreshToken);

    return webClient
        .post()
        .uri(POST_TOKEN_URL) // Example:
        // http://localhost:8383/realms/investino/protocol/openid-connect/token
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(BodyInserters.fromFormData(formData))
        .retrieve()
        .onStatus(
            HttpStatusCode::is4xxClientError,
            response -> {
              if (response.statusCode().equals(HttpStatus.UNAUTHORIZED)) {
                return Mono.error(new UnauthorizedException());
              }
              return Mono.error(
                  new UncaughtRuntimeException("Client error: " + response.statusCode()));
            })
        .onStatus(
            HttpStatusCode::is5xxServerError,
            response ->
                Mono.error(new UncaughtRuntimeException("Client error: " + response.statusCode())))
        .bodyToMono(KeycloakWebToken.class)
        .block();
  }

  @Setter
  @Getter
  @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
  public static class ExchangeTokenPostBody {

    public ExchangeTokenPostBody(String subjectToken, String requestedSubject) {
      this.subjectToken = subjectToken;
      this.requestedSubject = requestedSubject;
    }

    String clientId = "tardi-manager-customer-web-panel";
    String grantType = "urn:ietf:params:oauth:grant-type:token-exchange";
    String subjectToken;
    String subjectTokenType = "urn:ietf:params:oauth:token-type:access_token";
    String audience = "tardi-manager-customer-web-panel";
    String requestedTokenType = "urn:ietf:params:oauth:token-type:refresh_token";
    String requestedSubject;
  }

  @Setter
  @Getter
  @Builder
  @AllArgsConstructor
  @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
  public static class TokenPostBody {
    private String clientId;
    private String grantType;
    private String username;
    private String password;
  }
}
