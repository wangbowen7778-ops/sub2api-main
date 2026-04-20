package com.sub2api.module.user.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户VO - 用于API响应
 * 匹配Go后端dto.User的JSON格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("email")
    private String email;

    @JsonProperty("username")
    private String username;

    @JsonProperty("role")
    private String role;

    @JsonProperty("balance")
    private BigDecimal balance;

    @JsonProperty("concurrency")
    private Integer concurrency;

    @JsonProperty("status")
    private String status;

    @JsonProperty("allowed_groups")
    private List<Long> allowedGroups;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    /**
     * 运行模式
     */
    @JsonProperty("run_mode")
    private String runMode;
}