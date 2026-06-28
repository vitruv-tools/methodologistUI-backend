package tools.vitruv.methodologist.general.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Service responsible for sending email messages using an SMTP backend.
 *
 * <p>Configures a {@link JavaMailSenderImpl} from application properties and provides methods to
 * render simple templates from classpath resources and send HTML emails (OTP and password reset).
 */
@Service
public class SmtpMailService {

  private final JavaMailSender mailSender;
  private final String fromEmail;
  private final String fromName;

  /**
   * Constructs the SMTP mail service and configures the underlying JavaMail sender.
   *
   * @param host SMTP host
   * @param port SMTP port
   * @param username SMTP username
   * @param password SMTP password
   * @param fromEmail default sender email address; must not be {@code null}
   * @param fromName default sender display name; must not be {@code null}
   * @param startTls whether STARTTLS is enabled
   * @param ssl whether SSL is enabled
   * @param connectionTimeoutMs connection timeout in milliseconds
   * @param timeoutMs read timeout in milliseconds
   * @param writeTimeoutMs write timeout in milliseconds
   * @throws NullPointerException if {@code fromEmail} or {@code fromName} is {@code null}
   */
  public SmtpMailService(
      @Value("${mail.smtp.host}") String host,
      @Value("${mail.smtp.port}") int port,
      @Value("${mail.smtp.username}") String username,
      @Value("${mail.smtp.password}") String password,
      @Value("${mail.smtp.fromEmail}") String fromEmail,
      @Value("${mail.smtp.fromName}") String fromName,
      @Value("${mail.smtp.starttls:true}") boolean startTls,
      @Value("${mail.smtp.ssl:false}") boolean ssl,
      @Value("${mail.smtp.connectionTimeoutMs:10000}") int connectionTimeoutMs,
      @Value("${mail.smtp.timeoutMs:10000}") int timeoutMs,
      @Value("${mail.smtp.writeTimeoutMs:10000}") int writeTimeoutMs) {
    this.fromEmail = Objects.requireNonNull(fromEmail);
    this.fromName = Objects.requireNonNull(fromName);

    JavaMailSenderImpl impl = new JavaMailSenderImpl();
    impl.setHost(host);
    impl.setPort(port);
    impl.setUsername(username);
    impl.setPassword(password);
    impl.setDefaultEncoding("UTF-8");

    var props = impl.getJavaMailProperties();
    props.put("mail.transport.protocol", "smtp");
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
    props.put("mail.smtp.ssl.enable", String.valueOf(ssl));
    props.put("mail.smtp.connectiontimeout", String.valueOf(connectionTimeoutMs));
    props.put("mail.smtp.timeout", String.valueOf(timeoutMs));
    props.put("mail.smtp.writetimeout", String.valueOf(writeTimeoutMs));

    this.mailSender = impl;
  }

  /**
   * Sends an OTP (one-time password) email to a recipient.
   *
   * <p>Renders a small HTML template from the classpath and sends it via configured SMTP server.
   *
   * @param toEmail recipient email address
   * @param toName recipient display name (may be {@code null} or blank)
   * @param otpCode the one-time code to include in the message
   * @param ttlMinutes time-to-live for the OTP, in minutes
   * @throws RuntimeException if reading the template or sending the message fails
   */
  public void sendOtpMail(String toEmail, String toName, String otpCode, int ttlMinutes) {
    String subject = "Verify your email";
    String html =
        loadAndRenderTemplate(
            "templates/mail/send_otp_message.txt",
            Map.of(
                "otp_code", otpCode,
                "ttl_minutes", String.valueOf(ttlMinutes),
                "recipient_name", safe(toName)));
    sendHtml(toEmail, toName, subject, html);
  }

  /**
   * Sends a "forgot password" email containing a newly generated password.
   *
   * <p>Renders a small HTML template from the classpath and sends it via configured SMTP server.
   *
   * @param toEmail recipient email address
   * @param toName recipient display name (may be {@code null} or blank)
   * @param newPassword the new password to include in the email
   * @throws RuntimeException if reading the template or sending the message fails
   */
  public void sendForgotPasswordMail(String toEmail, String toName, String newPassword) {
    String subject = "Your new password";
    String html =
        loadAndRenderTemplate(
            "templates/mail/send_new_password.txt",
            Map.of("password", newPassword, "recipient_name", safe(toName)));
    sendHtml(toEmail, toName, subject, html);
  }

  /**
   * Sends an HTML email using the configured {@link JavaMailSender}.
   *
   * @param toEmail recipient email address
   * @param toName recipient display name (may be {@code null} or blank)
   * @param subject email subject
   * @param html HTML payload (already escaped/rendered)
   * @throws RuntimeException if creating or sending the message fails
   */
  private void sendHtml(String toEmail, String toName, String subject, String html) {
    try {
      MimeMessage msg = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(msg, StandardCharsets.UTF_8.name());
      helper.setFrom(fromEmail, fromName);
      helper.setTo(toEmail);
      if (toName != null && !toName.isBlank()) {
        helper.setTo(String.format("%s <%s>", toName, toEmail));
      }
      helper.setSubject(subject);
      helper.setText(html, true); // true => HTML
      mailSender.send(msg);
    } catch (MessagingException | MailException | java.io.UnsupportedEncodingException e) {
      throw new RuntimeException("Failed to send email via SMTP: " + e.getMessage(), e);
    }
  }

  /**
   * Loads a template from the classpath and replaces simple placeholders of the form {@code
   * {{key}}} with escaped values provided in {@code vars}.
   *
   * @param classpathPath classpath resource path to the template file
   * @param vars map of placeholder names to values
   * @return rendered HTML string
   */
  private String loadAndRenderTemplate(String classpathPath, Map<String, String> vars) {
    String raw = readClasspathFile(classpathPath);
    String rendered = raw;

    // Simple placeholder replacement: {{key}}
    for (var entry : vars.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue() == null ? "" : entry.getValue();
      rendered = rendered.replace("{{" + key + "}}", htmlEscape(value));
    }

    // If any placeholders remain, you’ll see it immediately instead of silently sending garbage.
    return rendered;
  }

  /**
   * Reads the contents of a classpath resource as UTF-8.
   *
   * @param classpathPath classpath resource path
   * @return file contents as UTF-8 string
   * @throws RuntimeException if the resource cannot be read
   */
  private String readClasspathFile(String classpathPath) {
    try (var in = new ClassPathResource(classpathPath).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(
          "Failed to load email template from classpath: " + classpathPath, e);
    }
  }

  private String safe(String s) {
    return s == null ? "" : s;
  }

  private String htmlEscape(String s) {
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;");
  }
}
