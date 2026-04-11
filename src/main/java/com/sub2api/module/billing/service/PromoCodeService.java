package com.sub2api.module.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.billing.mapper.PromoCodeMapper;
import com.sub2api.module.billing.mapper.PromoCodeUsageMapper;
import com.sub2api.module.billing.model.entity.PromoCode;
import com.sub2api.module.billing.model.entity.PromoCodeUsage;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import com.sub2api.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠码服务
 *
 * @author Alibaba Java Code Guidelines
 */
@Service
@RequiredArgsConstructor
public class PromoCodeService extends ServiceImpl<PromoCodeMapper, PromoCode> {

    private static final Logger log = LoggerFactory.getLogger(PromoCodeService.class);

    private final PromoCodeMapper promoCodeMapper;
    private final PromoCodeUsageMapper promoCodeUsageMapper;
    private final UserService userService;

    /**
     * 根据优惠码查找
     */
    public PromoCode findByCode(String code) {
        return getOne(new LambdaQueryWrapper<PromoCode>()
                .eq(PromoCode::getCode, code));
    }

    /**
     * 使用优惠码 (注册时赠送)
     */
    @Transactional(rollbackFor = Exception.class)
    public void redeem(String code, Long userId) {
        PromoCode promoCode = findByCode(code);
        if (promoCode == null) {
            throw new BusinessException(ErrorCode.PROMO_CODE_INVALID, "优惠码不存在");
        }

        if (!"active".equals(promoCode.getStatus())) {
            throw new BusinessException(ErrorCode.PROMO_CODE_INVALID, "优惠码已禁用");
        }

        // 检查过期
        if (promoCode.getExpiresAt() != null && LocalDateTime.now().isAfter(promoCode.getExpiresAt())) {
            throw new BusinessException(ErrorCode.PROMO_CODE_EXPIRED);
        }

        // 检查使用次数
        if (promoCode.getMaxUses() > 0 && promoCode.getUsedCount() >= promoCode.getMaxUses()) {
            throw new BusinessException(ErrorCode.PROMO_CODE_QUOTA_EXCEEDED);
        }

        // 赠送余额
        if (promoCode.getBonusAmount() != null && promoCode.getBonusAmount().compareTo(BigDecimal.ZERO) > 0) {
            userService.updateBalance(userId, promoCode.getBonusAmount());
        }

        // 更新使用次数
        PromoCode updateCode = new PromoCode();
        updateCode.setId(promoCode.getId());
        updateCode.setUsedCount(promoCode.getUsedCount() + 1);
        updateById(updateCode);

        // 记录使用
        PromoCodeUsage usage = new PromoCodeUsage();
        usage.setPromoCodeId(promoCode.getId());
        usage.setUserId(userId);
        usage.setPromoCode(code);
        usage.setBonusAmount(promoCode.getBonusAmount());
        usage.setCreatedAt(LocalDateTime.now());
        promoCodeUsageMapper.insert(usage);

        log.info("用户 {} 使用优惠码 {}, 获得赠送 {}", userId, code, promoCode.getBonusAmount());
    }

    /**
     * 创建优惠码
     */
    @Transactional(rollbackFor = Exception.class)
    public PromoCode createPromoCode(String code, BigDecimal bonusAmount, Integer maxUses, LocalDateTime expiresAt, String notes) {
        PromoCode promoCode = new PromoCode();
        promoCode.setCode(code);
        promoCode.setBonusAmount(bonusAmount);
        promoCode.setMaxUses(maxUses != null ? maxUses : 0);
        promoCode.setUsedCount(0);
        promoCode.setStatus("active");
        promoCode.setExpiresAt(expiresAt);
        promoCode.setNotes(notes);
        promoCode.setCreatedAt(LocalDateTime.now());
        promoCode.setUpdatedAt(LocalDateTime.now());

        if (!save(promoCode)) {
            throw new BusinessException(ErrorCode.FAIL, "创建优惠码失败");
        }

        return promoCode;
    }
}
