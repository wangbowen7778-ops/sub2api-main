package com.sub2api.module.admin.controller;

import com.sub2api.module.billing.model.entity.PromoCode;
import com.sub2api.module.billing.model.entity.RedeemCode;
import com.sub2api.module.billing.service.PromoCodeService;
import com.sub2api.module.billing.service.RedeemCodeService;
import com.sub2api.module.common.model.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 计费管理控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "管理 - 计费", description = "计费管理接口")
@RestController
@RequestMapping("/admin/billing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BillingAdminController {

    private final RedeemCodeService redeemCodeService;
    private final PromoCodeService promoCodeService;

    @Operation(summary = "创建兑换码")
    @PostMapping("/redeem-codes")
    public Result<RedeemCode> createRedeemCode(@RequestBody Map<String, Object> params) {
        String code = (String) params.get("code");
        BigDecimal value = new BigDecimal(params.get("value").toString());
        Integer validityDays = params.get("validityDays") != null ?
                Integer.parseInt(params.get("validityDays").toString()) : 30;
        Long groupId = params.get("groupId") != null ?
                Long.parseLong(params.get("groupId").toString()) : null;
        String notes = (String) params.get("notes");

        RedeemCode redeemCode = redeemCodeService.createRedeemCode(code, value, validityDays, groupId, notes);
        return Result.ok(redeemCode);
    }

    @Operation(summary = "使用兑换码")
    @PostMapping("/redeem-codes/redeem")
    public Result<Void> redeemCode(@RequestBody Map<String, Object> params) {
        String code = (String) params.get("code");
        Long userId = Long.parseLong(params.get("userId").toString());
        redeemCodeService.redeem(code, userId);
        return Result.ok();
    }

    @Operation(summary = "创建优惠码")
    @PostMapping("/promo-codes")
    public Result<PromoCode> createPromoCode(@RequestBody Map<String, Object> params) {
        String code = (String) params.get("code");
        BigDecimal bonusAmount = new BigDecimal(params.get("bonusAmount").toString());
        Integer maxUses = params.get("maxUses") != null ?
                Integer.parseInt(params.get("maxUses").toString()) : 0;
        OffsetDateTime expiresAt = params.get("expiresAt") != null ?
                OffsetDateTime.parse(params.get("expiresAt").toString()) : null;
        String notes = (String) params.get("notes");

        PromoCode promoCode = promoCodeService.createPromoCode(code, bonusAmount, maxUses, expiresAt, notes);
        return Result.ok(promoCode);
    }

    @Operation(summary = "使用优惠码")
    @PostMapping("/promo-codes/redeem")
    public Result<Void> redeemPromoCode(@RequestBody Map<String, Object> params) {
        String code = (String) params.get("code");
        Long userId = Long.parseLong(params.get("userId").toString());
        promoCodeService.redeem(code, userId);
        return Result.ok();
    }
}
