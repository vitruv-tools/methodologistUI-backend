package com.vitruv.methodologist.apihandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken;
import com.vitruv.methodologist.exception.ParseThirdPartyApiResponseException;
import com.vitruv.methodologist.exception.UnauthorizedException;
import com.vitruv.methodologist.exception.UncheckedRuntimeException;
import java.time.Duration;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Component responsible for handling authentication-related API calls to Keycloak. Provides
 * functionality for token exchange, access token retrieval, and refresh token operations.
 */
@Component
public class KeycloakApiHandler {
  private final WebClient webClient;
  private final ObjectMapper mapper = new ObjectMapper();

  @Value("${investino.keycloak.client-id}")
  private String clientId;

  public static final int RESPONSE_TIMEOUT_IN_SECONDS = 5;

  public static final String POST_TOKEN_URL = "/realms/methodologist/protocol/openid-connect/token";

  /**
   * Constructs a KeycloakApiHandler with the specified base URL. Configures WebClient with response
   * timeout and content type headers.
   *
   * @param baseUrl the base URL of the Keycloak server
   */
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

  /**
   * Exchanges a token for a new token with different claims or scope.
   *
   * @param token the original token to exchange
   * @param userId the ID of the user for whom to exchange the token
   * @return KeycloakWebToken containing the exchanged token information
   * @throws com.vitruv.methodologist.exception.ParseThirdPartyApiResponseException if token exchange request body cannot be parsed
   * @throws com.vitruv.methodologist.exception.UncheckedRuntimeException if the exchange request fails
   */
  public KeycloakWebToken getExchangeTokenOrThrow(String token, String userId) {
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    try {
      Map<String, String> fieldMap =
          mapper.convertValue(
              new ExchangeTokenPostBody(token, userId),
              new TypeReference<Map<String, String>>() {});
      formData.setAll(fieldMap);
    } catch (Exception e) {
      throw new ParseThirdPartyApiResponseException(e.getMessage());
    }

    return webClient
        .post()
        .uri(POST_TOKEN_URL)
        .body(BodyInserters.fromFormData(formData))
        .retrieve()
        .bodyToMono(KeycloakWebToken.class)
        .doOnError(
            (exception) -> {
              throw new UncheckedRuntimeException(exception.getMessage());
            })
        .block();
  }

  /**
   * Retrieves an access token using username and password credentials.
   *
   * @param username the user's username
   * @param password the user's password
   * @return KeycloakWebToken containing the access token information
   * @throws UnauthorizedException if credentials are invalid
   * @throws com.vitruv.methodologist.exception.UncheckedRuntimeException if the token request fails
   */
  public KeycloakWebToken getAccessTokenOrThrow(String username, String password) {
    LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<String, String>();
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
                          if (response.statusCode().equals(HttpStatus.UNAUTHORIZED)) {
                            throw new UnauthorizedException();
                          }
                          throw new UncheckedRuntimeException(body);
                        }))
        .bodyToMono(KeycloakWebToken.class)
        .doOnError(
            exception -> {
              throw new UncheckedRuntimeException(exception.getMessage());
            })
        .block();
  }

  /**
   * Retrieves a new access token using a refresh token.
   *
   * @param refreshToken the refresh token to use
   * @return KeycloakWebToken containing the new access token information
   * @throws UnauthorizedException if the refresh token is invalid
   * @throws com.vitruv.methodologist.exception.UncheckedRuntimeException if the token refresh request fails
   */
  public KeycloakWebToken getAccessTokenByRefreshToken(String refreshToken) {
    LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<String, String>();
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
                  new UncheckedRuntimeException("Client error: " + response.statusCode()));
            })
        .onStatus(
            HttpStatusCode::is5xxServerError,
            response ->
                Mono.error(new UncheckedRuntimeException("Client error: " + response.statusCode())))
        .bodyToMono(KeycloakWebToken.class)
        .block();
  }

  /** DTO for token exchange request body parameters. */
  @Setter
  @Getter
  @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
  public static class ExchangeTokenPostBody {

    /**
     * Constructs a new ExchangeTokenPostBody with the specified tokens.
     *
     * @param subjectToken the token to exchange
     * @param requestedSubject the subject for whom to exchange the token
     */
    public ExchangeTokenPostBody(String subjectToken, String requestedSubject) {
      this.subjectToken = subjectToken;
      this.requestedSubject = requestedSubject;
    }

    /** DTO for token request body parameters. */
    String clientId = "tardi-manager-customer-web-panel";

    String grantType = "urn:ietf:params:oauth:grant-type:token-exchange";
    String subjectToken;
    String subjectTokenType = "urn:ietf:params:oauth:token-type:access_token";
    String audience = "tardi-manager-customer-web-panel";
    String requestedTokenType = "urn:ietf:params:oauth:token-type:refresh_token";
    String requestedSubject;
  }

  /**
   * Data Transfer Object (DTO) representing the request body parameters for token operations.
   * Uses snake_case naming strategy for JSON serialization.
   */
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
