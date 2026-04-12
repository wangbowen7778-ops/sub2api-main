package com.sub2api.module.common.model.enums;

import lombok.Getter;

/**
 * 错误码枚举
 * <p>
 * 按模块划分:
 * 1xxx - 通用错误
 * 2xxx - 认证模块错误
 * 3xxx - 用户模块错误
 * 4xxx - 账号模块错误
 * 5xxx - 网关模块错误
 * 6xxx - 计费模块错误
 * 7xxx - 管理模块错误
 *
 * @author Alibaba Java Code Guidelines
 */
@Getter
public enum ErrorCode {

    // ========== 通用错误 1xxx ==========
    SUCCESS(1000, "操作成功"),
    FAIL(1001, "操作失败"),
    PARAM_INVALID(1002, "参数无效"),
    PARAM_MISSING(1003, "缺少必要参数"),
    DATA_NOT_FOUND(1004, "数据不存在"),
    DATA_EXISTS(1005, "数据已存在"),
    DATA_DELETED(1006, "数据已删除"),
    CONFLICT(1007, "数据冲突"),
    NOT_FOUND(1008, "资源不存在"),
    BAD_REQUEST(1009, "请求无效"),
    INTERNAL_ERROR(1999, "内部系统错误"),

    // ========== 认证模块错误 2xxx ==========
    AUTH_FAIL(2001, "认证失败"),
    AUTH_INVALID(2002, "认证信息无效"),
    AUTH_EXPIRED(2003, "认证已过期"),
    AUTH_DISABLED(2004, "认证已被禁用"),
    AUTH_FORBIDDEN(2005, "无访问权限"),
    AUTH_UNAUTHORIZED(2006, "未登录或登录已失效"),
    AUTH_CODE_INVALID(2007, "验证码无效"),
    AUTH_CODE_EXPIRED(2008, "验证码已过期"),
    AUTH_MFA_REQUIRED(2009, "需要双因素认证"),
    AUTH_MFA_INVALID(2010, "双因素认证失败"),
    AUTH_OAUTH_FAIL(2020, "OAuth认证失败"),
    AUTH_OAUTH_STATE_INVALID(2021, "OAuth状态验证失败"),
    AUTH_OAUTH_USER_CANCELLED(2022, "用户取消OAuth授权"),
    PASSWORD_WRONG(2030, "密码错误"),
    PASSWORD_FORMAT_INVALID(2031, "密码格式不符合要求"),
    USERNAME_EXISTS(2032, "用户名已存在"),
    EMAIL_EXISTS(2033, "邮箱已被使用"),

    // ========== 用户模块错误 3xxx ==========
    USER_NOT_FOUND(3001, "用户不存在"),
    USER_DISABLED(3002, "用户已被禁用"),
    USER_BALANCE_INSUFFICIENT(3003, "余额不足"),
    USER_SUBSCRIPTION_EXPIRED(3004, "订阅已过期"),
    USER_ATTRIBUTE_NOT_FOUND(3010, "用户属性不存在"),

    // ========== 账号模块错误 4xxx ==========
    ACCOUNT_NOT_FOUND(4001, "账号不存在"),
    ACCOUNT_DISABLED(4002, "账号已被禁用"),
    ACCOUNT_EXHAUSTED(4003, "账号额度已用完"),
    ACCOUNT_CREDENTIAL_INVALID(4010, "账号凭证无效"),
    ACCOUNT_CREDENTIAL_EXPIRED(4011, "账号凭证已过期"),
    ACCOUNT_REFRESH_FAIL(4012, "账号凭证刷新失败"),
    ACCOUNT_GROUP_NOT_FOUND(4020, "账号分组不存在"),
    ACCOUNT_ALL_UNAVAILABLE(4030, "所有账号均不可用"),

    // ========== 网关模块错误 5xxx ==========
    GATEWAY_PROXY_FAIL(5001, "代理请求失败"),
    GATEWAY_TIMEOUT(5002, "请求超时"),
    GATEWAY_UPSTREAM_ERROR(5003, "上游服务错误"),
    GATEWAY_RATE_LIMIT(5004, "请求频率超限"),
    GATEWAY_QUOTA_EXCEEDED(5005, "额度超限"),
    GATEWAY_STREAM_ERROR(5010, "流式响应错误"),
    API_KEY_INVALID(5020, "API Key无效"),
    API_KEY_DISABLED(5021, "API Key已被禁用"),
    API_KEY_EXPIRED(5022, "API Key已过期"),

    // ========== 计费模块错误 6xxx ==========
    BILLING_CALC_ERROR(6001, "计费计算错误"),
    BILLING_USAGE_LOG_NOT_FOUND(6010, "用量记录不存在"),
    PROMO_CODE_INVALID(6020, "优惠码无效"),
    PROMO_CODE_EXPIRED(6021, "优惠码已过期"),
    PROMO_CODE_USED(6022, "优惠码已被使用"),
    PROMO_CODE_QUOTA_EXCEEDED(6023, "优惠码已用完"),
    REDEEM_CODE_INVALID(6030, "兑换码无效"),
    REDEEM_CODE_EXPIRED(6031, "兑换码已过期"),

    // ========== 管理模块错误 7xxx ==========
    ADMIN_FORBIDDEN(7001, "无管理员权限"),
    SETTING_NOT_FOUND(7010, "配置项不存在"),
    ANNOUNCEMENT_NOT_FOUND(7020, "公告不存在"),
    PROXY_NOT_FOUND(7030, "代理配置不存在"),
    PROXY_DISABLED(7031, "代理已被禁用"),
    BACKUP_FAIL(7040, "数据备份失败"),

    // ========== 外部服务错误 8xxx ==========
    EXTERNAL_SERVICE_UNAVAILABLE(8001, "外部服务不可用"),
    EXTERNAL_SERVICE_TIMEOUT(8002, "外部服务超时"),
    EXTERNAL_SERVICE_ERROR(8003, "外部服务返回错误"),

    ;

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getMessage() {
        return this.message;
    }

    public Integer getCode() {
        return this.code;
    }

    public static ErrorCode fromCode(Integer code) {
        if (code == null) {
            return FAIL;
        }
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        return FAIL;
    }
}
