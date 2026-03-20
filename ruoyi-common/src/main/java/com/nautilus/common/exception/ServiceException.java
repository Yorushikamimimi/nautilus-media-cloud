package com.nautilus.common.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务异常类
 * 用于封装业务逻辑中的异常情况
 *
 * @author nautilus-media-cloud
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private Integer code;

    /**
     * 错误提示
     */
    private String message;

    /**
     * 错误明细,内部调试使用
     */
    private String detailMessage;

    public ServiceException() {
    }

    public ServiceException(String message) {
        this.message = message;
    }

    public ServiceException(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public ServiceException(String message, Integer code) {
        this.message = message;
        this.code = code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public ServiceException setMessage(String message) {
        this.message = message;
        return this;
    }

    public String getDetailMessage() {
        return detailMessage;
    }

    public ServiceException setDetailMessage(String detailMessage) {
        this.detailMessage = detailMessage;
        return this;
    }
}
