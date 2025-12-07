package com.amannmalik.web.perception;

import com.amannmalik.web.chromium.CdpEvent;
import com.amannmalik.web.chromium.CdpEventListener;
import com.amannmalik.web.chromium.CdpClient;
import com.amannmalik.web.chromium.EventSubscription;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the frame tree for a single CDP target using Page.* events so that captures
 * can avoid re-fetching the full tree for every snapshot.
 */
final class FrameTracker implements AutoCloseable, CdpEventListener {

    private final CdpClient client;
    private final Map<String, FrameNode> frames = new ConcurrentHashMap<>();
    private volatile EventSubscription subscription;

    private FrameTracker(CdpClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    static FrameTracker bootstrap(CdpClient client, JsonObject frameTree) {
        Objects.requireNonNull(frameTree, "frameTree must not be null");
        var tracker = new FrameTracker(client);
        tracker.ingestFrameTree(frameTree.getJsonObject("frameTree"), null);
        tracker.subscription = client.onEvent(tracker);
        return tracker;
    }

    FrameView snapshot() {
        Map<String, FrameNodeView> copies = new HashMap<>();
        List<String> roots = new ArrayList<>();
        for (var node : frames.values()) {
            var parentId = node.parentFrameId();
            if (parentId == null || parentId.isBlank() || !frames.containsKey(parentId)) {
                roots.add(node.frameId());
            }
            copies.put(node.frameId(), node.view());
        }
        return new FrameView(Collections.unmodifiableMap(copies), Collections.unmodifiableList(roots));
    }

    @Override
    public void close() {
        if (subscription != null) {
            subscription.close();
        }
        frames.clear();
    }

    @Override
    public void onEvent(CdpEvent event) {
        switch (event.method()) {
            case "Page.frameAttached" -> handleFrameAttached(event.params());
            case "Page.frameDetached" -> handleFrameDetached(event.params());
            case "Page.frameNavigated" -> handleFrameNavigated(event.params());
            case "Page.documentOpened" -> handleFrameNavigated(event.params());
            default -> { }
        }
    }

    private void handleFrameAttached(JsonObject params) {
        var frameId = params.getString("frameId", "");
        if (frameId.isBlank()) {
            return;
        }
        var parentFrameId = params.getString("parentFrameId", "");
        var node = frames.computeIfAbsent(frameId, FrameNode::new);
        node.setParentFrameId(parentFrameId);
        attachChild(parentFrameId, frameId);
    }

    private void handleFrameDetached(JsonObject params) {
        var frameId = params.getString("frameId", "");
        if (frameId.isBlank()) {
            return;
        }
        var removed = frames.remove(frameId);
        if (removed != null && removed.parentFrameId() != null) {
            var parent = frames.get(removed.parentFrameId());
            if (parent != null) {
                parent.removeChild(frameId);
            }
        }
    }

    private void handleFrameNavigated(JsonObject params) {
        var frame = params.getJsonObject("frame");
        if (frame == null || !frame.containsKey("id")) {
            return;
        }
        var frameId = frame.getString("id");
        var node = frames.computeIfAbsent(frameId, FrameNode::new);
        node.setFrame(frame);
        var parentFrameId = frame.getString("parentId", node.parentFrameId());
        node.setParentFrameId(parentFrameId);
        if (parentFrameId != null && !parentFrameId.isBlank()) {
            attachChild(parentFrameId, frameId);
        }
    }

    private void attachChild(String parentFrameId, String childFrameId) {
        if (parentFrameId == null || parentFrameId.isBlank()) {
            return;
        }
        var parent = frames.computeIfAbsent(parentFrameId, FrameNode::new);
        parent.addChild(childFrameId);
    }

    private void ingestFrameTree(JsonObject frameTree, String parentFrameId) {
        var frame = frameTree.getJsonObject("frame");
        if (frame == null || !frame.containsKey("id")) {
            throw new IllegalStateException("frameTree entry missing frame id");
        }
        var frameId = frame.getString("id");
        var node = frames.computeIfAbsent(frameId, FrameNode::new);
        node.setFrame(frame);
        node.setParentFrameId(parentFrameId);
        if (parentFrameId != null) {
            attachChild(parentFrameId, frameId);
        }
        if (frameTree.containsKey("childFrames")) {
            frameTree.getJsonArray("childFrames")
                .forEach(child -> ingestFrameTree(child.asJsonObject(), frameId));
        }
    }

    record FrameView(Map<String, FrameNodeView> frames, List<String> rootFrameIds) {
        FrameNodeView frame(String frameId) {
            return frames.get(frameId);
        }
    }

    static final class FrameNode {
        private final String frameId;
        private volatile String parentFrameId;
        private volatile JsonObject frame;
        private final Set<String> childFrameIds = ConcurrentHashMap.newKeySet();

        FrameNode(String frameId) {
            if (frameId == null || frameId.isBlank()) {
                throw new IllegalArgumentException("frameId must be non-blank");
            }
            this.frameId = frameId;
        }

        String frameId() {
            return frameId;
        }

        String parentFrameId() {
            return parentFrameId;
        }

        void setParentFrameId(String parentFrameId) {
            this.parentFrameId = parentFrameId;
        }

        JsonObject frame() {
            return frame;
        }

        void setFrame(JsonObject frame) {
            this.frame = frame;
        }

        void addChild(String childId) {
            if (childId != null && !childId.isBlank()) {
                childFrameIds.add(childId);
            }
        }

        void removeChild(String childId) {
            childFrameIds.remove(childId);
        }

        FrameNodeView view() {
            var safeFrame = frame != null ? frame : Json.createObjectBuilder().add("id", frameId).build();
            return new FrameNodeView(frameId, parentFrameId, safeFrame, List.copyOf(childFrameIds));
        }
    }

    record FrameNodeView(String frameId, String parentFrameId, JsonObject frame, List<String> childFrameIds) { }
}
