package com.amannmalik.web.chromium;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.Objects;

/**
 * Represents an unsolicited CDP event pushed by Chromium.
 */
public record CdpEvent(String method, JsonObject params) {

    public CdpEvent(String method) {
        this(method, Json.createObjectBuilder().build());
    }

    public CdpEvent {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("CDP event method must be non-blank");
        }
        Objects.requireNonNull(params, "CDP event params must not be null");
    }
}
