package com.sub2api.module.auth.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Schema(description = "注册请求")
public class RegisterRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度必须在 8-64 之间")
    @Schema(description = "密码")
    private String password;

    @Schema(description = "邮箱验证码")
    private String emailCode;

    @Schema(description = "推荐码")
    private String promoCode;
}
