package com.amannmalik.web;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import java.io.StringReader;
import java.util.Objects;

record CdpCommand(String method,
                         JsonObject params,
                         String sessionId) {

    public CdpCommand {
        Objects.requireNonNull(method, "method");
        if (method.isBlank()) {
            throw new IllegalArgumentException("CDP method cannot be blank");
        }
        if (sessionId != null && sessionId.isBlank()) {
            sessionId = null;
        }
    }

    JsonObject toJson(int id) {
        var builder = Json.createObjectBuilder()
                .add("id", id)
                .add("method", method);
        if (params != null) {
            builder.add("params", params);
        }
        if (sessionId != null) {
            builder.add("sessionId", sessionId);
        }
        return builder.build();
    }

    static CdpCommand fromJson(String raw) {
        Objects.requireNonNull(raw, "raw");
        try (var reader = Json.createReader(new StringReader(raw))) {
            var obj = reader.readObject();
            var method = obj.getString("method", "").trim();
            if (method.isEmpty()) {
                throw new IllegalArgumentException("CDP command JSON missing non-blank method");
            }
            JsonObject params = null;
            if (obj.containsKey("params")) {
                var val = obj.get("params");
                if (!(val instanceof JsonObject p)) {
                    throw new IllegalArgumentException("params must be a JSON object when provided");
                }
                params = p;
            }
            String sessionId = null;
            if (obj.containsKey("sessionId")) {
                var val = obj.get("sessionId");
                if (val != JsonValue.NULL && !(val.getValueType() == JsonValue.ValueType.STRING)) {
                    throw new IllegalArgumentException("sessionId must be a string when provided");
                }
                sessionId = obj.getString("sessionId", "").trim();
                if (sessionId.isEmpty()) {
                    sessionId = null;
                }
            }
            return new CdpCommand(method, params, sessionId);
        } catch (RuntimeException parseErr) {
            var cause = parseErr.getCause() == null ? parseErr : parseErr.getCause();
            throw new IllegalArgumentException("Invalid CDP command JSON: " + cause.getMessage(), cause);
        }
    }
}
