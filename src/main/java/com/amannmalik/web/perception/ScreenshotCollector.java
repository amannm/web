package com.amannmalik.web.perception;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class ScreenshotCollector {

    private final JsonBuilderFactory jsonFactory;

    ScreenshotCollector(JsonBuilderFactory jsonFactory) {
        this.jsonFactory = Objects.requireNonNull(jsonFactory, "jsonFactory must not be null");
    }

    Map<String, JsonObject> collect(CdpRequestor requestor, FrameTracker.FrameView frameView) {
        Objects.requireNonNull(requestor, "requestor must not be null");
        Objects.requireNonNull(frameView, "frameView must not be null");

        if (frameView.rootFrameIds().isEmpty()) {
            return Map.of();
        }

        var mainFrameId = frameView.rootFrameIds().getFirst();
        var screenshot = requestor.request("Page.captureScreenshot", jsonFactory.createObjectBuilder()
            .add("format", "png")
            .add("captureBeyondViewport", true)
            .build());

        var data = screenshot.getString("data", "");
        if (data.isBlank()) {
            return Map.of();
        }

        Map<String, JsonObject> screenshots = new HashMap<>();
        var builder = jsonFactory.createObjectBuilder()
            .add("frameId", mainFrameId)
            .add("format", "png")
            .add("data", data);

        if (screenshot.containsKey("view")) {
            builder.add("view", screenshot.getJsonObject("view"));
        }
        screenshots.put(mainFrameId, builder.build());
        return screenshots;
    }
}
