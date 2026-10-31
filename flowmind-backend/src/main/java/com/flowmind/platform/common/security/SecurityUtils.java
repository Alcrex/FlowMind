package com.flowmind.platform.common.security;
import com.flowmind.platform.common.api.ErrorCode;
import com.flowmind.platform.common.exception.BusinessException;
import org.springframework.security.core.context.SecurityContextHolder;
public final class SecurityUtils {
    private SecurityUtils() {}
    public static LoginUser getLoginUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return user;
    }
    public static Long getUserId() { return getLoginUser().id(); }
    public static String getRoleType() { return getLoginUser().roleType(); }
}
