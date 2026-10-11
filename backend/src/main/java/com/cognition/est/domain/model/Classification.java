package com.cognition.est.domain.model;

import java.util.List;

public record Classification(double score, String bucket, List<String> reasons) {}
