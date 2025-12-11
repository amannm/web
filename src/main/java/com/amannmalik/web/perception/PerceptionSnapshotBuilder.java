package com.amannmalik.web.perception;

import com.amannmalik.web.chromium.CdpClient;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Collects a perception snapshot from a live CDP target.
 *
 * <p>The resulting {@link JsonObject} conforms to the {@code perception.json} schema in the
 * repository root. Only required fields are populated today; optional fields are added when the
 * CDP responses expose them without further processing. CDP calls are issued in parallel with
 * short retries; failures are surfaced immediately to avoid silent schema drift.</p>
 */
public final class PerceptionSnapshotBuilder {

    private static final int MAX_IFRAMES = 32;
    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(150);

    private final JsonBuilderFactory jsonFactory;
    private final Map<CdpClient, FrameTracker> frameTrackers = new HashMap<>();
    private final DeviceStateCollector deviceStateCollector;
    private final FrameSnapshotsCollector frameSnapshotsCollector;
    private final AccessibilitySnapshotCollector accessibilitySnapshotCollector;
    private final ScrollOffsetCollector scrollOffsetCollector;
    private final ScreenshotCollector screenshotCollector;
    private final FrameAssembler frameAssembler;

    public PerceptionSnapshotBuilder() {
        this(Json.createBuilderFactory(Map.of()));
    }

    PerceptionSnapshotBuilder(JsonBuilderFactory jsonFactory) {
        this.jsonFactory = Objects.requireNonNull(jsonFactory, "jsonFactory must not be null");
        this.deviceStateCollector = new DeviceStateCollector(jsonFactory);
        this.frameSnapshotsCollector = new FrameSnapshotsCollector(jsonFactory);
        this.accessibilitySnapshotCollector = new AccessibilitySnapshotCollector(jsonFactory);
        this.scrollOffsetCollector = new ScrollOffsetCollector(jsonFactory);
        this.screenshotCollector = new ScreenshotCollector(jsonFactory);
        this.frameAssembler = new FrameAssembler(jsonFactory);
    }

    public JsonObject capture(CdpClient client) {
        Objects.requireNonNull(client, "client must not be null");

        var requestor = new CdpRequestor(client, jsonFactory);
        var layoutMetricsFuture = asyncRequest(requestor, "Page.getLayoutMetrics");
        var frameTreeFuture = asyncRequest(requestor, "Page.getFrameTree");
        var accessibilityFuture = asyncRequest(requestor, "Accessibility.getFullAXTree");
        var snapshotFuture = asyncRequest(requestor, "DOMSnapshot.captureSnapshot", frameSnapshotsCollector.snapshotParams());
        var deviceStateFuture = CompletableFuture.supplyAsync(() -> deviceStateCollector.collect(requestor));

        var frameTree = frameTreeFuture.join();
        var frameTracker = frameTrackers.computeIfAbsent(
            client,
            ignored -> FrameTracker.bootstrap(client, frameTree)
        );
        var frameView = frameTracker.snapshot();
        var allowedFrameOrder = limitFrames(frameView, MAX_IFRAMES);
        var allowedFrameIds = new LinkedHashSet<>(allowedFrameOrder);

        var deviceState = deviceStateFuture.join();
        var deviceScaleFactor = deviceScaleFactor(deviceState);
        var scrollOffsets = scrollOffsetCollector.collect(requestor, allowedFrameOrder, MAX_IFRAMES, deviceScaleFactor);

        var rawSnapshot = snapshotFuture.join();
        var frameSnapshots = frameSnapshotsCollector.collect(
            rawSnapshot,
            deviceScaleFactor,
            scrollOffsets,
            allowedFrameIds,
            MAX_IFRAMES
        );

        var accessibilityTree = accessibilityFuture.join();
        var accessibilitySnapshots = accessibilitySnapshotCollector.collectFromSnapshot(accessibilityTree, allowedFrameIds);

        var layoutMetrics = layoutMetricsFuture.join();
        var viewportState = ViewportBuilder.build(jsonFactory, layoutMetrics);
        var framesState = frameAssembler.buildFrames(frameView, frameSnapshots, accessibilitySnapshots);
        var screenshots = screenshotCollector.collect(requestor, frameView);

        return jsonFactory.createObjectBuilder()
            .add("timestamp", System.currentTimeMillis())
            .add("device", deviceState)
            .add("viewport", viewportState)
            .add("frames", framesState)
            .add("screenshots", mapToObject(screenshots))
            .build();
    }

    private JsonObject mapToObject(Map<String, JsonObject> entries) {
        var builder = jsonFactory.createObjectBuilder();
        entries.forEach(builder::add);
        return builder.build();
    }

    private CompletableFuture<JsonObject> asyncRequest(CdpRequestor requestor, String method) {
        return asyncRequest(requestor, method, jsonFactory.createObjectBuilder().build());
    }

    private CompletableFuture<JsonObject> asyncRequest(CdpRequestor requestor, String method, JsonObject params) {
        return CompletableFuture.supplyAsync(
            () -> requestor.requestWithRetries(method, params, MAX_RETRIES, RETRY_DELAY)
        );
    }

    private List<String> limitFrames(FrameTracker.FrameView frameView, int maxFrames) {
        if (frameView == null || frameView.frames().isEmpty() || maxFrames < 1) {
            return List.of();
        }
        Deque<String> queue = new ArrayDeque<>();
        if (!frameView.rootFrameIds().isEmpty()) {
            queue.addAll(frameView.rootFrameIds());
        } else {
            queue.addAll(frameView.frames().keySet());
        }
        List<String> ordered = new ArrayList<>(Math.min(maxFrames, queue.size()));
        Set<String> seen = new LinkedHashSet<>();
        while (!queue.isEmpty() && ordered.size() < maxFrames) {
            var frameId = queue.removeFirst();
            if (!seen.add(frameId)) {
                continue;
            }
            ordered.add(frameId);
            var node = frameView.frame(frameId);
            if (node != null) {
                queue.addAll(node.childFrameIds());
            }
        }
        return ordered;
    }

    private double deviceScaleFactor(JsonObject deviceState) {
        if (deviceState == null || !deviceState.containsKey("deviceScaleFactor")) {
            return 1.0;
        }
        return deviceState.getJsonNumber("deviceScaleFactor").doubleValue();
    }
}
