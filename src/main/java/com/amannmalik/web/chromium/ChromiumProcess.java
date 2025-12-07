package com.amannmalik.web.chromium;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Manages the lifecycle of a single Chromium process that exposes a CDP endpoint.
 */
public final class ChromiumProcess implements AutoCloseable {
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration READINESS_BACKOFF = Duration.ofMillis(200);
    private static final Logger LOG = System.getLogger(ChromiumProcess.class.getName());

    private final Process process;
    private final int debuggingPort;
    private final Path userDataDir;
    private final HttpClient httpClient;
    private final Supplier<String> outputSupplier;

    private ChromiumProcess(Process process, int debuggingPort, Path userDataDir, Supplier<String> outputSupplier) {
        this.process = process;
        this.debuggingPort = debuggingPort;
        this.userDataDir = userDataDir;
        this.httpClient = HttpClient.newHttpClient();
        this.outputSupplier = outputSupplier;
    }

    public int debuggingPort() {
        return debuggingPort;
    }

    public URI webSocketDebuggerUrl() {
        return URI.create("http://localhost:" + debuggingPort + "/json/version");
    }

    public static ChromiumProcess launch(ChromiumDistribution distribution) {
        Objects.requireNonNull(distribution);
        try {
            var port = allocatePort();
            var userDataDir = Files.createTempDirectory("web-chromium-profile-");
            try {
                var binary = distribution.binary();
                var command = launchCommand(binary, port, userDataDir);
                var processBuilder = new ProcessBuilder(command);
                processBuilder.redirectErrorStream(true);
                var capturedOutput = new StringBuilder();
                Process process = processBuilder.start();
                var in = process.getInputStream();
                Thread.ofPlatform().daemon(true).name("chromium-log-drain").start(() -> {
                    try (var stream = in) {
                        stream.transferTo(new StringBuilderOutputStream(capturedOutput));
                    } catch (IOException ignored) {
                        // Process termination closes the stream.
                    }
                });
                var chromium = new ChromiumProcess(process, port, userDataDir, capturedOutput::toString);
                chromium.awaitReadiness();
                return chromium;
            } catch (IOException | InterruptedException e) {
                deleteProfile(userDataDir);
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to launch Chromium", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to launch Chromium", e);
        }
    }

    private static int allocatePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private static List<String> launchCommand(Path binary, int port, Path userDataDir) {
        var command = new ArrayList<String>();
        command.add(binary.toAbsolutePath().toString());
        command.add("--remote-debugging-port=" + port);
        command.add("--headless=new");
        command.add("--disable-background-networking");
        command.add("--disable-default-apps");
        command.add("--disable-extensions");
        command.add("--enable-automation");
        command.add("--no-first-run");
        command.add("--no-default-browser-check");
        command.add("--disable-sync");
        command.add("--hide-scrollbars");
        command.add("--mute-audio");
        command.add("--user-data-dir=" + userDataDir.toAbsolutePath());
        command.add("about:blank");
        return command;
    }

    private void awaitReadiness() throws IOException, InterruptedException {
        var deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        var readinessUri = webSocketDebuggerUrl();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw startupFailure("Chromium exited during startup");
            }
            try {
                var request = HttpRequest.newBuilder()
                        .uri(readinessUri)
                        .timeout(READINESS_BACKOFF)
                        .GET()
                        .build();
                var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (ConnectException ignored) {
                // Port not yet listening.
            }
            Thread.sleep(READINESS_BACKOFF);
        }
        throw startupFailure("Timed out waiting for CDP port on " + readinessUri);
    }

    private IllegalStateException startupFailure(String message) {
        var output = outputSupplier.get();
        if (!output.isBlank()) {
            LOG.log(Level.ERROR, "Chromium output during startup:\n{0}", output);
        }
        destroy();
        return new IllegalStateException(message + " (port " + debuggingPort + ")");
    }

    @Override
    public void close() {
        destroy();
        deleteUserDataDir();
    }

    private void destroy() {
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private void deleteUserDataDir() {
        deleteProfile(userDataDir);
    }

    private static void deleteProfile(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to delete Chromium profile {0}: {1}", root, e.getMessage());
        }
    }

    private static final class StringBuilderOutputStream extends java.io.OutputStream {
        private final StringBuilder delegate;

        StringBuilderOutputStream(StringBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) {
            delegate.append((char) b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            delegate.append(new String(b, off, len, StandardCharsets.UTF_8));
        }
    }
}
