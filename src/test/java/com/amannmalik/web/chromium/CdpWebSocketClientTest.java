package com.amannmalik.web.chromium.test;

import java.net.URI;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.amannmalik.web.chromium.CdpCommand;
import com.amannmalik.web.chromium.CdpEvent;
import com.amannmalik.web.chromium.CdpRequestException;
import com.amannmalik.web.chromium.CdpSuccess;
import com.amannmalik.web.chromium.CdpWebSocketClient;

import static org.junit.jupiter.api.Assertions.*;

class CdpWebSocketClientTest {

    @Test
    void completesCommandOnResult() {
        FakeConnector connector = new FakeConnector();
        CompletableFuture<CdpWebSocketClient> clientFuture = CdpWebSocketClient.connect(
            URI.create("ws://example.test/devtools"),
            Duration.ofSeconds(1),
            connector
        );
        connector.socket.open();
        CdpWebSocketClient client = clientFuture.join();

        CompletableFuture<CdpSuccess> result = client.send(new CdpCommand("Target.getTargets")).orTimeout(1, java.util.concurrent.TimeUnit.SECONDS);
        connector.socket.emit("""
            {"id":1,"result":{"ok":true}}
            """);

        CdpSuccess success = result.join();
        assertEquals(1L, success.id());
        assertTrue(success.result().getBoolean("ok"));
    }

    @Test
    void surfacesCommandErrorAsException() {
        FakeConnector connector = new FakeConnector();
        CompletableFuture<CdpWebSocketClient> clientFuture = CdpWebSocketClient.connect(
            URI.create("ws://example.test/devtools"),
            Duration.ofSeconds(1),
            connector
        );
        connector.socket.open();
        CdpWebSocketClient client = clientFuture.join();

        CompletableFuture<CdpSuccess> result = client.send(new CdpCommand("Page.enable")).orTimeout(1, java.util.concurrent.TimeUnit.SECONDS);
        connector.socket.emit("""
            {"id":1,"error":{"code":-32000,"message":"Page disabled"}}
            """);

        var failure = assertThrows(
            java.util.concurrent.CompletionException.class,
            result::join
        );
        assertNotNull(failure.getCause());
        assertInstanceOf(CdpRequestException.class, failure.getCause());
        assertEquals(-32000, ((CdpRequestException) failure.getCause()).code());
    }

    @Test
    void forwardsEventsToListeners() {
        FakeConnector connector = new FakeConnector();
        CompletableFuture<CdpWebSocketClient> clientFuture = CdpWebSocketClient.connect(
            URI.create("ws://example.test/devtools"),
            Duration.ofSeconds(1),
            connector
        );
        connector.socket.open();
        CdpWebSocketClient client = clientFuture.join();

        AtomicReference<CdpEvent> captured = new AtomicReference<>();
        client.onEvent(captured::set);

        connector.socket.emit("""
            {"method":"Target.attachedToTarget","params":{"targetId":"t-1"}}
            """);

        assertNotNull(captured.get());
        assertEquals("Target.attachedToTarget", captured.get().method());
        assertEquals("t-1", captured.get().params().getString("targetId"));
    }

    private static final class FakeConnector implements CdpWebSocketClient.WebSocketConnector {
        private final FakeWebSocket socket = new FakeWebSocket();

        @Override
        public CompletableFuture<WebSocket> connect(URI endpoint, WebSocket.Listener listener) {
            socket.attach(listener);
            return CompletableFuture.completedFuture(socket);
        }
    }

    private static final class FakeWebSocket implements WebSocket {
        private WebSocket.Listener listener;
        private final List<String> sent = new ArrayList<>();
        private volatile boolean inputClosed;
        private volatile boolean outputClosed;

        void attach(WebSocket.Listener listener) {
            this.listener = listener;
        }

        void open() {
            listener.onOpen(this);
        }

        void emit(String message) {
            listener.onText(this, message, true);
        }

        List<String> sentMessages() {
            return List.copyOf(sent);
        }

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            sent.add(data.toString());
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(java.nio.ByteBuffer data, boolean last) {
            throw new UnsupportedOperationException("sendBinary not used in tests");
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(java.nio.ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(java.nio.ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            inputClosed = true;
            outputClosed = true;
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
            // No-op for the test harness.
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return outputClosed;
        }

        @Override
        public boolean isInputClosed() {
            return inputClosed;
        }

        @Override
        public void abort() {
            inputClosed = true;
            outputClosed = true;
        }
    }
}
