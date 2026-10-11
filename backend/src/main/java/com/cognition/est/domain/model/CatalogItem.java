package com.cognition.est.domain.model;

import java.time.Instant;
import java.util.List;

public record CatalogItem(
        String id,
        String kind,
        String label,
        String description,
        List<String> keywords,
        Integer typicalMinutes,
        boolean archived,
        int sortOrder,
        int version,
        String updatedBy,
        Instant updatedAt) {}
