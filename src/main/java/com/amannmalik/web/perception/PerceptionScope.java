package com.amannmalik.web.perception;

import java.util.Locale;
import java.util.Objects;

/**
 * Spatial scope for perception capture.
 *
 * <p>These values align with common analysis needs and intentionally map to the
 * short, hyphenated CLI tokens:
 * <ul>
 *     <li>{@code viewport} – only what is currently visible, plus nearby DOM/ARIA context.</li>
 *     <li>{@code full-page} – the entire scrollable document as a stitched visual and DOM snapshot.</li>
 *     <li>{@code main-content} – the primary content region (e.g., {@code <main>} or landmark roles).</li>
 *     <li>{@code element} – the element currently in focus/selected/last-interacted, its subtree, and minimal surroundings.</li>
 *     <li>{@code chrome} – the page plus browser chrome such as URL bar, tabs, and extension indicators.</li>
 * </ul>
 * </p>
 *
 * <p>The enum constants remain uppercase for Java ergonomics while the CLI-facing names stay
 * lowercase and hyphenated. Conversions should always flow through {@link #fromCliName(String)}
 * or {@link #cliName()} to avoid leaking Java naming conventions into user-visible surfaces.</p>
 */
public enum PerceptionScope {
    VIEWPORT("viewport"),
    FULL_PAGE("full-page"),
    MAIN_CONTENT("main-content"),
    ELEMENT("element"),
    CHROME("chrome");

    private final String cliName;

    PerceptionScope(String cliName) {
        this.cliName = cliName;
    }

    public String cliName() {
        return cliName;
    }

    public static PerceptionScope fromCliName(String value) {
        var token = Objects.requireNonNull(value, "value must not be null")
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace('_', '-');
        for (var scope : values()) {
            if (scope.cliName.equals(token)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Unrecognized scope: " + value);
    }

    public static String cliNameList() {
        var builder = new StringBuilder();
        for (var scope : values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(scope.cliName);
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return cliName;
    }
}
