package com.amannmalik.web.cli.test;

import com.amannmalik.web.cli.Entrypoint;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PerceiveCommandTest {

    @Test
    void capturesRealProjection() {
        var cli = configureCli();
        var stdout = new ByteArrayOutputStream();
        cli.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));

        var exitCode = cli.execute("perceive", "https://example.com", "--profile", "debug", "--scope", "viewport");

        assertEquals(0, exitCode);
        var projection = readJson(stdout);
        assertTrue(projection.containsKey("frames"));
        assertFalse(projection.getJsonObject("frames").isEmpty());
        assertTrue(projection.containsKey("viewport"));
        var contentSize = projection.getJsonObject("viewport").getJsonObject("contentSize");
        assertTrue(contentSize.getJsonNumber("width").doubleValue() > 0);
        assertTrue(contentSize.getJsonNumber("height").doubleValue() > 0);
        var screenshots = projection.getJsonObject("screenshots");
        assertFalse(screenshots.isEmpty());
        var firstScreenshot = screenshots.values().iterator().next().asJsonObject();
        assertTrue(firstScreenshot.getString("data").length() > 100, "screenshot data should be populated");
    }

    @Test
    void rejectsNonHttpSchemes() {
        var cli = configureCli();
        var stderr = new ByteArrayOutputStream();
        cli.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));

        var exitCode = cli.execute("perceive", "file:///tmp/index.html");

        assertEquals(CommandLine.ExitCode.SOFTWARE, exitCode);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("http or https"));
    }

    @Test
    void usesMultimodalDefaults() {
        var cli = configureCli();
        var stdout = new ByteArrayOutputStream();
        cli.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));

        var exitCode = cli.execute("perceive", "https://example.com");

        assertEquals(0, exitCode);
        var projection = readJson(stdout);
        var frames = projection.getJsonObject("frames");
        assertFalse(frames.isEmpty(), "projection should include at least one frame");
        var firstFrame = frames.entrySet().iterator().next().getValue().asJsonObject();
        assertTrue(firstFrame.containsKey("text"), "default multimodal profile should include text runs");
        assertTrue(firstFrame.getJsonArray("text").size() > 0, "first frame should contain at least one text run");
    }

    private static CommandLine configureCli() {
        var cli = Entrypoint.commandLine();
        cli.setCaseInsensitiveEnumValuesAllowed(false);
        cli.setExecutionStrategy(new CommandLine.RunLast());
        return cli;
    }

    private static JsonObject readJson(ByteArrayOutputStream stdout) {
        var payload = stdout.toString(StandardCharsets.UTF_8).trim();
        try (var reader = Json.createReader(new java.io.StringReader(payload))) {
            return reader.readObject();
        }
    }
}
