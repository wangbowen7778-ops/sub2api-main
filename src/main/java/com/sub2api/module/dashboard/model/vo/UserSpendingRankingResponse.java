package com.sub2api.module.dashboard.model.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 用户消费排名响应
 */
@Data
@Accessors(chain = true)
public class UserSpendingRankingResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<UserSpendingRankingItem> ranking;
    private double totalActualCost;
    private long totalRequests;
    private long totalTokens;

    @Data
    public static class UserSpendingRankingItem implements Serializable {
        private Long userId;
        private String email;
        private double actualCost;
        private long requests;
        private long tokens;
    }
}
