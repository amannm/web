package com.amannmalik.web.perception;

import jakarta.json.JsonObject;

final class JsonAccessors {

    private JsonAccessors() {
    }

    static JsonObject requiredObject(JsonObject parent, String key, String message) {
        var value = parent.getJsonObject(key);
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    static String requiredString(JsonObject object, String property) {
        if (!object.containsKey(property)) {
            throw new IllegalStateException("Missing required property: " + property);
        }
        return object.getString(property);
    }

    static double requiredNumber(JsonObject object, String property) {
        if (!object.containsKey(property)) {
            throw new IllegalStateException("Missing required property: " + property);
        }
        return object.getJsonNumber(property).doubleValue();
    }

    static boolean requiredBoolean(JsonObject object, String property) {
        if (!object.containsKey(property)) {
            throw new IllegalStateException("Missing required property: " + property);
        }
        return object.getBoolean(property);
    }

    static String optionalString(JsonObject object, String property) {
        return object.containsKey(property) ? object.getString(property, "") : "";
    }
}
