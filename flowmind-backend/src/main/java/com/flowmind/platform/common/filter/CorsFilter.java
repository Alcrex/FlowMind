package com.flowmind.platform.common.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;

@Component
@Order(1)
public class CorsFilter extends OncePerRequestFilter {
    private final CorsConfiguration config = new CorsConfiguration();
    public CorsFilter(@Value("${flowmind.allowed-origins}") String origins) {
        config.setAllowedOrigins(Arrays.asList(origins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (new DefaultCorsProcessor().processRequest(config, req, res) && !CorsUtils.isPreFlightRequest(req)) chain.doFilter(req, res);
    }
}
