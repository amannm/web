package com.amannmalik.web;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

final class State {
    private final StringBuilder fullText;
    private final Consumer<String> onTextDelta;
    private final Consumer<JsonObject> onEvent;

    private final Map<String, JsonObject> outputItems = new LinkedHashMap<>();
    private final Map<String, JsonObject> reasoningItems = new LinkedHashMap<>();
    private final Map<String, StringBuilder> toolInputs = new LinkedHashMap<>();
    private final Map<ContentKey, Boolean> contentWithDelta = new LinkedHashMap<>();
    private final Map<ContentKey, ContentKind> contentKinds = new LinkedHashMap<>();

    private PendingToolCall pendingToolCall;
    private TerminalState terminal = TerminalState.IN_PROGRESS;
    private String failureDetail = "";
    private String incompleteDetail = "";
    private Long nextSequenceNumber;

    State(StringBuilder fullText, Consumer<String> onTextDelta, Consumer<JsonObject> onEvent) {
        this.fullText = fullText;
        this.onTextDelta = onTextDelta;
        this.onEvent = onEvent;
    }

    boolean shouldStop() {
        return pendingToolCall != null || terminal.isDone();
    }

    Outcome toOutcome() {
        if (pendingToolCall != null) {
            return new ToolCallOutcome(pendingToolCall, outputItems, reasoningItems);
        }
        return new CompletedOutcome(outputItems, reasoningItems);
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
        try (var r = Json.createReader(new StringReader(dataJson))) {
            evt = r.readObject();
        } catch (RuntimeException parseErr) {
            throw new IOException("Malformed SSE payload: " + dataJson, parseErr);
        }
        validateSequence(evt);
        if (onEvent != null) {
            onEvent.accept(evt);
        }
        switch (ResponseEventType.from(evt.getString("type", ""))) {
            case OUTPUT_TEXT_DELTA, REFUSAL_DELTA -> applyDelta(evt);
            case REASONING_TEXT_DELTA, REASONING_SUMMARY_TEXT_DELTA -> markContentWithDelta(evt);
            case OUTPUT_TEXT_DONE -> applyFinalText(evt, "text");
            case REFUSAL_DONE -> applyFinalText(evt, "refusal");
            case CONTENT_PART_ADDED -> trackContentPart(evt);
            case CONTENT_PART_DONE -> finalizeContentPart(evt);
            case OUTPUT_ITEM_ADDED, OUTPUT_ITEM_DONE -> trackOutputItem(evt);
            case CUSTOM_TOOL_CALL_INPUT_DELTA -> bufferToolInput(evt);
            case CUSTOM_TOOL_CALL_INPUT_DONE -> finalizeToolCall(evt);
            case COMPLETED -> terminal = TerminalState.COMPLETED;
            case FAILED -> markFailed(evt);
            case INCOMPLETE -> markIncomplete(evt);
            case ERROR -> markTopLevelError(evt);
            case OUTPUT_TEXT_ANNOTATION_ADDED, CREATED, QUEUED, IN_PROGRESS,
                    FILE_SEARCH, FILE_SEARCH_SEARCHING, FILE_SEARCH_COMPLETED,
                    WEB_SEARCH, WEB_SEARCH_SEARCHING, WEB_SEARCH_COMPLETED,
                    IMAGE_GEN_IN_PROGRESS, IMAGE_GEN_GENERATING, IMAGE_GEN_PARTIAL, IMAGE_GEN_COMPLETED,
                    CODE_INTERPRETER_IN_PROGRESS, CODE_INTERPRETER_INTERPRETING, CODE_INTERPRETER_COMPLETED,
                    CODE_INTERPRETER_CODE_DELTA, CODE_INTERPRETER_CODE_DONE,
                    MCP_CALL_IN_PROGRESS, MCP_CALL_COMPLETED, MCP_CALL_FAILED,
                    MCP_CALL_ARGUMENTS_DELTA, MCP_CALL_ARGUMENTS_DONE,
                    MCP_LIST_TOOLS_IN_PROGRESS, MCP_LIST_TOOLS_COMPLETED, MCP_LIST_TOOLS_FAILED,
                    FUNCTION_CALL_ARGUMENTS_DELTA, FUNCTION_CALL_ARGUMENTS_DONE,
                    UNKNOWN -> {
                // intentionally ignored; unknown events are tolerated to remain forward-compatible
            }
            case REASONING_TEXT_DONE, REASONING_SUMMARY_TEXT_DONE -> markContentWithDelta(evt);
        }
    }

