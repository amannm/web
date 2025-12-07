package com.amannmalik.web.perception;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Projects dense perception snapshots into LLM-oriented profiles.
 *
 * <p>The raw CDP snapshot is intentionally rich. Downstream agents typically
 * need a more opinionated projection that emphasizes either text, visuals, a
 * blended multimodal view, or a full fidelity debug dump. This transformer
 * trims or enriches the base snapshot to match those profiles.</p>
 */
public final class PerceptionProfileProjector {

    public enum PerceptionProfile {
        TEXT,
        VISUAL,
        MULTIMODAL,
        DEBUG;

        public static PerceptionProfile fromCliName(String cliName) {
            if (cliName == null) {
                throw new IllegalArgumentException("profile value must not be null");
            }
            return switch (cliName.trim().toLowerCase(Locale.ROOT)) {
                case "text" -> TEXT;
                case "visual" -> VISUAL;
                case "multimodal" -> MULTIMODAL;
                case "debug" -> DEBUG;
                default -> throw new IllegalArgumentException("Unknown profile '" + cliName + "'");
            };
        }
    }

    private final JsonBuilderFactory jsonFactory;

    public PerceptionProfileProjector() {
        this(Json.createBuilderFactory(Map.of()));
    }

    PerceptionProfileProjector(JsonBuilderFactory jsonFactory) {
        this.jsonFactory = Objects.requireNonNull(jsonFactory, "jsonFactory must not be null");
    }

    public JsonObject project(JsonObject snapshot, PerceptionProfile profile) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(profile, "profile must not be null");

