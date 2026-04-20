package com.sub2api.module.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * TLS 指纹配置实体
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Accessors(chain = true)
@TableName("tls_fingerprint_profiles")
public class TLSFingerprintProfile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 配置ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 配置名称
     */
    private String name;

    /**
     * 指纹类型: chrome, firefox, safari, ios, android, custom
     */
    private String fingerprintType;

    /**
     * JA3 指纹字符串
     */
    private String ja3;

    /**
     * HTTP2 指纹
     */
    private String http2Fingerprint;

    /**
     * TLS 版本
     */
    private String tlsVersion;

    /**
     * 密码套件
     */
    private String cipherSuites;

    /**
     * 扩展列表 (JSON)
     */
    private String extensions;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 备注
     */
    private String notes;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    /**
     * 删除时间
     */
    private OffsetDateTime deletedAt;
}
