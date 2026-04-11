package com.sub2api.module.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.admin.model.entity.Setting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 设置 Mapper
 *
 * @author Alibaba Java Code Guidelines
 */
@Mapper
public interface SettingMapper extends BaseMapper<Setting> {

    /**
     * 根据键获取设置
     */
    @Select("SELECT * FROM settings WHERE setting_key = #{key} AND deleted_at IS NULL LIMIT 1")
    Setting selectByKey(@Param("key") String key);

    /**
     * 根据键获取值
     */
    @Select("SELECT setting_value FROM settings WHERE setting_key = #{key} AND deleted_at IS NULL LIMIT 1")
    String selectValueByKey(@Param("key") String key);

    /**
     * 根据键列表获取设置
     */
    @Select("<script>SELECT * FROM settings WHERE setting_key IN " +
            "<foreach item='item' collection='keys' open='(' separator=',' close=')'>" +
            "#{item}</foreach> AND deleted_at IS NULL</script>")
    List<Setting> selectByKeys(@Param("keys") List<String> keys);

    /**
     * 获取所有设置
     */
    @Select("SELECT * FROM settings WHERE deleted_at IS NULL")
    List<Setting> selectAll();

    /**
     * 根据分类获取设置
     */
    @Select("SELECT * FROM settings WHERE category = #{category} AND deleted_at IS NULL")
    List<Setting> selectByCategory(@Param("category") String category);
}
