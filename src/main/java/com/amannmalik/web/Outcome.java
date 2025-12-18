package com.amannmalik.web;

import jakarta.json.JsonObject;

import java.util.Map;
import java.util.Objects;

sealed interface Outcome permits Outcome.Completed, Outcome.ToolCall {

    record Completed(Map<String, JsonObject> outputItems,
                     Map<String, JsonObject> reasoningItems) implements Outcome {
        public Completed {
            outputItems = Map.copyOf(outputItems);
            reasoningItems = Map.copyOf(reasoningItems);
        }
    }

    record ToolCall(State.PendingToolCall pendingToolCall,
                    Map<String, JsonObject> outputItems,
                    Map<String, JsonObject> reasoningItems) implements Outcome {
        public ToolCall {
            pendingToolCall = Objects.requireNonNull(pendingToolCall, "pendingToolCall");
            outputItems = Map.copyOf(outputItems);
            reasoningItems = Map.copyOf(reasoningItems);
        }
    }
}
