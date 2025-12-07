package com.amannmalik.web.perception;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.util.Map;
import java.util.Objects;

import static com.amannmalik.web.perception.JsonAccessors.optionalString;
import static com.amannmalik.web.perception.JsonAccessors.requiredBoolean;
import static com.amannmalik.web.perception.JsonAccessors.requiredNumber;

final class DeviceStateCollector {

    private static final String DEVICE_STATE_EXPRESSION = """
        (() => {
          const media = (query) => globalThis.matchMedia ? globalThis.matchMedia(query).matches : false;
          const orientation = globalThis.screen?.orientation?.type ?? "portraitPrimary";
          const colorScheme = media("(prefers-color-scheme: dark)") ? "dark"
              : media("(prefers-color-scheme: light)") ? "light"
              : "no-preference";
          const reducedMotion = media("(prefers-reduced-motion: reduce)") ? "reduce" : "no-preference";
          return {
            width: globalThis.innerWidth ?? 0,
            height: globalThis.innerHeight ?? 0,
            deviceScaleFactor: globalThis.devicePixelRatio ?? 1,
            mobile: globalThis.navigator?.userAgentData?.mobile ?? /Mobi/i.test(globalThis.navigator?.userAgent ?? ""),
            userAgent: globalThis.navigator?.userAgent ?? "",
            locale: globalThis.navigator?.language ?? "",
            timezone: Intl.DateTimeFormat().resolvedOptions().timeZone ?? "",
            screenOrientation: orientation,
            visionDeficiency: "none",
            reducedMotion,
            colorScheme
          };
        })()
        """;

    private final JsonBuilderFactory jsonFactory;

    DeviceStateCollector(JsonBuilderFactory jsonFactory) {
        this.jsonFactory = Objects.requireNonNull(jsonFactory, "jsonFactory must not be null");
    }

    JsonObject collect(CdpRequestor requestor) {
        Objects.requireNonNull(requestor, "requestor must not be null");

        var params = jsonFactory.createObjectBuilder()
            .add("expression", DEVICE_STATE_EXPRESSION)
            .add("returnByValue", true)
            .add("awaitPromise", true)
            .build();

        var result = requestor.request("Runtime.evaluate", params);
        var runtimeResult = result.getJsonObject("result");
        var value = runtimeResult != null ? runtimeResult.get("value") : null;
        if (value == null || value.getValueType() != JsonValue.ValueType.OBJECT) {
            throw new IllegalStateException("Runtime.evaluate did not return an object value");
        }
        var device = value.asJsonObject();

        return jsonFactory.createObjectBuilder()
            .add("width", requiredNumber(device, "width"))
            .add("height", requiredNumber(device, "height"))
            .add("deviceScaleFactor", requiredNumber(device, "deviceScaleFactor"))
            .add("mobile", requiredBoolean(device, "mobile"))
            .add("userAgent", optionalString(device, "userAgent"))
            .add("locale", optionalString(device, "locale"))
            .add("timezone", optionalString(device, "timezone"))
            .add("screenOrientation", optionalString(device, "screenOrientation"))
            .add("visionDeficiency", optionalString(device, "visionDeficiency"))
            .add("reducedMotion", optionalString(device, "reducedMotion"))
            .add("colorScheme", optionalString(device, "colorScheme"))
            .build();
    }
}
