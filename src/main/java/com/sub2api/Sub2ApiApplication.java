package com.sub2api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Sub2API 启动类
 *
 * @author Sub2API Team
 */
@SpringBootApplication(exclude = {OAuth2ClientAutoConfiguration.class})
@EnableScheduling
@MapperScan("com.sub2api.module.*.mapper")
public class Sub2ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(Sub2ApiApplication.class, args);
    }
}
