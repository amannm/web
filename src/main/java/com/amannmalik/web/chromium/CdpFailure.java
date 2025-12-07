package com.amannmalik.web.chromium;

import jakarta.json.JsonValue;
import java.util.Objects;

public record CdpFailure(long id, int code, String message, JsonValue data) implements CdpResponse {

    public CdpFailure {
        if (id <= 0) {
            throw new IllegalArgumentException("CDP id must be positive");
        }
        Objects.requireNonNull(message, "CDP error message must not be null");
        Objects.requireNonNull(data, "CDP error data must not be null");
    }
}
