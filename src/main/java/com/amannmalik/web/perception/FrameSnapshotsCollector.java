package com.amannmalik.web.perception;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class FrameSnapshotsCollector {

    private static final Logger LOG = System.getLogger(FrameSnapshotsCollector.class.getName());
    private static final List<String> COMPUTED_STYLE_PROPERTIES = List.of(
        "display",
        "visibility",
        "opacity",
        "z-index",
        "color",
        "background-color",
        "font-size",
        "font-family",
        "font-weight",
        "font-style"
    );

    private final JsonBuilderFactory jsonFactory;

    FrameSnapshotsCollector(JsonBuilderFactory jsonFactory) {
        this.jsonFactory = Objects.requireNonNull(jsonFactory, "jsonFactory must not be null");
    }

    JsonObject snapshotParams() {
        return jsonFactory.createObjectBuilder()
            .add("computedStyles", jsonFactory.createArrayBuilder(COMPUTED_STYLE_PROPERTIES))
            .add("includeDOMRects", true)
            .add("includePaintOrder", true)
            .add("includeAuthorShadowDOM", true)
            .add("includeBlendedBackgroundColors", false)
            .add("includeTextColorOpacities", false)
            .build();
    }

    Map<String, FrameSnapshots> collect(CdpRequestor requestor,
                                        double devicePixelRatio,
                                        Map<String, ScrollOffsetCollector.ScrollOffset> scrollOffsets,
                                        Set<String> allowedFrameIds,
                                        int maxFrames) {
        Objects.requireNonNull(requestor, "requestor must not be null");
        var snapshot = requestor.request("DOMSnapshot.captureSnapshot", snapshotParams());
        return collect(snapshot, devicePixelRatio, scrollOffsets, allowedFrameIds, maxFrames);
    }

    Map<String, FrameSnapshots> collect(JsonObject snapshot,
                                        double devicePixelRatio,
                                        Map<String, ScrollOffsetCollector.ScrollOffset> scrollOffsets,
                                        Set<String> allowedFrameIds,
                                        int maxFrames) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(scrollOffsets, "scrollOffsets must not be null");

        var stringsArray = snapshot.getJsonArray("strings");
        var documents = snapshot.getJsonArray("documents");
        if (stringsArray == null || documents == null) {
            return Map.of();
        }

        List<String> strings = new ArrayList<>(stringsArray.size());
        stringsArray.forEach(value -> strings.add(((JsonString) value).getString()));

        List<String> documentFrameIds = new ArrayList<>(documents.size());
        for (var value : documents) {
            var doc = value.asJsonObject();
            var frameIdIndex = doc.getInt("frameId", -1);
            documentFrameIds.add(stringOrEmpty(strings, frameIdIndex));
        }

        var normalizedMaxFrames = maxFrames < 1 ? documents.size() : maxFrames;
        var allowList = allowedFrameIds == null || allowedFrameIds.isEmpty()
            ? Set.<String>of()
            : new HashSet<>(allowedFrameIds);
        var computedStyleIndex = computedStyleIndices();
        var processedCount = 0;
        var truncated = false;
        Map<String, FrameSnapshots> snapshots = new HashMap<>();
        for (var i = 0; i < documents.size(); i++) {
            if (processedCount >= normalizedMaxFrames) {
                truncated = true;
                break;
            }
            var document = documents.getJsonObject(i);
            var frameId = documentFrameIds.get(i);
            if (!allowList.isEmpty() && !allowList.contains(frameId)) {
                continue;
            }
            var frameSnapshots = parseDocumentSnapshot(
                document,
                strings,
                documentFrameIds,
                frameId,
                devicePixelRatio,
                scrollOffsets.get(frameId),
                computedStyleIndex
            );
            snapshots.put(frameId, frameSnapshots);
            processedCount++;
        }
        if (truncated) {
            LOG.log(Level.INFO, "Truncated frame snapshots at {0} frames (of {1})", normalizedMaxFrames, documents.size());
        }
        return snapshots;
    }

    private FrameSnapshots parseDocumentSnapshot(JsonObject document,
                                                List<String> strings,
                                                List<String> documentFrameIds,
                                                String frameId,
                                                double devicePixelRatio,
                                                ScrollOffsetCollector.ScrollOffset scrollOffset,
                                                Map<String, Integer> computedStyleIndex) {
        var nodes = document.getJsonObject("nodes");
        if (nodes == null) {
            return FrameSnapshots.EMPTY;
        }

        var nodeTypes = nodes.getJsonArray("nodeType");
        var nodeNames = nodes.getJsonArray("nodeName");
        var nodeValues = nodes.getJsonArray("nodeValue");
        var backendNodeIds = nodes.getJsonArray("backendNodeId");
        var parentIndexArray = nodes.getJsonArray("parentIndex");
        var attributesArray = nodes.getJsonArray("attributes");

        var textValueByIndex = rareStringMap(nodes, "textValue", strings);
        var pseudoTypes = rareStringMap(nodes, "pseudoType", strings);
        var clickable = rareBooleanMap(nodes, "isClickable");
        var contentDocumentIndex = rareIntegerMap(nodes, "contentDocumentIndex");

        var nodeCount = nodeTypes.size();
        List<String> syntheticNodeIds = new ArrayList<>(nodeCount);
        List<JsonObjectBuilder> nodeBuilders = new ArrayList<>(nodeCount);
        List<List<String>> childLists = new ArrayList<>(nodeCount);
        var rootIndex = 0;
        for (var i = 0; i < nodeCount; i++) {
            syntheticNodeIds.add("n" + i);
            childLists.add(new ArrayList<>());
        }

        if (parentIndexArray != null) {
            for (var i = 0; i < parentIndexArray.size(); i++) {
                var parentIndex = parentIndexArray.getInt(i, -1);
                if (parentIndex >= 0 && parentIndex < childLists.size()) {
                    childLists.get(parentIndex).add(syntheticNodeIds.get(i));
                }
            }
        }

        for (var i = 0; i < nodeCount; i++) {
            var builder = jsonFactory.createObjectBuilder();
            builder.add("nodeId", syntheticNodeIds.get(i));
            builder.add("backendNodeId", backendNodeIds.getJsonNumber(i).longValue());
            builder.add("nodeType", nodeTypes.getInt(i));
            builder.add("nodeName", stringOrEmpty(strings, nodeNames.getInt(i, -1)));
            builder.add("frameId", frameId);

            if (nodeValues != null && nodeValues.size() > i) {
                var nodeValue = stringAt(strings, nodeValues.getInt(i, -1));
                if (nodeValue != null) {
                    builder.add("nodeValue", nodeValue);
                }
            } else if (textValueByIndex.containsKey(i)) {
                builder.add("nodeValue", textValueByIndex.get(i));
            }

            if (attributesArray != null && attributesArray.size() > i) {
                var attributePairs = attributesArray.getJsonArray(i);
                var attributes = jsonFactory.createObjectBuilder();
                for (var j = 0; j + 1 < attributePairs.size(); j += 2) {
                    var name = stringAt(strings, attributePairs.getInt(j, -1));
                    var value = stringAt(strings, attributePairs.getInt(j + 1, -1));
                    if (name != null && value != null) {
                        attributes.add(name, value);
                    }
                }
                builder.add("attributes", attributes.build());
            }

            if (contentDocumentIndex.containsKey(i)) {
                int docIndex = contentDocumentIndex.get(i);
                if (docIndex >= 0 && docIndex < documentFrameIds.size()) {
                    builder.add("contentDocumentFrameId", documentFrameIds.get(docIndex));
                }
            }
            if (pseudoTypes.containsKey(i)) {
                builder.add("pseudoType", pseudoTypes.get(i));
            }
            if (clickable.getOrDefault(i, false)) {
                builder.add("isClickable", true);
            }
            var children = childLists.get(i);
            if (!children.isEmpty()) {
                var childArray = jsonFactory.createArrayBuilder();
                children.forEach(childArray::add);
                builder.add("childNodeIds", childArray);
            }
            nodeBuilders.add(builder);
        }

        var rootNodeId = syntheticNodeIds.getFirst();
        if (parentIndexArray != null) {
            for (var i = 0; i < parentIndexArray.size(); i++) {
                var parentIndex = parentIndexArray.getInt(i, -1);
                if (parentIndex < 0) {
                    rootNodeId = syntheticNodeIds.get(i);
                    rootIndex = i;
                    break;
                }
            }
        }

        var layoutParts = document.containsKey("layout")
            ? buildLayoutSnapshot(
            document.getJsonObject("layout"),
            syntheticNodeIds,
            backendNodeIds,
            frameId,
            strings,
            devicePixelRatio,
            computedStyleIndex)
            : LayoutSnapshotParts.empty();
        layoutParts.applyScrollOffset(rootIndex, scrollOffset, jsonFactory);
        var layoutNodes = jsonFactory.createArrayBuilder();
        layoutParts.layoutNodes().forEach(builder -> layoutNodes.add(builder.build()));
        var layoutSnapshot = jsonFactory.createObjectBuilder()
            .add("layoutNodes", layoutNodes)
            .build();

        var styleNodes = document.containsKey("layout")
            ? buildStyleSnapshot(document.getJsonObject("layout"), syntheticNodeIds, strings)
            : jsonFactory.createArrayBuilder().build();
        var styleSnapshot = jsonFactory.createObjectBuilder()
            .add("nodes", styleNodes)
            .build();

        var domNodesArray = jsonFactory.createArrayBuilder();
        nodeBuilders.forEach(builder -> domNodesArray.add(builder.build()));

        var domSnapshot = jsonFactory.createObjectBuilder()
            .add("nodes", domNodesArray)
            .add("rootNodeId", rootNodeId)
            .build();

        var interactions = interactions(scrollOffset);
        return new FrameSnapshots(domSnapshot, layoutSnapshot, styleSnapshot, interactions);
    }

    private LayoutSnapshotParts buildLayoutSnapshot(JsonObject layout,
                                                    List<String> nodeIds,
                                                    JsonArray backendNodeIds,
                                                    String frameId,
                                                    List<String> strings,
                                                    double devicePixelRatio,
                                                    Map<String, Integer> computedStyleIndex) {
        var nodeIndex = layout.getJsonArray("nodeIndex");
        var bounds = layout.getJsonArray("bounds");
        var styles = layout.getJsonArray("styles");
        var clientRects = layout.getJsonArray("clientRects");
        var scrollRects = layout.getJsonArray("scrollRects");
        var offsetRects = layout.getJsonArray("offsetRects");
        var stackingContexts = rareBooleanMap(layout, "stackingContexts");
        var paintOrders = layout.getJsonArray("paintOrders");

        var normalizedScale = devicePixelRatio <= 0.0 ? 1.0 : devicePixelRatio;
        var displayIdx = computedStyleIndex.getOrDefault("display", -1);
        var opacityIdx = computedStyleIndex.getOrDefault("opacity", -1);

        List<JsonObjectBuilder> layoutNodes = new ArrayList<>(nodeIndex.size());
        Map<Integer, Integer> layoutIndexBySnapshotIndex = new HashMap<>(nodeIndex.size());
        for (var i = 0; i < nodeIndex.size(); i++) {
            var nodeIdx = nodeIndex.getInt(i);
            layoutIndexBySnapshotIndex.put(nodeIdx, i);
            var nodeId = nodeIds.get(nodeIdx);
            var backendId = backendNodeIds.getJsonNumber(nodeIdx).longValue();

            var rect = bounds.getJsonArray(i);
            var x = rect.getJsonNumber(0).doubleValue();
            var y = rect.getJsonNumber(1).doubleValue();
            var width = rect.getJsonNumber(2).doubleValue();
            var height = rect.getJsonNumber(3).doubleValue();

            var boxModel = buildBoxModel(x, y, width, height, normalizedScale);
            var layoutNode = jsonFactory.createObjectBuilder()
                .add("nodeId", nodeId)
                .add("backendNodeId", backendId)
                .add("frameId", frameId)
                .add("box", boxModel);

            if (clientRects != null && clientRects.size() > i) {
                var clientRect = clientRects.getJsonArray(i);
                if (hasRectCoordinates(clientRect)) {
                    layoutNode.add("clientRect", toRect(clientRect, normalizedScale));
                }
            }
            if (scrollRects != null && scrollRects.size() > i) {
                var scrollRect = scrollRects.getJsonArray(i);
                if (hasRectCoordinates(scrollRect)) {
                    layoutNode.add("scrollRect", toRect(scrollRect, normalizedScale));
                }
            }
            if (offsetRects != null && offsetRects.size() > i) {
                var offsetRect = offsetRects.getJsonArray(i);
                if (hasRectCoordinates(offsetRect)) {
                    layoutNode.add("offsetRect", toRect(offsetRect, normalizedScale));
                }
            }
            if (stackingContexts.getOrDefault(i, false)) {
                layoutNode.add("isStackingContext", true);
            }
            if (paintOrders != null && paintOrders.size() > i) {
                layoutNode.add("zIndex", paintOrders.getInt(i));
            }
            if (styles != null && styles.size() > i) {
                var styleIndices = styles.getJsonArray(i);
                if (displayIdx >= 0 && displayIdx < styleIndices.size()) {
                    var display = stringAt(strings, styleIndices.getInt(displayIdx, -1));
                    if (display != null) {
                        layoutNode.add("displayType", display);
                    }
                }
                if (opacityIdx >= 0 && opacityIdx < styleIndices.size()) {
                    var opacity = stringAt(strings, styleIndices.getInt(opacityIdx, -1));
                    if (opacity != null) {
                        try {
                            layoutNode.add("opacity", Double.parseDouble(opacity));
                        } catch (NumberFormatException ignored) {
                            // leave blank when not numeric
                        }
                    }
                }
            }

            layoutNodes.add(layoutNode);
        }
        return new LayoutSnapshotParts(layoutNodes, layoutIndexBySnapshotIndex);
    }

    private JsonObject buildBoxModel(double x, double y, double width, double height, double devicePixelRatio) {
        var cssX = x / devicePixelRatio;
        var cssY = y / devicePixelRatio;
        var cssWidth = width / devicePixelRatio;
        var cssHeight = height / devicePixelRatio;
        var x2 = cssX + cssWidth;
        var y2 = cssY + cssHeight;
        var quad = List.of(cssX, cssY, x2, cssY, x2, y2, cssX, y2);

        var quadBuilder = jsonFactory.createArrayBuilder();
        quad.forEach(quadBuilder::add);

        return jsonFactory.createObjectBuilder()
            .add("contentQuad", quadBuilder)
            .add("paddingQuad", quadBuilder)
            .add("borderQuad", quadBuilder)
            .add("marginQuad", quadBuilder)
            .add("width", cssWidth)
            .add("height", cssHeight)
            .build();
    }

    private boolean hasRectCoordinates(JsonArray rectArray) {
        return rectArray != null && rectArray.size() >= 4;
    }

    private JsonObject toRect(JsonArray rectArray, double devicePixelRatio) {
        if (!hasRectCoordinates(rectArray)) {
            return jsonFactory.createObjectBuilder().build();
        }
        var scale = devicePixelRatio <= 0.0 ? 1.0 : devicePixelRatio;
        return jsonFactory.createObjectBuilder()
            .add("x", rectArray.getJsonNumber(0).doubleValue() / scale)
            .add("y", rectArray.getJsonNumber(1).doubleValue() / scale)
            .add("width", rectArray.getJsonNumber(2).doubleValue() / scale)
            .add("height", rectArray.getJsonNumber(3).doubleValue() / scale)
            .build();
    }

    private JsonArray buildStyleSnapshot(JsonObject layout,
                                         List<String> nodeIds,
                                         List<String> strings) {
        var nodeIndex = layout.getJsonArray("nodeIndex");
        var styles = layout.getJsonArray("styles");
        var styleNodes = jsonFactory.createArrayBuilder();
        if (styles == null) {
            return styleNodes.build();
        }
        for (var i = 0; i < styles.size(); i++) {
            var styleValues = styles.getJsonArray(i);
            var computed = jsonFactory.createObjectBuilder();
            for (var propertyIndex = 0; propertyIndex < Math.min(styleValues.size(), COMPUTED_STYLE_PROPERTIES.size()); propertyIndex++) {
                var value = stringAt(strings, styleValues.getInt(propertyIndex, -1));
                if (value != null) {
                    computed.add(COMPUTED_STYLE_PROPERTIES.get(propertyIndex), value);
                }
            }
            var styleNode = jsonFactory.createObjectBuilder()
                .add("nodeId", nodeIds.get(nodeIndex.getInt(i)))
                .add("computed", computed.build())
                .build();
            styleNodes.add(styleNode);
        }
        return styleNodes.build();
    }

    private Map<String, Integer> computedStyleIndices() {
        Map<String, Integer> index = new HashMap<>(COMPUTED_STYLE_PROPERTIES.size());
        for (var i = 0; i < COMPUTED_STYLE_PROPERTIES.size(); i++) {
            index.put(COMPUTED_STYLE_PROPERTIES.get(i), i);
        }
        return index;
    }

    private JsonObject interactions(ScrollOffsetCollector.ScrollOffset scrollOffset) {
        if (scrollOffset == null) {
            return jsonFactory.createObjectBuilder().build();
        }
        return jsonFactory.createObjectBuilder()
            .add("scrollOffset", scrollOffset.toJson(jsonFactory))
            .build();
    }

    private String stringAt(List<String> strings, int index) {
        if (index < 0 || index >= strings.size()) {
            return null;
        }
        return strings.get(index);
    }

    private String stringOrEmpty(List<String> strings, int index) {
        var value = stringAt(strings, index);
        return value == null ? "" : value;
    }

    private Map<Integer, String> rareStringMap(JsonObject parent, String property, List<String> strings) {
        Map<Integer, String> map = new HashMap<>();
        if (!parent.containsKey(property)) {
            return map;
        }
        var rare = parent.getJsonObject(property);
        var indices = rare.getJsonArray("index");
        var values = rare.getJsonArray("value");
        if (indices == null || values == null) {
            return map;
        }
        for (var i = 0; i < indices.size(); i++) {
            var valueIndex = values.getInt(i, -1);
            var value = stringAt(strings, valueIndex);
            if (value != null) {
                map.put(indices.getInt(i), value);
            }
        }
        return map;
    }

    private Map<Integer, Boolean> rareBooleanMap(JsonObject parent, String property) {
        Map<Integer, Boolean> map = new HashMap<>();
        if (!parent.containsKey(property)) {
            return map;
        }
        var rare = parent.getJsonObject(property);
        var indices = rare.getJsonArray("index");
        if (indices == null) {
            return map;
        }
        for (var value : indices) {
            map.put(((JsonNumber) value).intValue(), true);
        }
        return map;
    }

    private Map<Integer, Integer> rareIntegerMap(JsonObject parent, String property) {
        Map<Integer, Integer> map = new HashMap<>();
        if (!parent.containsKey(property)) {
            return map;
        }
        var rare = parent.getJsonObject(property);
        var indices = rare.getJsonArray("index");
        var values = rare.getJsonArray("value");
        if (indices == null || values == null) {
            return map;
        }
        for (var i = 0; i < indices.size(); i++) {
            map.put(indices.getInt(i), values.getInt(i));
        }
        return map;
    }

    private record LayoutSnapshotParts(List<JsonObjectBuilder> layoutNodes,
                                       Map<Integer, Integer> layoutIndexBySnapshotIndex) {

        static LayoutSnapshotParts empty() {
            return new LayoutSnapshotParts(List.of(), Map.of());
        }

        void applyScrollOffset(int rootSnapshotIndex,
                               ScrollOffsetCollector.ScrollOffset scrollOffset,
                               JsonBuilderFactory factory) {
            if (scrollOffset == null || layoutNodes.isEmpty()) {
                return;
            }
            var layoutIndex = layoutIndexBySnapshotIndex.getOrDefault(rootSnapshotIndex, -1);
            if (layoutIndex < 0 || layoutIndex >= layoutNodes.size()) {
                return;
            }
            layoutNodes.get(layoutIndex).add("scrollOffset", scrollOffset.toJson(factory));
        }
    }

    record FrameSnapshots(JsonObject domSnapshot,
                          JsonObject layoutSnapshot,
                          JsonObject styleSnapshot,
                          JsonObject interactions) {
        static final FrameSnapshots EMPTY = new FrameSnapshots(
            Json.createObjectBuilder().add("nodes", Json.createArrayBuilder()).build(),
            Json.createObjectBuilder().add("layoutNodes", Json.createArrayBuilder()).build(),
            Json.createObjectBuilder().add("nodes", Json.createArrayBuilder()).build(),
            Json.createObjectBuilder().build()
        );
    }
}
