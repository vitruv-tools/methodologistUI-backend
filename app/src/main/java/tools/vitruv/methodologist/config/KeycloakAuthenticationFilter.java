package tools.vitruv.methodologist.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that enforces Keycloak-style Bearer token authentication.
 *
 * <p>Behavior:
 *
 * <ul>
 *   <li>Skips filtering for HTTP OPTIONS requests.
 *   <li>Skips filtering for requests without an `Authorization` header starting with `Bearer `.
 *   <li>Allows unauthenticated POST requests to configured public endpoints defined in {@code
 *       PUBLIC_POST}.
 *   <li>Otherwise the filter should apply authentication/authorization logic (currently delegates
 *       to the chain).
 * </ul>
 *
 * <p>This class extends {@link org.springframework.web.filter.OncePerRequestFilter} so it is
 * guaranteed to run only once per request.
 */
public class KeycloakAuthenticationFilter extends OncePerRequestFilter {

  /**
   * Determines whether this filter should not be applied to the given request.
   *
   * <p>Returns {@code true} for:
   *
   * <ul>
   *   <li>HTTP OPTIONS requests
   *   <li>Requests that either have no {@code Authorization} header or whose header does not start
   *       with {@code "Bearer "}
   * </ul>
   *
   * @param request the current {@link jakarta.servlet.http.HttpServletRequest}
   * @return {@code true} to skip filter execution for this request
   */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }

    String auth = request.getHeader("Authorization");
    return auth == null || !auth.startsWith("Bearer ");
  }

  /**
   * Filter logic executed when the filter is applied.
   *
   * <p>Current implementation simply delegates to the filter chain. Replace or extend this method
   * to validate the Bearer token, populate the security context, and enforce authorization.
   *
   * @param req the {@link jakarta.servlet.http.HttpServletRequest}
   * @param res the {@link jakarta.servlet.http.HttpServletResponse}
   * @param chain the {@link jakarta.servlet.FilterChain} to delegate to
   * @throws jakarta.servlet.ServletException on servlet error
   * @throws java.io.IOException on I/O error
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    chain.doFilter(req, res);
  }
}
