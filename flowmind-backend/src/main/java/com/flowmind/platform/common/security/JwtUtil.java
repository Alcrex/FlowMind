package com.flowmind.platform.common.security;
import com.flowmind.platform.common.api.ErrorCode;
import com.flowmind.platform.common.exception.BusinessException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Component
public class JwtUtil {
    static final String DEVELOPMENT_SECRET = "flowmind-local-development-secret-change-before-deployment-2026";
    private final SecretKey key;
    private final long expirationMillis;
    public JwtUtil(@Value("${flowmind.jwt.secret}") String secret, @Value("${flowmind.jwt.expiration-ms:7200000}") long expirationMillis, Environment environment) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        boolean test = Arrays.asList(environment.getActiveProfiles()).contains("test") || Arrays.asList(environment.getActiveProfiles()).contains("mysql-test");
        if (bytes.length < 32 || (DEVELOPMENT_SECRET.equals(secret) && !test))
            throw new IllegalStateException("JWT_SECRET 至少 32 字节，且不能使用仓库中的开发默认值。测试 profile 除外。");
        this.key = Keys.hmacShaKeyFor(bytes); this.expirationMillis = expirationMillis;
    }
    public String generateToken(Long userId, String username, String realName, String roleType) {
        return Jwts.builder().subject(userId.toString()).id(UUID.randomUUID().toString())
            .claim("userId", userId).claim("username", username).claim("realName", realName).claim("roleType", roleType)
            .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + expirationMillis)).signWith(key).compact();
    }
    public Claims parseToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (claims.getExpiration() == null || claims.getId() == null || claims.getSubject() == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
            return claims;
        } catch (JwtException | IllegalArgumentException ex) { throw new BusinessException(ErrorCode.UNAUTHORIZED); }
    }
}
