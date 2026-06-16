package tools.vitruv.methodologist.config;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

class UserEmailMdcFilterTest {

  private final OncePerRequestFilter filter = new UserEmailMdcFilter();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  @Test
  void doFilterInternal_setsCallerEmailForAuthenticatedUser() throws Exception {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("email", "alice@example.com")
            .claim("preferred_username", "alice")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    SecurityContextHolder.getContext()
        .setAuthentication(new KeycloakAuthentication(jwt, List.of()));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertNull(MDC.get("user_email"));
  }

  @Test
  void doFilterInternal_usesAnonymousWhenAuthenticationIsMissing() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertNull(MDC.get("user_email"));
  }
}
