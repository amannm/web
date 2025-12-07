package com.amannmalik.web.webdriver.ffi;

import java.util.List;
import java.util.StringJoiner;

public final class WebDriverSymbolNotFoundException extends IllegalStateException {
    public WebDriverSymbolNotFoundException(List<String> attemptedSymbols) {
        super(formatMessage(attemptedSymbols));
    }

    private static String formatMessage(List<String> attemptedSymbols) {
        var joiner = new StringJoiner(", ", "[", "]");
        for (var symbol : attemptedSymbols) {
            joiner.add(symbol);
        }
        return "Failed to locate a WebDriver entrypoint symbol in the native library. Checked " + joiner;
    }
}
