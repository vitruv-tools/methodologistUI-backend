package com.vitruv.methodologist.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Application configuration class for setting up global CORS policies.
 * Reads allowed origins and headers from application properties.
 */
@Configuration
public class ApplicationConfiguration {
  @Value("${allowed.origins}")
  private String allowedOrigins;

  @Value("${allowed.headers}")
  private String allowedHeaders;

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
