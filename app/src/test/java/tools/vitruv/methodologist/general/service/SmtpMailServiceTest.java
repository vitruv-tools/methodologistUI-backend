package tools.vitruv.methodologist.general.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests for {@link SmtpMailService}. */
@ExtendWith(MockitoExtension.class)
public class SmtpMailServiceTest {

  private JavaMailSender mailSender;
  private SmtpMailService smtpMailService;
  private MimeMessage mimeMessageMock;

  private final String recipientEmail = "recipient@gmail.com";
  private final String recipientName = "recipientName";

  @BeforeEach
  void setUp() {
    String host = "localhost";
    int port = 587;
    String username = "smtp-user";
    String password = "smtp-password";
    String fromEmail = "test@gmail.com";
    String fromName = "testName";
    boolean startTls = true;
    boolean ssl = false;
    int connectionTimeoutMs = 5000;
    int timeoutMs = 5000;
    int writeTimeoutMs = 5000;

    smtpMailService =
        new SmtpMailService(
            host,
            port,
            username,
            password,
            fromEmail,
            fromName,
            startTls,
            ssl,
            connectionTimeoutMs,
            timeoutMs,
            writeTimeoutMs);

    mailSender = mock(JavaMailSender.class);
    ReflectionTestUtils.setField(smtpMailService, "mailSender", mailSender);

    mimeMessageMock = mock(MimeMessage.class);
  }

  // Constructor guard tests

  @Test
  void constructor_withNullFromEmail_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            new SmtpMailService(
                "localhost", 587, "user", "pass", null, "Name", true, false, 5000, 5000, 5000));
  }

  @Test
  void constructor_withNullFromName_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            new SmtpMailService(
                "localhost",
                587,
                "user",
                "pass",
                "from@test.com",
                null,
                true,
                false,
                5000,
                5000,
                5000));
  }

  // sendOtpMail -> happy paths

  @Test
  void sendOtpMail_success() {
    when(mailSender.createMimeMessage()).thenReturn(mimeMessageMock);

    assertDoesNotThrow(
        () -> smtpMailService.sendOtpMail(recipientEmail, recipientName, "123456", 5));

    verify(mailSender, times(1)).send(mimeMessageMock);
  }

  @Test
  void sendOtpMail_withNullRecipientName_handlesGracefully() {
    when(mailSender.createMimeMessage()).thenReturn(mimeMessageMock);

    assertDoesNotThrow(() -> smtpMailService.sendOtpMail(recipientEmail, null, "123456", 5));

    verify(mailSender, times(1)).send(mimeMessageMock);
  }

  @Test
  void sendOtpMail_withBlankRecipientName_handlesGracefully() {
    when(mailSender.createMimeMessage()).thenReturn(mimeMessageMock);

    assertDoesNotThrow(() -> smtpMailService.sendOtpMail(recipientEmail, "   ", "123456", 5));

    verify(mailSender, times(1)).send(mimeMessageMock);
  }

  // sendOtpMail -> error paths

  @Test
  void sendOtpMail_mailSenderThrowsException_throwsRuntimeException() {
    when(mailSender.createMimeMessage()).thenReturn(mimeMessageMock);
    doThrow(new MailSendException("SMTP Connection dropped"))
        .when(mailSender)
        .send(any(MimeMessage.class));

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> smtpMailService.sendOtpMail(recipientEmail, recipientName, "123456", 5));

    assertTrue(exception.getMessage().contains("Failed to send email via SMTP"));
    assertNotNull(exception.getCause());
  }

  @Test
  void sendOtpMail_createMimeMessageThrows_propagatesException() {
    when(mailSender.createMimeMessage())
        .thenThrow(new RuntimeException("Mail session unavailable"));

    assertThrows(
        RuntimeException.class,
        () -> smtpMailService.sendOtpMail(recipientEmail, recipientName, "123456", 5));
  }

  // sendForgotPasswordMail — happy paths

  @Test
  void sendForgotPasswordMail_success() {
    when(mailSender.createMimeMessage()).thenReturn(mimeMessageMock);

    assertDoesNotThrow(
        () ->
            smtpMailService.sendForgotPasswordMail(
                recipientEmail, recipientName, "new-secure-pass"));

    verify(mailSender, times(1)).send(mimeMessageMock);
  }

  @Test
  void sendForgotPasswordMail_withNullRecipientName_handlesGracefully() {
    when(mailSender.createMimeMessage()).thenReturn(mimeMessageMock);

    assertDoesNotThrow(
        () -> smtpMailService.sendForgotPasswordMail(recipientEmail, null, "new-secure-pass"));

    verify(mailSender, times(1)).send(mimeMessageMock);
  }

  @Test
  void sendForgotPasswordMail_withBlankRecipientName_handlesGracefully() {
    when(mailSender.createMimeMessage()).thenReturn(mimeMessageMock);

    assertDoesNotThrow(
        () -> smtpMailService.sendForgotPasswordMail(recipientEmail, "   ", "new-secure-pass"));

    verify(mailSender, times(1)).send(mimeMessageMock);
  }

  // sendForgotPasswordMail — error paths

  @Test
  void sendForgotPasswordMail_mailSenderThrowsException_throwsRuntimeException() {
    when(mailSender.createMimeMessage()).thenReturn(mimeMessageMock);
    doThrow(new MailSendException("SMTP failed")).when(mailSender).send(any(MimeMessage.class));

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> smtpMailService.sendForgotPasswordMail(recipientEmail, recipientName, "pw"));

    assertTrue(exception.getMessage().contains("Failed to send email via SMTP"));
    assertNotNull(exception.getCause());
  }

  @Test
  void sendForgotPasswordMail_createMimeMessageThrows_propagatesException() {
    when(mailSender.createMimeMessage())
        .thenThrow(new RuntimeException("Mail session unavailable"));

    assertThrows(
        RuntimeException.class,
        () -> smtpMailService.sendForgotPasswordMail(recipientEmail, recipientName, "pw"));
  }
}
