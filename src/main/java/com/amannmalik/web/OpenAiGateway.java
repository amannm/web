package com.amannmalik.web;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;


public class OpenAiGateway {

    private static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");
    private static final int MAX_TOOL_CALLS = 8;
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);

    private static final String CDP_TOOL_NAME = "cdp_command";

    private final HttpClient http;
    private final String openAiApiKey;
    private final String model;

    public OpenAiGateway() {
        this(System.getenv("OPENAI_API_KEY"),
                System.getenv().getOrDefault("OPENAI_MODEL", "gpt-5"),
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(20))
                        .build());
    }

    private OpenAiGateway(String openAiApiKey, String model, HttpClient http) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required");
        }
        this.openAiApiKey = openAiApiKey;
        this.model = Objects.requireNonNullElse(model, "gpt-5");
        this.http = Objects.requireNonNull(http, "http");
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
        inputItems.add(Json.createObjectBuilder()
                .add("role", "user")
                .add("content", prompt)
                .build());
        var full = new StringBuilder(4096);
        for (var toolCalls = 0; toolCalls < MAX_TOOL_CALLS; toolCalls++) {
            var st = new StreamState(full, onTextDelta, onEvent);
            var pending = streamOnce(inputItems, tools, "If you need to use tools, call the tool first and do not produce user-visible text until after tool outputs are provided.", st);
            // For reasoning models (e.g., GPT-5 / o-series), reasoning items returned alongside tool calls
            // must be replayed back to the API together with the tool call output.
            if (pending != null && !st.reasoningItemById.isEmpty()) {
                for (var ri : st.reasoningItemById.values()) {
                    inputItems.add(ri);
                }
            }
            if (pending == null) {
                return full.toString();
            }
            if (!CDP_TOOL_NAME.equals(pending.name)) {
                throw new IOException("Unexpected custom tool call: " + pending.name);
            }
            String toolOutput;
            try {
                toolOutput = cdp.sendRaw(pending.input).toString();
            } catch (RuntimeException e) {
                toolOutput = Json.createObjectBuilder()
                        .add("error", String.valueOf(e.getMessage()))
                        .build()
                        .toString();
            }
            inputItems.add(Json.createObjectBuilder()
                    .add("type", "custom_tool_call")
                    .add("call_id", pending.callId)
                    .add("name", pending.name)
                    .add("input", pending.input)
                    .build());
            inputItems.add(Json.createObjectBuilder()
                    .add("type", "custom_tool_call_output")
                    .add("call_id", pending.callId)
                    .add("output", toolOutput)
                    .build());
        }
        throw new IOException("Too many tool calls without completing a response");
    }

    private PendingToolCall streamOnce(List<JsonObject> inputItems,
                                       JsonArray tools,
                                       String instructions,
                                       StreamState st) throws IOException, InterruptedException {
        var inputArr = Json.createArrayBuilder();
        for (var it : inputItems) {
            inputArr.add(it);
        }
        var bodyBuilder = Json.createObjectBuilder()
                .add("model", model)
                .add("input", inputArr.build())
                .add("stream", true)
                .add("tool_choice", "auto")
                .add("parallel_tool_calls", false);
        if (instructions != null && !instructions.isBlank()) {
            bodyBuilder.add("instructions", instructions);
        }
        if (tools != null) {
            bodyBuilder.add("tools", tools);
        }
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
        try (var is = resp.body(); var br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            var dataBuf = new StringBuilder(2048);
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!dataBuf.isEmpty()) {
                        var stop = handleSseData(dataBuf.toString(), st);
                        dataBuf.setLength(0);
                        if (stop) {
                            break;
                        }
                    }
                    continue;
                }
                if (line.startsWith("data:")) {
                    var data = line.substring("data:".length()).trim();
                    if ("[DONE]".equals(data)) {
                        break;
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
                handleSseData(dataBuf.toString(), st);
            }
        }
        return st.pending;
    }

    private static final class PendingToolCall {
        final String name;
        final String callId;
        final String input;
        PendingToolCall(String name, String callId, String input) {
            this.name = name;
            this.callId = callId;
            this.input = input;
        }
    }

    private static final class StreamState {
        final StringBuilder full;
        final Consumer<String> onTextDelta;
        final Consumer<JsonObject> onEvent;
        final Map<String, JsonObject> outputItemById = new HashMap<>();
        final Map<String, StringBuilder> customToolInputByItemId = new HashMap<>();
        final Map<String, JsonObject> reasoningItemById = new java.util.LinkedHashMap<>();
        PendingToolCall pending;
        StreamState(StringBuilder full, Consumer<String> onTextDelta, Consumer<JsonObject> onEvent) {
            this.full = full;
            this.onTextDelta = onTextDelta;
            this.onEvent = onEvent;
        }
    }

    private static boolean handleSseData(String dataJson,
                                         StreamState st) {
        JsonObject evt;
        try (var r = Json.createReader(new StringReader(dataJson))) {
            evt = r.readObject();
        }
        if (st.onEvent != null) {
            st.onEvent.accept(evt);
        }
        var type = evt.getString("type", "");
        return switch (type) {
            case "response.output_text.delta", "response.refusal.delta" -> {
                var delta = evt.getString("delta", "");
                if (!delta.isEmpty()) {
                    if (st.onTextDelta != null) {
                        st.onTextDelta.accept(delta);
                    }
                    st.full.append(delta);
                }
                yield false;
            }
            case "response.output_item.added", "response.output_item.done" -> {
                var item = evt.getJsonObject("item");
                if (item != null) {
                    var id = item.getString("id", "");
                    if (!id.isEmpty()) {
                        st.outputItemById.put(id, item);
                        if ("reasoning".equals(item.getString("type", ""))) {
                            st.reasoningItemById.put(id, item);
                        }
                    }
                }
                yield false;
            }
            case "response.custom_tool_call_input.delta" -> {
                var itemId = evt.getString("item_id", "");
                var delta = evt.getString("delta", "");
                if (!itemId.isEmpty() && !delta.isEmpty()) {
                    st.customToolInputByItemId
                            .computeIfAbsent(itemId, k -> new StringBuilder(1024))
                            .append(delta);
                }
                yield false;
            }
            case "response.custom_tool_call_input.done" -> {
                var itemId = evt.getString("item_id", "");
                var input = evt.getString("input", "");
                if (input.isEmpty() && !itemId.isEmpty()) {
                    var buf = st.customToolInputByItemId.get(itemId);
                    if (buf != null) {
                        input = buf.toString();
                    }
                }
                var name = "";
                var callId = "";
                if (!itemId.isEmpty()) {
                    var item = st.outputItemById.get(itemId);
                    if (item != null) {
                        name = item.getString("name", "");
                        callId = item.getString("call_id", "");
                    }
                }
                if (name.isEmpty()) {
                    name = CDP_TOOL_NAME; // best-effort fallback
                }
                if (callId.isEmpty()) {
                    callId = itemId.isEmpty() ? "call_unknown" : itemId;
                }
                st.pending = new PendingToolCall(name, callId, input);
                yield true;
            }
            case "response.completed", "response.failed", "response.incomplete" -> true;
            default -> false;
        };
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
                                // Best-effort: ensure the tool input is a single JSON object string.
                                // /// reference/devtools-protocol/browser_protocol.json
                                .add("definition", "^\\\\{[\\\\s\\\\S]*\\\\}$")
                                .build())
                        .build())
                .build();
    }
}
