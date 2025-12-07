package com.amannmalik.web.perception;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import java.util.Objects;

import static com.amannmalik.web.perception.JsonAccessors.requiredObject;

final class ViewportBuilder {

    private ViewportBuilder() {
    }

    static JsonObject build(JsonBuilderFactory jsonFactory, JsonObject layoutMetrics) {
        Objects.requireNonNull(jsonFactory, "jsonFactory must not be null");
        Objects.requireNonNull(layoutMetrics, "layoutMetrics must not be null");

        var layoutViewport = requiredObject(layoutMetrics, "layoutViewport", "layoutViewport missing from Page.getLayoutMetrics");
        var visualViewport = requiredObject(layoutMetrics, "visualViewport", "visualViewport missing from Page.getLayoutMetrics");
        var contentSize = requiredObject(layoutMetrics, "contentSize", "contentSize missing from Page.getLayoutMetrics");

        var layoutViewportJson = jsonFactory.createObjectBuilder()
            .add("x", numberOrDefault(layoutViewport, "x"))
            .add("y", numberOrDefault(layoutViewport, "y"))
            .add("width", numberOrDefault(layoutViewport, "width"))
            .add("height", numberOrDefault(layoutViewport, "height"))
            .build();

        var visualViewportJson = jsonFactory.createObjectBuilder()
            .add("offsetX", numberOrDefault(visualViewport, "offsetX"))
            .add("offsetY", numberOrDefault(visualViewport, "offsetY"))
            .add("pageX", numberOrDefault(visualViewport, "pageX"))
            .add("pageY", numberOrDefault(visualViewport, "pageY"))
            .add("width", numberOrDefault(visualViewport, "width"))
            .add("height", numberOrDefault(visualViewport, "height"))
            .add("scale", numberOrDefault(visualViewport, "scale", 1.0))
            .build();

        var contentSizeJson = jsonFactory.createObjectBuilder()
            .add("width", numberOrDefault(contentSize, "width"))
            .add("height", numberOrDefault(contentSize, "height"))
            .build();

        return jsonFactory.createObjectBuilder()
            .add("layoutViewport", layoutViewportJson)
            .add("visualViewport", visualViewportJson)
            .add("contentSize", contentSizeJson)
            .build();
    }

    private static double numberOrDefault(JsonObject object, String property) {
        return numberOrDefault(object, property, 0.0);
    }

    private static double numberOrDefault(JsonObject object, String property, double defaultValue) {
        if (object.containsKey(property) && object.getJsonNumber(property) != null) {
            return object.getJsonNumber(property).doubleValue();
        }
        return defaultValue;
    }
}
