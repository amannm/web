package com.amannmalik.web;

import jakarta.json.JsonObject;

import java.util.Map;
import java.util.Objects;

record ToolCallOutcome(PendingToolCall pendingToolCall,
                       Map<String, JsonObject> outputItems,
                       Map<String, JsonObject> reasoningItems) implements Outcome {
    ToolCallOutcome {
        pendingToolCall = Objects.requireNonNull(pendingToolCall, "pendingToolCall");
        outputItems = Map.copyOf(outputItems);
        reasoningItems = Map.copyOf(reasoningItems);
    }
}
