package com.sub2api.module.dashboard.model.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 趋势数据点
 */
@Data
@Accessors(chain = true)
public class TrendDataPoint implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String date;
    private long requests;
    private long inputTokens;
    private long outputTokens;
    private long cacheCreationTokens;
    private long cacheReadTokens;
    private long totalTokens;
    private double cost;
    private double actualCost;
}
