package com.sub2api.module.apikey.controller;

import com.sub2api.module.account.model.entity.Group;
import com.sub2api.module.apikey.model.vo.GroupResponse;
import com.sub2api.module.apikey.service.ApiKeyService;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User-side Group Controller
 * Handles group-related operations for regular users (available groups, rates)
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "User - Groups", description = "User Group management API")
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserGroupController {

    private final ApiKeyService apiKeyService;
    private final UserService userService;

    @Operation(summary = "Get available groups for current user")
    @GetMapping("/available")
    public Result<List<GroupResponse>> getAvailableGroups() {
        Long userId = userService.getCurrentUserId();
        List<Group> groups = apiKeyService.getAvailableGroups(userId);

        List<GroupResponse> records = groups.stream()
                .map(this::toGroupResponse)
                .collect(Collectors.toList());

        return Result.ok(records);
    }

    @Operation(summary = "Get user group rates config")
    @GetMapping("/rates")
    public Result<Map<Long, Double>> getUserGroupRates() {
        Long userId = userService.getCurrentUserId();
        Map<Long, Double> rates = apiKeyService.getUserGroupRates(userId);
        return Result.ok(rates);
    }

    /**
     * Convert to group response
     */
    private GroupResponse toGroupResponse(Group group) {
        if (group == null) {
            return null;
        }

        GroupResponse response = new GroupResponse();
        response.setId(group.getId());
        response.setName(group.getName());
        response.setDescription(group.getDescription());
        response.setPlatform(group.getPlatform());
        if (group.getRateMultiplier() != null) {
            response.setRateMultiplier(group.getRateMultiplier().doubleValue());
        }
        response.setIsExclusive(group.getIsExclusive());
        response.setStatus(group.getStatus());
        response.setSubscriptionType(group.getSubscriptionType());
        if (group.getDailyLimitUsd() != null) {
            response.setDailyLimitUsd(group.getDailyLimitUsd().doubleValue());
        }
        if (group.getWeeklyLimitUsd() != null) {
            response.setWeeklyLimitUsd(group.getWeeklyLimitUsd().doubleValue());
        }
        if (group.getMonthlyLimitUsd() != null) {
            response.setMonthlyLimitUsd(group.getMonthlyLimitUsd().doubleValue());
        }

        return response;
    }
}