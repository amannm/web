package com.amannmalik.web.perception;

import com.amannmalik.web.chromium.CdpClient;
import com.amannmalik.web.chromium.CdpCommand;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionException;

final class CdpRequestor {

    private final CdpClient client;
    private final JsonBuilderFactory jsonFactory;

    CdpRequestor(CdpClient client, JsonBuilderFactory jsonFactory) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.jsonFactory = Objects.requireNonNull(jsonFactory, "jsonFactory must not be null");
    }

    JsonObject request(String method) {
        return request(method, jsonFactory.createObjectBuilder().build());
    }

    JsonObject request(String method, JsonObject params) {
        try {
            return client.send(new CdpCommand(method, params)).join().result();
        } catch (CompletionException e) {
            var cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("CDP request failed: " + method, cause);
        }
    }

    JsonObject requestWithRetries(String method, JsonObject params, int attempts, Duration delay) {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be at least 1");
        }
        Objects.requireNonNull(delay, "delay must not be null");

        RuntimeException lastError = null;
        for (var remaining = attempts; remaining > 0; remaining--) {
            try {
                return request(method, params);
            } catch (RuntimeException error) {
                lastError = error;
                if (remaining > 1) {
                    try {
                        Thread.sleep(Math.max(1L, delay.toMillis()));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Retry interrupted for " + method, interrupted);
                    }
                }
            }
        }
        throw lastError != null ? lastError : new IllegalStateException("CDP request failed: " + method);
    }
}
