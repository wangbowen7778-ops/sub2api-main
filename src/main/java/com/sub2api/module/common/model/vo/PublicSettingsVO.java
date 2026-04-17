package com.sub2api.module.common.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 公开设置响应
 */
@Data
public class PublicSettingsVO {
    @JsonProperty("registration_enabled")
    private boolean registrationEnabled;

    @JsonProperty("email_verify_enabled")
    private boolean emailVerifyEnabled;

    @JsonProperty("registration_email_suffix_whitelist")
    private List<String> registrationEmailSuffixWhitelist;

    @JsonProperty("promo_code_enabled")
    private boolean promoCodeEnabled;

    @JsonProperty("password_reset_enabled")
    private boolean passwordResetEnabled;

    @JsonProperty("invitation_code_enabled")
    private boolean invitationCodeEnabled;

    @JsonProperty("totp_enabled")
    private boolean totpEnabled;

    @JsonProperty("turnstile_enabled")
    private boolean turnstileEnabled;

    @JsonProperty("turnstile_site_key")
    private String turnstileSiteKey;

    @JsonProperty("site_name")
    private String siteName;

    @JsonProperty("site_logo")
    private String siteLogo;

    @JsonProperty("site_subtitle")
    private String siteSubtitle;

    @JsonProperty("api_base_url")
    private String apiBaseUrl;

    @JsonProperty("contact_info")
    private String contactInfo;

    @JsonProperty("doc_url")
    private String docUrl;

    @JsonProperty("home_content")
    private String homeContent;

    @JsonProperty("hide_ccs_import_button")
    private boolean hideCcsImportButton;

    @JsonProperty("purchase_subscription_enabled")
    private boolean purchaseSubscriptionEnabled;

    @JsonProperty("purchase_subscription_url")
    private String purchaseSubscriptionUrl;

    @JsonProperty("custom_menu_items")
    private List<CustomMenuItemVO> customMenuItems;

    @JsonProperty("custom_endpoints")
    private List<CustomEndpointVO> customEndpoints;

    @JsonProperty("linuxdo_oauth_enabled")
    private boolean linuxdoOauthEnabled;

    @JsonProperty("oidc_oauth_enabled")
    private boolean oidcOauthEnabled;

    @JsonProperty("oidc_oauth_provider_name")
    private String oidcOauthProviderName;

    @JsonProperty("sora_client_enabled")
    private boolean soraClientEnabled;

    @JsonProperty("backend_mode_enabled")
    private boolean backendModeEnabled;

    @JsonProperty("version")
    private String version;

    @Data
    public static class CustomMenuItemVO {
        private String title;
        private String url;
        private String icon;
        private int sort;
    }

    @Data
    public static class CustomEndpointVO {
        private String name;
        private String url;
    }
}
