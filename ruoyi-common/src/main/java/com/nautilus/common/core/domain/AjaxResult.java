package com.nautilus.common.core.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;

/**
 * RuoYi 标准响应结果封装类
 * 用于统一 API 响应格式
 *
 * @author nautilus-media-cloud
 */
@Data
@NoArgsConstructor
public class AjaxResult extends HashMap<String, Object> implements Serializable {
    
    private static final long serialVersionUID = 1L;

    public static final int SUCCESS = 200;
    public static final int WARN = 301;
    public static final int ERROR = 500;
    public static final int NO_CONTENT = 204;

    public static final String CODE_TAG = "code";
    public static final String MSG_TAG = "msg";
    public static final String DATA_TAG = "data";

    public AjaxResult(int code, String msg) {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
    }

    public AjaxResult(int code, String msg, Object data) {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
        if (data != null) {
            super.put(DATA_TAG, data);
        }
    }

    /**
     * 返回成功消息
     */
    public static AjaxResult success() {
        return AjaxResult.success("夜行 - 操作成功");
    }

    /**
     * 返回成功消息
     */
    public static AjaxResult success(String msg) {
        return new AjaxResult(SUCCESS, msg);
    }

    /**
     * 返回成功消息
     */
    public static AjaxResult success(String msg, Object data) {
        return new AjaxResult(SUCCESS, msg, data);
    }

    /**
     * 返回成功数据
     */
    public static AjaxResult success(Object data) {
        return AjaxResult.success("夜行 - 操作成功", data);
    }

    /**
     * 返回警告消息
     */
    public static AjaxResult warn(String msg) {
        return new AjaxResult(WARN, msg);
    }

    /**
     * 返回警告消息
     */
    public static AjaxResult warn(String msg, Object data) {
        return new AjaxResult(WARN, msg, data);
    }

    /**
     * 返回错误消息
     */
    public static AjaxResult error() {
        return AjaxResult.error("思想犯 - 操作失败");
    }

    /**
     * 返回错误消息
     */
    public static AjaxResult error(String msg) {
        return new AjaxResult(ERROR, msg);
    }

    /**
     * 返回错误消息
     */
    public static AjaxResult error(String msg, Object data) {
        return new AjaxResult(ERROR, msg, data);
    }

    /**
     * 返回错误消息
     */
    public static AjaxResult error(int code, String msg) {
        return new AjaxResult(code, msg);
    }

    /**
     * 返回无内容响应
     */
    public static AjaxResult noContent(String msg) {
        return new AjaxResult(NO_CONTENT, msg);
    }

    /**
     * 方便链式调用
     */
    @Override
    public AjaxResult put(String key, Object value) {
        super.put(key, value);
        return this;
    }
}
