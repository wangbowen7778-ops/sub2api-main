package com.sub2api.module.dashboard.model.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分组统计
 */
@Data
@Accessors(chain = true)
public class GroupStat implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long groupId;
    private String groupName;
    private long requests;
    private long totalTokens;
    private double cost;
    private double actualCost;
}
