package com.sub2api.module.channel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.channel.mapper.ChannelMapper;
import com.sub2api.module.channel.mapper.ChannelModelPricingMapper;
import com.sub2api.module.channel.mapper.PricingIntervalMapper;
import com.sub2api.module.channel.model.entity.Channel;
import com.sub2api.module.channel.model.entity.ChannelModelPricing;
import com.sub2api.module.channel.model.entity.PricingInterval;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 渠道管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelMapper channelMapper;
    private final ChannelModelPricingMapper pricingMapper;
    private final PricingIntervalMapper intervalMapper;
    private final ObjectMapper objectMapper;

    private final AtomicReference<Map<Long, ChannelCache>> cacheRef = new AtomicReference<>(new ConcurrentHashMap<>());

    // 缓存 TTL: 10分钟
    private static final long CACHE_TTL_MS = 10 * 60 * 1000;
    // 错误缓存 TTL: 5秒
    private static final long ERROR_CACHE_TTL_MS = 5 * 1000;

    // 计费模式常量
    public static final String BILLING_MODE_TOKEN = "token";
    public static final String BILLING_MODE_PER_REQUEST = "per_request";
    public static final String BILLING_MODE_IMAGE = "image";

    // 计费模型来源常量
    public static final String BILLING_MODEL_SOURCE_REQUESTED = "requested";
    public static final String BILLING_MODEL_SOURCE_UPSTREAM = "upstream";
    public static final String BILLING_MODEL_SOURCE_CHANNEL_MAPPED = "channel_mapped";

    /**
     * 创建渠道
     */
    @Transactional
    public Channel create(CreateChannelInput input) {
        // 检查名称是否存在
        if (channelMapper.existsByName(input.getName())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Channel name already exists");
        }

        // 检查分组冲突
        if (input.getGroupIds() != null && !input.getGroupIds().isEmpty()) {
            List<Long> conflicting = getGroupsInOtherChannels(0L, input.getGroupIds());
            if (!conflicting.isEmpty()) {
                throw new BusinessException(ErrorCode.CONFLICT, "One or more groups already belong to another channel");
            }
        }

        // 构建渠道实体
        Channel channel = new Channel();
        channel.setName(input.getName());
        channel.setDescription(input.getDescription());
        channel.setStatus("active");
        channel.setBillingModelSource(input.getBillingModelSource() != null ?
                input.getBillingModelSource() : BILLING_MODEL_SOURCE_CHANNEL_MAPPED);
        channel.setRestrictModels(input.getRestrictModels());
        channel.setModelMapping(serializeModelMapping(input.getModelMapping()));

        channelMapper.insert(channel);

        // 设置分组关联
        if (input.getGroupIds() != null && !input.getGroupIds().isEmpty()) {
            setGroupIds(channel.getId(), input.getGroupIds());
        }

        // 设置模型定价
        if (input.getModelPricing() != null && !input.getModelPricing().isEmpty()) {
            replaceModelPricing(channel.getId(), input.getModelPricing());
        }

        invalidateCache();
        return getById(channel.getId());
    }

    /**
     * 根据ID获取渠道
     */
    public Channel getById(Long id) {
        Channel channel = channelMapper.selectById(id);
        if (channel == null) {
            return null;
        }
        loadRelations(channel);
        return channel;
    }

    /**
     * 更新渠道
     */
    @Transactional
    public Channel update(Long id, UpdateChannelInput input) {
        Channel channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Channel not found");
        }

        // 更新名称
        if (StringUtils.hasText(input.getName()) && !input.getName().equals(channel.getName())) {
            if (channelMapper.existsByNameExcluding(input.getName(), id)) {
                throw new BusinessException(ErrorCode.CONFLICT, "Channel name already exists");
            }
            channel.setName(input.getName());
        }

        // 更新描述
        if (input.getDescription() != null) {
            channel.setDescription(input.getDescription());
        }

        // 更新状态
        if (StringUtils.hasText(input.getStatus())) {
            channel.setStatus(input.getStatus());
        }

        // 更新模型限制
        if (input.getRestrictModels() != null) {
            channel.setRestrictModels(input.getRestrictModels());
        }

        // 更新分组
        if (input.getGroupIds() != null) {
            List<Long> conflicting = getGroupsInOtherChannels(id, input.getGroupIds());
            if (!conflicting.isEmpty()) {
                throw new BusinessException(ErrorCode.CONFLICT, "One or more groups already belong to another channel");
            }
            setGroupIds(id, input.getGroupIds());
            channel.setGroupIds(input.getGroupIds());
        }

        // 更新模型定价
        if (input.getModelPricing() != null) {
            replaceModelPricing(id, input.getModelPricing());
        }

        // 更新模型映射
        if (input.getModelMapping() != null) {
            channel.setModelMapping(serializeModelMapping(input.getModelMapping()));
        }

        // 更新计费模型来源
        if (StringUtils.hasText(input.getBillingModelSource())) {
            channel.setBillingModelSource(input.getBillingModelSource());
        }

        channelMapper.updateById(channel);
        invalidateCache();
        return getById(id);
    }

    /**
     * 删除渠道
     */
    @Transactional
    public void delete(Long id) {
        Channel channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Channel not found");
        }

        // 删除渠道
        channelMapper.deleteById(id);

        // 清理关联数据
        // 注意: channel_groups 和 channel_model_pricing 通过外键级联或手动清理
        invalidateCache();
    }

    /**
     * 获取渠道列表（分页）
     */
    public PageResult<Channel> list(int page, int pageSize, String status, String search) {
        LambdaQueryWrapper<Channel> wrapper = channelMapper.selectList(new LambdaQueryWrapper<>())
                .like(StringUtils.hasText(search), "name", search)
                .eq(StringUtils.hasText(status), "status", status)
                .orderByDesc("id");

        // 使用 MyBatis-Plus 分页
        long total = channelMapper.selectCount(wrapper);
        List<Channel> channels = channelMapper.selectList(wrapper
                .last("LIMIT " + pageSize + " OFFSET " + (page - 1) * pageSize));

        // 加载关联数据
        for (Channel ch : channels) {
            loadRelations(ch);
        }

        return new PageResult<>(channels, total, page, pageSize);
    }

    /**
     * 获取所有活跃渠道
     */
    public List<Channel> listAll() {
        List<Channel> channels = channelMapper.selectList(
                new LambdaQueryWrapper<Channel>().eq("status", "active")
        );
        for (Channel ch : channels) {
            loadRelations(ch);
        }
        return channels;
    }

    /**
     * 获取分组关联的渠道
     */
    public Channel getChannelForGroup(Long groupId) {
        Long channelId = channelMapper.selectChannelIdByGroupId(groupId);
        if (channelId == null) {
            return null;
        }
        Channel channel = channelMapper.selectById(channelId);
        if (channel != null && channel.isActive()) {
            loadRelations(channel);
            return channel;
        }
        return null;
    }

    /**
     * 获取指定分组+模型的渠道定价
     */
    public ChannelModelPricing getChannelModelPricing(Long groupId, String model) {
        Channel channel = getChannelForGroup(groupId);
        if (channel == null || channel.getModelPricing() == null) {
            return null;
        }

        String modelLower = model.toLowerCase();
        for (ChannelModelPricing pricing : channel.getModelPricing()) {
            for (String m : pricing.getModelList()) {
                if (m.toLowerCase().equals(modelLower)) {
                    return pricing.deepClone();
                }
            }
        }
        return null;
    }

    /**
     * 解析渠道级模型映射
     */
    public ChannelMappingResult resolveChannelMapping(Long groupId, String model) {
        ChannelMappingResult result = new ChannelMappingResult();
        result.setMappedModel(model);

        Channel channel = getChannelForGroup(groupId);
        if (channel == null) {
            return result;
        }

        result.setChannelId(channel.getId());
        result.setBillingModelSource(channel.getBillingModelSource() != null ?
                channel.getBillingModelSource() : BILLING_MODEL_SOURCE_CHANNEL_MAPPED);

        // 解析模型映射
        Map<String, Map<String, String>> modelMapping = deserializeModelMapping(channel.getModelMapping());
        Map<String, String> platformMapping = modelMapping.get(channel.getPlatform());
        if (platformMapping != null) {
            String mapped = platformMapping.get(model.toLowerCase());
            if (mapped != null && !mapped.isEmpty()) {
                result.setMappedModel(mapped);
                result.setMapped(true);
            }
        }

        return result;
    }

    /**
     * 检查模型是否被限制
     */
    public boolean isModelRestricted(Long groupId, String model) {
        Channel channel = getChannelForGroup(groupId);
        if (channel == null || !channel.getRestrictModels()) {
            return false;
        }

        ChannelModelPricing pricing = getChannelModelPricing(groupId, model);
        return pricing == null;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 加载渠道的关联数据（分组、定价）
     */
    private void loadRelations(Channel channel) {
        // 加载分组ID
        channel.setGroupIds(channelMapper.selectGroupIdsByChannelId(channel.getId()));

        // 加载模型定价
        List<ChannelModelPricing> pricingList = pricingMapper.selectByChannelId(channel.getId());
        for (ChannelModelPricing pricing : pricingList) {
            // 加载区间
            List<PricingInterval> intervals = intervalMapper.selectByPricingId(pricing.getId());
            pricing.setIntervals(intervals);
        }
        channel.setModelPricing(pricingList);
    }

    /**
     * 设置渠道的分组关联
     */
    private void setGroupIds(Long channelId, List<Long> groupIds) {
        // 删除旧的关联
        // 注意：需要根据实际表结构实现
        // 假设有 channel_groups 表存储关联

        // 插入新的关联
        // 需要根据实际表结构实现
    }

    /**
     * 获取在其他渠道中的分组ID列表
     */
    private List<Long> getGroupsInOtherChannels(Long channelId, List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Collections.emptyList();
        }
        return channelMapper.selectChannelIdsByGroupIdsExcluding(groupIds, channelId);
    }

    /**
     * 替换渠道的模型定价
     */
    private void replaceModelPricing(Long channelId, List<ChannelModelPricing> newPricing) {
        // 删除旧的定价
        List<ChannelModelPricing> oldPricing = pricingMapper.selectByChannelId(channelId);
        for (ChannelModelPricing p : oldPricing) {
            intervalMapper.delete(new LambdaQueryWrapper<PricingInterval>().eq("pricing_id", p.getId()));
        }
        pricingMapper.delete(new LambdaQueryWrapper<ChannelModelPricing>().eq("channel_id", channelId));

        // 插入新的定价
        for (ChannelModelPricing pricing : newPricing) {
            pricing.setChannelId(channelId);
            pricing.setId(null);
            pricing.setCreatedAt(null);
            pricing.setUpdatedAt(null);
            pricingMapper.insert(pricing);

            // 插入区间
            if (pricing.getIntervals() != null) {
                for (PricingInterval interval : pricing.getIntervals()) {
                    interval.setId(null);
                    interval.setPricingId(pricing.getId());
                    interval.setCreatedAt(null);
                    interval.setUpdatedAt(null);
                    intervalMapper.insert(interval);
                }
            }
        }
    }

    /**
     * 使缓存失效
     */
    private void invalidateCache() {
        cacheRef.set(new ConcurrentHashMap<>());
    }

    /**
     * 序列化模型映射为 JSON 字符串
     */
    private String serializeModelMapping(Map<String, Map<String, String>> modelMapping) {
        if (modelMapping == null || modelMapping.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(modelMapping);
        } catch (Exception e) {
            log.error("Failed to serialize model mapping", e);
            return "{}";
        }
    }

    /**
     * 反序列化模型映射
     */
    private Map<String, Map<String, String>> deserializeModelMapping(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Map<String, String>>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize model mapping", e);
            return Collections.emptyMap();
        }
    }

    // ========== 内部类 ==========

    /**
     * 创建渠道输入
     */
    @Data
    public static class CreateChannelInput {
        private String name;
        private String description;
        private List<Long> groupIds;
        private List<ChannelModelPricing> modelPricing;
        private Map<String, Map<String, String>> modelMapping;
        private String billingModelSource;
        private Boolean restrictModels;
    }

    /**
     * 更新渠道输入
     */
    @Data
    public static class UpdateChannelInput {
        private String name;
        private String description;
        private String status;
        private List<Long> groupIds;
        private List<ChannelModelPricing> modelPricing;
        private Map<String, Map<String, String>> modelMapping;
        private String billingModelSource;
        private Boolean restrictModels;
    }

    /**
     * 分页结果
     */
    @Data
    public static class PageResult<T> {
        private List<T> list;
        private long total;
        private int page;
        private int pageSize;

        public PageResult(List<T> list, long total, int page, int pageSize) {
            this.list = list;
            this.total = total;
            this.page = page;
            this.pageSize = pageSize;
        }
    }

    /**
     * 渠道映射结果
     */
    @Data
    public static class ChannelMappingResult {
        private String mappedModel;
        private Long channelId;
        private boolean mapped;
        private String billingModelSource;
    }

    /**
     * 渠道缓存条目
     */
    @Data
    private static class ChannelCache {
        private Channel channel;
        private long loadedAt;
        private Map<String, String> groupPlatform;
    }
}
