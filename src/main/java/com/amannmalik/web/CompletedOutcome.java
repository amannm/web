package com.amannmalik.web;

import jakarta.json.JsonObject;

import java.util.Map;

record CompletedOutcome(Map<String, JsonObject> outputItems,
                        Map<String, JsonObject> reasoningItems) implements Outcome {
    CompletedOutcome {
        outputItems = Map.copyOf(outputItems);
        reasoningItems = Map.copyOf(reasoningItems);
    }
}
