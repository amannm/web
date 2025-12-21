package com.amannmalik.web;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

final class State {

    private final StringBuilder fullText;
    private final Consumer<String> onTextDelta;
    private final Consumer<JsonObject> onEvent;
    private final Map<String, JsonObject> outputItems = new LinkedHashMap<>();
    private final Map<String, JsonObject> reasoningItems = new LinkedHashMap<>();
    private final ContentRegistry contentRegistry = new ContentRegistry();
    private final ToolCallAssembler toolCallAssembler = new ToolCallAssembler();
    private final SequenceValidator sequenceValidator = new SequenceValidator();
    private final TerminalTracker terminalTracker = new TerminalTracker();
    private PendingToolCall pendingToolCall;

    State(StringBuilder fullText, Consumer<String> onTextDelta, Consumer<JsonObject> onEvent) {
        this.fullText = fullText;
        this.onTextDelta = onTextDelta;
        this.onEvent = onEvent;
    }

    boolean shouldStop() {
        return pendingToolCall != null || terminalTracker.isDone();
    }

    Outcome toOutcome() {
        if (pendingToolCall != null) {
            return new Outcome.ToolCall(pendingToolCall, outputItems, reasoningItems);
        }
        return new Outcome.Completed(outputItems, reasoningItems);
    }

    void throwIfUnsuccessful() throws IOException {
        terminalTracker.throwIfUnsuccessful();
    }

