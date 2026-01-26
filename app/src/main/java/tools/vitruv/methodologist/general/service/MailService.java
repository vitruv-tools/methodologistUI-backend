package tools.vitruv.methodologist.general.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Service responsible for sending emails via a configured {@link JavaMailSender}.
 *
 * <p>The service reads the configured sender address and optional sender name from application
 * properties and uses the injected {@link JavaMailSender} to build and send MIME messages with HTML
 * content.
 */
@Service
public class MailService {

  private final JavaMailSender mailSender;
  private final String fromAddress;
  private final String fromName;

  /**
   * Constructs the mail service.
   *
   * @param mailSender injected {@link JavaMailSender} used to create and send messages
   * @param fromAddress the configured sender email address from property {@code app.mail.from}
   * @param fromName optional sender display name from property {@code app.mail.fromName}; may be
   *     empty
   */
  public MailService(
      JavaMailSender mailSender,
      @Value("${app.mail.from}") String fromAddress,
      @Value("${app.mail.fromName:}") String fromName) {
    this.mailSender = mailSender;
    this.fromAddress = fromAddress;
    this.fromName = fromName == null ? "" : fromName;
  }

  /**
   * Sends an HTML email to the specified recipient.
   *
   * <p>The method creates a MIME message, sets the from address (with optional display name),
   * recipient, subject and HTML body, and delegates sending to the injected {@link JavaMailSender}.
   *
   * @param to recipient email address
   * @param subject email subject
   * @param html HTML body of the email
   * @throws RuntimeException if message creation or sending fails
   */
  public void send(String to, String subject, String html) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      if (fromName.isBlank()) {
        helper.setFrom(fromAddress);
      } else {
        helper.setFrom(new InternetAddress(fromAddress, fromName));
      }

      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(html, true);

      mailSender.send(message);
    } catch (Exception e) {
      throw new MailSendException(e.getMessage());
    }
  }
}
