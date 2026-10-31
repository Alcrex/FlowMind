package com.flowmind.platform.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flowmind.platform.common.api.ErrorCode;
import com.flowmind.platform.common.exception.BusinessException;
import com.flowmind.platform.common.security.*;
import com.flowmind.platform.system.dto.*;
import com.flowmind.platform.system.entity.SysUser;
import com.flowmind.platform.system.mapper.SysUserMapper;
import com.flowmind.platform.system.vo.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysAuthService {
    private final SysUserMapper users;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;
    private final TokenCacheService cache;
    private final SysAuditAndConfigService audit;
    private final String dummyHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("unknown-account-password");

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(RegisterReqDTO input) {
        if (users.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, input.username())) > 0) throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        var u = new SysUser();
        u.setUsername(input.username()); u.setPasswordHash(encoder.encode(input.password()));
        u.setRealName(input.realName()); u.setRoleType(input.roleType()); u.setDeptClass(input.deptClass()); u.setEmail(input.email());
        u.setTokenQuota(50000); u.setTokenUsed(0); u.setStatus(1);
        u.setCreatedAt(LocalDateTime.now()); u.setUpdatedAt(u.getCreatedAt());
        try { users.insert(u); } catch (DuplicateKeyException ex) { throw new BusinessException(ErrorCode.USERNAME_EXISTS); }
        log.info("User registered id={} role={}", u.getId(), u.getRoleType());
        return Map.of("userId", u.getId(), "username", u.getUsername());
    }

    public LoginRespVO login(LoginReqDTO input, HttpServletRequest req) {
        var u = users.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, input.username()));
        boolean matches = encoder.matches(input.password(), u == null ? dummyHash : u.getPasswordHash());
        if (u == null || !matches) {
            audit.record(null, "AUTH", "LOGIN", 401, req, Map.of("result", "BAD_CREDENTIALS"), "账号或密码错误");
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        if (u.getStatus() != 1) {
            audit.record(principal(u, null), "AUTH", "LOGIN", 403, req, Map.of("result", "ACCOUNT_DISABLED"), "账号已禁用");
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        var patch = new SysUser(); patch.setId(u.getId()); patch.setLastLoginAt(LocalDateTime.now());
        users.updateById(patch); u.setLastLoginAt(patch.getLastLoginAt());
        String token = jwt.generateToken(u.getId(), u.getUsername(), u.getRealName(), u.getRoleType());
        String jti = jwt.parseToken(token).getId();
        audit.record(principal(u, jti), "AUTH", "LOGIN", 200, req, Map.of("roleType", u.getRoleType()), null);
        return new LoginRespVO(token, jti, SysUserVO.from(u));
    }

    public boolean logout(String token, HttpServletRequest req) {
        var claims = jwt.parseToken(token);
        cache.blacklistToken(claims.getId(), claims.getExpiration().getTime() - System.currentTimeMillis());
        audit.record(SecurityUtils.getLoginUser(), "AUTH", "LOGOUT", 200, req, Map.of("tokenId", claims.getId()), null);
        return true;
    }

    public SysUserVO me() {
        var u = users.selectById(SecurityUtils.getUserId());
        if (u == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return SysUserVO.from(u);
    }
    private LoginUser principal(SysUser u, String jti) { return new LoginUser(u.getId(), u.getUsername(), u.getRealName(), u.getRoleType(), jti); }
}
