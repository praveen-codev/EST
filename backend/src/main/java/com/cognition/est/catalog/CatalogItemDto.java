package com.cognition.est.catalog;

import java.time.Instant;
import java.util.List;

/** Read model of one reference-table row (mirrors web/lib/domain/types.ts CatalogItem). */
public record CatalogItemDto(
        String id,
        String kind,
        String label,
        String description,
        List<String> keywords,
        Integer typicalMinutes,
        boolean archived,
        int sortOrder,
        String updatedBy,
        Instant updatedAt) {}
