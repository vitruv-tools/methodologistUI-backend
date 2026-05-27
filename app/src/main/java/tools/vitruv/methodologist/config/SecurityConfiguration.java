package tools.vitruv.methodologist.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.vitruv.methodologist.user.service.UserService;

/**
 * Spring Security configuration for the application.
 *
 * <p>Configures CORS, stateless session management, and JWT-based OAuth2 resource server support.
 * Method security is enabled via annotations.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true, prePostEnabled = true)
public class SecurityConfiguration {

  private final UserService userService;

  @Value("${allowed.origins:*}")
  private String allowedOrigins;

  @Value("${allowed.headers:*}")
  private String allowedHeaders;

  /**
   * Constructs a new SecurityConfiguration.
   *
   * @param userService the {@link UserService} used for user synchronization and related logic.
   */
  public SecurityConfiguration(UserService userService) {
    this.userService = userService;
  }

  /**
   * Defines the security filter chain:
   *
   * <ul>
   *   <li>Disables CSRF (REST API).
   *   <li>Applies CORS configuration from {@link #corsConfigurationSource()}.
   *   <li>Enforces stateless session management.
   *   <li>Configures request authorization and the OAuth2 resource server JWT converter.
   * </ul>
   *
   * @param http the {@link HttpSecurity} to configure
   * @return the built {@link SecurityFilterChain}
   * @throws Exception on configuration errors
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .anyRequest()
                    .permitAll())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(customJwtConverter())))
        .build();
  }

  private Converter<Jwt, ? extends AbstractAuthenticationToken> customJwtConverter() {
    return jwt -> {
      userService.syncWithKeycloak(
          jwt.getClaimAsString("email"),
          jwt.getClaimAsString("preferred_username"),
          jwt.getClaimAsString("given_name"),
          jwt.getClaimAsString("family_name"));

      var grantedAuthoritiesConverter = new GrantedAuthoritiesConverter();
      var authorities = grantedAuthoritiesConverter.convert(jwt);
      return new KeycloakAuthentication(jwt, authorities);
    };
  }

  /**
   * Builds the CORS configuration source used by Spring Security.
   *
   * <p>Configuration includes:
   *
   * <ul>
   *   <li>Allowed origin patterns from {@code allowedOrigins}.
   *   <li>Allowed HTTP methods and headers.
   *   <li>Credentials support and exposed headers (Authorization, Content-Type, Cache-Control,
   *       Pragma, Expires).
   *   <li>Max age for preflight responses.
   * </ul>
   *
   * @return the {@link CorsConfigurationSource} registered for all paths
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    var cfg = new CorsConfiguration();
    cfg.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
    cfg.setAllowedHeaders(List.of(allowedHeaders.split(",")));
    cfg.setAllowCredentials(true);
    cfg.setExposedHeaders(
        List.of("Authorization", "Content-Type", "Cache-Control", "Pragma", "Expires"));
    cfg.setMaxAge(3600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }
}
