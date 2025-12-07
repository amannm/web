package com.amannmalik.web.chromium;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles discovery and caching of a Chromium binary for the host platform.
 */
public final class ChromiumDistribution {
    private static final URI SNAPSHOT_ROOT = URI.create("https://storage.googleapis.com/chromium-browser-snapshots/");
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final Logger LOG = System.getLogger(ChromiumDistribution.class.getName());

    private final ChromiumPlatform platform;
    private final Path cacheRoot;
    private final HttpClient httpClient;

    public ChromiumDistribution(ChromiumPlatform platform, Path cacheRoot) {
        this.platform = Objects.requireNonNull(platform);
        this.cacheRoot = Objects.requireNonNull(cacheRoot);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    public Path binary() {
        try {
            var revision = latestRevision();
            var platformRoot = cacheRoot.resolve(platform.snapshotLabel());
            var revisionRoot = platformRoot.resolve(revision);
            var executable = revisionRoot.resolve(platform.executableRelativePath());
            if (Files.isExecutable(executable)) {
                return executable;
            }
            Files.createDirectories(revisionRoot);
            downloadAndExtract(revision, revisionRoot);
            ensureExecutable(executable);
            dropOlderRevisions(platformRoot, revision);
            return executable;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to provision Chromium binary", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to provision Chromium binary", e);
        }
    }

    private String latestRevision() throws IOException, InterruptedException {
        var lastChangeUri = SNAPSHOT_ROOT.resolve(platform.snapshotLabel() + "/LAST_CHANGE");
        var request = HttpRequest.newBuilder()
                .uri(lastChangeUri)
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Unable to resolve Chromium revision: " + response.statusCode());
        }
        return response.body().trim();
    }

    private void downloadAndExtract(String revision, Path revisionRoot) throws IOException, InterruptedException {
        var archiveUri = SNAPSHOT_ROOT.resolve(platform.snapshotLabel() + "/" + revision + "/" + platform.archiveName());
        var archiveTarget = Files.createTempFile("chromium-", ".zip");
        try {
            LOG.log(Level.INFO, "Downloading Chromium {0} from {1}", revision, archiveUri);
            var request = HttpRequest.newBuilder()
                    .uri(archiveUri)
                    .timeout(Duration.ofMinutes(2))
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(archiveTarget));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Download failed: " + response.statusCode());
            }
            unpackZip(archiveTarget, revisionRoot);
        } finally {
            Files.deleteIfExists(archiveTarget);
        }
    }

    private void unpackZip(Path archive, Path destination) throws IOException {
        // Using ZipInputStream avoids retaining the entire archive on disk if a caller later replaces this with a stream.
        try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                var target = destination.resolve(entry.getName()).normalize();
                if (!target.startsWith(destination)) {
                    throw new IOException("Refusing to write outside destination: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void ensureExecutable(Path executable) throws IOException {
        if (Files.notExists(executable)) {
            throw new IllegalStateException("Chromium executable missing after extraction: " + executable);
        }
        try {
            var permissions = Files.getPosixFilePermissions(executable);
            var updated = EnumSet.copyOf(permissions);
            updated.addAll(Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
                    java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
                    java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE
            ));
            if (!permissions.equals(updated)) {
                Files.setPosixFilePermissions(executable, updated);
            }
        } catch (UnsupportedOperationException ignored) {
            // Windows file systems do not expose POSIX permissions.
        }
    }

    private void dropOlderRevisions(Path platformRoot, String keepRevision) throws IOException {
        // Clean up older revisions to avoid unbounded growth. Failures are non-fatal.
        if (!Files.isDirectory(platformRoot)) {
            return;
        }
        try (var directories = Files.list(platformRoot)) {
            directories
                    .filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().equals(keepRevision))
                    .forEach(this::deleteQuietly);
        }
    }

    private void deleteQuietly(Path root) {
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
        } catch (IOException ignored) {
            LOG.log(Level.WARNING, "Failed to delete {0}", root);
        }
    }
}
