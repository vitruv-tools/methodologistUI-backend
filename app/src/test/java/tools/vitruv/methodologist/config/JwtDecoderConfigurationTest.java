package tools.vitruv.methodologist.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtDecoderConfigurationTest {

  private static final String HOST_ISSUER = "https://stage.example.org/auth/realms/methodologist";
  private static final String IPV6_ISSUER = "https://[2001:db8::1]/auth/realms/methodologist";

  private static Jwt jwtWithIssuer(String issuer) {
    var builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60));
    if (issuer != null) {
      builder.claim("iss", issuer);
    }
    return builder.build();
  }

  @Test
  void allowedIssuers_combinesPrimaryAndAdditionalAndIgnoresBlanks() {
    assertThat(JwtDecoderConfiguration.allowedIssuers(HOST_ISSUER, " " + IPV6_ISSUER + " , ,"))
        .containsExactlyInAnyOrder(HOST_ISSUER, IPV6_ISSUER);
  }

  @Test
  void allowedIssuers_withoutAdditionalKeepsOnlyPrimary() {
    assertThat(JwtDecoderConfiguration.allowedIssuers(HOST_ISSUER, ""))
        .containsExactly(HOST_ISSUER);
  }

  @Test
  void issuerValidator_acceptsEveryListedIssuer() {
    var validator =
        JwtDecoderConfiguration.issuerValidator(
            JwtDecoderConfiguration.allowedIssuers(HOST_ISSUER, IPV6_ISSUER));

    assertThat(validator.validate(jwtWithIssuer(HOST_ISSUER)).hasErrors()).isFalse();
    assertThat(validator.validate(jwtWithIssuer(IPV6_ISSUER)).hasErrors()).isFalse();
  }

  @Test
  void issuerValidator_rejectsUnknownOrMissingIssuer() {
    var validator =
        JwtDecoderConfiguration.issuerValidator(
            JwtDecoderConfiguration.allowedIssuers(HOST_ISSUER, ""));

    assertThat(validator.validate(jwtWithIssuer(IPV6_ISSUER)).hasErrors()).isTrue();
    assertThat(
            validator
                .validate(jwtWithIssuer("https://evil.example/realms/methodologist"))
                .hasErrors())
        .isTrue();
    assertThat(validator.validate(jwtWithIssuer(null)).hasErrors()).isTrue();
  }
}
