package com.amannmalik.web.chromium;

public interface EventSubscription extends AutoCloseable {
    @Override
    void close();
}
