package com.flowmind.platform.common.exception;

import com.flowmind.platform.common.api.ErrorCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    public BusinessException(ErrorCode errorCode) { this(errorCode, errorCode.getMessage()); }
    public BusinessException(ErrorCode errorCode, String message) { super(message); this.errorCode = errorCode; }
}
