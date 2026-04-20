package com.sub2api.module.common.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 日期时间工具类
 *
 * @author Alibaba Java Code Guidelines
 */
public class DateTimeUtil {

    public static final String PATTERN_DATE = "yyyy-MM-dd";
    public static final String PATTERN_TIME = "HH:mm:ss";
    public static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";
    public static final String PATTERN_DATETIME_MINI = "yyyyMMddHHmmss";
    public static final String PATTERN_DATETIME_COMPACT = "yyyyMMddHHmmssSSS";

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private DateTimeUtil() {
    }

    /**
     * 格式化日期时间
     */
    public static String format(LocalDateTime dateTime) {
        return format(dateTime, PATTERN_DATETIME);
    }

    /**
     * 格式化日期时间
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        if (dateTime == null) {
            return null;
        }
        return DateUtil.format(dateTime, pattern);
    }

    /**
     * 格式化日期
     */
    public static String formatDate(LocalDateTime dateTime) {
        return format(dateTime, PATTERN_DATE);
    }

    /**
     * 解析日期时间
     */
    public static LocalDateTime parse(String dateTimeStr) {
        return parse(dateTimeStr, PATTERN_DATETIME);
    }

    /**
     * 解析日期时间
     */
    public static LocalDateTime parse(String dateTimeStr, String pattern) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        return LocalDateTimeUtil.parse(dateTimeStr, pattern);
    }

    /**
     * LocalDateTime 转 Date
     */
    public static Date toDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return Date.from(dateTime.atZone(ZONE_ID).toInstant());
    }

    /**
     * Date 转 LocalDateTime
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZONE_ID).toLocalDateTime();
    }

    /**
     * 获取当前时间戳 (秒)
     */
    public static Long getUnixTimestamp() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * 获取当前时间戳 (毫秒)
     */
    public static Long getUnixTimestampMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 时间戳 (秒) 转 LocalDateTime
     */
    public static LocalDateTime fromUnixTimestamp(Long timestamp) {
        if (timestamp == null) {
            return null;
        }
        return LocalDateTimeUtil.of(timestamp * 1000);
    }

    /**
     * 计算时间差 (秒)
     */
    public static Long betweenSecond(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return Duration.between(start, end).getSeconds();
    }

    /**
     * 计算时间差 (分钟)
     */
    public static Long betweenMinute(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return Duration.between(start, end).toMinutes();
    }

    /**
     * 计算时间差 (小时)
     */
    public static Long betweenHour(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return Duration.between(start, end).toHours();
    }

    /**
     * 计算时间差 (天)
     */
    public static Long betweenDay(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return Duration.between(start, end).toDays();
    }

    /**
     * 判断是否过期
     */
    public static boolean isExpired(LocalDateTime expireTime) {
        if (expireTime == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 判断是否过期 (OffsetDateTime)
     */
    public static boolean isExpired(OffsetDateTime expireTime) {
        if (expireTime == null) {
            return true;
        }
        return OffsetDateTime.now().isAfter(expireTime);
    }

    /**
     * 判断是否在时间范围内
     */
    public static boolean isBetween(LocalDateTime target, LocalDateTime start, LocalDateTime end) {
        if (target == null || start == null || end == null) {
            return false;
        }
        return !target.isBefore(start) && !target.isAfter(end);
    }

    /**
     * 判断是否在时间范围内 (OffsetDateTime)
     */
    public static boolean isBetween(OffsetDateTime target, OffsetDateTime start, OffsetDateTime end) {
        if (target == null || start == null || end == null) {
            return false;
        }
        return !target.isBefore(start) && !target.isAfter(end);
    }

    /**
     * 获取当天开始时间
     */
    public static LocalDateTime getDayStart(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.toLocalDate().atStartOfDay();
    }

    /**
     * 获取当天结束时间
     */
    public static LocalDateTime getDayEnd(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.toLocalDate().atTime(23, 59, 59, 999999999);
    }

    /**
     * 加上秒数
     */
    public static LocalDateTime plusSeconds(LocalDateTime dateTime, long seconds) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.plusSeconds(seconds);
    }

    /**
     * 减去秒数
     */
    public static LocalDateTime minusSeconds(LocalDateTime dateTime, long seconds) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.minusSeconds(seconds);
    }
}
