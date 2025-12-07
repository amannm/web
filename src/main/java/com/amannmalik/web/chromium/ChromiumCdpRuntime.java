package com.amannmalik.web.chromium;

import jakarta.json.Json;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates a Chromium process and the CDP clients that connect to it.
 * Creates Chromium on demand, resolves the primary DevTools WebSocket endpoint, and
 * ensures both the browser process and all open CDP connections are torn down together.
 */
public final class ChromiumCdpRuntime implements AutoCloseable {

    private static final Duration VERSION_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final ChromiumProcess chromiumProcess;
    private final URI webSocketEndpoint;
    private final Duration connectTimeout;
    private final Collection<CdpClient> clients = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private ChromiumCdpRuntime(ChromiumProcess chromiumProcess, URI webSocketEndpoint, Duration connectTimeout) {
        this.chromiumProcess = chromiumProcess;
        this.webSocketEndpoint = webSocketEndpoint;
        this.connectTimeout = connectTimeout;
    }

    public static ChromiumCdpRuntime start(ChromiumDistribution distribution) {
        return start(distribution, DEFAULT_CONNECT_TIMEOUT);
    }

    public static ChromiumCdpRuntime start(ChromiumDistribution distribution, Duration connectTimeout) {
        Objects.requireNonNull(distribution, "distribution must not be null");
        Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
        ChromiumProcess process = ChromiumProcess.launch(distribution);
        HttpClient httpClient = HttpClient.newHttpClient();
        try {
            URI endpoint = resolveWebSocketEndpoint(process, httpClient);
            return new ChromiumCdpRuntime(process, endpoint, connectTimeout);
        } catch (RuntimeException e) {
            process.close();
            throw e;
        }
    }

    public CdpClient openClient() {
        if (closed.get()) {
            throw new IllegalStateException("Runtime already closed");
        }
        CdpWebSocketClient client = CdpWebSocketClient.connect(webSocketEndpoint, connectTimeout).join();
        if (closed.get()) {
            client.close();
            throw new IllegalStateException("Runtime already closed");
        }
        clients.add(client);
        return client;
    }

    public URI webSocketEndpoint() {
        return webSocketEndpoint;
    }

    public int debuggingPort() {
        return chromiumProcess.debuggingPort();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        RuntimeException failure = null;
        for (CdpClient client : clients) {
            try {
                client.close();
            } catch (RuntimeException error) {
                failure = aggregate(failure, error);
            }
        }
        try {
            chromiumProcess.close();
        } catch (RuntimeException error) {
            failure = aggregate(failure, error);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException aggregate(RuntimeException head, RuntimeException tail) {
        if (head == null) {
            return tail;
        }
        head.addSuppressed(tail);
        return head;
    }

    private static URI resolveWebSocketEndpoint(ChromiumProcess process, HttpClient httpClient) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(process.webSocketDebuggerUrl())
                .timeout(VERSION_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Chromium /json/version returned " + response.statusCode());
            }
            try (var reader = Json.createReader(new StringReader(response.body()))) {
                var version = reader.readObject();
                String endpoint = version.getString("webSocketDebuggerUrl", "");
                if (endpoint.isBlank()) {
                    throw new IllegalStateException("Chromium did not advertise a CDP WebSocket endpoint");
                }
                return URI.create(endpoint);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while resolving CDP endpoint", e);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to resolve CDP endpoint", e);
        }
    }
}
