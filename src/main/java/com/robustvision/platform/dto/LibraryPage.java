package com.robustvision.platform.dto;

import com.robustvision.platform.common.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import java.util.List;

/** Bounded list response. All counts and rows use the same authorization scope. */
public record LibraryPage<T>(List<T> items, int page, int size, long total) {
    public static PageRequest request(int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 50)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PAGE_INVALID", "页码或每页数量无效");
        return PageRequest.of(page, size);
    }
    public static String query(String query) {
        String value = query == null ? "" : query.trim();
        if (value.length() > 160)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PAGE_INVALID", "搜索内容最多 160 字");
        return value;
    }
}
