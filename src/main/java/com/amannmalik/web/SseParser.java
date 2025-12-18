package com.amannmalik.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Objects;

final class SseParser {

    private final ThrowingConsumer<SseEvent> onEvent;
    private final StringBuilder data = new StringBuilder(2048);

    private String eventName = "message";
    private String lastEventId = "";
    private Integer retryMillis;

    SseParser(ThrowingConsumer<SseEvent> onEvent) {
        this.onEvent = Objects.requireNonNull(onEvent, "onEvent");
    }

    void readAll(BufferedReader reader, State state) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (dispatchIfNeeded(state)) {
                    return;
                }
                continue;
            }
            if (line.startsWith(":")) {
                continue;
            }
            parseField(line);
        }
        dispatchIfNeeded(state);
    }

    private void parseField(String line) {
        var colon = line.indexOf(':');
        final String field;
        String value = "";
        if (colon == -1) {
            field = line;
        } else {
            field = line.substring(0, colon);
            value = line.substring(colon + 1);
            if (!value.isEmpty() && value.charAt(0) == ' ') {
                value = value.substring(1);
            }
        }
        switch (field) {
            case "data" -> data.append(value).append('\n');
            case "event" -> eventName = value.isBlank() ? "message" : value;
            case "id" -> {
                if (!value.contains("\u0000")) {
                    lastEventId = value;
                }
            }
            case "retry" -> retryMillis = parseRetry(value);
            default -> {
            }
        }
    }

    private Integer parseRetry(String value) {
        try {
            var parsed = Integer.parseInt(value.trim());
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean dispatchIfNeeded(State state) throws IOException {
        if (data.isEmpty()) {
            resetEvent();
            return false;
        }
        var payload = data.charAt(data.length() - 1) == '\n'
                ? data.substring(0, data.length() - 1)
                : data.toString();
        onEvent.accept(new SseEvent(eventName, payload, lastEventId, retryMillis));
        resetEvent();
        return state.shouldStop();
    }

    private void resetEvent() {
        data.setLength(0);
        eventName = "message";
        retryMillis = null;
    }

    interface ThrowingConsumer<T> {
        void accept(T value) throws IOException;
    }

    record SseEvent(String event,
                    String data,
                    String lastEventId,
                    Integer retryMillis) {
    }
}
