package com.sub2api.module.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sub2api.module.account.model.entity.AccountGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 账号分组关联 Mapper
 *
 * @author Alibaba Java Code Guidelines
 */
@Mapper
public interface AccountGroupMapper extends BaseMapper<AccountGroup> {

    /**
     * 获取分组下的所有账号ID
     */
    @Select("SELECT account_id FROM account_groups WHERE group_id = #{groupId}")
    List<Long> selectAccountIdsByGroupId(@Param("groupId") Long groupId);

    /**
     * 获取分组下所有账号ID（分页）
     */
    @Select("SELECT account_id FROM account_groups WHERE group_id = #{groupId}")
    IPage<Long> selectAccountIdsByGroupId(Page<Long> page, @Param("groupId") Long groupId);

    /**
     * 获取账号所属的所有分组ID
     */
    @Select("SELECT group_id FROM account_groups WHERE account_id = #{accountId}")
    List<Long> selectGroupIdsByAccountId(@Param("accountId") Long accountId);

    /**
     * 插入账号分组关联
     */
    @Select("INSERT INTO account_groups (account_id, group_id, priority, created_at) VALUES (#{accountId}, #{groupId}, #{priority}, NOW())")
    int insertAccountGroup(@Param("accountId") Long accountId, @Param("groupId") Long groupId, @Param("priority") Integer priority);

    /**
     * 插入账号分组关联 (使用默认优先级)
     */
    @Select("INSERT INTO account_groups (account_id, group_id, priority, created_at) VALUES (#{accountId}, #{groupId}, 0, NOW())")
    int insertAccountGroup(@Param("accountId") Long accountId, @Param("groupId") Long groupId);

    /**
     * 删除账号的所有分组关联
     */
    @Select("DELETE FROM account_groups WHERE account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);

    /**
     * 获取所有已分组的账号ID
     */
    @Select("SELECT DISTINCT account_id FROM account_groups")
    List<Long> selectAllGroupedAccountIds();
}
