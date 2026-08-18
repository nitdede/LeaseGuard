package com.leaseguard.dto;

import java.util.List;

public record PagedResult<T>(List<T> items, int page, int pageSize, long totalItems, int totalPages) {

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return page < totalPages - 1;
    }
}
