package com.sub2api.module.common.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.admin.service.SettingService;
import com.sub2api.module.common.model.vo.PublicSettingsVO;
import com.sub2api.module.common.model.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 公开设置控制器 - 前端初始化需要
 */
@Slf4j
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
@Tag(name = "公开设置", description = "无需认证的公开设置接口")
public class PublicSettingsController {

    private final SettingService settingService;
    private final ObjectMapper objectMapper;

    @GetMapping("/public")
    @Operation(summary = "获取公开设置")
    public Result<PublicSettingsVO> getPublicSettings() {
        PublicSettingsVO vo = new PublicSettingsVO();

        vo.setRegistrationEnabled(settingService.getBoolean("registration_enabled", true));
        vo.setEmailVerifyEnabled(settingService.getBoolean("email_verify_enabled", false));
        vo.setRegistrationEmailSuffixWhitelist(parseStringList("registration_email_suffix_whitelist"));
        vo.setPromoCodeEnabled(settingService.getBoolean("promo_code_enabled", true));
        vo.setPasswordResetEnabled(settingService.getBoolean("password_reset_enabled", true));
        vo.setInvitationCodeEnabled(settingService.getBoolean("invitation_code_enabled", false));
        vo.setTotpEnabled(settingService.getBoolean("totp_enabled", false));
        vo.setTurnstileEnabled(settingService.getBoolean("turnstile_enabled", false));
        vo.setTurnstileSiteKey(settingService.getValue("turnstile_site_key", ""));
        vo.setSiteName(settingService.getValue("site_name", "Sub2API"));
        vo.setSiteLogo(settingService.getValue("site_logo", ""));
        vo.setSiteSubtitle(settingService.getValue("site_subtitle", ""));
        vo.setApiBaseUrl(settingService.getValue("api_base_url", ""));
        vo.setContactInfo(settingService.getValue("contact_info", ""));
        vo.setDocUrl(settingService.getValue("doc_url", ""));
        vo.setHomeContent(settingService.getValue("home_content", ""));
        vo.setHideCcsImportButton(settingService.getBoolean("hide_ccs_import_button", false));
        vo.setPurchaseSubscriptionEnabled(settingService.getBoolean("purchase_subscription_enabled", false));
        vo.setPurchaseSubscriptionUrl(settingService.getValue("purchase_subscription_url", ""));
        vo.setCustomMenuItems(parseCustomMenuItems());
        vo.setCustomEndpoints(parseCustomEndpoints());
        vo.setLinuxdoOauthEnabled(settingService.getBoolean("linuxdo_oauth_enabled", false));
        vo.setOidcOauthEnabled(settingService.getBoolean("oidc_oauth_enabled", false));
        vo.setOidcOauthProviderName(settingService.getValue("oidc_oauth_provider_name", ""));
        vo.setSoraClientEnabled(settingService.getBoolean("sora_client_enabled", false));
        vo.setBackendModeEnabled(settingService.getBoolean("backend_mode_enabled", false));
        vo.setVersion("1.0.0");

        return Result.ok(vo);
    }

    private List<String> parseStringList(String key) {
        String value = settingService.getValue(key);
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse string list for key {}: {}", key, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<PublicSettingsVO.CustomMenuItemVO> parseCustomMenuItems() {
        String value = settingService.getValue("custom_menu_items");
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<PublicSettingsVO.CustomMenuItemVO>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse custom menu items: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<PublicSettingsVO.CustomEndpointVO> parseCustomEndpoints() {
        String value = settingService.getValue("custom_endpoints");
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<PublicSettingsVO.CustomEndpointVO>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse custom endpoints: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
