package com.sub2api.module.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.admin.model.entity.TLSFingerprintProfile;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * TLS 指纹配置 Mapper
 */
@Mapper
public interface TLSFingerprintProfileMapper extends BaseMapper<TLSFingerprintProfile> {

    /**
     * 获取所有启用的配置
     */
    List<TLSFingerprintProfile> selectEnabled();

    /**
     * 获取所有配置（按优先级排序）
     */
    List<TLSFingerprintProfile> selectAllOrderByPriority();
}
