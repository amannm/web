package com.amannmalik.web;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

final class ResponsesStream {

    record PendingToolCall(String name, String callId, String input) {}

    record Outcome(Optional<PendingToolCall> pendingToolCall,
                   Map<String, JsonObject> reasoningItems) {
        Outcome {
            reasoningItems = Map.copyOf(reasoningItems);
        }
    }

    private enum TerminalState {
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        INCOMPLETE;

        boolean isDone() {
            return this != IN_PROGRESS;
        }
    }

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
        var dataBuf = new StringBuilder(2048);
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (!dataBuf.isEmpty()) {
                    state.handleData(dataBuf.toString());
                    dataBuf.setLength(0);
                    if (state.shouldStop()) {
                        return;
                    }
                }
                continue;
            }
            if (line.startsWith(":")) {
                continue; // comment / heartbeat
            }
            if (line.startsWith("data:")) {
                var data = line.substring("data:".length()).trim();
                if ("[DONE]".equals(data)) {
                    state.markDoneSignal();
                    return;
                }
                if (!data.isEmpty()) {
                    if (!dataBuf.isEmpty()) {
                        dataBuf.append('\n');
                    }
                    dataBuf.append(data);
                }
            }
        }
        if (!dataBuf.isEmpty()) {
            state.handleData(dataBuf.toString());
        }
        state.markIncompleteIfNeeded("SSE stream ended without terminal event");
    }

    private static final class State {
        private final StringBuilder fullText;
        private final Consumer<String> onTextDelta;
        private final Consumer<JsonObject> onEvent;

        private final Map<String, JsonObject> outputItems = new LinkedHashMap<>();
        private final Map<String, JsonObject> reasoningItems = new LinkedHashMap<>();
        private final Map<String, StringBuilder> toolInputs = new LinkedHashMap<>();

        private PendingToolCall pendingToolCall;
        private TerminalState terminal = TerminalState.IN_PROGRESS;
        private String failureDetail = "";
        private String incompleteDetail = "";
        private Long nextSequenceNumber;
        private boolean sawDone;

        State(StringBuilder fullText, Consumer<String> onTextDelta, Consumer<JsonObject> onEvent) {
            this.fullText = fullText;
            this.onTextDelta = onTextDelta;
            this.onEvent = onEvent;
        }

        boolean shouldStop() {
            return pendingToolCall != null || terminal.isDone();
        }

        Outcome toOutcome() {
            return new Outcome(Optional.ofNullable(pendingToolCall), reasoningItems);
        }

        void throwIfUnsuccessful() throws IOException {
            if (terminal == TerminalState.FAILED) {
                throw new IOException("OpenAI response failed: " + failureDetail);
            }
            if (terminal == TerminalState.INCOMPLETE) {
                throw new IOException("OpenAI response incomplete: " + incompleteDetail);
            }
        }

        void handleData(String dataJson) throws IOException {
            JsonObject evt;
            try (var r = Json.createReader(new java.io.StringReader(dataJson))) {
                evt = r.readObject();
            } catch (RuntimeException parseErr) {
                throw new IOException("Malformed SSE payload: " + dataJson, parseErr);
            }
            validateSequence(evt);
            if (onEvent != null) {
                onEvent.accept(evt);
            }
            var type = evt.getString("type", "");
            switch (type) {
                case "response.output_text.delta", "response.refusal.delta" -> applyDelta(evt);
                case "response.output_item.added", "response.output_item.done" -> trackOutputItem(evt);
                case "response.custom_tool_call_input.delta" -> bufferToolInput(evt);
                case "response.custom_tool_call_input.done" -> finalizeToolCall(evt);
                case "response.completed" -> terminal = TerminalState.COMPLETED;
                case "response.failed" -> markFailed(evt);
                case "response.incomplete" -> markIncomplete(evt);
                default -> { /* deliberately ignore other lifecycle events */ }
            }
        }

        void markDoneSignal() {
            sawDone = true;
            markIncompleteIfNeeded("Received [DONE] before a terminal response event");
        }

        private void applyDelta(JsonObject evt) {
            var delta = evt.getString("delta", "");
            if (delta.isEmpty()) {
                return;
            }
            fullText.append(delta);
            if (onTextDelta != null) {
                onTextDelta.accept(delta);
            }
        }

        private void trackOutputItem(JsonObject evt) {
            var item = evt.getJsonObject("item");
            if (item == null) {
                return;
            }
            var id = item.getString("id", "");
            if (id.isEmpty()) {
                return;
            }
            outputItems.put(id, item);
            if ("reasoning".equals(item.getString("type", ""))) {
                reasoningItems.put(id, item);
            }
        }

        private void bufferToolInput(JsonObject evt) {
            var itemId = evt.getString("item_id", "");
            var delta = evt.getString("delta", "");
            if (itemId.isEmpty() || delta.isEmpty()) {
                return;
            }
            toolInputs.computeIfAbsent(itemId, k -> new StringBuilder(256)).append(delta);
        }

        private void finalizeToolCall(JsonObject evt) {
            var itemId = evt.getString("item_id", "");
            var rawInput = evt.getString("input", "");
            var input = rawInput.isEmpty() && toolInputs.containsKey(itemId)
                    ? toolInputs.get(itemId).toString()
                    : rawInput;
            var item = outputItems.getOrDefault(itemId, Json.createObjectBuilder().build());
            var name = item.getString("name", "");
            var callId = item.getString("call_id", "");
            pendingToolCall = new PendingToolCall(
                    name.isBlank() ? "cdp_command" : name,
                    callId.isBlank() ? fallbackCallId(itemId) : callId,
                    input
            );
        }

        private void markFailed(JsonObject evt) {
            terminal = TerminalState.FAILED;
            var response = evt.getJsonObject("response");
            if (response != null) {
                var err = response.getJsonObject("error");
                if (err != null) {
                    var code = err.getString("code", "");
                    var msg = err.getString("message", "");
                    failureDetail = msg.isBlank() ? code : code.isBlank() ? msg : code + ": " + msg;
                    return;
                }
            }
            failureDetail = evt.toString();
        }

        private void markIncomplete(JsonObject evt) {
            terminal = TerminalState.INCOMPLETE;
            var response = evt.getJsonObject("response");
            if (response != null) {
                var incomplete = response.getJsonObject("incomplete_details");
                if (incomplete != null) {
                    incompleteDetail = incomplete.getString("reason", "");
                }
            }
            if (incompleteDetail.isEmpty()) {
                incompleteDetail = evt.toString();
            }
        }

        void markIncompleteIfNeeded(String detail) {
            if (pendingToolCall != null || terminal.isDone()) {
                return; // tool-call hand-off or already finalized
            }
            terminal = TerminalState.INCOMPLETE;
            if (incompleteDetail.isEmpty()) {
                incompleteDetail = detail;
            }
        }

        private void validateSequence(JsonObject evt) throws IOException {
            var val = evt.get("sequence_number");
            if (!(val instanceof JsonNumber num) || !num.isIntegral()) {
                throw new IOException("Streaming event missing integral sequence_number: " + evt);
            }
            var seq = num.longValue();
            if (nextSequenceNumber == null) {
                nextSequenceNumber = seq + 1;
                return;
            }
            if (seq != nextSequenceNumber) {
                throw new IOException("Out-of-order streaming event: expected sequence " + nextSequenceNumber + " but got " + seq + " for type " + evt.getString("type", "<unknown>"));
            }
            nextSequenceNumber = seq + 1;
        }

        private static String fallbackCallId(String itemId) {
            return itemId == null || itemId.isBlank() ? "call_unknown" : itemId;
        }
    }
}
