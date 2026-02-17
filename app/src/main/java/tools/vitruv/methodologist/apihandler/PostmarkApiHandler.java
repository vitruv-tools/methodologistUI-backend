package tools.vitruv.methodologist.apihandler;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.vitruv.methodologist.exception.UncheckedRuntimeException;

/**
 * Client component responsible for sending templated OTP emails via the Postmark API.
 *
 * <p>This class builds a configured {@link WebClient} using {@link GeneralWebClient} and provides a
 * convenience method to post a template-based OTP email.
 */
@Component
@Slf4j
public class PostmarkApiHandler {
  private static final String POST_SEND_EMAIL = "/email/withTemplate";
  private final WebClient webClient;

  /**
   * Constructs a new PostmarkApiHandler.
   *
   * @param generalWebClient shared web client factory used to create configured clients
   * @param baseUrl base URL of the Postmark API
   * @param token Postmark server token used for authentication
   */
  public PostmarkApiHandler(
      GeneralWebClient generalWebClient,
      @Value("${third_api.postmark.base_url}") String baseUrl,
      @Value("${third_api.postmark.token}") String token) {
    this.webClient =
        generalWebClient.get(
            baseUrl,
            headers -> {
              headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
              headers.set("X-Postmark-Server-Token", token);
            });
  }

  /**
   * Sends an OTP email to a single recipient using a Postmark template.
   *
   * @param to the recipient email address
   * @param otp the one-time password value to include in the template
   * @param time the TTL string to include in the template (e.g. "5 minutes")
   * @throws UncheckedRuntimeException when the remote API call fails
   */
  public void postOTPMail(String to, String otp, String time) {
    webClient
        .post()
        .uri(POST_SEND_EMAIL)
        .bodyValue(
            PostOtpMail.builder()
                .from("mohammadali.mirzaei@kit.edu")
                .to(to)
                .templateId(43479555L)
                .templateModel(
                    PostOtpMail.TemplateModel.builder().ttlMinutes(time).otpCode(otp).build())
                .build())
        .retrieve()
        .bodyToMono(String.class)
        .doOnError(
            exception -> {
              throw new UncheckedRuntimeException(exception.getMessage());
            })
        .block();
  }

  /**
   * Sends a templated password email to a single recipient via Postmark.
   *
   * <p>Builds the request payload using the configured template and template model, posts to the
   * Postmark template endpoint with the configured WebClient, and blocks until the call completes.
   *
   * @param to recipient email address; must not be null or blank
   * @param password the new password to include in the template
   * @throws UncheckedRuntimeException if the remote API call fails
   */
  public void postPasswordMail(String to, String password) {
    webClient
        .post()
        .uri(POST_SEND_EMAIL)
        .bodyValue(
            PostPasswordMail.builder()
                .from("mohammadali.mirzaei@kit.edu")
                .to(to)
                .templateId(43556523L)
                .templateModel(PostPasswordMail.TemplateModel.builder().password(password).build())
                .build())
        .retrieve()
        .bodyToMono(String.class)
        .doOnError(
            exception -> {
              throw new UncheckedRuntimeException(exception.getMessage());
            })
        .block();
  }

  /** Request payload for Postmark's template send endpoint (OTP email). */
  @Setter
  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class PostOtpMail {
    String from;
    String to;
    Long templateId;
    TemplateModel templateModel;

    /** Template model values that are serialized in snake_case for the Postmark template. */
    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class TemplateModel {
      String ttlMinutes;
      String otpCode;
    }
  }

  /** Request payload for Postmark's template send endpoint (OTP email). */
  @Setter
  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class PostPasswordMail {
    String from;
    String to;
    Long templateId;
    TemplateModel templateModel;

    /**
     * Template model values for the password email template.
     *
     * <p>Used as the template model when sending password-reset emails via Postmark. Contains the
     * plain-text password value that will be injected into the template.
     */
    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TemplateModel {
      String password;
    }
  }
}
