package com.amannmalik.web.chromium;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class CdpWebSocketClient implements CdpClient, WebSocket.Listener {

    private final URI endpoint;
    private final Duration connectTimeout;
    private final WebSocketConnector connector;
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, CompletableFuture<CdpSuccess>> pending = new ConcurrentHashMap<>();
    private final Collection<CdpEventListener> listeners = new CopyOnWriteArrayList<>();
    private final JsonBuilderFactory builderFactory = Json.createBuilderFactory(Map.of());
    private final JsonReaderFactory readerFactory = Json.createReaderFactory(Map.of());
    private final StringBuilder textBuffer = new StringBuilder();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final CompletableFuture<Void> closed = new CompletableFuture<>();

    private volatile WebSocket webSocket;

    public static CompletableFuture<CdpWebSocketClient> connect(URI endpoint) {
        return connect(endpoint, Duration.ofSeconds(5));
    }

    public static CompletableFuture<CdpWebSocketClient> connect(URI endpoint, Duration connectTimeout) {
        var httpClient = HttpClient.newHttpClient();
        WebSocketConnector connector = new DefaultConnector(httpClient, connectTimeout);
        return connect(endpoint, connectTimeout, connector);
    }

    public static CompletableFuture<CdpWebSocketClient> connect(URI endpoint, Duration connectTimeout, WebSocketConnector connector) {
        Objects.requireNonNull(endpoint, "CDP endpoint must not be null");
        Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
        Objects.requireNonNull(connector, "connector must not be null");
        var client = new CdpWebSocketClient(endpoint, connectTimeout, connector);
        connector.connect(endpoint, client)
            .whenComplete((socket, error) -> {
                if (error != null) {
                    client.ready.completeExceptionally(error);
                    client.failPending(error);
                } else {
                    client.webSocket = socket;
                }
            });
        return client.ready.thenApply(unused -> client);
    }

    CdpWebSocketClient(URI endpoint, Duration connectTimeout, WebSocketConnector connector) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null");
        this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
        this.connector = Objects.requireNonNull(connector, "connector must not be null");
    }

    @Override
    public CompletableFuture<CdpSuccess> send(CdpCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (closed.isDone()) {
            return CompletableFuture.failedFuture(new IllegalStateException("CDP client already closed"));
        }
        return ready.thenCompose(unused -> dispatch(command));
    }

    @Override
    public CompletableFuture<CdpSuccess> send(String method, JsonObject params) {
        return send(new CdpCommand(method, params));
    }

    @Override
    public EventSubscription onEvent(CdpEventListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public boolean isOpen() {
        return ready.isDone() && !ready.isCompletedExceptionally() && !closed.isDone();
    }

    @Override
    public void close() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "client-initiated");
        }
        closed.complete(null);
        failPending(new IllegalStateException("CDP client closed"));
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
        ready.complete(null);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        textBuffer.append(data);
        if (last) {
            var payload = textBuffer.toString();
            textBuffer.setLength(0);
            handlePayload(payload);
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        closed.complete(null);
        failPending(new IllegalStateException("CDP connection closed: " + statusCode + " " + reason));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        ready.completeExceptionally(error);
        closed.completeExceptionally(error);
        failPending(error);
    }

    private CompletableFuture<CdpSuccess> dispatch(CdpCommand command) {
        var id = nextId.getAndIncrement();
        var future = new CompletableFuture<CdpSuccess>();
        pending.put(id, future);
        var payload = builderFactory.createObjectBuilder()
            .add("id", id)
            .add("method", command.method())
            .add("params", command.params())
            .build();
        try {
            webSocket.sendText(payload.toString(), true).join();
        } catch (RuntimeException error) {
            pending.remove(id);
            future.completeExceptionally(error);
        }
        return future;
    }

    private void handlePayload(String payload) {
        try (var reader = readerFactory.createReader(new StringReader(payload))) {
            var message = reader.readObject();
            if (message.containsKey("id")) {
                handleResponse(message);
                return;
            }
            if (message.containsKey("method")) {
                handleEvent(message);
                return;
            }
            throw new IllegalArgumentException("Unrecognized CDP message: " + message);
        } catch (RuntimeException error) {
            failPending(error);
            throw error;
        }
    }

    private void handleResponse(JsonObject message) {
        var id = message.getJsonNumber("id").longValueExact();
        var future = pending.remove(id);
        if (future == null) {
            return;
        }
        if (message.containsKey("error")) {
            var error = message.getJsonObject("error");
            var code = error.getInt("code");
            var msg = error.getString("message", "unknown CDP error");
            var data = error.containsKey("data") ? error.get("data") : JsonValue.NULL;
            future.completeExceptionally(new CdpRequestException(code, msg));
            return;
        }
        var result = message.containsKey("result") ? message.getJsonObject("result") : Json.createObjectBuilder().build();
        future.complete(new CdpSuccess(id, result));
    }

    private void handleEvent(JsonObject message) {
        var method = message.getString("method");
        var params = message.containsKey("params") ? message.getJsonObject("params") : Json.createObjectBuilder().build();
        var event = new CdpEvent(method, params);
        for (var listener : listeners) {
            listener.onEvent(event);
        }
    }

    private void failPending(Throwable error) {
        for (var future : pending.values()) {
            future.completeExceptionally(error);
        }
        pending.clear();
    }

    private record DefaultConnector(HttpClient httpClient, Duration connectTimeout) implements WebSocketConnector {

        private DefaultConnector {
            Objects.requireNonNull(httpClient, "httpClient must not be null");
            Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
        }

        @Override
        public CompletableFuture<WebSocket> connect(URI endpoint, WebSocket.Listener listener) {
            return httpClient.newWebSocketBuilder()
                .connectTimeout(connectTimeout)
                .buildAsync(endpoint, listener);
        }
    }

    public interface WebSocketConnector {
        CompletableFuture<WebSocket> connect(URI endpoint, WebSocket.Listener listener);
    }
}
