package com.sub2api.module.account.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 账号分组关联实体
 * 表名: account_groups
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Accessors(chain = true)
@TableName("account_groups")
public class AccountGroup implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 账号ID
     */
    private Long accountId;

    /**
     * 分组ID
     */
    private Long groupId;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
