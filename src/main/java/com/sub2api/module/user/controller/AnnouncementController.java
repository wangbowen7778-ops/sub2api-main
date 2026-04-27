package com.sub2api.module.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sub2api.module.admin.mapper.AnnouncementMapper;
import com.sub2api.module.admin.model.entity.Announcement;
import com.sub2api.module.user.mapper.AnnouncementReadMapper;
import com.sub2api.module.user.model.entity.AnnouncementRead;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.service.UserService;
import com.sub2api.module.user.service.SubscriptionService;
import com.sub2api.module.common.model.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户公告控制器
 * 路径: /api/v1/announcements
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementMapper announcementMapper;
    private final AnnouncementReadMapper announcementReadMapper;
    private final UserService userService;
    private final SubscriptionService subscriptionService;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * 获取当前登录用户ID
     */
    private Long getCurrentUserId() {
        return userService.getCurrentUserId();
    }

    /**
     * 获取用户可见的公告列表
     * GET /api/v1/announcements
     */
    @GetMapping
    public Result<List<Map<String, Object>>> listAnnouncements(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {

        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录或登录已过期");
        }

        User user = userService.findById(userId);
        if (user == null) {
            return Result.fail(3001, "用户不存在");
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 查询活跃公告
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Announcement::getStatus, "active")
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
                .orderByDesc(Announcement::getCreatedAt);

        List<Announcement> announcements = announcementMapper.selectList(wrapper);

        // 获取用户已读记录
        Map<Long, OffsetDateTime> readMap = getReadMap(userId);

        // 获取用户活跃订阅
        List<Long> activeGroupIds = getActiveSubscriptionGroupIds(userId);

        // 过滤可见公告（根据 targeting 条件）
        List<Map<String, Object>> visibleAnnouncements = announcements.stream()
                .filter(a -> isAnnouncementVisible(a, user, activeGroupIds))
                .filter(a -> {
                    if (unreadOnly) {
                        return !readMap.containsKey(a.getId());
                    }
                    return true;
                })
                .map(a -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", a.getId());
                    map.put("title", a.getTitle());
                    map.put("content", a.getContent());
                    map.put("notify_mode", a.getNotifyMode());
                    map.put("starts_at", formatDateTime(a.getStartsAt()));
                    map.put("ends_at", formatDateTime(a.getEndsAt()));
                    map.put("created_at", formatDateTime(a.getCreatedAt()));
                    map.put("updated_at", formatDateTime(a.getUpdatedAt()));
                    map.put("read_at", formatDateTime(readMap.get(a.getId())));
                    return map;
                })
                .collect(Collectors.toList());

        return Result.ok(visibleAnnouncements);
    }

    /**
     * 标记公告已读
     * POST /api/v1/announcements/:id/read
     */
    @PostMapping("/{id}/read")
    public Result<Map<String, Object>> markAsRead(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录或登录已过期");
        }

        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            return Result.fail(4041, "公告不存在");
        }

        User user = userService.findById(userId);
        if (user == null) {
            return Result.fail(3001, "用户不存在");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (!"active".equals(announcement.getStatus())) {
            return Result.fail(4001, "公告未激活");
        }
        if (announcement.getStartsAt() != null && announcement.getStartsAt().isAfter(now)) {
            return Result.fail(4001, "公告未开始");
        }
        if (announcement.getEndsAt() != null && announcement.getEndsAt().isBefore(now)) {
            return Result.fail(4001, "公告已过期");
        }

        List<Long> activeGroupIds = getActiveSubscriptionGroupIds(userId);
        if (!isAnnouncementVisible(announcement, user, activeGroupIds)) {
            return Result.fail(4001, "公告不可见");
        }

        // 检查是否已读
        LambdaQueryWrapper<AnnouncementRead> readWrapper = new LambdaQueryWrapper<>();
        readWrapper.eq(AnnouncementRead::getUserId, userId)
                .eq(AnnouncementRead::getAnnouncementId, id);
        AnnouncementRead existingRead = announcementReadMapper.selectOne(readWrapper);

        if (existingRead == null) {
            AnnouncementRead read = new AnnouncementRead();
            read.setUserId(userId);
            read.setAnnouncementId(id);
            read.setReadAt(OffsetDateTime.now());
            announcementReadMapper.insert(read);
            log.info("User {} read announcement {}", userId, id);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("message", "ok");
        return Result.ok(result);
    }

    /**
     * 获取用户已读公告ID映射
     */
    private Map<Long, OffsetDateTime> getReadMap(Long userId) {
        LambdaQueryWrapper<AnnouncementRead> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnnouncementRead::getUserId, userId);
        List<AnnouncementRead> reads = announcementReadMapper.selectList(wrapper);

        Map<Long, OffsetDateTime> readMap = new HashMap<>();
        for (AnnouncementRead read : reads) {
            readMap.put(read.getAnnouncementId(), read.getReadAt());
        }
        return readMap;
    }

    /**
     * 获取用户活跃订阅的分组ID列表
     */
    private List<Long> getActiveSubscriptionGroupIds(Long userId) {
        var subscriptions = subscriptionService.getUserSubscriptions(userId);
        return subscriptions.stream()
                .filter(s -> "active".equals(s.getStatus()))
                .filter(s -> s.getExpiresAt() == null || s.getExpiresAt().isAfter(OffsetDateTime.now()))
                .map(s -> s.getGroupId())
                .collect(Collectors.toList());
    }

    /**
     * 检查公告是否对用户可见
     */
    private boolean isAnnouncementVisible(Announcement announcement, User user, List<Long> activeGroupIds) {
        Map<String, Object> targeting = announcement.getTargeting();
        if (targeting == null || targeting.isEmpty()) {
            return true; // 无 targeting 条件，对所有用户可见
        }

        // 解析 targeting 条件
        // 简单实现：检查 any_of 条件
        List<Map<String, Object>> anyOf = (List<Map<String, Object>>) targeting.get("any_of");
        if (anyOf == null || anyOf.isEmpty()) {
            return true;
        }

        for (Map<String, Object> conditionGroup : anyOf) {
            List<Map<String, Object>> allOf = (List<Map<String, Object>>) conditionGroup.get("all_of");
            if (allOf == null || allOf.isEmpty()) {
                continue;
            }

            // 组内所有条件都满足才算命中
            boolean groupMatched = true;
            for (Map<String, Object> condition : allOf) {
                if (!evaluateCondition(condition, user, activeGroupIds)) {
                    groupMatched = false;
                    break;
                }
            }

            if (groupMatched) {
                return true; // OR: 任意一个条件组满足即可
            }
        }

        return false;
    }

    /**
     * 评估单个条件
     */
    private boolean evaluateCondition(Map<String, Object> condition, User user, List<Long> activeGroupIds) {
        String type = (String) condition.get("type");
        if (type == null) {
            return false;
        }

        switch (type) {
            case "subscription": {
                String operator = (String) condition.get("operator");
                if (!"in".equals(operator)) {
                    return false;
                }
                List<?> groupIds = (List<?>) condition.get("group_ids");
                if (groupIds == null || groupIds.isEmpty()) {
                    return false;
                }
                for (Object gid : groupIds) {
                    long groupId = ((Number) gid).longValue();
                    if (activeGroupIds.contains(groupId)) {
                        return true;
                    }
                }
                return false;
            }

            case "balance": {
                String operator = (String) condition.get("operator");
                Double value = condition.get("value") != null
                        ? ((Number) condition.get("value")).doubleValue()
                        : null;

                if (value == null || user.getBalance() == null) {
                    return false;
                }

                double balance = user.getBalance().doubleValue();
                switch (operator) {
                    case "gt":
                        return balance > value;
                    case "gte":
                        return balance >= value;
                    case "lt":
                        return balance < value;
                    case "lte":
                        return balance <= value;
                    case "eq":
                        return Math.abs(balance - value) < 0.01;
                    default:
                        return false;
                }
            }

            default:
                return false;
        }
    }

    private String formatDateTime(OffsetDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.format(ISO_FORMATTER);
    }
}
