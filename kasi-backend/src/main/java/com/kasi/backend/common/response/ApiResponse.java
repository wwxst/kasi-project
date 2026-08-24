package com.kasi.backend.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一API响应体
 *
 * @param <T> 响应数据类型
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** 状态码，0表示成功 */
    private int code;
    /** 提示消息 */
    private String message;
    /** 响应数据 */
    private T data;

    /**
     * 成功响应（无数据，默认消息）
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(0, "成功", null);
    }

    /**
     * 成功响应（无数据，自定义消息）
     */
    public static ApiResponse<Void> successMessage(String message) {
        return new ApiResponse<>(0, message, null);
    }

    /**
     * 成功响应（带数据，默认消息）
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "成功", data);
    }

    /**
     * 成功响应（带数据，自定义消息）
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(0, message, data);
    }

    /**
     * 失败响应
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /**
     * 失败响应（带数据）
     */
    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }
}
