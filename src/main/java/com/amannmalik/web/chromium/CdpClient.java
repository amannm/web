package com.amannmalik.web.chromium;

import jakarta.json.JsonObject;
import java.util.concurrent.CompletableFuture;

public interface CdpClient extends AutoCloseable {

    CompletableFuture<CdpSuccess> send(CdpCommand command);

    CompletableFuture<CdpSuccess> send(String method, JsonObject params);

    EventSubscription onEvent(CdpEventListener listener);

    boolean isOpen();

    @Override
    void close();
}
