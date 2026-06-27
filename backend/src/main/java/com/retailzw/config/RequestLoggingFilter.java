package com.retailzw.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Set<String> QUIET_PREFIXES = Set.of(
            "/css/", "/js/", "/img/", "/fonts/", "/webjars/", "/favicon.ico"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isQuiet(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long started = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = System.currentTimeMillis() - started;
            int status = response.getStatus();
            String client = clientIp(request);
            String message = "HTTP {} {} -> {} ({} ms) client={} requestId={}";
            if (status >= 500) {
                log.error(message, request.getMethod(), request.getRequestURI(), status, elapsedMs, client, requestId);
            } else if (status >= 400) {
                log.warn(message, request.getMethod(), request.getRequestURI(), status, elapsedMs, client, requestId);
            } else {
                log.info(message, request.getMethod(), request.getRequestURI(), status, elapsedMs, client, requestId);
            }
        }
    }

    private boolean isQuiet(String uri) {
        return QUIET_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
