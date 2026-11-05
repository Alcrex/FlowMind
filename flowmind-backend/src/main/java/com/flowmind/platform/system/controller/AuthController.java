package com.flowmind.platform.system.controller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.common.api.ErrorCode;
import com.flowmind.platform.common.api.Result;
import com.flowmind.platform.common.exception.BusinessException;
import com.flowmind.platform.system.dto.*;
import com.flowmind.platform.system.service.SysAuthService;
import com.flowmind.platform.system.vo.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final SysAuthService auth;
    private final ObjectMapper json;
    private final Validator validator;
    @PostMapping("/register") public Result<Map<String, Object>> register(@RequestBody JsonNode body) throws Exception {
        if (body.has("roleType")) throw new BusinessException(ErrorCode.PARAM_ERROR, "公开注册不能指定角色");
        RegisterReqDTO input = json.treeToValue(body, RegisterReqDTO.class);
        if (!validator.validate(input).isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR);
        return Result.success(auth.register(input));
    }
    @PostMapping("/login") public Result<LoginRespVO> login(@Valid @RequestBody LoginReqDTO input, HttpServletRequest req) { return Result.success(auth.login(input, req)); }
    @PostMapping("/logout") public Result<Boolean> logout(HttpServletRequest req) { return Result.success(auth.logout(req.getHeader("Authorization").substring(7), req)); }
    @GetMapping("/me") public Result<SysUserVO> me() { return Result.success(auth.me()); }
}
