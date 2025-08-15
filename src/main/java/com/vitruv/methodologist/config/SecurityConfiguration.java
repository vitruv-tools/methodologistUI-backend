package com.vitruv.methodologist.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configures Spring Security settings including:
 * - CORS (based on allowed origins/headers)
 * - Disabling CSRF for APIs
 * - Permissive HTTP authorization (all requests allowed)
 * - Session authentication strategy for tracking sessions
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true)
public class SecurityConfiguration {

  /**
   * Comma-separated list of allowed origins for CORS.
   */
  @Value("${allowed.origins}")
  private String allowedOrigins;

  /**
   * Comma-separated list of allowed headers for CORS.
   */
  @Value("${allowed.headers}")
  private String allowedHeaders;

  /**
   * Defines the session authentication strategy for tracking authenticated sessions.
   *
   * @return the session authentication strategy bean
   */
  @Bean
  protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
    return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());
  }

  /**
   * Configures CORS settings based on allowed origins and headers.
   *
   * @return the CORS configuration source bean
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(List.of(allowedOrigins.split(",")));
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    cfg.setAllowedHeaders(List.of(allowedHeaders.split(",")));
    cfg.setExposedHeaders(
        List.of("Access-Control-Allow-Origin", "Access-Control-Allow-Credentials"));
    cfg.setAllowCredentials(true);
    cfg.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }

  /**
   * Configures the security filter chain.
   * <ul>
   *   <li>Enables CORS</li>
   *   <li>Disables CSRF protection</li>
   *   <li>Permits all HTTP requests</li>
   * </ul>
   *
   * @param http the HttpSecurity to modify
   * @return the configured SecurityFilterChain bean
   * @throws Exception if an error occurs during configuration
   */
  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    http.cors(Customizer.withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }
}
