package com.flowmind.platform.common.filter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.common.api.*;
import com.flowmind.platform.common.exception.BusinessException;
import com.flowmind.platform.common.security.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final TokenAuthenticator authenticator;
    private final ObjectMapper json;
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
        String header = req.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ")) throw new BusinessException(ErrorCode.UNAUTHORIZED);
                LoginUser user = authenticator.authenticate(header.substring(7));
                var auth = new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.roleType())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (BusinessException ex) {
                res.setStatus(ex.getErrorCode().getHttpStatus()); res.setContentType("application/json;charset=UTF-8");
                json.writeValue(res.getOutputStream(), Result.failure(ex.getErrorCode(), ex.getMessage())); return;
            }
        }
        chain.doFilter(req, res);
    }
}
