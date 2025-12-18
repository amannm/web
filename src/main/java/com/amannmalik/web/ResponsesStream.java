package com.amannmalik.web;

import jakarta.json.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

final class ResponsesStream {

    Outcome read(InputStream stream,
                 StringBuilder fullText,
                 Consumer<String> onTextDelta,
                 Consumer<JsonObject> onEvent) throws IOException {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(fullText, "fullText");
        var state = new State(fullText, onTextDelta, onEvent);
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            parseSse(reader, state);
        }
        state.throwIfUnsuccessful();
        return state.toOutcome();
    }

    private static void parseSse(BufferedReader reader, State state) throws IOException {
        var parser = new SseParser(evt -> {
            if (evt.data().isEmpty()) {
                return;
            }
            if ("[DONE]".equals(evt.data())) {
                state.markDoneSignal();
                return;
            }
            state.handleData(evt.data());
        });
        parser.readAll(reader, state);
        state.markIncompleteIfNeeded("SSE stream ended without terminal event");
    }

}
