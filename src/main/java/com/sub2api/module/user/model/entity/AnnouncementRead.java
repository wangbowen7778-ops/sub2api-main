package com.sub2api.module.user.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公告已读记录实体
 * 表名: announcement_reads
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Accessors(chain = true)
@TableName("announcement_reads")
public class AnnouncementRead implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 公告ID
     */
    private Long announcementId;

    /**
     * 阅读时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime readAt;
}
