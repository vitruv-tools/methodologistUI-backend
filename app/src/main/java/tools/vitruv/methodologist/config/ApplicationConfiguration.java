package tools.vitruv.methodologist.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Application configuration class for setting up global CORS policies. Reads allowed origins and
 * headers from application properties.
 */
@Configuration
public class ApplicationConfiguration {
  @Value("${allowed.origins}")
  private String allowedOrigins;

  @Value("${allowed.headers}")
  private String allowedHeaders;

  /**
   * Configures CORS mappings for the application. Allows specified origins, headers, and HTTP
   * methods.
   *
   * @return a {@link org.springframework.web.servlet.config.annotation.WebMvcConfigurer} bean with
   *     CORS settings
   */
  @Bean
  public WebMvcConfigurer corsConfiguror() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/**")
            .allowedHeaders(allowedHeaders.split(","))
            .allowedMethods("GET", "PUT", "POST", "PATCH", "DELETE", "OPTIONS", "HEAD")
            .allowedOrigins(allowedOrigins.split(","));
      }
    };
  }
}
