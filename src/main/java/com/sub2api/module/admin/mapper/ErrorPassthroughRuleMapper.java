package com.sub2api.module.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.admin.model.entity.ErrorPassthroughRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 错误透传规则 Mapper
 */
@Mapper
public interface ErrorPassthroughRuleMapper extends BaseMapper<ErrorPassthroughRule> {

    /**
     * 获取所有启用的规则（按优先级排序）
     */
    @Select("SELECT * FROM error_passthrough_rules WHERE deleted_at IS NULL AND enabled = true ORDER BY priority ASC")
    List<ErrorPassthroughRule> selectEnabledOrderByPriority();

    /**
     * 获取所有规则（按优先级排序）
     */
    @Select("SELECT * FROM error_passthrough_rules WHERE deleted_at IS NULL ORDER BY priority ASC")
    List<ErrorPassthroughRule> selectAllOrderByPriority();

    /**
     * 根据ID查询（排除已删除）
     */
    @Select("SELECT * FROM error_passthrough_rules WHERE id = #{id} AND deleted_at IS NULL")
    ErrorPassthroughRule selectByIdNotDeleted(@Param("id") Long id);
}
