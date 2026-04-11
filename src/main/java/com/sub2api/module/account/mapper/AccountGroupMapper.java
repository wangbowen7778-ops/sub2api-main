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
}
