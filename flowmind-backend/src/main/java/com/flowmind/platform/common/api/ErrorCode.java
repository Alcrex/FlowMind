package com.flowmind.platform.common.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    SUCCESS(200, 200, "操作成功"),
    PARAM_ERROR(40000, 400, "请求参数校验失败"),
    USERNAME_EXISTS(40001, 400, "用户名已被注册"),
    RESOURCE_NOT_FOUND(40004, 404, "请求的资源不存在"),
    DAG_CYCLE_ERROR(40005, 400, "工作流存在循环依赖或孤立节点，无法执行"),
    TASK_NODE_RULE_FAILED(40006, 400, "提交的工作流未满足课题必含节点要求"),
    SENSITIVE_WORD_BLOCKED(40009, 400, "请求包含违规敏感词或恶意脚本，已被安全过滤器拦截"),
    UNAUTHORIZED(40100, 401, "未登录或 Token 格式无效"),
    BAD_CREDENTIALS(40101, 401, "账号或密码错误"),
    TOKEN_BLACKLISTED(40102, 401, "登录凭证已安全退出失效，请重新登录"),
    QUOTA_INSUFFICIENT(40201, 402, "Token 算力额度不足，请联系教师或管理员充值"),
    FORBIDDEN(40300, 403, "权限不足，无法访问该资源"),
    ACCOUNT_DISABLED(40301, 403, "账号已被管理员封禁"),
    INTERNAL_ERROR(50000, 500, "系统内部异常，请稍后重试");
    private final int code;
    private final int httpStatus;
    private final String message;
}
