package com.sub2api.module.apikey.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理员更新 API Key 分组请求
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Schema(description = "管理员更新 API Key 分组请求")
public class AdminUpdateGroupRequest {

    @JsonProperty("group_id")
    @Schema(description = "分组ID: null=不修改, 0=解绑, >0=绑定到目标分组")
    private Long groupId;
}