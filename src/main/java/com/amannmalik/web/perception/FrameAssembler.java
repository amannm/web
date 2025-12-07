package com.amannmalik.web.perception;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.amannmalik.web.perception.JsonAccessors.requiredString;

final class FrameAssembler {

    private final JsonBuilderFactory jsonFactory;

    FrameAssembler(JsonBuilderFactory jsonFactory) {
        this.jsonFactory = Objects.requireNonNull(jsonFactory, "jsonFactory must not be null");
    }

    JsonObject buildFrames(FrameTracker.FrameView frameView,
                           Map<String, FrameSnapshotsCollector.FrameSnapshots> frameSnapshots,
                           Map<String, JsonObject> accessibilitySnapshots) {
        var framesBuilder = jsonFactory.createObjectBuilder();
        var roots = frameView.rootFrameIds().isEmpty()
            ? frameView.frames().keySet()
            : frameView.rootFrameIds();
        for (var rootId : roots) {
            appendFrame(framesBuilder, frameView, rootId, null, frameSnapshots, accessibilitySnapshots);
        }
        return framesBuilder.build();
    }

    private void appendFrame(JsonObjectBuilder framesBuilder,
                             FrameTracker.FrameView frameView,
                             String frameId,
                             String parentFrameId,
                             Map<String, FrameSnapshotsCollector.FrameSnapshots> frameSnapshots,
                             Map<String, JsonObject> accessibilitySnapshots) {
        var nodeView = frameView.frame(frameId);
        if (nodeView == null) {
            return;
        }
        var frame = nodeView.frame();

        var snapshots = frameSnapshots.getOrDefault(frameId, FrameSnapshotsCollector.FrameSnapshots.EMPTY);
        var frameBuilder = jsonFactory.createObjectBuilder()
            .add("frameId", frameId)
            .add("url", requiredString(frame, "url"))
            .add("domTree", snapshots.domSnapshot())
            .add("layoutTree", snapshots.layoutSnapshot())
            .add("styles", snapshots.styleSnapshot())
            .add("layers", emptyLayerSnapshot())
            .add("accessibilityTree", accessibilitySnapshots.getOrDefault(frameId, emptyAccessibilitySnapshot()))
            .add("isMainFrame", parentFrameId == null);

        if (parentFrameId != null) {
            frameBuilder.add("parentFrameId", parentFrameId);
        }
        if (frame.containsKey("loaderId")) {
            frameBuilder.add("loaderId", frame.getString("loaderId", ""));
        }
        if (frame.containsKey("name")) {
            frameBuilder.add("name", frame.getString("name", ""));
        }
        if (frame.containsKey("securityOrigin")) {
            frameBuilder.add("securityOrigin", frame.getString("securityOrigin", ""));
        }
        if (frame.containsKey("domainAndRegistry")) {
            frameBuilder.add("origin", frame.getString("domainAndRegistry", ""));
        }

        framesBuilder.add(frameId, frameBuilder.build());

        for (var childId : nodeView.childFrameIds()) {
            appendFrame(framesBuilder, frameView, childId, frameId, frameSnapshots, accessibilitySnapshots);
        }
    }

    private JsonObject emptyLayerSnapshot() {
        return jsonFactory.createObjectBuilder()
            .add("layers", jsonFactory.createArrayBuilder())
            .build();
    }

    private JsonObject emptyAccessibilitySnapshot() {
        return jsonFactory.createObjectBuilder()
            .add("nodes", jsonFactory.createArrayBuilder())
            .build();
    }
}
