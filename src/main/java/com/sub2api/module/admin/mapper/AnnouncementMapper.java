package com.sub2api.module.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.admin.model.entity.Announcement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公告 Mapper
 *
 * @author Alibaba Java Code Guidelines
 */
@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {
}
