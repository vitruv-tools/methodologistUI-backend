package tools.vitruv.methodologist.log;

import static net.logstash.logback.marker.Markers.append;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.marker.LogstashMarker;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Filter component that logs HTTP request and response details in a structured format. Implements
 * request/response content caching and JSON parsing for detailed logging. Supports skipping
 * specific paths and handles large payloads appropriately.
 *
 * @see OncePerRequestFilter
 */
@Slf4j
@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

  private static final int MAX_TEXT_LOG = 4000; // avoid huge logs
  private static final Set<String> SKIP_PATHS = Set.of("swagger", "actuator");
  private final ObjectMapper mapper = new ObjectMapper();

  private static boolean shouldSkip(String uri) {
    if (!StringUtils.hasText(uri)) {
      return true;
    }
    ;
    String u = uri.toLowerCase();
    for (String s : SKIP_PATHS) {
      if (u.contains(s)) {
        return true;
      }
    }
    return false;
  }

  // ---------- helpers ----------

  private static boolean isJson(String contentType) {
    if (!StringUtils.hasText(contentType)) {
      return false;
    }
    try {
      return MediaType.valueOf(contentType).isCompatibleWith(MediaType.APPLICATION_JSON);
    } catch (Exception ignored) {
      return false;
    }
  }

  private static String safeContentType(String ct) {
    return StringUtils.hasText(ct) ? ct : "application/octet-stream";
  }

  private static Charset safeCharset(String enc) {
    if (!StringUtils.hasText(enc)) {
      return StandardCharsets.UTF_8;
    }
    try {
      return Charset.forName(enc);
    } catch (Exception e) {
      return StandardCharsets.UTF_8;
    }
  }

  private static String bytesToString(byte[] bytes, Charset cs) {
    return new String(bytes, cs);
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() <= max ? s : s.substring(0, max) + "…";
  }

  /**
   * Processes HTTP requests and responses, generating structured logs with detailed information.
   * Caches request/response content for logging while preserving the original stream. Includes
   * timing, status codes, and content details in log output.
   *
   * @param request incoming HTTP request
   * @param response outgoing HTTP response
   * @param filterChain filter chain for request processing
   * @throws ServletException if a servlet error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    ContentCachingRequestWrapper reqWrapper = new ContentCachingRequestWrapper(request);
    ContentCachingResponseWrapper resWrapper = new ContentCachingResponseWrapper(response);

    MDC.put("requestId", UUID.randomUUID().toString());
    MDC.put("api", request.getRequestURI());
    MDC.put("method", request.getMethod());
    MDC.put("ip", request.getRemoteAddr());
    MDC.put("type", "SERVED_API");

    long start = System.currentTimeMillis();

    try {
      filterChain.doFilter(reqWrapper, resWrapper);
    } finally {
      try {
        // Build structured log entry
        Map<String, Object> logEntry = new LinkedHashMap<>();

        if (!shouldSkip(request.getRequestURI())) {
          // ---- REQUEST ----
          String reqCt = safeContentType(reqWrapper.getContentType());
          byte[] reqBodyBytes = reqWrapper.getContentAsByteArray();
          Charset reqCharset = safeCharset(reqWrapper.getCharacterEncoding());

          logEntry.put(
              "requestMeta",
              Map.of(
                  "method",
                  request.getMethod(),
                  "uri",
                  request.getRequestURI(),
                  "contentType",
                  reqCt,
                  "length",
                  reqBodyBytes.length));

          if (isJson(reqCt)
              && reqBodyBytes.length > 0
              && !"GET".equalsIgnoreCase(request.getMethod())) {
            // parse JSON request bodies (non-GET)
            addJsonSafely(logEntry, "request", bytesToString(reqBodyBytes, reqCharset));
          } else if (reqBodyBytes.length > 0) {
            // non-JSON: log a truncated text preview only
            logEntry.put(
                "requestPreview", truncate(bytesToString(reqBodyBytes, reqCharset), MAX_TEXT_LOG));
          }

          // ---- RESPONSE ----
          String resCt = safeContentType(resWrapper.getContentType());
          byte[] resBodyBytes = resWrapper.getContentAsByteArray();
          Charset resCharset = safeCharset(resWrapper.getCharacterEncoding());

          logEntry.put(
              "responseMeta",
              Map.of(
                  "status",
                  resWrapper.getStatus(),
                  "contentType",
                  resCt,
                  "length",
                  resBodyBytes.length));

          if (isJson(resCt) && resBodyBytes.length > 0) {
            addJsonSafely(logEntry, "response", bytesToString(resBodyBytes, resCharset));
          } else if (resBodyBytes.length > 0) {
            logEntry.put(
                "responsePreview", truncate(bytesToString(resBodyBytes, resCharset), MAX_TEXT_LOG));
          }
        }

        // Write body back to client
        resWrapper.copyBodyToResponse();

        // Log with markers
        long duration = System.currentTimeMillis() - start;
        LogstashMarker marker =
            append("type", "SERVED_API")
                .and(append("status", resWrapper.getStatus()))
                .and(append("duration_ms", duration))
                .and(append("detail", logEntry));

        // You can vary level based on status if you want
        log.info(marker, "HTTP {} {}", request.getMethod(), request.getRequestURI());
      } catch (Exception e) {
        // Never break responses due to logging
        log.warn("Request/Response logging failed: {}", e.toString());
        resWrapper.copyBodyToResponse();
      } finally {
        MDC.clear();
      }
    }
  }

  private void addJsonSafely(Map<String, Object> logEntry, String key, String body) {
    try {
      JsonNode node = mapper.readTree(body);
      logEntry.put(key, node);
    } catch (Exception ex) {
      // If body isn't valid JSON (bad client / different content), don't explode:
      logEntry.put(key + "Raw", truncate(body, MAX_TEXT_LOG));
      logEntry.put(key + "ParseError", ex.getClass().getSimpleName());
    }
  }
}
