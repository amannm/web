package com.amannmalik.web;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import java.io.IOException;
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

public final class OpenAiGateway {

    private static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");
    private static final int MAX_TOOL_CALLS = 8;
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);
    private static final String CDP_TOOL_NAME = "cdp_command";
    private static final String TOOL_USE_INSTRUCTION = "Use the `cdp_command` custom tool to drive the browser. Call a tool before emitting any user-visible text and wait for its output before continuing.";

    private final HttpClient http;
    private final String openAiApiKey;
    private final String model;
    private final ResponsesStream responsesStream;

    public OpenAiGateway() {
        this(System.getenv("OPENAI_API_KEY"),
                System.getenv().getOrDefault("OPENAI_MODEL", "gpt-5"),
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(20))
                        .build(),
                new ResponsesStream());
    }

    OpenAiGateway(String openAiApiKey, String model, HttpClient http, ResponsesStream responsesStream) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required");
        }
        this.openAiApiKey = openAiApiKey;
        this.model = Objects.requireNonNullElse(model, "gpt-5");
        this.http = Objects.requireNonNull(http, "http");
        this.responsesStream = Objects.requireNonNull(responsesStream, "responsesStream");
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
            if (outcome instanceof ResponsesStream.CompletedOutcome(var outputItems, var reasoningItems)) {
                inputItems.addAll(outputItems.values());
                inputItems.addAll(reasoningItems.values());
                return fullText.toString();
            }
            if (!(outcome instanceof ResponsesStream.ToolCallOutcome(var pendingToolCall, var outputItems, var reasoningItems))) {
                throw new IOException("Unexpected ResponsesStream outcome: " + outcome.getClass().getSimpleName());
            }
            if (!CDP_TOOL_NAME.equals(pendingToolCall.name())) {
                throw new IOException("Unexpected custom tool call: " + pendingToolCall.name());
            }
            inputItems.addAll(outputItems.values());
            inputItems.addAll(reasoningItems.values());
            String toolOutput;
            try {
                toolOutput = cdp.sendRaw(pendingToolCall.input()).toString();
            } catch (RuntimeException e) {
                toolOutput = Json.createObjectBuilder()
                        .add("error", String.valueOf(e.getMessage()))
                        .build()
                        .toString();
            }
            inputItems.add(toCustomToolCallItem(pendingToolCall));
            inputItems.add(toCustomToolOutputItem(pendingToolCall, toolOutput));
        }
        throw new IOException("Exceeded max tool calls (" + MAX_TOOL_CALLS + ") without completing a response");
    }

    private ResponsesStream.Outcome streamOnce(List<JsonObject> inputItems,
                                               JsonArray tools,
                                               StringBuilder fullText,
                                               Consumer<String> onTextDelta,
                                               Consumer<JsonObject> onEvent) throws IOException, InterruptedException {
        var bodyBuilder = Json.createObjectBuilder()
                .add("model", model)
                .add("input", toArray(inputItems))
                .add("stream", true)
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
                                "Send exactly ONE Chrome DevTools Protocol (CDP) command as a raw JSON object string. " +
                                        "The JSON should include: method (string), optional params (object), and optional sessionId (string). " +
                                        "Do NOT include an id; the client will inject it. The tool returns the raw JSON response.")
                        .add("format", Json.createObjectBuilder()
                                .add("type", "grammar")
                                .add("syntax", "regex")
                                .add("definition", "^\\\\{[\\\\s\\\\S]*\\\\}$")
                                .build())
                        .build())
                .build();
    }

    private static JsonObject toCustomToolCallItem(ResponsesStream.PendingToolCall call) {
        return Json.createObjectBuilder()
                .add("type", "custom_tool_call")
                .add("call_id", call.callId())
                .add("name", call.name())
                .add("input", call.input())
                .build();
    }

    private static JsonObject toCustomToolOutputItem(ResponsesStream.PendingToolCall call, String output) {
        return Json.createObjectBuilder()
                .add("type", "custom_tool_call_output")
                .add("call_id", call.callId())
                .add("output", output)
                .build();
    }
}
