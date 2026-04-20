package com.sub2api.module.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.admin.model.entity.IdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

import java.time.OffsetDateTime;

/**
 * 幂等性记录 Mapper
 *
 * @author Alibaba Java Code Guidelines
 */
@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecord> {

    /**
     * 插入处理中的记录 (ON CONFLICT DO NOTHING)
     */
    @Insert("INSERT INTO idempotency_records (scope, idempotency_key_hash, request_fingerprint, status, locked_until, expires_at, created_at, updated_at) " +
            "VALUES (#{scope}, #{idempotencyKeyHash}, #{requestFingerprint}, #{status}, #{lockedUntil}, #{expiresAt}, NOW(), NOW()) " +
            "ON CONFLICT (scope, idempotency_key_hash) DO NOTHING")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertProcessingIgnoreConflict(IdempotencyRecord record);

    /**
     * 根据 scope 和 key hash 查询记录
     */
    @Select("SELECT id, scope, idempotency_key_hash, request_fingerprint, status, response_status, " +
            "response_body, error_reason, locked_until, expires_at, created_at, updated_at " +
            "FROM idempotency_records WHERE scope = #{scope} AND idempotency_key_hash = #{keyHash}")
    IdempotencyRecord selectByScopeAndKeyHash(@Param("scope") String scope, @Param("keyHash") String keyHash);

    /**
     * 尝试延长处理锁
     */
    @Update("UPDATE idempotency_records SET locked_until = #{newLockedUntil}, expires_at = #{newExpiresAt}, updated_at = NOW() " +
            "WHERE id = #{id} AND request_fingerprint = #{requestFingerprint} AND status = 'processing'")
    int extendProcessingLock(@Param("id") Long id,
                            @Param("requestFingerprint") String requestFingerprint,
                            @Param("newLockedUntil") OffsetDateTime newLockedUntil,
                            @Param("newExpiresAt") OffsetDateTime newExpiresAt);

    /**
     * 标记为成功
     */
    @Update("UPDATE idempotency_records SET status = 'succeeded', response_status = #{responseStatus}, " +
            "response_body = #{responseBody}, expires_at = #{expiresAt}, updated_at = NOW() " +
            "WHERE id = #{id}")
    int markSucceeded(@Param("id") Long id,
                      @Param("responseStatus") Integer responseStatus,
                      @Param("responseBody") String responseBody,
                      @Param("expiresAt") OffsetDateTime expiresAt);

    /**
     * 标记为可重试失败
     */
    @Update("UPDATE idempotency_records SET status = 'failed_retryable', error_reason = #{errorReason}, " +
            "locked_until = #{lockedUntil}, expires_at = #{expiresAt}, updated_at = NOW() " +
            "WHERE id = #{id}")
    int markFailedRetryable(@Param("id") Long id,
                           @Param("errorReason") String errorReason,
                           @Param("lockedUntil") OffsetDateTime lockedUntil,
                           @Param("expiresAt") OffsetDateTime expiresAt);

    /**
     * 删除过期记录 (PostgreSQL版本，使用子查询)
     */
    @Update("DELETE FROM idempotency_records WHERE id IN (" +
            "SELECT id FROM idempotency_records WHERE expires_at < #{now} LIMIT #{limit})")
    int deleteExpired(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    /**
     * 尝试重新获取锁 (用于竞争处理)
     */
    @Update("UPDATE idempotency_records SET locked_until = #{newLockedUntil}, expires_at = #{newExpiresAt}, updated_at = NOW() " +
            "WHERE id = #{id} AND status = #{fromStatus} AND (locked_until IS NULL OR locked_until < #{now})")
    int tryReclaim(@Param("id") Long id,
                   @Param("fromStatus") String fromStatus,
                   @Param("newLockedUntil") OffsetDateTime newLockedUntil,
                   @Param("newExpiresAt") OffsetDateTime newExpiresAt,
                   @Param("now") OffsetDateTime now);
}
