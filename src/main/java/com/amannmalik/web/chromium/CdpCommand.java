package com.amannmalik.web.chromium;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.Objects;

/**
 * Represents a single Chrome DevTools Protocol command.
 */
public record CdpCommand(String method, JsonObject params) {

    public CdpCommand(String method) {
        this(method, Json.createObjectBuilder().build());
    }

    public CdpCommand {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("CDP method must be non-blank");
        }
        Objects.requireNonNull(params, "CDP params must not be null");
    }
}
