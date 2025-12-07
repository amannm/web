package com.amannmalik.web.chromium;

import jakarta.json.JsonObject;
import java.util.Objects;

public record CdpSuccess(long id, JsonObject result) implements CdpResponse {

    public CdpSuccess {
        if (id <= 0) {
            throw new IllegalArgumentException("CDP id must be positive");
        }
        Objects.requireNonNull(result, "CDP result must not be null");
    }
}
