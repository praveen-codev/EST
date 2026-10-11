package com.cognition.est.domain.model;

import java.util.List;

public record Client(String id, String name, String industry, List<String> domains, int hue) {}
