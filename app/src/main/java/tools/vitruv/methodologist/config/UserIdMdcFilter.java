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

/**
 * Adds the Keycloak user ID of the authenticated caller (the {@code sub} claim of the token) to the
 * MDC, so that the log entries of a request show who made it without containing an email address.
 */
public class UserIdMdcFilter extends OncePerRequestFilter {

  /** MDC key of the caller's Keycloak user ID. */
  public static final String USER_ID = "user_id";

  private static final String ANONYMOUS = "anonymous";

  /**
   * Populates MDC with the caller's user ID and continues the filter chain.
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
    MDC.put(USER_ID, resolveUserId());
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(USER_ID);
    }
  }

  /**
   * Resolves the caller's Keycloak user ID from the current Spring Security authentication.
   *
   * @return the {@code sub} claim of the caller's token, or {@code anonymous} when unavailable
   */
  private static String resolveUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
      String subject = jwtAuthenticationToken.getToken().getSubject();
      if (StringUtils.hasText(subject)) {
        return subject;
      }
    }
    return ANONYMOUS;
  }
}
