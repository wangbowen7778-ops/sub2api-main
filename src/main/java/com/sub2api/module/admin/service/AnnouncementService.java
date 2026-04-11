package com.sub2api.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.admin.mapper.AnnouncementMapper;
import com.sub2api.module.admin.model.entity.Announcement;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Announcement service
 *
 * @author Alibaba Java Code Guidelines
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService extends ServiceImpl<AnnouncementMapper, Announcement> {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementService.class);

    private final AnnouncementMapper announcementMapper;

    /**
     * Get active announcement list
     */
    public List<Announcement> listActive() {
        LocalDateTime now = LocalDateTime.now();
        return list(new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, "active")
                .and(w -> w
                        .isNull(Announcement::getStartsAt)
                        .or()
                        .le(Announcement::getStartsAt, now)
                )
                .and(w -> w
                        .isNull(Announcement::getEndsAt)
                        .or()
                        .ge(Announcement::getEndsAt, now)
                )
                .orderByDesc(Announcement::getCreatedAt));
    }

    /**
     * Create announcement
     */
    @Transactional(rollbackFor = Exception.class)
    public Announcement createAnnouncement(Announcement announcement) {
        if (announcement.getStatus() == null) {
            announcement.setStatus("draft");
        }
        if (announcement.getNotifyMode() == null) {
            announcement.setNotifyMode("silent");
        }
        announcement.setCreatedAt(LocalDateTime.now());
        announcement.setUpdatedAt(LocalDateTime.now());

        if (!save(announcement)) {
            throw new BusinessException(ErrorCode.FAIL, "Failed to create announcement");
        }

        log.info("Created announcement: id={}, title={}", announcement.getId(), announcement.getTitle());
        return announcement;
    }

    /**
     * Update announcement
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateAnnouncement(Announcement announcement) {
        announcement.setUpdatedAt(LocalDateTime.now());
        updateById(announcement);
        log.info("Updated announcement: id={}", announcement.getId());
    }

    /**
     * Publish announcement
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long announcementId, Long operatorId) {
        Announcement announcement = new Announcement();
        announcement.setId(announcementId);
        announcement.setStatus("active");
        announcement.setUpdatedBy(operatorId);
        announcement.setUpdatedAt(LocalDateTime.now());
        updateById(announcement);
        log.info("Published announcement: id={}", announcementId);
    }

    /**
     * Archive announcement
     */
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long announcementId) {
        Announcement announcement = new Announcement();
        announcement.setId(announcementId);
        announcement.setStatus("archived");
        announcement.setUpdatedAt(LocalDateTime.now());
        updateById(announcement);
        log.info("Archived announcement: id={}", announcementId);
    }
}
