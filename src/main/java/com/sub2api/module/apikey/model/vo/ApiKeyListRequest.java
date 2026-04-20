package com.sub2api.module.apikey.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * API Key 列表请求
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Schema(description = "API Key 列表请求")
public class ApiKeyListRequest {

    @Schema(description = "搜索关键词")
    private String search;

    @Schema(description = "状态过滤")
    private String status;

    @Schema(description = "分组ID过滤 (null=不筛选, 0=无分组, >0=指定分组)")
    private Long groupId;
}