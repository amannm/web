package com.amannmalik.web.cli;

import com.amannmalik.web.chromium.CdpClient;
import com.amannmalik.web.chromium.ChromiumCdpRuntime;
import com.amannmalik.web.chromium.ChromiumDistribution;
import com.amannmalik.web.chromium.ChromiumPlatform;
import com.amannmalik.web.perception.PerceptionProfileProjector;
import com.amannmalik.web.perception.PerceptionSnapshotBuilder;
import com.amannmalik.web.perception.PerceptionScope;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class ChromiumPerceptionExecutor {

    private static final Duration NAVIGATION_TIMEOUT = Duration.ofSeconds(15);

    private final JsonBuilderFactory jsonFactory = Json.createBuilderFactory(Map.of());
    private final PerceptionSnapshotBuilder snapshotBuilder;
    private final PerceptionProfileProjector projector;
    private final Path cacheRoot;

    ChromiumPerceptionExecutor() {
        this(Path.of(System.getProperty("user.home"), ".cache", "web", "chromium"),
                new PerceptionSnapshotBuilder(),
                new PerceptionProfileProjector());
    }

    ChromiumPerceptionExecutor(Path cacheRoot,
                               PerceptionSnapshotBuilder snapshotBuilder,
                               PerceptionProfileProjector projector) {
        this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot must not be null");
        this.snapshotBuilder = Objects.requireNonNull(snapshotBuilder, "snapshotBuilder must not be null");
        this.projector = Objects.requireNonNull(projector, "projector must not be null");
    }

    public JsonObject capture(URI target,
                              PerceptionProfileProjector.PerceptionProfile profile,
                              PerceptionScope scope) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(scope, "scope must not be null");

        var distribution = new ChromiumDistribution(ChromiumPlatform.detect(), cacheRoot);
        try (var runtime = ChromiumCdpRuntime.start(distribution);
             var client = runtime.openClient()) {

            enableDomains(client);
            navigate(client, target);
            // TODO: Scope-specific capture (viewport/main-content/element/chrome) is not implemented yet.
            var snapshot = snapshotBuilder.capture(client);
            return projector.project(snapshot, profile);
        }
    }

    private void enableDomains(CdpClient client) {
        client.send("Page.enable", jsonFactory.createObjectBuilder().build()).join();
        client.send("Runtime.enable", jsonFactory.createObjectBuilder().build()).join();
    }

    private void navigate(CdpClient client, URI target) {
        var loadEvent = new CompletableFuture<Void>();
        var subscription = client.onEvent(event -> {
            if ("Page.loadEventFired".equals(event.method())) {
                loadEvent.complete(null);
            }
        });
        try {
            var navigateParams = jsonFactory.createObjectBuilder()
                    .add("url", target.toString())
                    .build();
            client.send("Page.navigate", navigateParams).join();
            loadEvent.orTimeout(NAVIGATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).join();
        } finally {
            subscription.close();
        }
    }
}
