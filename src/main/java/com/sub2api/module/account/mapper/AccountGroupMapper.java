package com.sub2api.module.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.account.model.entity.AccountGroup;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账号分组关联 Mapper
 *
 * @author Alibaba Java Code Guidelines
 */
public interface AccountGroupMapper extends BaseMapper<AccountGroup> {

    /**
     * 获取分组下的所有账号ID
     */
    List<Long> selectAccountIdsByGroupId(@Param("groupId") Long groupId);

    /**
     * 获取账号所属的所有分组ID
     */
    List<Long> selectGroupIdsByAccountId(@Param("accountId") Long accountId);

    /**
     * 插入账号分组关联
     */
    int insertAccountGroup(@Param("accountId") Long accountId, @Param("groupId") Long groupId, @Param("priority") Integer priority);

    /**
     * 插入账号分组关联 (使用默认优先级)
     */
    int insertAccountGroupWithDefaultPriority(@Param("accountId") Long accountId, @Param("groupId") Long groupId);

    /**
     * 删除账号的所有分组关联
     */
    int deleteByAccountId(@Param("accountId") Long accountId);

    /**
     * 获取所有已分组的账号ID
     */
    List<Long> selectAllGroupedAccountIds();
}
