package tools.vitruv.methodologist.apihandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Duration;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import tools.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken;
import tools.vitruv.methodologist.exception.ParseThirdPartyApiResponseException;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.exception.UncheckedRuntimeException;

/**
 * Component responsible for handling authentication-related API calls to Keycloak. Provides
 * functionality for token exchange, access token retrieval, and refresh token operations.
 */
@Component
public class KeycloakApiHandler {
  public static final int RESPONSE_TIMEOUT_IN_SECONDS = 5;
  public static final String POST_TOKEN_URL = "/realms/methodologist/protocol/openid-connect/token";
  private static final String OAUTH_ERROR = "error";
  private static final String INVALID_GRANT = "invalid_grant";
  private static final String UNREADABLE_ERROR = "unreadable";
  private final WebClient webClient;
  private final ObjectMapper mapper = new ObjectMapper();

  @Value("${methodologist.keycloak.client-id}")
  private String clientId;

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
   * @throws tools.vitruv.methodologist.exception.ParseThirdPartyApiResponseException if token
   *     exchange request body cannot be parsed
   * @throws tools.vitruv.methodologist.exception.UncheckedRuntimeException if the exchange request
   *     fails
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
        .onErrorMap(
            exception ->
                new UncheckedRuntimeException("Token exchange with Keycloak failed", exception))
        .block();
  }

  /**
   * Retrieves an access token using username and password credentials.
   *
   * @param username the user's username
   * @param password the user's password
   * @return KeycloakWebToken containing the access token information
   * @throws tools.vitruv.methodologist.exception.UnauthorizedException if Keycloak rejects the
   *     credentials
   * @throws tools.vitruv.methodologist.exception.UncheckedRuntimeException if Keycloak answers the
   *     token request with another error
   * @throws org.springframework.web.reactive.function.client.WebClientException if Keycloak cannot
   *     be reached
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
        .onStatus(HttpStatusCode::isError, KeycloakApiHandler::tokenRequestError)
        .bodyToMono(KeycloakWebToken.class)
        .block();
  }

  /**
   * Retrieves a new access token using a refresh token.
   *
   * @param refreshToken the refresh token to use
   * @return KeycloakWebToken containing the new access token information
   * @throws tools.vitruv.methodologist.exception.UnauthorizedException if Keycloak rejects the
   *     refresh token, e.g. because it expired
   * @throws tools.vitruv.methodologist.exception.UncheckedRuntimeException if Keycloak answers the
   *     token request with another error
   * @throws org.springframework.web.reactive.function.client.WebClientException if Keycloak cannot
   *     be reached
   */
  public KeycloakWebToken getAccessTokenByRefreshToken(String refreshToken) {
    LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<String, String>();
    formData.add("client_id", clientId);
    formData.add("grant_type", "refresh_token");
    formData.add("refresh_token", refreshToken);

    return webClient
        .post()
        .uri(POST_TOKEN_URL)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(BodyInserters.fromFormData(formData))
        .retrieve()
        .onStatus(HttpStatusCode::isError, KeycloakApiHandler::tokenRequestError)
        .bodyToMono(KeycloakWebToken.class)
        .block();
  }

  /**
   * Turns an error response of the token endpoint into an exception. A grant that Keycloak rejects
   * (OAuth error {@code invalid_grant}: wrong credentials, a disabled account, an expired refresh
   * token) becomes an {@link UnauthorizedException}; any other error, such as a misconfigured
   * client or a failing Keycloak, becomes an {@link UncheckedRuntimeException}. The response body
   * is not put into the exception, only the status and the OAuth error code.
   */
  private static Mono<Throwable> tokenRequestError(ClientResponse response) {
    int status = response.statusCode().value();
    return response
        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
        .map(body -> String.valueOf(body.get(OAUTH_ERROR)))
        .onErrorReturn(UNREADABLE_ERROR)
        .defaultIfEmpty(UNREADABLE_ERROR)
        .map(error -> toTokenRequestException(status, error));
  }

  private static Throwable toTokenRequestException(int status, String error) {
    boolean rejectedGrant =
        INVALID_GRANT.equals(error)
            || (status == HttpStatus.UNAUTHORIZED.value() && UNREADABLE_ERROR.equals(error));
    if (rejectedGrant) {
      return new UnauthorizedException();
    }
    return new UncheckedRuntimeException(
        String.format(
            "Keycloak answered the token request with status %d and error '%s'", status, error));
  }

  /** DTO for token exchange request body parameters. */
  @Setter
  @Getter
  @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
  public static class ExchangeTokenPostBody {

    /** DTO for token request body parameters. */
    String clientId = "tardi-manager-customer-web-panel";

    String grantType = "urn:ietf:params:oauth:grant-type:token-exchange";
    String subjectToken;
    String subjectTokenType = "urn:ietf:params:oauth:token-type:access_token";
    String audience = "tardi-manager-customer-web-panel";
    String requestedTokenType = "urn:ietf:params:oauth:token-type:refresh_token";
    String requestedSubject;

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
  }

  /**
   * Data Transfer Object (DTO) representing the request body parameters for token operations. Uses
   * snake_case naming strategy for JSON serialization.
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
