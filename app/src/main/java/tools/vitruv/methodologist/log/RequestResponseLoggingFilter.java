package tools.vitruv.methodologist.log;

import static net.logstash.logback.marker.Markers.append;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.marker.LogstashMarker;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that writes exactly one log entry per HTTP request, with its method, path, status
 * and duration.
 *
 * <p>Bodies, query strings, headers and the client's IP address are never logged, because they can
 * contain personal data, credentials and models. The entry is logged at INFO for successful
 * requests, at WARN for client errors (4xx) and at ERROR for server errors (5xx). Requests for
 * health checks and the API documentation are logged at DEBUG only.
 *
 * <p>While the request is handled, the MDC holds its ID, method and path, so that all log entries
 * of a request can be found together.
 */
@Slf4j
@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

  /** MDC key of the ID of the current request. */
  public static final String REQUEST_ID = "requestId";

  private static final String API = "api";
  private static final String METHOD = "method";
  private static final String TYPE = "type";
  private static final String MESSAGE = "{} {} -> {} in {} ms";

  /** Paths that are requested often and tell nothing about what users do. */
  private static final List<String> HOUSEKEEPING_PATHS =
      List.of("/actuator", "/swagger-ui", "/v3/api-docs");

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    long startTime = System.nanoTime();
    MDC.put(REQUEST_ID, UUID.randomUUID().toString());
    MDC.put(API, request.getRequestURI());
    MDC.put(METHOD, request.getMethod());
    MDC.put(TYPE, "SERVED_API");
    try {
      filterChain.doFilter(request, response);
    } finally {
      logRequest(request, response.getStatus(), (System.nanoTime() - startTime) / 1_000_000);
      MDC.remove(REQUEST_ID);
      MDC.remove(API);
      MDC.remove(METHOD);
      MDC.remove(TYPE);
    }
  }

  private static void logRequest(HttpServletRequest request, int status, long durationMs) {
    LogstashMarker fields = append("status", status).and(append("duration_in_ms", durationMs));
    Object[] arguments = {request.getMethod(), request.getRequestURI(), status, durationMs};
    if (status >= 500) {
      log.error(fields, MESSAGE, arguments);
    } else if (isHousekeeping(request.getRequestURI())) {
      log.debug(fields, MESSAGE, arguments);
    } else if (status >= 400) {
      log.warn(fields, MESSAGE, arguments);
    } else {
      log.info(fields, MESSAGE, arguments);
    }
  }

  private static boolean isHousekeeping(String path) {
    return path != null && HOUSEKEEPING_PATHS.stream().anyMatch(path::startsWith);
  }
}
