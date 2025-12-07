package com.amannmalik.web.chromium;

public sealed interface CdpResponse permits CdpSuccess, CdpFailure {
    long id();
}
