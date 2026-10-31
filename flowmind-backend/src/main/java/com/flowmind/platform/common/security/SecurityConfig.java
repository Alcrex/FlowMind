package com.flowmind.platform.common.security;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.common.api.*;
import com.flowmind.platform.common.filter.JwtAuthenticationFilter;
import com.flowmind.platform.common.filter.SensitiveAndXssFilter;
import com.flowmind.platform.system.service.SysAuditAndConfigService;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean public SecurityFilterChain securityFilterChain(HttpSecurity http, TokenAuthenticator authenticator, ObjectMapper json, SysAuditAndConfigService audit) throws Exception {
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/ws/**", "/doc.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/market/templates/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/dashboard/big-screen").hasAnyRole("ADMIN", "TEACHER")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/edu/tasks").hasAnyRole("ADMIN", "TEACHER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/edu/tasks/**").hasAnyRole("ADMIN", "TEACHER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/edu/tasks/**").hasAnyRole("ADMIN", "TEACHER")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> {
                res.setStatus(401); res.setContentType("application/json;charset=UTF-8"); json.writeValue(res.getOutputStream(), Result.failure(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getMessage()));
            }).accessDeniedHandler((req,res,ex) -> {
                res.setStatus(403); res.setContentType("application/json;charset=UTF-8"); json.writeValue(res.getOutputStream(), Result.failure(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage()));
            })).addFilterBefore(new JwtAuthenticationFilter(authenticator, json), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new SensitiveAndXssFilter(audit, json), JwtAuthenticationFilter.class).build();
    }
}
