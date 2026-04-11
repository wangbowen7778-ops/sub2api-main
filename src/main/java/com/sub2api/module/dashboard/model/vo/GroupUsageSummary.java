package com.sub2api.module.dashboard.model.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分组用量摘要
 */
@Data
@Accessors(chain = true)
public class GroupUsageSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long groupId;
    private double todayCost;
    private double totalCost;
}
