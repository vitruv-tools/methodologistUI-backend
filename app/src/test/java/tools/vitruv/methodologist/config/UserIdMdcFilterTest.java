package tools.vitruv.methodologist.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

class UserIdMdcFilterTest {

  private final OncePerRequestFilter filter = new UserIdMdcFilter();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  @Test
  void doFilterInternal_setsTheCallersUserIdButNoEmail_whileTheRequestRuns() throws Exception {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("8f14e45f-ceea-467f-a0b4-1234567890ab")
            .claim("email", "alice@example.com")
            .claim("preferred_username", "alice")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new KeycloakAuthentication(jwt, List.of()));
    AtomicReference<Map<String, String>> mdcDuringRequest = new AtomicReference<>();

    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/v1/users"),
        new MockHttpServletResponse(),
        (request, response) -> mdcDuringRequest.set(MDC.getCopyOfContextMap()));

    assertThat(mdcDuringRequest.get())
        .containsEntry(UserIdMdcFilter.USER_ID, "8f14e45f-ceea-467f-a0b4-1234567890ab")
        .doesNotContainValue("alice@example.com")
        .doesNotContainValue("alice");
    assertThat(MDC.get(UserIdMdcFilter.USER_ID)).isNull();
  }

  @Test
  void doFilterInternal_usesAnonymousWhenAuthenticationIsMissing() throws Exception {
    AtomicReference<String> userIdDuringRequest = new AtomicReference<>();

    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/v1/users"),
        new MockHttpServletResponse(),
        (request, response) -> userIdDuringRequest.set(MDC.get(UserIdMdcFilter.USER_ID)));

    assertThat(userIdDuringRequest.get()).isEqualTo("anonymous");
    assertThat(MDC.get(UserIdMdcFilter.USER_ID)).isNull();
  }
}
