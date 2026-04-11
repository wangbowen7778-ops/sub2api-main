package com.sub2api.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.user.model.entity.AnnouncementRead;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公告已读记录 Mapper
 *
 * @author Alibaba Java Code Guidelines
 */
@Mapper
public interface AnnouncementReadMapper extends BaseMapper<AnnouncementRead> {
}
