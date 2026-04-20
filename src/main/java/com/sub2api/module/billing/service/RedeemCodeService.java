package com.sub2api.module.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.billing.mapper.RedeemCodeMapper;
import com.sub2api.module.billing.model.entity.RedeemCode;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import com.sub2api.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 兑换码服务
 *
 * @author Alibaba Java Code Guidelines
 */
@Service
@RequiredArgsConstructor
public class RedeemCodeService extends ServiceImpl<RedeemCodeMapper, RedeemCode> {

    private static final Logger log = LoggerFactory.getLogger(RedeemCodeService.class);

    private final RedeemCodeMapper redeemCodeMapper;
    private final UserService userService;

    /**
     * 根据兑换码查找
     */
    public RedeemCode findByCode(String code) {
        return getOne(new LambdaQueryWrapper<RedeemCode>()
                .eq(RedeemCode::getCode, code));
    }

    /**
     * 使用兑换码
     */
    @Transactional(rollbackFor = Exception.class)
    public void redeem(String code, Long userId) {
        RedeemCode redeemCode = findByCode(code);
        if (redeemCode == null) {
            throw new BusinessException(ErrorCode.REDEEM_CODE_INVALID, "兑换码不存在");
        }

        if (!"unused".equals(redeemCode.getStatus())) {
            throw new BusinessException(ErrorCode.REDEEM_CODE_INVALID, "兑换码已被使用");
        }

        // 检查有效期
        if (redeemCode.getValidityDays() != null && redeemCode.getValidityDays() > 0) {
            OffsetDateTime expiryDate = redeemCode.getCreatedAt().plusDays(redeemCode.getValidityDays());
            if (OffsetDateTime.now().isAfter(expiryDate)) {
                throw new BusinessException(ErrorCode.REDEEM_CODE_EXPIRED);
            }
        }

        // 扣除余额
        if (redeemCode.getValue() != null && redeemCode.getValue().compareTo(BigDecimal.ZERO) > 0) {
            userService.updateBalance(userId, redeemCode.getValue());
        }

        // 更新兑换码状态
        RedeemCode updateCode = new RedeemCode();
        updateCode.setId(redeemCode.getId());
        updateCode.setStatus("used");
        updateCode.setUsedBy(userId);
        updateCode.setUsedAt(OffsetDateTime.now());
        updateById(updateCode);

        log.info("用户 {} 使用兑换码 {}, 获得余额 {}", userId, code, redeemCode.getValue());
    }

    /**
     * 创建兑换码
     */
    @Transactional(rollbackFor = Exception.class)
    public RedeemCode createRedeemCode(String code, BigDecimal value, Integer validityDays, Long groupId, String notes) {
        RedeemCode redeemCode = new RedeemCode();
        redeemCode.setCode(code);
        redeemCode.setType("balance");
        redeemCode.setValue(value);
        redeemCode.setStatus("unused");
        redeemCode.setValidityDays(validityDays != null ? validityDays : 30);
        redeemCode.setGroupId(groupId);
        redeemCode.setNotes(notes);
        redeemCode.setCreatedAt(OffsetDateTime.now());

        if (!save(redeemCode)) {
            throw new BusinessException(ErrorCode.FAIL, "创建兑换码失败");
        }

        return redeemCode;
    }

    /**
     * 生成兑换码
     */
    @Transactional(rollbackFor = Exception.class)
    public List<RedeemCode> generateCodes(int count, double balance, String codeType) {
        List<RedeemCode> codes = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            String code = generateRandomCode();
            RedeemCode redeemCode = new RedeemCode();
            redeemCode.setCode(code);
            redeemCode.setType(codeType != null ? codeType : "balance");
            redeemCode.setValue(java.math.BigDecimal.valueOf(balance));
            redeemCode.setStatus("unused");
            redeemCode.setValidityDays(30);
            redeemCode.setCreatedAt(OffsetDateTime.now());
            save(redeemCode);
            codes.add(redeemCode);
        }
        return codes;
    }

    private String generateRandomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 验证兑换码是否有效（不实际使用）
     */
    public Map<String, Object> validateInvitationCode(String code) {
        Map<String, Object> result = new HashMap<>();
        result.put("valid", false);

        RedeemCode redeemCode = findByCode(code);
        if (redeemCode == null) {
            result.put("error_code", "INVALID");
            return result;
        }

        if (!"unused".equals(redeemCode.getStatus())) {
            result.put("error_code", "ALREADY_USED");
            return result;
        }

        if (redeemCode.getValidityDays() != null && redeemCode.getValidityDays() > 0) {
            OffsetDateTime expiryDate = redeemCode.getCreatedAt().plusDays(redeemCode.getValidityDays());
            if (OffsetDateTime.now().isAfter(expiryDate)) {
                result.put("error_code", "EXPIRED");
                return result;
            }
        }

        result.put("valid", true);
        return result;
    }
}
