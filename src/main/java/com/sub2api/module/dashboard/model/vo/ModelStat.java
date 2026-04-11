package com.sub2api.module.dashboard.model.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模型统计
 */
@Data
@Accessors(chain = true)
public class ModelStat implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String model;
    private long requests;
    private long inputTokens;
    private long outputTokens;
    private long cacheCreationTokens;
    private long cacheReadTokens;
    private long totalTokens;
    private double cost;
    private double actualCost;
}
