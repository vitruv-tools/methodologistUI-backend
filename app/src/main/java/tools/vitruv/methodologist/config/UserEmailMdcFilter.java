package tools.vitruv.methodologist.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds the authenticated caller email to the MDC for request and error logs. */
public class UserEmailMdcFilter extends OncePerRequestFilter {

  /**
   * Populates MDC with the caller email and continues the filter chain.
   *
   * @param request the incoming HTTP request
   * @param response the outgoing HTTP response
   * @param filterChain the filter chain to continue processing
   * @throws ServletException if a servlet error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    MDC.put("user_email", resolveUserEmail());
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove("user_email");
    }
  }

  /**
   * Resolves the caller email from the current Spring Security authentication.
   *
   * @return the authenticated caller email or {@code anonymous} when unavailable
   */
  private String resolveUserEmail() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return "anonymous";
    }

    if (authentication instanceof KeycloakAuthentication keycloakAuthentication) {
      String email = keycloakAuthentication.getParsedToken().getEmail();
      if (StringUtils.hasText(email)) {
        return email;
      }
    }

    if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
      String email = jwtAuthenticationToken.getToken().getClaimAsString("email");
      if (StringUtils.hasText(email)) {
        return email;
      }
    }

    if (StringUtils.hasText(authentication.getName())) {
      return authentication.getName();
    }

    return "anonymous";
  }
}
