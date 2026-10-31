package com.flowmind.platform.common.security;
import com.flowmind.platform.common.api.ErrorCode;
import com.flowmind.platform.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class TokenAuthenticator {
    private final JwtUtil jwt;
    private final TokenCacheService cache;
    private final JdbcTemplate jdbc;
    public LoginUser authenticate(String token) {
        var claims = jwt.parseToken(token);
        if (cache.isBlacklisted(claims.getId())) throw new BusinessException(ErrorCode.TOKEN_BLACKLISTED);
        long id;
        try { id = Long.parseLong(claims.getSubject()); } catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.UNAUTHORIZED); }
        var users = jdbc.query("SELECT id, username, real_name, role_type, status FROM sys_user WHERE id = ?", (rs, n) -> {
            if (rs.getInt("status") != 1) throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
            return new LoginUser(rs.getLong("id"), rs.getString("username"), rs.getString("real_name"), rs.getString("role_type"), claims.getId());
        }, id);
        if (users.isEmpty()) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return users.getFirst();
    }
}
