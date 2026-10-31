package com.flowmind.platform.workflow;

import com.flowmind.platform.FlowMindApplication;
import com.flowmind.platform.common.api.Result;
import com.flowmind.platform.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Test-only server: workflow APIs are real; /auth/me supplies the external system module's integration seam. */
public class WorkflowBrowserServer {
    public static void main(String[] args) {
        var application = new SpringApplication(FlowMindApplication.class);
        application.setAdditionalProfiles("test", "browser-test");
        application.run(args);
    }
    @RestController
    @Profile("browser-test")
    @RequiredArgsConstructor
    public static class SessionController {
        private final JdbcTemplate jdbc;
        @GetMapping("/api/v1/auth/me") public Result<Map<String, Object>> me() {
            var id = SecurityUtils.getUserId();
            return Result.success(jdbc.queryForObject("SELECT id, username, real_name, role_type, dept_class, token_quota, token_used FROM sys_user WHERE id = ?",
                (rs, n) -> Map.of("id", rs.getLong("id"), "username", rs.getString("username"), "realName", rs.getString("real_name"),
                    "roleType", rs.getString("role_type"), "deptClass", rs.getString("dept_class"), "tokenQuota", rs.getInt("token_quota"), "tokenUsed", rs.getInt("token_used")), id));
        }
    }
}
