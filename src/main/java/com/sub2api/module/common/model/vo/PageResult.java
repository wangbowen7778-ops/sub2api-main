package com.sub2api.module.common.model.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页结果
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Accessors(chain = true)
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页数据
     */
    private List<T> records;

    /**
     * 当前页码
     */
    private Long current;

    /**
     * 每页大小
     */
    private Long size;

    /**
     * 总页数
     */
    private Long pages;

    public PageResult() {
    }

    public PageResult(Long total, List<T> records, Long current, Long size) {
        this.total = total;
        this.records = records;
        this.current = current;
        this.size = size;
        this.pages = total / size + (total % size > 0 ? 1 : 0);
    }

    public static <T> PageResult<T> of(Long total, List<T> records, Long current, Long size) {
        return new PageResult<>(total, records, current, size);
    }

    public boolean hasNext() {
        return this.current < this.pages;
    }

    public boolean hasPrevious() {
        return this.current > 1;
    }
}
