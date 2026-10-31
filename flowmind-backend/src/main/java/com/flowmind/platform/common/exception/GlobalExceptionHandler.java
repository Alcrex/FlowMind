package com.flowmind.platform.common.exception;

import com.flowmind.platform.common.api.*;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> business(BusinessException ex) { return response(ex.getErrorCode(), ex.getMessage()); }
    @ExceptionHandler({BindException.class, ConstraintViolationException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class, IllegalArgumentException.class})
    public ResponseEntity<Result<Void>> validation(Exception ex) { return response(ErrorCode.PARAM_ERROR, ErrorCode.PARAM_ERROR.getMessage()); }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> forbidden(Exception ex) { return response(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage()); }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> notFound(Exception ex) { return response(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> conflict(Exception ex) { return response(ErrorCode.PARAM_ERROR, "数据约束冲突，请检查引用记录或重复的元素/版本编号"); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> unexpected(Exception ex) {
        log.error("Unhandled request exception", ex);
        return response(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }
    private ResponseEntity<Result<Void>> response(ErrorCode code, String message) {
        return ResponseEntity.status(code.getHttpStatus()).body(Result.failure(code, message));
    }
}
