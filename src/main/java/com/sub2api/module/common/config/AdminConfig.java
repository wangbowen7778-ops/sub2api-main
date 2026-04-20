package com.sub2api.module.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Admin configuration properties
 * Maps to sub2api.admin.* in application.yml
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "sub2api.admin")
public class AdminConfig {

    /**
     * Admin email for initial setup
     */
    private String email;

    /**
     * Admin password for initial setup
     */
    private String password;
}
