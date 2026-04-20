package com.sub2api.module.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.account.mapper.GroupMapper;
import com.sub2api.module.account.model.entity.Group;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 分组服务
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService extends ServiceImpl<GroupMapper, Group> {

    private final GroupMapper groupMapper;

    /**
     * 根据ID查询分组
     */
    public Group findById(Long id) {
        Group group = getById(id);
        if (group == null || group.getDeletedAt() != null) {
            return null;
        }
        return group;
    }

    /**
     * 根据名称查询分组
     */
    public Group findByName(String name) {
        return getOne(new LambdaQueryWrapper<Group>()
                .eq(Group::getName, name)
                .isNull(Group::getDeletedAt));
    }

    /**
     * 根据平台查询分组列表
     */
    public List<Group> listByPlatform(String platform) {
        return list(new LambdaQueryWrapper<Group>()
                .eq(Group::getPlatform, platform)
                .eq(Group::getStatus, "active")
                .isNull(Group::getDeletedAt)
                .orderByAsc(Group::getSortOrder));
    }

    /**
     * 查询所有活跃分组
     */
    public List<Group> listActive() {
        return list(new LambdaQueryWrapper<Group>()
                .eq(Group::getStatus, "active")
                .isNull(Group::getDeletedAt)
                .orderByAsc(Group::getSortOrder));
    }

    /**
     * 创建分组
     */
    @Transactional(rollbackFor = Exception.class)
    public Group createGroup(Group group) {
        if (group.getRateMultiplier() == null) {
            group.setRateMultiplier(java.math.BigDecimal.ONE);
        }
        if (group.getStatus() == null) {
            group.setStatus("active");
        }
        if (group.getSortOrder() == null) {
            group.setSortOrder(0);
        }
        group.setCreatedAt(OffsetDateTime.now());
        group.setUpdatedAt(OffsetDateTime.now());

        if (!save(group)) {
            throw new BusinessException(ErrorCode.FAIL, "创建分组失败");
        }

        log.info("创建分组: groupId={}, name={}", group.getId(), group.getName());
        return group;
    }

    /**
     * 更新分组
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateGroup(Group group) {
        Group existing = findById(group.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_GROUP_NOT_FOUND);
        }
        group.setUpdatedAt(OffsetDateTime.now());
        updateById(group);
        log.info("更新分组: groupId={}", group.getId());
    }

    /**
     * 软删除分组
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long groupId) {
        Group updateGroup = new Group();
        updateGroup.setId(groupId);
        updateGroup.setDeletedAt(OffsetDateTime.now());
        updateGroup.setUpdatedAt(OffsetDateTime.now());
        updateById(updateGroup);
        log.info("删除分组: groupId={}", groupId);
    }
}
