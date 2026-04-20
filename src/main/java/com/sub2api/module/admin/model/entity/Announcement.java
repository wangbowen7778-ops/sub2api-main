package com.sub2api.module.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 公告实体
 * 表名: announcements
 *
 * @author Alibaba Java Code Guidelines
 */
@Accessors(chain = true)
@TableName("announcements")
public class Announcement implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 公告ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 公告标题
     */
    private String title;

    /**
     * 公告内容 (支持 Markdown)
     */
    private String content;

    /**
     * 状态: draft, active, archived
     */
    private String status;

    /**
     * 通知模式: silent, popup
     */
    private String notifyMode;

    /**
     * 展示条件 (JSONB)
     */
    private Map<String, Object> targeting;

    /**
     * 开始展示时间
     */
    private OffsetDateTime startsAt;

    /**
     * 结束展示时间
     */
    private OffsetDateTime endsAt;

    /**
     * 创建人用户ID
     */
    private Long createdBy;

    /**
     * 更新人用户ID
     */
    private Long updatedBy;

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

    // Getters and Setters
    public Long getId() { return id; }
    public Announcement setId(Long id) { this.id = id; return this; }

    public String getTitle() { return title; }
    public Announcement setTitle(String title) { this.title = title; return this; }

    public String getContent() { return content; }
    public Announcement setContent(String content) { this.content = content; return this; }

    public String getStatus() { return status; }
    public Announcement setStatus(String status) { this.status = status; return this; }

    public String getNotifyMode() { return notifyMode; }
    public Announcement setNotifyMode(String notifyMode) { this.notifyMode = notifyMode; return this; }

    public Map<String, Object> getTargeting() { return targeting; }
    public Announcement setTargeting(Map<String, Object> targeting) { this.targeting = targeting; return this; }

    public OffsetDateTime getStartsAt() { return startsAt; }
    public Announcement setStartsAt(OffsetDateTime startsAt) { this.startsAt = startsAt; return this; }

    public OffsetDateTime getEndsAt() { return endsAt; }
    public Announcement setEndsAt(OffsetDateTime endsAt) { this.endsAt = endsAt; return this; }

    public Long getCreatedBy() { return createdBy; }
    public Announcement setCreatedBy(Long createdBy) { this.createdBy = createdBy; return this; }

    public Long getUpdatedBy() { return updatedBy; }
    public Announcement setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; return this; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public Announcement setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; return this; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Announcement setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
}
