package io.consulting.security;

import io.consulting.config.ApplicationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerTokenFilter extends OncePerRequestFilter {

    private final String token;
    private final java.util.Set<String> origins;

    public BearerTokenFilter(ApplicationProperties properties) {
        String token = properties.token();
        String allowedOrigins = properties.allowedOrigins();
        if (token.length() < 24) throw new IllegalStateException(
            "APP_TOKEN must contain at least 24 characters"
        );
        this.token = token;
        this.origins = new java.util.HashSet<>(
            java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList()
        );
    }

    protected void doFilterInternal(HttpServletRequest q, HttpServletResponse r, FilterChain chain)
        throws ServletException, IOException {
        r.setHeader("X-Content-Type-Options", "nosniff");
        r.setHeader("X-Frame-Options", "DENY");
        r.setHeader("Referrer-Policy", "no-referrer");
        String origin = q.getHeader("Origin");
        if (q.getRequestURI().startsWith("/api/") && origin != null) {
            String sameOrigin = q.getScheme() + "://" + q.getHeader("Host");
            if (!origin.equals(sameOrigin) && !origins.contains(origin)) {
                r.sendError(403);
                return;
            }
            r.setHeader("Access-Control-Allow-Origin", origin);
            r.setHeader("Vary", "Origin");
            r.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
            r.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            if (q.getMethod().equals("OPTIONS")) {
                r.setStatus(204);
                return;
            }
        }
        if (
            q.getRequestURI().startsWith("/api/") &&
            !MessageDigest.isEqual(
                ("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                String.valueOf(q.getHeader("Authorization")).getBytes(StandardCharsets.UTF_8)
            )
        ) {
            r.sendError(401);
            return;
        }
        chain.doFilter(q, r);
    }
}
