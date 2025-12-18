package com.amannmalik.web;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class OpenAiClient {

    private static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");
    private static final int MAX_TOOL_CALLS = 8;
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);
    private static final String CDP_TOOL_NAME = "cdp_command";
    private static final String TOOL_USE_INSTRUCTION = "Use the `cdp_command` custom tool to drive the browser. Call a tool before emitting any user-visible text and wait for its output before continuing.";
    private static final String DEFAULT_BETA_HEADER_VALUE = "responses=v1";

    private final HttpClient http;
    private final String openAiApiKey;
    private final String model;
    private final ResponsesStream responsesStream;
    private final String openAiBetaHeader;

    public OpenAiClient() {
        this(System.getenv("OPENAI_API_KEY"),
                System.getenv().getOrDefault("OPENAI_MODEL", "gpt-5"),
                System.getenv("OPENAI_BETA_RESPONSES"),
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(20))
                        .build(),
                new ResponsesStream());
    }

    OpenAiClient(String openAiApiKey,
                 String model,
                 String openAiBetaHeader,
                 HttpClient http,
                 ResponsesStream responsesStream) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required");
        }
        this.openAiApiKey = openAiApiKey;
        this.model = Objects.requireNonNullElse(model, "gpt-5");
        this.http = Objects.requireNonNull(http, "http");
        this.responsesStream = Objects.requireNonNull(responsesStream, "responsesStream");
        this.openAiBetaHeader = normalizeBetaHeader(openAiBetaHeader);
    }

    public String streamResponseTextViaCdp(String prompt,
                                           CdpClient cdp,
                                           Consumer<String> onTextDelta,
                                           Consumer<JsonObject> onEvent) throws IOException, InterruptedException {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must be non-blank");
        }
        Objects.requireNonNull(cdp, "cdp");
        var tools = cdpToolDefinition();
        var inputItems = new ArrayList<JsonObject>();
        inputItems.add(userPrompt(prompt));
        var fullText = new StringBuilder(4096);
        for (var toolCalls = 0; toolCalls < MAX_TOOL_CALLS; toolCalls++) {
            var outcome = streamOnce(inputItems, tools, fullText, onTextDelta, onEvent);
            if (outcome instanceof Outcome.Completed(var outputItems, var reasoningItems)) {
                inputItems.addAll(outputItems.values());
                inputItems.addAll(reasoningItems.values());
                return fullText.toString();
            }
            if (!(outcome instanceof Outcome.ToolCall(var pendingToolCall, var outputItems, var reasoningItems))) {
                throw new IOException("Unexpected ResponsesStream outcome: " + outcome.getClass().getSimpleName());
            }
            if (!CDP_TOOL_NAME.equals(pendingToolCall.name())) {
                throw new IOException("Unexpected custom tool call: " + pendingToolCall.name());
            }
            inputItems.addAll(outputItems.values());
            inputItems.addAll(reasoningItems.values());
            var toolOutput = executeCdpToolCall(cdp, pendingToolCall);
            inputItems.add(toCustomToolCallItem(pendingToolCall));
            inputItems.add(toCustomToolOutputItem(pendingToolCall, toolOutput));
        }
        throw new IOException("Exceeded max tool calls (" + MAX_TOOL_CALLS + ") without completing a response");
    }

    private Outcome streamOnce(List<JsonObject> inputItems,
                               JsonArray tools,
                               StringBuilder fullText,
                               Consumer<String> onTextDelta,
                               Consumer<JsonObject> onEvent) throws IOException, InterruptedException {
        var bodyBuilder = Json.createObjectBuilder()
                .add("model", model)
                .add("input", toArray(inputItems))
                .add("stream", true)
                .add("max_tool_calls", MAX_TOOL_CALLS)
                .add("tool_choice", "auto")
                .add("parallel_tool_calls", false);
        if (tools != null) {
            bodyBuilder.add("tools", tools);
        }
        bodyBuilder.add("instructions", TOOL_USE_INSTRUCTION);
        var req = HttpRequest.newBuilder()
                .uri(RESPONSES_URI)
                .timeout(STREAM_TIMEOUT)
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .headers(optionalBetaHeader())
                .POST(HttpRequest.BodyPublishers.ofString(bodyBuilder.build().toString(), StandardCharsets.UTF_8))
                .build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        var status = resp.statusCode();
        if (status < 200 || status >= 300) {
            var err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("OpenAI Responses API failed: HTTP " + status + " — " + err);
        }
        try (var body = resp.body()) {
            return responsesStream.read(body, fullText, onTextDelta, onEvent);
        }
    }

    private String executeCdpToolCall(CdpClient cdp, State.PendingToolCall pendingToolCall) {
        return withFailureGuard("cdp tool call", () -> {
            var command = CdpCommand.fromJson(pendingToolCall.input());
            return cdp.send(command).toString();
        });
    }

    private static String withFailureGuard(String label, SupplierWithIOException action) {
        try {
            return action.get();
        } catch (Exception e) {
            var message = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            return Json.createObjectBuilder()
                    .add("error", Json.createObjectBuilder()
                            .add("where", label)
                            .add("message", message))
                    .build()
                    .toString();
        }
    }

    @FunctionalInterface
    private interface SupplierWithIOException {
        String get() throws Exception;
    }

    private static JsonObject userPrompt(String prompt) {
        return Json.createObjectBuilder()
                .add("role", "user")
                .add("content", prompt)
                .build();
    }

    private static JsonArray toArray(List<JsonObject> items) {
        var arr = Json.createArrayBuilder();
        for (var item : items) {
            arr.add(item);
        }
        return arr.build();
    }

    private static JsonArray cdpToolDefinition() {
        return Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                        .add("type", "custom")
                        .add("name", CDP_TOOL_NAME)
                        .add("description",
                                """
                                        Issue exactly one Chrome DevTools Protocol command. Format: {"method": "...", "params": {...}, "sessionId": "..."}.
                                        - "method" is required and must match a CDP method name (see https://chromedevtools.github.io/devtools-protocol/).
                                        - "params" is optional and must be an object when present.
                                        - "sessionId" is optional and required when targeting a specific session (e.g., Target.attachToTarget).
                                        Do NOT include an "id"; the client injects it. Submit only one command per tool call and wait for the result before continuing."""
                        )
                        .add("input_schema", Json.createObjectBuilder()
                                .add("type", "json_schema")
                                .add("json_schema", Json.createObjectBuilder()
                                        .add("type", "object")
                                        .add("properties", Json.createObjectBuilder()
                                                .add("method", Json.createObjectBuilder()
                                                        .add("type", "string")
                                                        .add("minLength", 1))
                                                .add("params", Json.createObjectBuilder()
                                                        .add("type", "object"))
                                                .add("sessionId", Json.createObjectBuilder()
                                                        .add("type", "string")
                                                        .add("minLength", 1)))
                                        .add("required", Json.createArrayBuilder().add("method"))
                                        .add("additionalProperties", false)))
                        .build())
                .build();
    }

    private static JsonObject toCustomToolCallItem(State.PendingToolCall call) {
        return Json.createObjectBuilder()
                .add("type", "custom_tool_call")
                .add("call_id", call.callId())
                .add("name", call.name())
                .add("input", call.input())
                .build();
    }

    private static JsonObject toCustomToolOutputItem(State.PendingToolCall call, String output) {
        return Json.createObjectBuilder()
                .add("type", "custom_tool_call_output")
                .add("call_id", call.callId())
                .add("output", output)
                .build();
    }

    private String[] optionalBetaHeader() {
        if (openAiBetaHeader == null || openAiBetaHeader.isBlank()) {
            return new String[0];
        }
        return new String[]{"OpenAI-Beta", openAiBetaHeader};
    }

    private static String normalizeBetaHeader(String requested) {
        var trimmed = requested == null ? "" : requested.trim();
        return trimmed.isEmpty() ? DEFAULT_BETA_HEADER_VALUE : trimmed;
    }

    static final class ResponsesStream {

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
}
