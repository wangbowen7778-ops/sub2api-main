package com.sub2api.module.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.admin.mapper.AnnouncementMapper;
import com.sub2api.module.admin.model.entity.Announcement;
import com.sub2api.module.user.mapper.AnnouncementReadMapper;
import com.sub2api.module.user.model.entity.AnnouncementRead;
import com.sub2api.module.user.service.UserService;
import com.sub2api.module.user.service.SubscriptionService;
import com.sub2api.module.common.model.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Announcement Controller
 * 用户公告接口，提供公告列表和已读功能
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

    /**
     * 获取用户可见的公告列表
     */
    @GetMapping
    public Result<List<Map<String, Object>>> listAnnouncements(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {

        Long userId = userService.getCurrentUserId();
        if (userId == null) {
            return Result.fail("User not logged in");
        }

        var user = userService.getUserById(userId);
        if (user == null) {
            return Result.fail("User not found");
        }

        LocalDateTime now = LocalDateTime.now();

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
        Map<Long, LocalDateTime> readMap = getReadMap(userId);

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
                    map.put("notifyMode", a.getNotifyMode());
                    map.put("createdAt", a.getCreatedAt());
                    map.put("readAt", readMap.get(a.getId()));
                    return map;
                })
                .collect(Collectors.toList());

        return Result.success(visibleAnnouncements);
    }

    /**
     * 获取公告详情
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getAnnouncement(@PathVariable Long id) {
        Long userId = userService.getCurrentUserId();
        if (userId == null) {
            return Result.fail("User not logged in");
        }

        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            return Result.fail("Announcement not found");
        }

        var user = userService.getUserById(userId);
        if (user == null) {
            return Result.fail("User not found");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!"active".equals(announcement.getStatus())) {
            return Result.fail("Announcement not active");
        }
        if (announcement.getStartsAt() != null && announcement.getStartsAt().isAfter(now)) {
            return Result.fail("Announcement not started");
        }
        if (announcement.getEndsAt() != null && announcement.getEndsAt().isBefore(now)) {
            return Result.fail("Announcement expired");
        }

        List<Long> activeGroupIds = getActiveSubscriptionGroupIds(userId);
        if (!isAnnouncementVisible(announcement, user, activeGroupIds)) {
            return Result.fail("Announcement not visible");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", announcement.getId());
        result.put("title", announcement.getTitle());
        result.put("content", announcement.getContent());
        result.put("notifyMode", announcement.getNotifyMode());
        result.put("createdAt", announcement.getCreatedAt());

        return Result.success(result);
    }

    /**
     * 标记公告已读
     */
    @PostMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        Long userId = userService.getCurrentUserId();
        if (userId == null) {
            return Result.fail("User not logged in");
        }

        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            return Result.fail("Announcement not found");
        }

        var user = userService.getUserById(userId);
        if (user == null) {
            return Result.fail("User not found");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!"active".equals(announcement.getStatus())) {
            return Result.fail("Announcement not active");
        }
        if (announcement.getStartsAt() != null && announcement.getStartsAt().isAfter(now)) {
            return Result.fail("Announcement not started");
        }
        if (announcement.getEndsAt() != null && announcement.getEndsAt().isBefore(now)) {
            return Result.fail("Announcement expired");
        }

        List<Long> activeGroupIds = getActiveSubscriptionGroupIds(userId);
        if (!isAnnouncementVisible(announcement, user, activeGroupIds)) {
            return Result.fail("Announcement not visible");
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
            read.setReadAt(LocalDateTime.now());
            announcementReadMapper.insert(read);
            log.info("User {} read announcement {}", userId, id);
        }

        return Result.success();
    }

    /**
     * 获取用户已读公告ID映射
     */
    private Map<Long, LocalDateTime> getReadMap(Long userId) {
        LambdaQueryWrapper<AnnouncementRead> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnnouncementRead::getUserId, userId);
        List<AnnouncementRead> reads = announcementReadMapper.selectList(wrapper);

        Map<Long, LocalDateTime> readMap = new HashMap<>();
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
                .filter(s -> s.getExpiresAt() == null || s.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(s -> s.getGroupId())
                .collect(Collectors.toList());
    }

    /**
     * 检查公告是否对用户可见
     */
    private boolean isAnnouncementVisible(Announcement announcement, var user, List<Long> activeGroupIds) {
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
    private boolean evaluateCondition(Map<String, Object> condition, var user, List<Long> activeGroupIds) {
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

                double balance = user.getBalance();
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
}