        return switch (profile) {
            case TEXT -> textProjection(snapshot);
            case VISUAL -> visualProjection(snapshot);
            case MULTIMODAL -> multimodalProjection(snapshot);
            case DEBUG -> debugProjection(snapshot);
        };
    }

    private JsonObject textProjection(JsonObject snapshot) {
        var builder = baseSnapshot(snapshot);
        builder.add("frames", transformFrames(snapshot, this::textFrame));
        return builder.build();
    }

    private JsonObject visualProjection(JsonObject snapshot) {
        var builder = baseSnapshot(snapshot);
        builder.add("frames", transformFrames(snapshot, this::visualFrame));
        builder.add("screenshots", screenshots(snapshot));
        return builder.build();
    }

    private JsonObject multimodalProjection(JsonObject snapshot) {
        var builder = baseSnapshot(snapshot);
        builder.add("frames", transformFrames(snapshot, this::multimodalFrame));
        builder.add("screenshots", screenshots(snapshot));
        return builder.build();
    }

    private JsonObject debugProjection(JsonObject snapshot) {
        var builder = baseSnapshot(snapshot);
        builder.add("frames", transformFrames(snapshot, this::debugFrame));
        builder.add("screenshots", screenshots(snapshot));
        return builder.build();
    }

    private JsonObjectBuilder baseSnapshot(JsonObject snapshot) {
        var builder = jsonFactory.createObjectBuilder();
        if (snapshot.containsKey("timestamp")) {
            builder.add("timestamp", snapshot.getJsonNumber("timestamp"));
        }
        if (snapshot.containsKey("device")) {
            builder.add("device", snapshot.getJsonObject("device"));
        }
        if (snapshot.containsKey("viewport")) {
            builder.add("viewport", snapshot.getJsonObject("viewport"));
        }
        return builder;
    }

    private JsonObject transformFrames(JsonObject snapshot, FrameTransform transform) {
        var frames = JsonAccessors.requiredObject(snapshot, "frames", "Perception snapshot missing frames");
        var builder = jsonFactory.createObjectBuilder();
        for (var entry : frames.entrySet()) {
            var frameId = entry.getKey();
            var frame = entry.getValue().asJsonObject();
            builder.add(frameId, transform.apply(frameId, frame));
        }
        return builder.build();
    }

    private JsonObject textFrame(String frameId, JsonObject frame) {
        var builder = baseFrame(frameId, frame);
        var domTree = frame.getJsonObject("domTree");
        var layoutIndex = indexLayoutNodes(frame.getJsonObject("layoutTree"));

        var content = jsonFactory.createArrayBuilder();
        if (domTree != null) {
            var nodes = domTree.getJsonArray("nodes");
            if (nodes != null) {
                for (var i = 0; i < nodes.size(); i++) {
                    var node = nodes.getJsonObject(i);
                    appendTextualContent(layoutIndex, content, node);
                }
            }
        }

        builder.add("content", content);
        return builder.build();
    }

    private JsonObject visualFrame(String frameId, JsonObject frame) {
        return baseFrame(frameId, frame).build();
    }

    private JsonObject multimodalFrame(String frameId, JsonObject frame) {
        var builder = baseFrame(frameId, frame);
        var layoutIndex = indexLayoutNodes(frame.getJsonObject("layoutTree"));
        var domTree = frame.getJsonObject("domTree");

        var textRuns = jsonFactory.createArrayBuilder();
        var actions = jsonFactory.createArrayBuilder();

        if (domTree != null) {
            var nodes = domTree.getJsonArray("nodes");
            if (nodes != null) {
                for (var i = 0; i < nodes.size(); i++) {
                    var node = nodes.getJsonObject(i);
                    appendTextRuns(layoutIndex, textRuns, node);
                    appendActions(layoutIndex, actions, frameId, node);
                }
            }
        }

        builder.add("text", textRuns);
        builder.add("actions", actions);
        builder.add("accessibility", frame.getOrDefault("accessibilityTree", emptyAccessibilitySnapshot()));
        return builder.build();
    }

    private JsonObject debugFrame(String frameId, JsonObject frame) {
        var builder = baseFrame(frameId, frame);
        builder.add("domTree", frame.getOrDefault("domTree", jsonFactory.createObjectBuilder().build()));
        builder.add("layoutTree", frame.getOrDefault("layoutTree", jsonFactory.createObjectBuilder().build()));
        builder.add("styles", frame.getOrDefault("styles", jsonFactory.createObjectBuilder().build()));
        builder.add("layers", frame.getOrDefault("layers", jsonFactory.createObjectBuilder().build()));
        builder.add("accessibilityTree", frame.getOrDefault("accessibilityTree", emptyAccessibilitySnapshot()));
        builder.add("diagnostics", diagnosticCounts(frame));
        return builder.build();
    }

    private JsonObjectBuilder baseFrame(String frameId, JsonObject frame) {
        var builder = jsonFactory.createObjectBuilder();
        builder.add("frameId", frameId);
        builder.add("url", frame.getString("url", ""));
        builder.add("isMainFrame", frame.getBoolean("isMainFrame", false));
        if (frame.containsKey("parentFrameId")) {
            builder.add("parentFrameId", frame.getString("parentFrameId", ""));
        }
        return builder;
    }

    private void appendTextualContent(Map<String, JsonObject> layoutIndex, JsonArrayBuilder content, JsonObject node) {
        if (isTextNode(node)) {
            var text = node.getString("nodeValue", "").trim();
            if (text.isEmpty()) {
                return;
            }
            var entry = jsonFactory.createObjectBuilder()
                .add("nodeId", node.getString("nodeId", ""))
                .add("text", text);
            if (layoutIndex.containsKey(node.getString("nodeId", ""))) {
                entry.add("box", layoutIndex.get(node.getString("nodeId", "")).getJsonObject("box"));
            }
            content.add(entry);
            return;
        }

        var attributes = node.getJsonObject("attributes");
        var alt = attributes == null ? null : attributes.getString("alt", "").trim();
        var ariaLabel = attributes == null ? null : attributes.getString("aria-label", "").trim();
        var role = attributes == null ? null : attributes.getString("role", "").trim();

        var headingLevel = headingLevel(node.getString("nodeName", ""));
        var text = node.getString("nodeValue", "").trim();

        if ((alt != null && !alt.isEmpty()) || (ariaLabel != null && !ariaLabel.isEmpty()) || headingLevel > 0 || !text.isEmpty()) {
            var entry = jsonFactory.createObjectBuilder()
                .add("nodeId", node.getString("nodeId", ""))
                .add("tag", node.getString("nodeName", ""));
            if (headingLevel > 0) {
                entry.add("headingLevel", headingLevel);
            }
            if (role != null && !role.isEmpty()) {
                entry.add("role", role);
            }
            if (alt != null && !alt.isEmpty()) {
                entry.add("alt", alt);
            }
            if (ariaLabel != null && !ariaLabel.isEmpty()) {
                entry.add("ariaLabel", ariaLabel);
            }
            if (!text.isEmpty()) {
                entry.add("text", text);
            }
            if (layoutIndex.containsKey(node.getString("nodeId", ""))) {
                entry.add("box", layoutIndex.get(node.getString("nodeId", "")).getJsonObject("box"));
            }
            content.add(entry);
        }
    }

    private void appendTextRuns(Map<String, JsonObject> layoutIndex, JsonArrayBuilder textRuns, JsonObject node) {
        if (!isTextNode(node)) {
            return;
        }
        var text = node.getString("nodeValue", "").trim();
        if (text.isEmpty()) {
            return;
        }
        var nodeId = node.getString("nodeId", "");
        if (nodeId.isEmpty()) {
            return;
        }
        var textRun = jsonFactory.createObjectBuilder()
            .add("nodeId", nodeId)
            .add("text", text);
        if (layoutIndex.containsKey(nodeId)) {
            textRun.add("box", layoutIndex.get(nodeId).getJsonObject("box"));
        }
        textRuns.add(textRun);
    }

    private void appendActions(Map<String, JsonObject> layoutIndex,
                               JsonArrayBuilder actions,
                               String frameId,
                               JsonObject node) {
        var nodeId = node.getString("nodeId", "");
        if (nodeId.isEmpty()) {
            return;
        }
        var tag = node.getString("nodeName", "");
        var clickable = node.getBoolean("isClickable", false) || hasHref(node) || isFormControl(tag);
        if (!clickable) {
            return;
        }

        var action = jsonFactory.createObjectBuilder();
        action.add("nodeId", nodeId);
        action.add("frameId", frameId);
        action.add("tag", tag);
        action.add("clickable", true);

        var label = node.getString("nodeValue", "").trim();
        if (!label.isEmpty()) {
            action.add("label", label);
        }

        var attributes = node.getJsonObject("attributes");
        var focusedAttributes = focusedAttributes(attributes);
        if (focusedAttributes != null) {
            action.add("attributes", focusedAttributes);
        }

        if (layoutIndex.containsKey(nodeId)) {
            var layoutNode = layoutIndex.get(nodeId);
            action.add("box", layoutNode.getJsonObject("box"));
            addRectIfPresent(action, layoutNode, "clientRect");
            addRectIfPresent(action, layoutNode, "scrollRect");
            addRectIfPresent(action, layoutNode, "offsetRect");
            if (layoutNode.containsKey("zIndex")) {
                action.add("zIndex", layoutNode.getInt("zIndex"));
            }
        }

        actions.add(action.build());
    }

    private JsonObject diagnosticCounts(JsonObject frame) {
        var domTree = frame.getJsonObject("domTree");
        var layoutTree = frame.getJsonObject("layoutTree");
        var accessibilityTree = frame.getJsonObject("accessibilityTree");

        var nodeCount = countArray(domTree, "nodes");
        var clickableCount = countClickable(domTree);
        var layoutCount = countArray(layoutTree, "layoutNodes");
        var accessibilityCount = countArray(accessibilityTree, "nodes");

        return jsonFactory.createObjectBuilder()
            .add("domNodes", nodeCount)
            .add("clickableNodes", clickableCount)
            .add("layoutNodes", layoutCount)
            .add("accessibilityNodes", accessibilityCount)
            .build();
    }

    private JsonObject screenshots(JsonObject snapshot) {
        if (!snapshot.containsKey("screenshots")) {
            return jsonFactory.createObjectBuilder().build();
        }
        var screenshots = snapshot.getJsonObject("screenshots");
        var builder = jsonFactory.createObjectBuilder();
        for (var entry : screenshots.entrySet()) {
            builder.add(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private Map<String, JsonObject> indexLayoutNodes(JsonObject layoutTree) {
        if (layoutTree == null) {
            return Map.of();
        }
        var layoutNodes = layoutTree.getJsonArray("layoutNodes");
        if (layoutNodes == null) {
            return Map.of();
        }
        Map<String, JsonObject> index = new HashMap<>(layoutNodes.size());
        for (var i = 0; i < layoutNodes.size(); i++) {
            var node = layoutNodes.getJsonObject(i);
            var nodeId = node.getString("nodeId", "");
            if (!nodeId.isEmpty()) {
                index.put(nodeId, node);
            }
        }
        return index;
    }

    private JsonObject focusedAttributes(JsonObject attributes) {
        if (attributes == null) {
            return null;
        }
        var builder = jsonFactory.createObjectBuilder();
        addIfPresent(builder, attributes, "type");
        addIfPresent(builder, attributes, "role");
        addIfPresent(builder, attributes, "href");
        addIfPresent(builder, attributes, "aria-label");
        addIfPresent(builder, attributes, "title");
        addIfPresent(builder, attributes, "value");
        return builder.build();
    }

    private void addIfPresent(JsonObjectBuilder builder, JsonObject attributes, String key) {
        if (attributes.containsKey(key)) {
            var value = attributes.get(key);
            if (value.getValueType() == JsonValue.ValueType.NUMBER) {
                builder.add(key, attributes.getJsonNumber(key));
            } else if (value.getValueType() == JsonValue.ValueType.STRING) {
                builder.add(key, attributes.getString(key));
            }
        }
    }

    private boolean isTextNode(JsonObject node) {
        return node.getInt("nodeType", -1) == 3 || "#text".equalsIgnoreCase(node.getString("nodeName", ""));
    }

    private boolean hasHref(JsonObject node) {
        var attributes = node.getJsonObject("attributes");
        return attributes != null && attributes.containsKey("href");
    }

    private boolean isFormControl(String tag) {
        return Set.of("BUTTON", "SELECT", "INPUT", "OPTION", "TEXTAREA").contains(tag);
    }

    private void addRectIfPresent(JsonObjectBuilder action, JsonObject layoutNode, String field) {
        if (layoutNode.containsKey(field)) {
            action.add(field, layoutNode.getJsonObject(field));
        }
    }

    private int countArray(JsonObject parent, String key) {
        var array = parent == null ? null : parent.getJsonArray(key);
        return array == null ? 0 : array.size();
    }

    private int countClickable(JsonObject domTree) {
        if (domTree == null) {
            return 0;
        }
        var nodes = domTree.getJsonArray("nodes");
        if (nodes == null) {
            return 0;
        }
        var count = 0;
        for (var i = 0; i < nodes.size(); i++) {
            var node = nodes.getJsonObject(i);
            var tag = node.getString("nodeName", "");
            if (node.getBoolean("isClickable", false) || hasHref(node) || isFormControl(tag)) {
                count++;
            }
        }
        return count;
    }

    private int headingLevel(String tagName) {
        if (tagName == null || tagName.length() != 2 || tagName.charAt(0) != 'H') {
            return 0;
        }
        var levelChar = tagName.charAt(1);
        if (levelChar >= '1' && levelChar <= '6') {
            return levelChar - '0';
        }
        return 0;
    }

    private JsonObject emptyAccessibilitySnapshot() {
        return jsonFactory.createObjectBuilder()
            .add("nodes", jsonFactory.createArrayBuilder())
            .build();
    }

    @FunctionalInterface
    interface FrameTransform {
        JsonObject apply(String frameId, JsonObject frame);
    }
}
