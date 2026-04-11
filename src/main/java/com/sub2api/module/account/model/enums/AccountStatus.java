package com.sub2api.module.account.model.enums;

import lombok.Getter;

/**
 * 账号状态枚举
 *
 * @author Alibaba Java Code Guidelines
 */
@Getter
public enum AccountStatus {

    /**
     * 活跃可用
     */
    ACTIVE("active", "活跃"),

    /**
     * 额度用尽
     */
    EXHAUSTED("exhausted", "额度用尽"),

    /**
     * 凭证过期
     */
    CREDENTIAL_EXPIRED("credential_expired", "凭证过期"),

    /**
     * 认证失败
     */
    AUTH_FAILED("auth_failed", "认证失败"),

    /**
     * 禁用
     */
    DISABLED("disabled", "已禁用"),

    /**
     * 未知错误
     */
    ERROR("error", "错误");

    private final String value;
    private final String description;

    AccountStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public static AccountStatus fromValue(String value) {
        for (AccountStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return ERROR;
    }

    public boolean isUsable() {
        return this == ACTIVE;
    }
}
