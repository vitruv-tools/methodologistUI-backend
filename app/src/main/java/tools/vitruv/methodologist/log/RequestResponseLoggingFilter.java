package tools.vitruv.methodologist.log;

import static net.logstash.logback.marker.Markers.append;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Servlet filter that logs HTTP request and response details, including bodies, for auditing and
 * debugging.
 *
 * <p>Sensitive fields and paths are masked to avoid leaking confidential information. Supports JSON
 * and non-JSON payloads, with recursive masking for JSON. Skips logging for multipart requests and
 * certain sensitive endpoints.
 */
@Slf4j
@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {
  private static final String STATUS = "status";

  /** Normalized sensitive base keys (lowercase, non-alnum removed). */
  private static final Set<String> SENSITIVE_KEYS =
      Set.of(
          "password",
          "newpassword",
          "oldpassword",
          "confirmpassword",
          "token",
          "accesstoken",
          "refreshtoken",
          "secret",
          "clientsecret",
          "authorization");

  private static final String MASK = "***";
  private final ObjectMapper mapper = new ObjectMapper();

  private static boolean isMultipart(String contentType) {
    return contentType != null && contentType.toLowerCase().contains("multipart/");
  }

  private static boolean isJson(String contentType) {
    if (contentType == null) {
      return false;
    }
    String ct = contentType.toLowerCase();
    return ct.contains(MediaType.APPLICATION_JSON_VALUE) || ct.matches(".*\\+json(;.*)?$");
  }

  private static String contentTypeOrEmpty(String ct) {
    return ct == null ? "" : ct;
  }

  private static Charset charsetOrDefault(String enc) {
    try {
      return enc == null ? StandardCharsets.UTF_8 : Charset.forName(enc);
    } catch (Exception e) {
      return StandardCharsets.UTF_8;
    }
  }

  private static String safeString(byte[] bytes, Charset charset) {
    if (bytes == null || bytes.length == 0) {
      return "";
    }
    return new String(bytes, charset);
  }

  /**
   * Filters each HTTP request/response, logs details, and masks sensitive data.
   *
   * @param request the incoming HTTP request
   * @param response the outgoing HTTP response
   * @param filterChain the filter chain to continue processing
   * @throws ServletException if a servlet error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    long startTime = System.currentTimeMillis();

    MDC.put("requestId", UUID.randomUUID().toString());
    MDC.put("api", request.getRequestURI());
    MDC.put("method", request.getMethod());
    MDC.put("ip", request.getRemoteHost());
    MDC.put("type", "SERVED_API");

    ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
    ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

    try {
      filterChain.doFilter(requestWrapper, responseWrapper);
    } finally {
      LinkedHashMap<String, Object> logEntry = new LinkedHashMap<>();

      boolean skipBodyLogging =
          request.getRequestURI().contains("swagger")
              || request.getRequestURI().contains("actuator")
              || isMultipart(request.getContentType());

      if (!skipBodyLogging) {
        String reqBody =
            safeString(
                requestWrapper.getContentAsByteArray(),
                charsetOrDefault(request.getCharacterEncoding()));
        String resBody =
            safeString(
                responseWrapper.getContentAsByteArray(),
                charsetOrDefault(responseWrapper.getCharacterEncoding()));

        String sanitizedReq = sanitizeBody(reqBody, contentTypeOrEmpty(request.getContentType()));
        String sanitizedRes =
            sanitizeBody(resBody, contentTypeOrEmpty(responseWrapper.getContentType()));

        logEntry.put("request", tryParseJson(sanitizedReq, isJson(request.getContentType())));
        logEntry.put(
            "response", tryParseJson(sanitizedRes, isJson(responseWrapper.getContentType())));
      }

      responseWrapper.copyBodyToResponse();
      long durationMs = System.currentTimeMillis() - startTime;

      if (responseWrapper.getStatus() == 200 || responseWrapper.getStatus() == 201) {
        logger.info(
            append(STATUS, responseWrapper.getStatus())
                .and(append("duration_in_ms", durationMs).and(append("detail", logEntry))));
      } else {
        logger.error(
            append(STATUS, responseWrapper.getStatus())
                .and(append("duration_in_ms", durationMs).and(append("detail", logEntry))));
      }

      MDC.clear();
    }
  }

  /**
   * Attempts to parse the body as JSON if indicated, otherwise returns the raw body.
   *
   * @param body the body string to parse
   * @param shouldParse whether to attempt JSON parsing
   * @return parsed JsonNode or raw string if parsing fails
   */
  private Object tryParseJson(String body, boolean shouldParse) {
    if (!shouldParse) {
      return body;
    }
    try {
      return mapper.readTree(body);
    } catch (Exception e) {
      return body;
    }
  }

  /**
   * Sanitizes the request or response body by masking sensitive fields. For JSON, uses recursive
   * field masking; for other types, uses regex.
   *
   * @param body the raw body string
   * @param contentType the content type of the body
   * @return the sanitized body string
   */
  private String sanitizeBody(String body, String contentType) {
    if (body == null || body.isEmpty()) {
      return body;
    }

    if (isJson(contentType)) {
      try {
        JsonNode node = mapper.readTree(body);
        redactJson(node);
        return mapper.writeValueAsString(node);
      } catch (Exception e) {
        log.error("Failed to parse JSON body: " + e.getMessage() + " for body: " + body);
      }
    }
    return redactWithRegex(body);
  }

  /**
   * Recursively masks sensitive fields in a JSON node.
   *
   * @param node the JSON node to redact
   */
  private void redactJson(JsonNode node) {
    if (node == null) {
      return;
    }

    if (node.isObject()) {
      ObjectNode obj = (ObjectNode) node;
      obj.fieldNames()
          .forEachRemaining(
              field -> {
                JsonNode child = obj.get(field);
                if (isSensitiveField(field)) {
                  obj.put(field, MASK);
                } else {
                  redactJson(child);
                }
              });
    } else if (node.isArray()) {
      node.forEach(this::redactJson);
    }
  }

  /**
   * Determines if a field name is considered sensitive and should be masked.
   *
   * @param field the field name to check
   * @return true if the field is sensitive, false otherwise
   */
  private boolean isSensitiveField(String field) {
    if (field == null) {
      return false;
    }
    String normalized = field.toLowerCase().replaceAll("[^a-z0-9]", "");
    if (SENSITIVE_KEYS.contains(normalized)) {
      return true;
    }

    return normalized.contains("token");
  }

  /**
   * Masks sensitive fields in non-JSON bodies using regular expressions.
   *
   * @param raw the raw body string
   * @return the masked body string
   */
  private String redactWithRegex(String raw) {
    return raw.replaceAll("(?i)(\"password\"\\s*:\\s*\")[^\"]*(\")", "$1" + MASK + "$2")
        .replaceAll(
            "(?i)(\"(newPassword|oldPassword|confirmPassword)\"\\s*:\\s*\")[^\"]*(\")",
            "$1" + MASK + "$3")
        .replaceAll(
            "(?i)(\"(accessToken|refreshToken|token|access_token|refresh_token|id_token)\"\\s*:\\s*\")[^\"]*(\")",
            "$1" + MASK + "$3")
        .replaceAll("(?i)(password=)[^&\\s]*", "$1" + MASK)
        .replaceAll("(?i)(accessToken=)[^&\\s]*", "$1" + MASK)
        .replaceAll("(?i)(refreshToken=)[^&\\s]*", "$1" + MASK)
        .replaceAll("(?i)(access_token=)[^&\\s]*", "$1" + MASK)
        .replaceAll("(?i)(refresh_token=)[^&\\s]*", "$1" + MASK)
        .replaceAll("(?i)(Authorization\\s*:\\s*Bearer\\s+)[A-Za-z0-9._-]+", "$1" + MASK);
  }
}
