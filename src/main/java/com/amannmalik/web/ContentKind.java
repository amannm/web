package com.amannmalik.web;

enum ContentKind {
    OUTPUT_TEXT,
    REFUSAL,
    REASONING_TEXT,
    REASONING_SUMMARY_TEXT,
    UNKNOWN;

    static ContentKind from(String type) {
        return switch (type) {
            case "output_text" -> OUTPUT_TEXT;
            case "refusal" -> REFUSAL;
            case "reasoning_text" -> REASONING_TEXT;
            case "reasoning_summary_text" -> REASONING_SUMMARY_TEXT;
            default -> UNKNOWN;
        };
    }
}
