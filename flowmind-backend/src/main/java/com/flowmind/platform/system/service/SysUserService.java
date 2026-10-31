package com.flowmind.platform.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flowmind.platform.common.api.*;
import com.flowmind.platform.common.exception.BusinessException;
import com.flowmind.platform.common.security.*;
import com.flowmind.platform.system.dto.*;
import com.flowmind.platform.system.entity.SysUser;
import com.flowmind.platform.system.mapper.SysUserMapper;
import com.flowmind.platform.system.vo.SysUserVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService {
    private final SysUserMapper users;
    private final PasswordEncoder encoder;
    private final SysAuditAndConfigService audit;

    public PageResult<SysUserVO> query(UserQueryDTO q) {
        var w = new LambdaQueryWrapper<SysUser>()
            .and(hasText(q.getKeyword()), x -> x.like(SysUser::getUsername, q.getKeyword()).or().like(SysUser::getRealName, q.getKeyword()))
            .eq(hasText(q.getRoleType()), SysUser::getRoleType, q.getRoleType())
            .like(hasText(q.getDeptClass()), SysUser::getDeptClass, q.getDeptClass())
            .eq(q.getStatus() != null, SysUser::getStatus, q.getStatus())
            .ge(q.getMinQuota() != null, SysUser::getTokenQuota, q.getMinQuota())
            .le(q.getMaxQuota() != null, SysUser::getTokenQuota, q.getMaxQuota()).orderByDesc(SysUser::getId);
        var p = users.selectPage(new Page<>(q.getPageNum(), q.getPageSize()), w);
        return new PageResult<>(p.getRecords().stream().map(SysUserVO::from).toList(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public SysUserVO update(Long id, UserUpdateDTO input, HttpServletRequest req) {
        SysUser old = users.lockById(id);
        if (old == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        var actor = SecurityUtils.getLoginUser();
        if (actor.id().equals(id) && ((input.roleType() != null && !"ADMIN".equals(input.roleType())) || Integer.valueOf(0).equals(input.status())))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能降级或禁用当前管理员账号");
        var patch = new SysUser(); patch.setId(id); patch.setRoleType(input.roleType()); patch.setDeptClass(input.deptClass());
        patch.setStatus(input.status()); patch.setTokenQuota(input.tokenQuota()); patch.setUpdatedAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(input.resetPassword())) patch.setPasswordHash(encoder.encode("123456"));
        users.updateById(patch);
        var detail = new LinkedHashMap<String, Object>();
        detail.put("targetUserId", id); detail.put("previousQuota", old.getTokenQuota()); detail.put("changes", input);
        audit.record(actor, "ADMIN", input.tokenQuota() == null ? "USER_UPDATE" : "QUOTA_CHANGE", 200, req, detail, null);
        log.info("Administrator userId={} updated targetUserId={}", actor.id(), id);
        return SysUserVO.from(users.selectById(id));
    }

    /** Atomic conditional update prevents concurrent executions from overspending the remaining quota. */
    @Transactional(rollbackFor = Exception.class)
    public void consumeTokens(Long userId, int cost) {
        if (cost < 0) throw new BusinessException(ErrorCode.PARAM_ERROR);
        if (users.consumeTokens(userId, cost) == 0) throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
    }
    private static boolean hasText(String s) { return s != null && !s.isBlank(); }
}
