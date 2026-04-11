package com.sub2api.module.dashboard.model.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户用量趋势数据点
 */
@Data
@Accessors(chain = true)
public class UserUsageTrendPoint implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String date;
    private Long userId;
    private String email;
    private String username;
    private long requests;
    private long tokens;
    private double cost;
    private double actualCost;
}
