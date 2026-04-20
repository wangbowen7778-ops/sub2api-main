package com.sub2api.module.apikey.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 管理员更新 API Key 分组响应
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Builder
@Schema(description = "管理员更新 API Key 分组响应")
public class AdminUpdateGroupResponse {

    @JsonProperty("api_key")
    @Schema(description = "更新后的 API Key")
    private ApiKeyResponse apiKey;

    @JsonProperty("auto_granted_group_access")
    @Schema(description = "是否自动授予了分组访问权限")
    private Boolean autoGrantedGroupAccess;

    @JsonProperty("granted_group_id")
    @Schema(description = "自动授予的分组ID")
    private Long grantedGroupId;

    @JsonProperty("granted_group_name")
    @Schema(description = "自动授予的分组名称")
    private String grantedGroupName;
}