    void handleData(String dataJson) throws IOException {
        var evt = parseEvent(dataJson);
        sequenceValidator.validate(evt);
        if (onEvent != null) {
            onEvent.accept(evt);
        }
        switch (ResponseEventType.from(evt.getString("type", ""))) {
            case OUTPUT_TEXT_DELTA, REFUSAL_DELTA -> applyDelta(evt);
            case REASONING_TEXT_DELTA, REASONING_SUMMARY_TEXT_DELTA,
                 REASONING_TEXT_DONE, REASONING_SUMMARY_TEXT_DONE -> contentRegistry.markDelta(contentKey(evt));
            case OUTPUT_TEXT_DONE -> applyFinalText(evt, "text");
            case REFUSAL_DONE -> applyFinalText(evt, "refusal");
            case CONTENT_PART_ADDED -> trackContentPart(evt);
            case CONTENT_PART_DONE -> finalizeContentPart(evt);
            case OUTPUT_ITEM_ADDED, OUTPUT_ITEM_DONE -> trackOutputItem(evt);
            case CUSTOM_TOOL_CALL_INPUT_DELTA -> bufferToolInput(evt);
            case CUSTOM_TOOL_CALL_INPUT_DONE -> finalizeToolCall(evt);
            case COMPLETED -> terminalTracker.markCompleted();
            case FAILED -> markFailed(evt);
            case INCOMPLETE -> markIncomplete(evt);
            case ERROR -> markTopLevelError(evt);
            case OUTPUT_AUDIO_DELTA, OUTPUT_AUDIO_DONE,
                 OUTPUT_AUDIO_TRANSCRIPT_DELTA, OUTPUT_AUDIO_TRANSCRIPT_DONE,
                 REASONING_SUMMARY_PART_ADDED, REASONING_SUMMARY_PART_DONE,
                 OUTPUT_TEXT_ANNOTATION_ADDED, CREATED, QUEUED, IN_PROGRESS,
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
            }
        }
    }

    void markIncompleteIfNeeded(String detail) {
        terminalTracker.markIncompleteIfPending(detail, pendingToolCall);
    }

    void markDoneSignal() {
        terminalTracker.markIncompleteIfPending("Received [DONE] before a terminal response event", pendingToolCall);
    }

    private void applyDelta(JsonObject evt) {
        var delta = evt.getString("delta", "");
        if (delta.isEmpty()) {
            return;
        }
        contentRegistry.markDelta(contentKey(evt));
        appendText(delta);
    }

    private void applyFinalText(JsonObject evt, String fieldName) {
        var text = evt.getString(fieldName, "");
        var key = contentKey(evt);
        appendFinalTextIfAllowed(key, text);
    }

    private void appendFinalTextIfAllowed(ContentKey key, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (!contentRegistry.shouldAppendFinal(key)) {
            return;
        }
        appendText(text);
    }

    private void appendText(String text) {
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
        contentRegistry.noteKind(key, kind);
        if (kind == ContentKind.OUTPUT_TEXT) {
            appendFinalTextIfAllowed(key, part.getString("text", ""));
        } else if (kind == ContentKind.REFUSAL) {
            appendFinalTextIfAllowed(key, part.getString("refusal", ""));
        }
    }

    private void finalizeContentPart(JsonObject evt) {
        var key = contentKey(evt);
        var part = evt.getJsonObject("part");
        if (key == null || part == null) {
            return;
        }
        var kind = contentRegistry.kindOf(key);
        if (kind == ContentKind.OUTPUT_TEXT) {
            appendFinalTextIfAllowed(key, part.getString("text", ""));
        } else if (kind == ContentKind.REFUSAL) {
            appendFinalTextIfAllowed(key, part.getString("refusal", ""));
        }
    }

    private void bufferToolInput(JsonObject evt) {
        var itemId = evt.getString("item_id", "");
        var delta = evt.getString("delta", "");
        if (itemId.isEmpty() || delta.isEmpty()) {
            return;
        }
        toolCallAssembler.appendDelta(itemId, delta);
    }

    private void finalizeToolCall(JsonObject evt) {
        var itemId = evt.getString("item_id", "");
        var rawInput = evt.getString("input", "");
        pendingToolCall = toolCallAssembler.finalizeToolCall(itemId, rawInput, outputItems);
    }

    private void markFailed(JsonObject evt) {
        var response = evt.getJsonObject("response");
        if (response != null) {
            var err = response.getJsonObject("error");
            if (err != null) {
                var code = err.getString("code", "");
                var msg = err.getString("message", "");
                var detail = msg.isBlank() ? code : code.isBlank() ? msg : code + ": " + msg;
                terminalTracker.markFailed(detail);
                return;
            }
        }
        terminalTracker.markFailed(evt.toString());
    }

    private void markTopLevelError(JsonObject evt) {
        var code = evt.getString("code", "");
        var msg = evt.getString("message", "");
        var detail = msg.isBlank() ? code : code.isBlank() ? msg : code + ": " + msg;
        terminalTracker.markFailed(detail.isEmpty() ? evt.toString() : detail);
    }

    private void markIncomplete(JsonObject evt) {
        var response = evt.getJsonObject("response");
        if (response != null) {
            var incomplete = response.getJsonObject("incomplete_details");
            if (incomplete != null) {
                var reason = incomplete.getString("reason", "");
                terminalTracker.markIncomplete(reason.isEmpty() ? evt.toString() : reason);
                return;
            }
        }
        terminalTracker.markIncomplete(evt.toString());
    }

    private static JsonObject parseEvent(String dataJson) throws IOException {
        try (var r = Json.createReader(new StringReader(dataJson))) {
            return r.readObject();
        } catch (RuntimeException parseErr) {
            throw new IOException("Malformed SSE payload: " + dataJson, parseErr);
        }
    }

    private static ContentKey contentKey(JsonObject evt) {
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

    record PendingToolCall(String name, String callId, String input) {

        PendingToolCall {
            name = Objects.requireNonNull(name, "name");
            callId = Objects.requireNonNull(callId, "callId");
            input = input == null ? "" : input;
        }
    }

    private static final class ToolCallAssembler {

        private final Map<String, StringBuilder> toolInputs = new LinkedHashMap<>();

        private void appendDelta(String itemId, String delta) {
            toolInputs
                    .computeIfAbsent(itemId, _ -> new StringBuilder(256))
                    .append(delta);
        }

        private PendingToolCall finalizeToolCall(String itemId, String rawInput, Map<String, JsonObject> items) {
            var safeItemId = itemId == null ? "" : itemId;
            var input = rawInput.isEmpty() && toolInputs.containsKey(safeItemId)
                    ? toolInputs.get(safeItemId).toString()
                    : rawInput;
            toolInputs.remove(safeItemId);
            var item = items.getOrDefault(safeItemId, Json.createObjectBuilder().build());
            var name = item.getString("name", "");
            var callId = item.getString("call_id", "");
            return new PendingToolCall(
                    name.isBlank() ? "cdp_command" : name,
                    callId.isBlank() ? fallbackCallId(safeItemId) : callId,
                    input
            );
        }
    }

    private static final class ContentRegistry {

        private final Map<ContentKey, ContentState> contentStates = new LinkedHashMap<>();

        private void noteKind(ContentKey key, ContentKind kind) {
            if (key == null) {
                return;
            }
            contentStates.merge(key, new ContentState(kind, false),
                    (existing, replacement) -> new ContentState(kind, existing.hasDelta));
        }

        private void markDelta(ContentKey key) {
            if (key == null) {
                return;
            }
            contentStates.merge(key, ContentState.UNKNOWN,
                    (existing, ignored) -> new ContentState(existing.kind, true));
        }

        private boolean shouldAppendFinal(ContentKey key) {
            if (key == null) {
                return true;
            }
            return !contentStates.getOrDefault(key, ContentState.UNKNOWN).hasDelta;
        }

        private ContentKind kindOf(ContentKey key) {
            if (key == null) {
                return ContentKind.UNKNOWN;
            }
            return contentStates.getOrDefault(key, ContentState.UNKNOWN).kind;
        }

        private record ContentState(ContentKind kind, boolean hasDelta) {

            private static final ContentState UNKNOWN = new ContentState(ContentKind.UNKNOWN, false);
        }
    }

    private static final class SequenceValidator {

        private Long nextSequenceNumber;

        private void validate(JsonObject evt) throws IOException {
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
    }

    private static final class TerminalTracker {

        private TerminalState state = TerminalState.IN_PROGRESS;
        private String failureDetail = "";
        private String incompleteDetail = "";

        private boolean isDone() {
            return state.isDone();
        }

        private void markCompleted() {
            state = TerminalState.COMPLETED;
        }

        private void markFailed(String detail) {
            state = TerminalState.FAILED;
            if (detail != null && !detail.isBlank()) {
                failureDetail = detail;
            }
        }

        private void markIncomplete(String detail) {
            state = TerminalState.INCOMPLETE;
            if (detail != null && !detail.isBlank()) {
                incompleteDetail = detail;
            }
        }

        private void markIncompleteIfPending(String detail, PendingToolCall pending) {
            if (pending != null || state.isDone()) {
                return;
            }
            markIncomplete(detail);
        }

        private void throwIfUnsuccessful() throws IOException {
            if (state == TerminalState.FAILED) {
                var message = failureDetail.isBlank() ? "response failed" : failureDetail;
                throw new IOException("OpenAI response failed: " + message);
            }
            if (state == TerminalState.INCOMPLETE) {
                var message = incompleteDetail.isBlank() ? "response incomplete" : incompleteDetail;
                throw new IOException("OpenAI response incomplete: " + message);
            }
        }
    }

    private record ContentKey(String itemId, int contentIndex) {

        ContentKey {
            Objects.requireNonNull(itemId, "itemId");
            if (itemId.isBlank()) {
                throw new IllegalArgumentException("itemId cannot be blank");
            }
            if (contentIndex < 0) {
                throw new IllegalArgumentException("contentIndex must be non-negative");
            }
        }
    }

    private enum TerminalState {
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        INCOMPLETE;

        private boolean isDone() {
            return this != IN_PROGRESS;
        }
    }

    private enum ContentKind {
        OUTPUT_TEXT,
        REFUSAL,
        REASONING_TEXT,
        REASONING_SUMMARY_TEXT,
        UNKNOWN;

        private static ContentKind from(String type) {
            return switch (type) {
                case "output_text" -> OUTPUT_TEXT;
                case "refusal" -> REFUSAL;
                case "reasoning_text" -> REASONING_TEXT;
                case "reasoning_summary_text" -> REASONING_SUMMARY_TEXT;
                default -> UNKNOWN;
            };
        }
    }

    private enum ResponseEventType {

        OUTPUT_TEXT_DELTA("response.output_text.delta"),
        OUTPUT_TEXT_DONE("response.output_text.done"),
        OUTPUT_TEXT_ANNOTATION_ADDED("response.output_text.annotation.added"),

        REFUSAL_DELTA("response.refusal.delta"),
        REFUSAL_DONE("response.refusal.done"),

        REASONING_TEXT_DELTA("response.reasoning_text.delta"),
        REASONING_TEXT_DONE("response.reasoning_text.done"),
        REASONING_SUMMARY_TEXT_DELTA("response.reasoning_summary_text.delta"),
        REASONING_SUMMARY_TEXT_DONE("response.reasoning_summary_text.done"),

        CONTENT_PART_ADDED("response.content_part.added"),
        CONTENT_PART_DONE("response.content_part.done"),

        OUTPUT_ITEM_ADDED("response.output_item.added"),
        OUTPUT_ITEM_DONE("response.output_item.done"),

        REASONING_SUMMARY_PART_ADDED("response.reasoning_summary_part.added"),
        REASONING_SUMMARY_PART_DONE("response.reasoning_summary_part.done"),

        CUSTOM_TOOL_CALL_INPUT_DELTA("response.custom_tool_call_input.delta"),
        CUSTOM_TOOL_CALL_INPUT_DONE("response.custom_tool_call_input.done"),

        OUTPUT_AUDIO_DELTA("response.output_audio.delta"),
        OUTPUT_AUDIO_DONE("response.output_audio.done"),
        OUTPUT_AUDIO_TRANSCRIPT_DELTA("response.output_audio_transcript.delta"),
        OUTPUT_AUDIO_TRANSCRIPT_DONE("response.output_audio_transcript.done"),

        COMPLETED("response.completed"),
        FAILED("response.failed"),
        INCOMPLETE("response.incomplete"),
        CREATED("response.created"),
        QUEUED("response.queued"),
        IN_PROGRESS("response.in_progress"),
        ERROR("error"),

        FILE_SEARCH("response.file_search_call.in_progress", true),
        FILE_SEARCH_SEARCHING("response.file_search_call.searching", true),
        FILE_SEARCH_COMPLETED("response.file_search_call.completed", true),

        WEB_SEARCH("response.web_search_call.in_progress", true),
        WEB_SEARCH_SEARCHING("response.web_search_call.searching", true),
        WEB_SEARCH_COMPLETED("response.web_search_call.completed", true),

        IMAGE_GEN_IN_PROGRESS("response.image_generation_call.in_progress", true),
        IMAGE_GEN_GENERATING("response.image_generation_call.generating", true),
        IMAGE_GEN_PARTIAL("response.image_generation_call.partial_image", true),
        IMAGE_GEN_COMPLETED("response.image_generation_call.completed", true),

        CODE_INTERPRETER_IN_PROGRESS("response.code_interpreter_call.in_progress", true),
        CODE_INTERPRETER_INTERPRETING("response.code_interpreter_call.interpreting", true),
        CODE_INTERPRETER_COMPLETED("response.code_interpreter_call.completed", true),
        CODE_INTERPRETER_CODE_DELTA("response.code_interpreter_call_code.delta", true),
        CODE_INTERPRETER_CODE_DONE("response.code_interpreter_call_code.done", true),

        MCP_CALL_IN_PROGRESS("response.mcp_call.in_progress", true),
        MCP_CALL_COMPLETED("response.mcp_call.completed", true),
        MCP_CALL_FAILED("response.mcp_call.failed", true),
        MCP_CALL_ARGUMENTS_DELTA("response.mcp_call_arguments.delta", true),
        MCP_CALL_ARGUMENTS_DONE("response.mcp_call_arguments.done", true),
        MCP_LIST_TOOLS_IN_PROGRESS("response.mcp_list_tools.in_progress", true),
        MCP_LIST_TOOLS_COMPLETED("response.mcp_list_tools.completed", true),
        MCP_LIST_TOOLS_FAILED("response.mcp_list_tools.failed", true),

        FUNCTION_CALL_ARGUMENTS_DELTA("response.function_call_arguments.delta", true),
        FUNCTION_CALL_ARGUMENTS_DONE("response.function_call_arguments.done", true),

        UNKNOWN("<unknown>", true);

        private static final Map<String, ResponseEventType> WIRE_NAME_LOOKUP = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(t -> t.wireName, Function.identity()));

        private final String wireName;

        ResponseEventType(String wireName) {
            this(wireName, false);
        }

        ResponseEventType(String wireName, boolean ignorable) {
            this.wireName = wireName;
        }

        private static ResponseEventType from(String rawType) {
            if (rawType == null || rawType.isBlank()) {
                return UNKNOWN;
            }
            return WIRE_NAME_LOOKUP.getOrDefault(rawType, UNKNOWN);
        }
    }
}
