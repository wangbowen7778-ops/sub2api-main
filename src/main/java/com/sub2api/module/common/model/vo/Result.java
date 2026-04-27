package com.sub2api.module.common.model.vo;

import com.sub2api.module.common.model.enums.ErrorCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * Unified response
 *
 * @author Alibaba Java Code Guidelines
 */
@Accessors(chain = true)
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Response code
     */
    private Integer code;

    /**
     * Response message
     */
    private String message;

    /**
     * Success flag
     */
    private Boolean success;

    /**
     * Data
     */
    private T data;

    /**
     * Timestamp
     */
    private Long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public Integer getCode() { return code; }
    public Result<T> setCode(Integer code) { this.code = code; return this; }

    public String getMessage() { return message; }
    public Result<T> setMessage(String message) { this.message = message; return this; }

    public Boolean getSuccess() { return success; }
    public Result<T> setSuccess(Boolean success) { this.success = success; return this; }

    public T getData() { return data; }
    public Result<T> setData(T data) { this.data = data; return this; }

    public Long getTimestamp() { return timestamp; }
    public Result<T> setTimestamp(Long timestamp) { this.timestamp = timestamp; return this; }

    public static <T> Result<T> ok() {
        return new Result<T>()
                .setCode(0)
                .setMessage("success")
                .setSuccess(true);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<T>()
                .setCode(0)
                .setMessage("success")
                .setSuccess(true)
                .setData(data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<T>()
                .setCode(0)
                .setMessage(message)
                .setSuccess(true)
                .setData(data);
    }

    public static <T> Result<T> fail() {
        return new Result<T>()
                .setCode(500)
                .setMessage("Operation failed")
                .setSuccess(false);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<T>()
                .setCode(500)
                .setMessage(message)
                .setSuccess(false);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<T>()
                .setCode(code)
                .setMessage(message)
                .setSuccess(false);
    }

    public static <T> Result<T> fail(Integer code, String message, T data) {
        return new Result<T>()
                .setCode(code)
                .setMessage(message)
                .setSuccess(false)
                .setData(data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<T>()
                .setCode(errorCode.getCode())
                .setMessage(errorCode.getMessage())
                .setSuccess(false);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<T>()
                .setCode(errorCode.getCode())
                .setMessage(message)
                .setSuccess(false);
    }

    public boolean isSuccess() {
        return Boolean.TRUE.equals(this.success);
    }
}
