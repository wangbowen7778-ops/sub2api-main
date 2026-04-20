package com.sub2api.module.account.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 代理配置实体
 * 表名: proxies
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Accessors(chain = true)
@TableName("proxies")
public class Proxy implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 代理ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 代理名称
     */
    private String name;

    /**
     * 协议: http, socks5
     */
    private String protocol;

    /**
     * 主机
     */
    private String host;

    /**
     * 端口
     */
    private Integer port;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 状态: active, disabled
     */
    private String status;

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
     * 删除时间 (软删除)
     */
    private OffsetDateTime deletedAt;
}
