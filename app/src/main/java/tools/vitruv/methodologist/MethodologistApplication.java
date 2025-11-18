package tools.vitruv.methodologist;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
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
@SecurityScheme(
    name = "bearer-key",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
@OpenAPIDefinition(security = @SecurityRequirement(name = "bearer-key"))
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
