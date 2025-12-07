package com.amannmalik.web.chromium;

/**
 * Raised when Chromium rejects a command or the transport fails.
 */
public final class CdpRequestException extends RuntimeException {
    private final int code;

    public CdpRequestException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
