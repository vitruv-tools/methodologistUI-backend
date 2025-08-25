package tools.vitruv.methodologist.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that replaces the default {@link JwtAuthenticationToken} with a custom {@link
 * KeycloakAuthentication} for each request. Ensures JWT claims are parsed into a strongly-typed
 * token.
 */
@Component
public class KeycloakAuthenticationFilter extends OncePerRequestFilter {

  /**
   * Processes each request to convert the authentication token to {@link KeycloakAuthentication}.
   *
   * @param request the HTTP servlet request
   * @param response the HTTP servlet response
   * @param filterChain the filter chain
   * @throws ServletException if a servlet error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
      Jwt jwt = jwtAuthenticationToken.getToken();
      var authenticated = SecurityContextHolder.getContext().getAuthentication().isAuthenticated();
      var authorities = authentication.getAuthorities();
      var details = authentication.getDetails();

      var newToken = new KeycloakAuthentication(jwt, authorities);
      newToken.setDetails(details);
      newToken.setAuthenticated(authenticated);

      SecurityContextHolder.getContext().setAuthentication(newToken);
    }

    filterChain.doFilter(request, response);
  }
}
