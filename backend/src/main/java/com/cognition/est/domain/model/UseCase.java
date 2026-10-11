package com.cognition.est.domain.model;

import java.util.List;

public record UseCase(
        String id,
        String title,
        String description,
        String useCaseTypeId,
        List<String> technologyIds) {}
