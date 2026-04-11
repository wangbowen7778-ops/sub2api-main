package com.sub2api.module.gateway.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型信息 VO
 * 用于 /v1/models 端点返回
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfo {
    /**
     * 模型ID
     */
    private String id;

    /**
     * 对象类型
     */
    private String object = "model";

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 创建时间
     */
    private String createdAt = "2024-01-01T00:00:00Z";
}