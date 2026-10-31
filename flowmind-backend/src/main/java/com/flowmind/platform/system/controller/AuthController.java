package com.flowmind.platform.system.controller;
import com.flowmind.platform.common.api.Result;
import com.flowmind.platform.system.dto.*;
import com.flowmind.platform.system.service.SysAuthService;
import com.flowmind.platform.system.vo.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final SysAuthService auth;
    @PostMapping("/register") public Result<Map<String, Object>> register(@Valid @RequestBody RegisterReqDTO input) { return Result.success(auth.register(input)); }
    @PostMapping("/login") public Result<LoginRespVO> login(@Valid @RequestBody LoginReqDTO input, HttpServletRequest req) { return Result.success(auth.login(input, req)); }
    @PostMapping("/logout") public Result<Boolean> logout(HttpServletRequest req) { return Result.success(auth.logout(req.getHeader("Authorization").substring(7), req)); }
    @GetMapping("/me") public Result<SysUserVO> me() { return Result.success(auth.me()); }
}
