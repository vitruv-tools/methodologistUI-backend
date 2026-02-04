package tools.vitruv.methodologist.apihandler;

import java.io.IOException;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

class MailjetApiHandlerTest {
  public static MockWebServer mockWebServer;
  private static GeneralWebClient generalWebClient;
  private final String filePath = "/apihandler/";
  private MailjetApiHandler mailjetApiHandler;

  @BeforeAll
  static void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
  }

  @AfterAll
  static void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @BeforeEach
  void initialize() {
    String baseUrl = String.format("http://localhost:%s", mockWebServer.getPort());
    generalWebClient = new GeneralWebClient(200);
    mailjetApiHandler =
        new MailjetApiHandler(
            generalWebClient, baseUrl, "userName", "password", "fromEmail", "Cyrus Team");
  }

  @Test
  @Disabled("It is not a local test, just to use if the network fetching is okay on demand!")
  void postRegisterWelcomeEmail() {
    mailjetApiHandler =
        new MailjetApiHandler(
            generalWebClient,
            "https://api.mailjet.com",
            "590779f4dd632204304e3e43ec669eb6",
            "e42dc46756e94ba7ec0a076f8a217b29",
            "admin-mwa@kastel.kit.edu",
            "no-reply");
    mailjetApiHandler.postMail(
        "mohammadali.mirzaei@kit.edu",
        "mohammadali",
        "dummy subject",
        7718546L,
        new MailjetApiHandler.PostSendMail.Message.VariableOTP("5", "123456"));
  }
}
