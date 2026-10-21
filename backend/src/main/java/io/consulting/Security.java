package io.consulting;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class Security extends OncePerRequestFilter {

  private final String token;

  public Security(@Value("${app.token}") String token) {
    if (token.length() < 24) throw new IllegalStateException(
      "APP_TOKEN must contain at least 24 characters"
    );
    this.token = token;
  }

  protected void doFilterInternal(
    HttpServletRequest q,
    HttpServletResponse r,
    FilterChain chain
  ) throws ServletException, IOException {
    r.setHeader("X-Content-Type-Options", "nosniff");
    r.setHeader("X-Frame-Options", "DENY");
    r.setHeader("Referrer-Policy", "no-referrer");
    if (
      q.getRequestURI().startsWith("/api/") &&
      !MessageDigest.isEqual(
        ("Bearer " + token).getBytes(StandardCharsets.UTF_8),
        String.valueOf(q.getHeader("Authorization")).getBytes(
          StandardCharsets.UTF_8
        )
      )
    ) {
      r.sendError(401);
      return;
    }
    chain.doFilter(q, r);
  }
}
