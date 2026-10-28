package com.flowforge.common.api;

import java.util.List;

/**
 * 统一分页结构。
 *
 * <p>{@code page} 从 1 开始；{@code total} 是满足条件的总条数而非当前页条数。
 */
public record PageResult<T>(List<T> records, long total, long page, long size) {

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(List.of(), 0, page, size);
    }
}
