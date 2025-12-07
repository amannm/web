package com.amannmalik.web.perception;

import com.amannmalik.web.chromium.CdpClient;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Collects a perception snapshot from a live CDP target.
 *
 * <p>The resulting {@link JsonObject} conforms to the {@code perception.json} schema in the
 * repository root. Only required fields are populated today; optional fields are added when the
 * CDP responses expose them without further processing. All CDP requests are made synchronously
 * and failures are surfaced immediately to avoid silent schema drift.</p>
 */
public final class PerceptionSnapshotBuilder {

    private final JsonBuilderFactory jsonFactory;
    private final Map<CdpClient, FrameTracker> frameTrackers = new HashMap<>();
    private final DeviceStateCollector deviceStateCollector;
    private final FrameSnapshotsCollector frameSnapshotsCollector;
    private final AccessibilitySnapshotCollector accessibilitySnapshotCollector;
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
        this.screenshotCollector = new ScreenshotCollector(jsonFactory);
        this.frameAssembler = new FrameAssembler(jsonFactory);
    }

    public JsonObject capture(CdpClient client) {
        Objects.requireNonNull(client, "client must not be null");

        var requestor = new CdpRequestor(client, jsonFactory);
        var layoutMetrics = requestor.request("Page.getLayoutMetrics");
        var frameTracker = frameTrackers.computeIfAbsent(
            client,
            ignored -> FrameTracker.bootstrap(client, requestor.request("Page.getFrameTree"))
        );

        var frameView = frameTracker.snapshot();
        var frameSnapshots = frameSnapshotsCollector.collect(requestor);
        var accessibilitySnapshots = accessibilitySnapshotCollector.collect(requestor);
        var deviceState = deviceStateCollector.collect(requestor);
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
}
