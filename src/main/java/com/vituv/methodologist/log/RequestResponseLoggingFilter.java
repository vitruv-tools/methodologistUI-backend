package com.vituv.methodologist.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

import static net.logstash.logback.marker.Markers.append;

//https://www.baeldung.com/logback-mask-sensitive-data
//https://stackoverflow.com/questions/33744875/spring-boot-how-to-log-all-requests-and-responses-with-exceptions-in-single-pl
//http://www.javabyexamples.com/request-logging-using-spring-mvc
@Slf4j
@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        MDC.put("requestId", UUID.randomUUID().toString());
        MDC.put("api", request.getRequestURI());
        MDC.put("method", request.getMethod());
        MDC.put("ip", request.getRemoteHost());
        MDC.put("type", "SERVED_API");

        long startTime = System.currentTimeMillis();

        filterChain.doFilter(requestWrapper, responseWrapper);

        ObjectMapper mapper = new ObjectMapper();
        var logEntry = new LinkedHashMap<String, Object>();

        if(!request.getRequestURI().contains("swagger") && !request.getRequestURI().contains("actuator")) {
            logEntry.put("request", mapper.readTree(getStringValue(requestWrapper.getContentAsByteArray(),
                    request.getCharacterEncoding())));
            logEntry.put("response", mapper.readTree(getStringValue(responseWrapper.getContentAsByteArray(),
                    request.getCharacterEncoding())));
        }

        responseWrapper.copyBodyToResponse();
        //todo: can we use marker instead of copying lines
        var marker = append("type", "SERVED_API")
                .and(append("status", response.getStatus())
                        .and(append("duration_in_ms", System.currentTimeMillis() - startTime)
                                .and(append("data", logEntry))));
        if(Set.of(200, 201).contains(response.getStatus()))
            logger.info(append("status", response.getStatus())
                    .and(append("duration_in_ms", System.currentTimeMillis() - startTime)
                            .and(append("detail", logEntry))));
        else
            logger.info(append("status", response.getStatus())
                    .and(append("duration_in_ms", System.currentTimeMillis() - startTime)
                            .and(append("detail", logEntry))));
        MDC.clear();
    }

    private String getStringValue(byte[] contentAsByteArray, String characterEncoding) {
        try {
            return new String(contentAsByteArray, 0, contentAsByteArray.length, characterEncoding);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "";
    }
}