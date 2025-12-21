package com.amannmalik.web;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class CdpClient implements WebSocket.Listener, AutoCloseable {

    private final HttpClient http;
    private final URI wsUri;
    private final Duration defaultTimeout;
    private final Consumer<JsonObject> onEvent;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, CompletableFuture<JsonObject>> pendingById = new ConcurrentHashMap<>();
    private final StringBuilder incomingText = new StringBuilder(8192);
    private volatile WebSocket ws;

    CdpClient(URI wsUri, HttpClient http, Duration defaultTimeout, Consumer<JsonObject> onEvent) {
        this.wsUri = Objects.requireNonNull(wsUri, "wsUri");
        this.http = Objects.requireNonNull(http, "http");
        this.defaultTimeout = Objects.requireNonNullElse(defaultTimeout, Duration.ofSeconds(10));
        this.onEvent = onEvent != null ? onEvent : _ -> {
        };
    }

    @Override
    public synchronized void close() {
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
            } catch (RuntimeException closeError) {
                System.err.println("Failed to close CDP WebSocket cleanly: " + closeError.getMessage());
                closeError.printStackTrace(System.err);
            }
            ws = null;
        }
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        synchronized (incomingText) {
            incomingText.append(data);
            if (!last) {
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }
            var msg = incomingText.toString();
            incomingText.setLength(0);
            JsonObject evt;
            try (var r = Json.createReader(new StringReader(msg))) {
                evt = r.readObject();
            } catch (RuntimeException parseErr) {
                System.err.println("Ignoring non-JSON CDP frame: " + msg);
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }
            if (evt.containsKey("id")) {
                var id = evt.getInt("id");
                var fut = pendingById.get(id);
                if (fut != null) {
                    fut.complete(evt);
                }
            } else {
                onEvent.accept(evt);
            }
        }
        return WebSocket.Listener.super.onText(webSocket, data, last);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        for (var e : pendingById.entrySet()) {
            e.getValue().completeExceptionally(error);
        }
        pendingById.clear();
        ws = null;
        WebSocket.Listener.super.onError(webSocket, error);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        var ex = new RuntimeException("CDP WebSocket closed: " + statusCode + " — " + reason);
        for (var e : pendingById.entrySet()) {
            e.getValue().completeExceptionally(ex);
        }
        pendingById.clear();
        ws = null;
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    JsonObject send(CdpCommand command) {
        return send(command, defaultTimeout);
    }

    private synchronized void connect() {
        if (isActive()) {
            return;
        }
        if (ws != null) {
            try {
                ws.abort();
            } catch (RuntimeException abortError) {
                System.err.println("Failed to abort stale CDP WebSocket: " + abortError.getMessage());
                abortError.printStackTrace(System.err);
            } finally {
                ws = null;
            }
        }
        ws = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(wsUri, this)
                .join();
    }

    private JsonObject send(CdpCommand command, Duration timeout) {
        Objects.requireNonNull(command, "command");
        connect();
        var id = nextId.getAndIncrement();
        var msg = command.toJson(id);
        return sendMessage(timeout, id, msg);
    }

    private JsonObject sendMessage(Duration timeout, int id, JsonObject msg) {
        var fut = new CompletableFuture<JsonObject>();
        pendingById.put(id, fut);
        try {
            ws.sendText(msg.toString(), true).join();
            var toMs = Math.max(1L, timeout.toMillis());
            return fut.get(toMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for CDP response", e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Timed out waiting for CDP response (id=" + id + ")", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed waiting for CDP response", e.getCause());
        } finally {
            pendingById.remove(id);
        }
    }

    private boolean isActive() {
        var socket = ws;
        return socket != null && !socket.isInputClosed() && !socket.isOutputClosed();
    }
}
