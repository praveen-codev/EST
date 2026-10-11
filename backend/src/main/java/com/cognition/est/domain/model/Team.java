package com.cognition.est.domain.model;

import java.util.List;

public record Team(String id, String clientId, String name, List<String> keywords) {}
