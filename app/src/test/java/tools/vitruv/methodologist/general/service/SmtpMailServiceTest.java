package tools.vitruv.methodologist.general.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SmtpMailServiceTest {
  private SmtpMailService mailService;

  @BeforeEach
  void setUp() {
    mailService =
        new SmtpMailService(
            "smtp.kit.edu",
            587,
            "svc-mwa-00001@kit.edu",
            "e)6mZC4/*6",
            "no-reply@mwa.sdq.kastel.kit.edu",
            "Methodologist System",
            true,
            false,
            10000,
            10000,
            10000);
  }

  @Test
  void sendRealOtpMail() {
    mailService.sendOtpMail("mr.mirzaei.ma@gmail.com.com", "Mohammadali", "678", 5);
    System.out.println("Mail sent!");
  }
}