    void markDoneSignal() {
        markIncompleteIfNeeded("Received [DONE] before a terminal response event");
    }

    private void applyDelta(JsonObject evt) {
        var delta = evt.getString("delta", "");
        if (delta.isEmpty()) {
            return;
        }
        markContentWithDelta(evt);
        fullText.append(delta);
        if (onTextDelta != null) {
            onTextDelta.accept(delta);
        }
    }

    private void applyFinalText(JsonObject evt, String fieldName) {
        var text = evt.getString(fieldName, "");
        if (text.isEmpty()) {
            return;
        }
        var key = contentKey(evt);
        appendFinalTextIfNeeded(key, text);
    }

    private void appendFinalTextIfNeeded(ContentKey key, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (key != null && Boolean.TRUE.equals(contentWithDelta.get(key))) {
            return;
        }
        fullText.append(text);
        if (onTextDelta != null) {
            onTextDelta.accept(text);
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

    private void trackContentPart(JsonObject evt) {
        var key = contentKey(evt);
        var part = evt.getJsonObject("part");
        if (key == null || part == null) {
            return;
        }
        var kind = ContentKind.from(part.getString("type", ""));
        contentKinds.put(key, kind);
        var text = part.getString("text", "");
        if (!text.isEmpty() && kind == ContentKind.OUTPUT_TEXT) {
            appendFinalTextIfNeeded(key, text);
        }
        var refusal = part.getString("refusal", "");
        if (!refusal.isEmpty() && kind == ContentKind.REFUSAL) {
            appendFinalTextIfNeeded(key, refusal);
        }
    }

    private void finalizeContentPart(JsonObject evt) {
        var key = contentKey(evt);
        var part = evt.getJsonObject("part");
        if (key == null || part == null) {
            return;
        }
        var kind = contentKinds.getOrDefault(key, ContentKind.UNKNOWN);
        if (kind == ContentKind.OUTPUT_TEXT) {
            appendFinalTextIfNeeded(key, part.getString("text", ""));
        } else if (kind == ContentKind.REFUSAL) {
            appendFinalTextIfNeeded(key, part.getString("refusal", ""));
        }
    }

    private void bufferToolInput(JsonObject evt) {
        var itemId = evt.getString("item_id", "");
        var delta = evt.getString("delta", "");
        if (itemId.isEmpty() || delta.isEmpty()) {
            return;
        }
        toolInputs
                .computeIfAbsent(itemId, k -> new StringBuilder(256))
                .append(delta);
    }

    private void finalizeToolCall(JsonObject evt) {
        var itemId = evt.getString("item_id", "");
        var rawInput = evt.getString("input", "");
        var input = rawInput.isEmpty() && toolInputs.containsKey(itemId)
                ? toolInputs.get(itemId).toString()
                : rawInput;
        toolInputs.remove(itemId);
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

    private void markTopLevelError(JsonObject evt) {
        terminal = TerminalState.FAILED;
        var code = evt.getString("code", "");
        var msg = evt.getString("message", "");
        failureDetail = msg.isBlank() ? code : code.isBlank() ? msg : code + ": " + msg;
        if (failureDetail.isEmpty()) {
            failureDetail = evt.toString();
        }
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
            return;
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

    private void markContentWithDelta(JsonObject evt) {
        var key = contentKey(evt);
        if (key != null) {
            contentWithDelta.put(key, Boolean.TRUE);
        }
    }

    private ContentKey contentKey(JsonObject evt) {
        var itemId = evt.getString("item_id", "");
        if (itemId.isEmpty()) {
            return null;
        }
        var contentIndex = evt.getInt("content_index", -1);
        if (contentIndex < 0) {
            return null;
        }
        return new ContentKey(itemId, contentIndex);
    }

    private static String fallbackCallId(String itemId) {
        return itemId == null || itemId.isBlank() ? "call_unknown" : itemId;
    }
}
