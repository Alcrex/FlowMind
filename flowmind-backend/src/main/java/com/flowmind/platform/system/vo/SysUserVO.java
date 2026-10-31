package com.flowmind.platform.system.vo;
import com.flowmind.platform.system.entity.SysUser;
import java.time.LocalDateTime;
public record SysUserVO(Long id, String username, String realName, String roleType, String deptClass,
    String email, String avatarUrl, Integer tokenQuota, Integer tokenUsed, Integer status,
    LocalDateTime lastLoginAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static SysUserVO from(SysUser u) {
        return new SysUserVO(u.getId(), u.getUsername(), u.getRealName(), u.getRoleType(), u.getDeptClass(),
            u.getEmail(), u.getAvatarUrl(), u.getTokenQuota(), u.getTokenUsed(), u.getStatus(), u.getLastLoginAt(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
