package com.flowmind.platform.common.api;

public record Result<T>(int code, String message, T data, long timestamp) {
    public static <T> Result<T> success(T data) {
        return new Result<>(200, ErrorCode.SUCCESS.getMessage(), data, System.currentTimeMillis());
    }
    public static <T> Result<T> failure(ErrorCode error, String message) {
        return new Result<>(error.getCode(), message, null, System.currentTimeMillis());
    }
}
