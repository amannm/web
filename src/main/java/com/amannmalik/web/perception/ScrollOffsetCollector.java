package com.amannmalik.web.perception;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ScrollOffsetCollector {

    private static final Logger LOG = System.getLogger(ScrollOffsetCollector.class.getName());
    private static final String SCROLL_OFFSET_SCRIPT = """
        (() => ({
          x: globalThis.scrollX ?? 0,
          y: globalThis.scrollY ?? 0
        }))()
        """;

    private final JsonBuilderFactory jsonFactory;

    ScrollOffsetCollector(JsonBuilderFactory jsonFactory) {
        this.jsonFactory = Objects.requireNonNull(jsonFactory, "jsonFactory must not be null");
    }

    Map<String, ScrollOffset> collect(CdpRequestor requestor,
                                      List<String> frameIds,
                                      int maxFrames,
                                      double devicePixelRatio) {
        Objects.requireNonNull(requestor, "requestor must not be null");
        Objects.requireNonNull(frameIds, "frameIds must not be null");
        if (maxFrames < 1) {
            return Map.of();
        }

        var normalizedScale = devicePixelRatio <= 0.0 ? 1.0 : devicePixelRatio;
        Map<String, ScrollOffset> offsets = new HashMap<>();
        var limit = Math.min(maxFrames, frameIds.size());
        for (var i = 0; i < limit; i++) {
            var frameId = frameIds.get(i);
            if (frameId == null || frameId.isBlank()) {
                continue;
            }
            try {
                var contextId = createIsolatedContext(requestor, frameId);
                if (contextId < 0) {
                    continue;
                }
                var params = jsonFactory.createObjectBuilder()
                    .add("contextId", contextId)
                    .add("expression", SCROLL_OFFSET_SCRIPT)
                    .add("returnByValue", true)
                    .add("awaitPromise", true)
                    .build();
                var result = requestor.request("Runtime.evaluate", params);
                var runtimeResult = result.getJsonObject("result");
                var value = runtimeResult != null ? runtimeResult.getJsonObject("value") : null;
                if (value == null) {
                    continue;
                }
                offsets.put(
                    frameId,
                    new ScrollOffset(
                        value.getJsonNumber("x").doubleValue() / normalizedScale,
                        value.getJsonNumber("y").doubleValue() / normalizedScale
                    )
                );
            } catch (RuntimeException error) {
                LOG.log(Level.DEBUG, "Failed to read scroll offset for frame {0}: {1}", frameId, error.toString());
            }
        }
        return offsets;
    }

    private int createIsolatedContext(CdpRequestor requestor, String frameId) {
        var params = jsonFactory.createObjectBuilder()
            .add("frameId", frameId)
            .add("worldName", "perception-scroll-offset")
            .add("grantUniveralAccess", false)
            .build();
        var response = requestor.request("Page.createIsolatedWorld", params);
        return response.getInt("executionContextId", -1);
    }

    record ScrollOffset(double x, double y) {
        JsonObject toJson(JsonBuilderFactory factory) {
            return factory.createObjectBuilder()
                .add("x", x)
                .add("y", y)
                .build();
        }
    }
}
