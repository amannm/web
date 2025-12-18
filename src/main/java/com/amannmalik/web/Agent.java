package com.amannmalik.web;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

public final class Agent {

    private static final Duration CDP_TIMEOUT = Duration.ofSeconds(10);

    private Agent() {
    }

    public static int run(int port, String prompt) {
        Objects.requireNonNull(prompt, "prompt");
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt cannot be blank.");
        }
        var http = HttpClient.newBuilder()
                .connectTimeout(CDP_TIMEOUT)
                .build();
        final URI wsUri;
        try {
            wsUri = resolveWebSocketDebuggerUrl(http, port);
        } catch (Exception e) {
            System.err.println("Failed to resolve CDP WebSocket URL from localhost:" + port + " — " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
        var gateway = new OpenAiGateway();
        try (var cdp = new CdpClient(wsUri, http, CDP_TIMEOUT, evt -> {
            System.err.println("[CDP event] " + evt);
        })) {
            gateway.streamResponseTextViaCdp(prompt, cdp, delta -> {
                        System.out.print(delta);
                        System.out.flush();
                    }, _ -> {
                    }
            );
            System.out.println();
            return 0;
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted.");
            return 130;
        }
    }

    private static URI resolveWebSocketDebuggerUrl(HttpClient http, int port) throws IOException, InterruptedException {
        var listUri = URI.create("http://127.0.0.1:" + port + "/json/list");
        var listReq = HttpRequest.newBuilder()
                .uri(listUri)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        var listResp = http.send(listReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (listResp.statusCode() >= 200 && listResp.statusCode() < 300) {
            try (var r = Json.createReader(new StringReader(listResp.body()))) {
                var arr = r.readArray();
                for (var v : arr) {
                    if (!(v instanceof JsonObject o)) {
                        continue;
                    }
                    var type = o.getString("type", "");
                    if (!"page".equals(type)) {
                        continue;
                    }
                    var ws = o.getString("webSocketDebuggerUrl", "");
                    if (ws != null && !ws.isBlank()) {
                        return URI.create(ws);
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        var versionUri = URI.create("http://127.0.0.1:" + port + "/json/version");
        var req = HttpRequest.newBuilder()
                .uri(versionUri)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode() + " from " + versionUri + ": " + resp.body());
        }
        JsonObject obj;
        try (var r = Json.createReader(new StringReader(resp.body()))) {
            obj = r.readObject();
        }
        var ws = obj.getString("webSocketDebuggerUrl", "");
        if (ws == null || ws.isBlank()) {
            throw new IOException("Missing webSocketDebuggerUrl in /json/version response");
        }
        return URI.create(ws);
    }
}
