package tools.vitruv.methodologist.apihandler;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
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
 * Handles interactions with the Mailjet API for sending emails.
 *
 * <p>This class uses a configured WebClient instance to perform API calls to the Mailjet service.
 * It supports sending emails with templates and custom variables.
 */
@Component
@Slf4j
public class MailjetApiHandler {
  private static final String POST_SEND_EMAIL = "/v3.1/send";
  private final WebClient webClient;
  private final String fromEmail;
  private final String fromName;

  /**
   * Create a new MailjetApiHandler.
   *
   * <p>Initializes a WebClient configured for Mailjet API calls and sets default sender details.
   *
   * @param generalWebClient provider of configured WebClient instances
   * @param baseUrl Mailjet base URL (e.g. <a href="https://api.mailjet.com">...</a>)
   * @param userName Mailjet API username for basic authentication
   * @param password Mailjet API password for basic authentication
   * @param fromEmail default sender email address
   * @param fromName default sender display name
   */
  public MailjetApiHandler(
      GeneralWebClient generalWebClient,
      @Value("${third_api.mailjet.base_url}") String baseUrl,
      @Value("${third_api.mailjet.user_name}") String userName,
      @Value("${third_api.mailjet.password}") String password,
      @Value("${third_api.mailjet.from_email}") String fromEmail,
      @Value("${third_api.mailjet.from_name}") String fromName) {
    this.fromEmail = fromEmail;
    this.fromName = fromName;
    this.webClient =
        generalWebClient.get(
            baseUrl,
            headers -> {
              headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
              headers.setBasicAuth(userName, password);
            });
  }

  /**
   * Send an email using Mailjet.
   *
   * <p>Builds the request payload and posts it to the Mailjet v3.1 send endpoint. If the remote
   * response indicates an error, the response body will be wrapped into an
   * UncaughtRuntimeException.
   *
   * @param to recipient email address
   * @param toName recipient display name
   * @param subject subject of the email
   * @param templateId Mailjet template ID to use
   * @param variable template variables (must extend PostSendMail.Message.Variable)
   */
  public void postMail(
      String to,
      String toName,
      String subject,
      Long templateId,
      PostSendMail.Message.Variable variable) {
    var body =
        PostSendMail.builder()
            .messages(
                List.of(
                    PostSendMail.Message.builder()
                        .subject(subject)
                        .templateID(templateId)
                        .templateLanguage(true)
                        .variables(variable)
                        .from(
                            PostSendMail.Message.Email.builder()
                                .emailAddress(fromEmail)
                                .name(fromName)
                                .build())
                        .to(
                            List.of(
                                PostSendMail.Message.Email.builder()
                                    .emailAddress(to)
                                    .name(toName)
                                    .build()))
                        .build()))
            .build();
    webClient
        .post()
        .uri(POST_SEND_EMAIL)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(String.class)
        .doOnError(
            (exception) -> {
              throw new UncheckedRuntimeException(exception.getMessage());
            })
        .block();
  }

  /**
   * Payload wrapper for Mailjet v3.1 send API.
   *
   * <p>Contains the list of messages to be sent. Uses Lombok to generate boilerplate.
   */
  @Setter
  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class PostSendMail {
    List<Message> messages;

    /** Represents a single Mailjet message entry. */
    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
      Boolean templateLanguage;

      @JsonProperty("templateID")
      Long templateID;

      String subject;
      Variable variables;
      Email from;
      List<Email> to;

      /** Simple email descriptor (address and display name). */
      @Setter
      @Getter
      @NoArgsConstructor
      @AllArgsConstructor
      @Builder
      public static class Email {
        String emailAddress;
        String name;
      }

      /**
       * Base type for template variables. Subclasses define concrete variable shapes expected by
       * templates.
       *
       * <p>Serialized field names use snake_case due to {@link JsonNaming} on the class.
       */
      @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
      public abstract static class Variable {
        // This class is abstract and used as a base class for different variable types.
      }

      /**
       * Variables used for OTP templates.
       *
       * <p>Fields: - ttl_minutes: time-to-live in minutes for the OTP (serialized as snake_case) -
       * otp_code: the one-time password code
       */
      @Setter
      @Getter
      @NoArgsConstructor
      @AllArgsConstructor
      @Builder
      public static class VariableOTP extends Variable {
        String ttlMinutes;
        String otpCode;
      }

      /** Variables used for forgot-password templates. */
      @Setter
      @Getter
      @NoArgsConstructor
      @AllArgsConstructor
      @Builder
      public static class ForgotPassword extends Variable {
        String newPassword;
      }
    }
  }
}
