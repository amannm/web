package com.amannmalik.web.perception;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class FrameSnapshotsCollector {

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
        "font-style",
        "overflow",
        "overflow-x",
        "overflow-y"
    );

    private final JsonBuilderFactory jsonFactory;

    FrameSnapshotsCollector(JsonBuilderFactory jsonFactory) {
        this.jsonFactory = Objects.requireNonNull(jsonFactory, "jsonFactory must not be null");
    }

    Map<String, FrameSnapshots> collect(CdpRequestor requestor) {
        Objects.requireNonNull(requestor, "requestor must not be null");

        var params = jsonFactory.createObjectBuilder()
            .add("computedStyles", jsonFactory.createArrayBuilder(COMPUTED_STYLE_PROPERTIES))
            .add("includeDOMRects", true)
            .add("includePaintOrder", true)
            .add("includeAuthorShadowDOM", true)
            .build();

        var snapshot = requestor.request("DOMSnapshot.captureSnapshot", params);
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

        Map<String, FrameSnapshots> snapshots = new HashMap<>();
        for (var i = 0; i < documents.size(); i++) {
            var document = documents.getJsonObject(i);
            var frameId = documentFrameIds.get(i);
            var frameSnapshots = parseDocumentSnapshot(document, strings, documentFrameIds, frameId);
            snapshots.put(frameId, frameSnapshots);
        }
        return snapshots;
    }

    private FrameSnapshots parseDocumentSnapshot(JsonObject document,
                                                List<String> strings,
                                                List<String> documentFrameIds,
                                                String frameId) {
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
                    break;
                }
            }
        }

        var layoutNodes = document.containsKey("layout")
            ? buildLayoutSnapshot(document.getJsonObject("layout"), syntheticNodeIds, backendNodeIds, frameId, strings)
            : jsonFactory.createArrayBuilder().build();
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

        return new FrameSnapshots(domSnapshot, layoutSnapshot, styleSnapshot);
    }

    private JsonArray buildLayoutSnapshot(JsonObject layout,
                                          List<String> nodeIds,
                                          JsonArray backendNodeIds,
                                          String frameId,
                                          List<String> strings) {
        var nodeIndex = layout.getJsonArray("nodeIndex");
        var bounds = layout.getJsonArray("bounds");
        var styles = layout.getJsonArray("styles");
        var clientRects = layout.getJsonArray("clientRects");
        var scrollRects = layout.getJsonArray("scrollRects");
        var offsetRects = layout.getJsonArray("offsetRects");
        var stackingContexts = rareBooleanMap(layout, "stackingContexts");
        var paintOrders = layout.getJsonArray("paintOrders");

        var layoutNodes = jsonFactory.createArrayBuilder();
        for (var i = 0; i < nodeIndex.size(); i++) {
            var nodeIdx = nodeIndex.getInt(i);
            var nodeId = nodeIds.get(nodeIdx);
            var backendId = backendNodeIds.getJsonNumber(nodeIdx).longValue();

            var rect = bounds.getJsonArray(i);
            var x = rect.getJsonNumber(0).doubleValue();
            var y = rect.getJsonNumber(1).doubleValue();
            var width = rect.getJsonNumber(2).doubleValue();
            var height = rect.getJsonNumber(3).doubleValue();

            var boxModel = buildBoxModel(x, y, width, height);
            var layoutNode = jsonFactory.createObjectBuilder()
                .add("nodeId", nodeId)
                .add("backendNodeId", backendId)
                .add("frameId", frameId)
                .add("box", boxModel);

            if (clientRects != null && clientRects.size() > i) {
                var clientRect = clientRects.getJsonArray(i);
                if (hasRectCoordinates(clientRect)) {
                    layoutNode.add("clientRect", toRect(clientRect));
                }
            }
            if (scrollRects != null && scrollRects.size() > i) {
                var scrollRect = scrollRects.getJsonArray(i);
                if (hasRectCoordinates(scrollRect)) {
                    layoutNode.add("scrollRect", toRect(scrollRect));
                }
            }
            if (offsetRects != null && offsetRects.size() > i) {
                var offsetRect = offsetRects.getJsonArray(i);
                if (hasRectCoordinates(offsetRect)) {
                    layoutNode.add("offsetRect", toRect(offsetRect));
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
                var displayIdx = COMPUTED_STYLE_PROPERTIES.indexOf("display");
                if (displayIdx >= 0 && displayIdx < styleIndices.size()) {
                    var display = stringAt(strings, styleIndices.getInt(displayIdx, -1));
                    if (display != null) {
                        layoutNode.add("displayType", display);
                    }
                }
                var opacityIdx = COMPUTED_STYLE_PROPERTIES.indexOf("opacity");
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

            layoutNodes.add(layoutNode.build());
        }
        return layoutNodes.build();
    }

    private JsonObject buildBoxModel(double x, double y, double width, double height) {
        var x2 = x + width;
        var y2 = y + height;
        var quad = List.of(x, y, x2, y, x2, y2, x, y2);

        var quadBuilder = jsonFactory.createArrayBuilder();
        quad.forEach(quadBuilder::add);

        return jsonFactory.createObjectBuilder()
            .add("contentQuad", quadBuilder)
            .add("paddingQuad", quadBuilder)
            .add("borderQuad", quadBuilder)
            .add("marginQuad", quadBuilder)
            .add("width", width)
            .add("height", height)
            .build();
    }

    private boolean hasRectCoordinates(JsonArray rectArray) {
        return rectArray != null && rectArray.size() >= 4;
    }

    private JsonObject toRect(JsonArray rectArray) {
        if (!hasRectCoordinates(rectArray)) {
            return jsonFactory.createObjectBuilder().build();
        }
        return jsonFactory.createObjectBuilder()
            .add("x", rectArray.getJsonNumber(0).doubleValue())
            .add("y", rectArray.getJsonNumber(1).doubleValue())
            .add("width", rectArray.getJsonNumber(2).doubleValue())
            .add("height", rectArray.getJsonNumber(3).doubleValue())
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

    record FrameSnapshots(JsonObject domSnapshot,
                          JsonObject layoutSnapshot,
                          JsonObject styleSnapshot) {
        static final FrameSnapshots EMPTY = new FrameSnapshots(
            Json.createObjectBuilder().add("nodes", Json.createArrayBuilder()).build(),
            Json.createObjectBuilder().add("layoutNodes", Json.createArrayBuilder()).build(),
            Json.createObjectBuilder().add("nodes", Json.createArrayBuilder()).build()
        );
    }
}
