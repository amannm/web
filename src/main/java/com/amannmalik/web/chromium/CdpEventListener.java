package com.amannmalik.web.chromium;

@FunctionalInterface
public interface CdpEventListener {
    void onEvent(CdpEvent event);
}
