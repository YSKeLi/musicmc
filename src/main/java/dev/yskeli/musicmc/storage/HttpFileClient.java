package dev.yskeli.musicmc.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

public final class HttpFileClient {
    private static final int DOWNLOAD_ATTEMPTS = 3;
    private final HttpClient client;
    private final Duration requestTimeout;

    public HttpFileClient(Duration connectTimeout, Duration requestTimeout) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.requestTimeout = requestTimeout;
    }

    public String getText(URI uri, long maximumBytes) throws IOException, InterruptedException {
        return getText(uri, maximumBytes, "application/json");
    }

    public String getText(URI uri, long maximumBytes, String accept) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = send(uri, accept);
        try (InputStream input = response.body()) {
            return new String(readLimited(input, maximumBytes), StandardCharsets.UTF_8);
        }
    }

    public void download(URI uri, Path destination, long maximumBytes) throws IOException, InterruptedException {
        IOException failure = null;
        for (int attempt = 1; attempt <= DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                downloadOnce(uri, destination, maximumBytes);
                return;
            } catch (IOException exception) {
                failure = exception;
                if (attempt < DOWNLOAD_ATTEMPTS) {
                    Thread.sleep(attempt * 1_000L);
                }
            }
        }
        throw failure;
    }

    private void downloadOnce(URI uri, Path destination, long maximumBytes) throws IOException, InterruptedException {
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".part-" + UUID.randomUUID());
        try {
            HttpResponse<InputStream> response = send(
                    uri, "application/zip, application/octet-stream;q=0.9, */*;q=0.1");
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength > maximumBytes) {
                response.body().close();
                throw new IOException("Download exceeds size limit: " + uri);
            }
            try (InputStream input = response.body(); var output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > maximumBytes) {
                        throw new IOException("Download exceeds size limit: " + uri);
                    }
                    output.write(buffer, 0, read);
                }
            }
            moveAtomically(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private HttpResponse<InputStream> send(URI uri, String accept) throws IOException, InterruptedException {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IOException("Only HTTPS downloads are allowed: " + uri);
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "MusicMC-Spigot/1")
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode() + " for " + uri);
        }
        return response;
    }

    private static byte[] readLimited(InputStream input, long maximumBytes) throws IOException {
        if (maximumBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Text limit is too large");
        }
        byte[] buffer = new byte[(int) maximumBytes + 1];
        int offset = 0;
        while (offset < buffer.length) {
            int read = input.read(buffer, offset, buffer.length - offset);
            if (read < 0) {
                break;
            }
            offset += read;
        }
        if (offset > maximumBytes) {
            throw new IOException("Response exceeds size limit");
        }
        return java.util.Arrays.copyOf(buffer, offset);
    }

    public static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
