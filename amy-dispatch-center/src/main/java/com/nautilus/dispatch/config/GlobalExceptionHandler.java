package com.nautilus.dispatch.config;

import com.nautilus.common.core.domain.AjaxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理 - 统一返回 JSON,避免 500 时无错误信息
 * 思想犯 - 所有未捕获异常在此兜底
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 参数校验失败 (如 @NotBlank) */
    @ExceptionHandler({ MethodArgumentNotValidException.class, BindException.class, MissingServletRequestParameterException.class })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AjaxResult handleValidation(Exception e) {
        String msg = e instanceof MissingServletRequestParameterException
                ? "思想犯 - 缺少参数: " + ((MissingServletRequestParameterException) e).getParameterName()
                : "思想犯 - 参数校验失败: " + (e.getMessage() != null ? e.getMessage() : "");
        log.warn("思想犯 - 参数校验: {}", e.getMessage());
        return AjaxResult.error(400, msg);
    }

    /** 其它未捕获异常 - 记录完整堆栈并返回错误信息,便于排查 */
    @ExceptionHandler(Throwable.class)
    @ResponseStatus(HttpStatus.OK)
    public AjaxResult handleThrowable(Throwable e) {
        log.error("思想犯 - 未捕获异常: {}", e.getMessage(), e);
        
        // 获取根因异常
        Throwable rootCause = e;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        
        String msg = rootCause.getMessage() != null ? rootCause.getMessage() : rootCause.getClass().getSimpleName();
        String exceptionType = rootCause.getClass().getSimpleName();
        
        return AjaxResult.error("思想犯 - " + exceptionType + ": " + msg);
    }
}
