package tools.vitruv.methodologist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.RestController;

/**
 * Main entry point for the Methodologist Spring Boot application. Enables scheduling and registers
 * REST controllers.
 */
@EnableScheduling
@SpringBootApplication
@RestController
public class MethodologistApplication {
  /**
   * Starts the Spring Boot application.
   *
   * @param args command-line arguments passed on startup
   */
  public static void main(String[] args) {
    SpringApplication.run(MethodologistApplication.class, args);
  }
}
