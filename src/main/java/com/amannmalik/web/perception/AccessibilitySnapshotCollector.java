package com.amannmalik.web.perception;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class AccessibilitySnapshotCollector {

    private final JsonBuilderFactory jsonFactory;

    AccessibilitySnapshotCollector(JsonBuilderFactory jsonFactory) {
        this.jsonFactory = Objects.requireNonNull(jsonFactory, "jsonFactory must not be null");
    }

    Map<String, JsonObject> collect(CdpRequestor requestor) {
        Objects.requireNonNull(requestor, "requestor must not be null");

        var accessibility = requestor.request("Accessibility.getFullAXTree");
        var nodes = accessibility.getJsonArray("nodes");
        if (nodes == null) {
            return Map.of();
        }

        Map<String, List<JsonObject>> byFrame = new HashMap<>();
        Map<String, List<String>> rootsByFrame = new HashMap<>();
        for (var value : nodes) {
            var node = value.asJsonObject();
            var axId = node.getString("nodeId", "");
            var frameId = node.getString("frameId", "");
            if (frameId.isBlank() || axId.isBlank()) {
                continue;
            }

            var role = readAxString(node, "role");
            if (role.isBlank()) {
                role = "unknown";
            }

            var builder = jsonFactory.createObjectBuilder()
                .add("axId", axId)
                .add("role", role);

            if (node.containsKey("backendDOMNodeId")) {
                builder.add("backendDOMNodeId", node.getJsonNumber("backendDOMNodeId").longValue());
            }
            if (node.containsKey("frameId")) {
                builder.add("frameId", frameId);
            }
            addIfPresent(builder, "chromeRole", readAxString(node, "chromeRole"));
            addIfPresent(builder, "name", readAxString(node, "name"));
            addIfPresent(builder, "description", readAxString(node, "description"));
            var valueField = readAxValue(node);
            if (valueField != null) {
                builder.add("value", valueField);
            }

            var states = jsonFactory.createObjectBuilder();
            var properties = jsonFactory.createObjectBuilder();
            if (node.containsKey("properties")) {
                var propertiesArray = node.getJsonArray("properties");
                for (var propertyValue : propertiesArray) {
                    var property = propertyValue.asJsonObject();
                    var name = property.getString("name", "");
                    var axValue = property.getJsonObject("value");
                    var rawValue = axValue != null ? axValue.get("value") : null;
                    if (rawValue != null && rawValue.getValueType() == JsonValue.ValueType.TRUE || rawValue != null && rawValue.getValueType() == JsonValue.ValueType.FALSE) {
                        states.add(name, rawValue.getValueType() == JsonValue.ValueType.TRUE);
                    } else if (rawValue != null) {
                        properties.add(name, rawValue);
                    }
                }
            }
            builder.add("states", states.build());
            builder.add("properties", properties.build());

            if (node.containsKey("childIds")) {
                builder.add("childIds", node.getJsonArray("childIds"));
            }
            addIfPresent(builder, "parentId", node.getString("parentId", ""));
            builder.add("labelledBy", jsonFactory.createArrayBuilder());
            builder.add("describedBy", jsonFactory.createArrayBuilder());
            builder.add("controls", jsonFactory.createArrayBuilder());

            byFrame.computeIfAbsent(frameId, unused -> new ArrayList<>()).add(builder.build());
            if (!node.containsKey("parentId") || node.isNull("parentId") || node.getString("parentId", "").isBlank()) {
                rootsByFrame.computeIfAbsent(frameId, unused -> new ArrayList<>()).add(axId);
            }
        }

        Map<String, JsonObject> snapshots = new HashMap<>();
        for (var entry : byFrame.entrySet()) {
            var nodesArray = jsonFactory.createArrayBuilder();
            entry.getValue().forEach(nodesArray::add);
            var roots = jsonFactory.createArrayBuilder();
            rootsByFrame.getOrDefault(entry.getKey(), List.of()).forEach(roots::add);
            snapshots.put(
                entry.getKey(),
                jsonFactory.createObjectBuilder()
                    .add("nodes", nodesArray)
                    .add("rootIds", roots)
                    .build()
            );
        }
        return snapshots;
    }

    private String readAxString(JsonObject node, String property) {
        if (!node.containsKey(property)) {
            return "";
        }
        var value = node.getJsonObject(property);
        var raw = value.get("value");
        if (raw == null || raw.getValueType() == JsonValue.ValueType.NULL) {
            return value.getString("type", "");
        }
        if (raw.getValueType() == JsonValue.ValueType.STRING) {
            return ((JsonString) raw).getString();
        }
        return raw.toString();
    }

    private JsonValue readAxValue(JsonObject node) {
        if (!node.containsKey("value")) {
            return null;
        }
        var value = node.getJsonObject("value");
        return value.get("value");
    }

    private void addIfPresent(JsonObjectBuilder builder, String key, String value) {
        if (value != null && !value.isBlank()) {
            builder.add(key, value);
        }
    }
}
