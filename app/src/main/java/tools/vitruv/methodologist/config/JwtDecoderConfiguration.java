package tools.vitruv.methodologist.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Configures JWT decoding with an allowlist of issuers.
 *
 * <p>When Keycloak runs behind a reverse proxy without a fixed hostname, the {@code iss} claim
 * depends on the address the client used (hostname or IPv6 literal). The signature is always
 * verified against the JWK set of the configured Keycloak; only the issuer comparison accepts
 * several explicitly listed values. Tokens with any other issuer are rejected.
 */
@Configuration
public class JwtDecoderConfiguration {

  private static final String CERTS_PATH = "/protocol/openid-connect/certs";

  /**
   * Builds the {@link JwtDecoder} used by the resource server.
   *
   * @param issuerUri the primary issuer ({@code
   *     spring.security.oauth2.resourceserver.jwt.issuer-uri})
   * @param jwkSetUri optional JWK set URI; defaults to the certs endpoint of {@code issuerUri}
   * @param additionalIssuers optional comma-separated list of further accepted issuers
   * @return a decoder validating signature, timestamps and the issuer allowlist
   */
  @Bean
  public JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
      @Value("${app.security.additional-issuers:}") String additionalIssuers) {
    var allowedIssuers = allowedIssuers(issuerUri, additionalIssuers);
    var resolvedJwkSetUri = jwkSetUri.isBlank() ? issuerUri + CERTS_PATH : jwkSetUri;
    var decoder = NimbusJwtDecoder.withJwkSetUri(resolvedJwkSetUri).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(), issuerValidator(allowedIssuers)));
    return decoder;
  }

  static Set<String> allowedIssuers(String issuerUri, String additionalIssuers) {
    return Arrays.stream((issuerUri + "," + additionalIssuers).split(","))
        .map(String::trim)
        .filter(issuer -> !issuer.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }

  static OAuth2TokenValidator<Jwt> issuerValidator(Set<String> allowedIssuers) {
    return new JwtClaimValidator<Object>(
        JwtClaimNames.ISS, issuer -> issuer != null && allowedIssuers.contains(issuer.toString()));
  }
}
