package com.flowmind.platform.common.security;
import com.flowmind.platform.common.api.ErrorCode;
import com.flowmind.platform.common.exception.BusinessException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Component
public class JwtUtil {
    private final SecretKey key;
    private final long expirationMillis;
    public JwtUtil(@Value("${flowmind.jwt.secret}") String secret, @Value("${flowmind.jwt.expiration-ms:7200000}") long expirationMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.expirationMillis = expirationMillis;
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
