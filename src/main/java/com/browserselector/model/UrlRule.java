package com.browserselector.model;

import java.time.Instant;
import java.util.Objects;

public record UrlRule(
    int id,
    String pattern,
    String browserId,
    int priority,
    Instant createdAt
) {
    public UrlRule {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        Objects.requireNonNull(browserId, "browserId cannot be null");
    }

    public UrlRule(String pattern, String browserId) {
        this(0, pattern, browserId, 0, Instant.now());
    }

    public UrlRule(String pattern, String browserId, int priority) {
        this(0, pattern, browserId, priority, Instant.now());
    }

    public UrlRule withId(int id) {
        return new UrlRule(id, pattern, browserId, priority, createdAt);
    }

    public UrlRule withPriority(int priority) {
        return new UrlRule(id, pattern, browserId, priority, createdAt);
    }
}
