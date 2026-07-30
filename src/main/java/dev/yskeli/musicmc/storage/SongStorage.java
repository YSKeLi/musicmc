package dev.yskeli.musicmc.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.yskeli.musicmc.model.Song;
import dev.yskeli.musicmc.model.SongCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class SongStorage {
    private static final long MAX_PACK_BYTES = 200L * 1024 * 1024;
    private static final long MAX_SOURCE_ZIP_BYTES = 25L * 1024 * 1024;
    private static final long MAX_PROCESSED_AUDIO_BYTES = 100L * 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private final Path dataDirectory;
    private final Path songsDirectory;
    private final Path catalogCache;
    private final HttpFileClient http;
    private final DownloadUrlResolver downloadUrls;

    public SongStorage(Path dataDirectory, HttpFileClient http, DownloadUrlResolver downloadUrls) throws IOException {
        this.dataDirectory = dataDirectory;
        this.songsDirectory = dataDirectory.resolve("songs");
        this.catalogCache = dataDirectory.resolve("catalog-cache.json");
        this.http = http;
        this.downloadUrls = downloadUrls;
        Files.createDirectories(songsDirectory);
    }

    public SongCatalog loadCachedCatalog() throws IOException {
        if (!Files.isRegularFile(catalogCache)) {
            return new SongCatalog(1, 0, List.of());
        }
        SongCatalog catalog = GSON.fromJson(Files.readString(catalogCache, StandardCharsets.UTF_8), SongCatalog.class);
        return catalog.validated();
    }

    public void saveCatalog(String rawCatalog) throws IOException {
        Path temporary = dataDirectory.resolve("catalog-cache.json.part-" + UUID.randomUUID());
        try {
            Files.writeString(temporary, rawCatalog, StandardCharsets.UTF_8);
            HttpFileClient.moveAtomically(temporary, catalogCache);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void materialize(Song song) throws IOException, InterruptedException {
        Path songDirectory = songsDirectory.resolve(song.songId());
        Files.createDirectories(songDirectory);
        Path pack = songDirectory.resolve(song.packFile());
        Path sourceZip = songDirectory.resolve("original-upload.zip");
        Path processed = songDirectory.resolve(song.songId() + ".ogg");
        Path localManifest = songDirectory.resolve("manifest.json");

        if (!Files.isRegularFile(pack) || !sha1(pack).equalsIgnoreCase(song.packSha1())) {
            downloadVerified(song.packUrl(), pack, MAX_PACK_BYTES, "SHA-1", song.packSha1());
        }

        String sourceSha256 = song.hasSourceArchive() ? song.sourceArchiveSha256() : null;
        if (!Files.isRegularFile(sourceZip)
                || sourceSha256 != null && !digest(sourceZip, "SHA-256").equalsIgnoreCase(sourceSha256)) {
            downloadVerified(song.sourceDownloadUrl(), sourceZip, MAX_SOURCE_ZIP_BYTES, "SHA-256", sourceSha256);
        }
        if (!Files.isRegularFile(processed)) {
            extractProcessedAudio(pack, processed, song.songId());
        }

        Files.writeString(localManifest, GSON.toJson(song) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    public boolean isReady(Song song) {
        Path songDirectory = songsDirectory.resolve(song.songId());
        Path pack = songDirectory.resolve(song.packFile());
        Path sourceZip = songDirectory.resolve("original-upload.zip");
        Path processed = songDirectory.resolve(song.songId() + ".ogg");
        Path manifest = songDirectory.resolve("manifest.json");
        try {
            return Files.isRegularFile(pack)
                    && sha1(pack).equalsIgnoreCase(song.packSha1())
                    && Files.isRegularFile(sourceZip)
                    && (!song.hasSourceArchive()
                            || digest(sourceZip, "SHA-256").equalsIgnoreCase(song.sourceArchiveSha256()))
                    && Files.isRegularFile(processed)
                    && Files.isRegularFile(manifest);
        } catch (IOException exception) {
            return false;
        }
    }

    private void downloadVerified(
            String originalUrl,
            Path destination,
            long maximumBytes,
            String algorithm,
            String expectedDigest) throws IOException, InterruptedException {
        IOException failure = new IOException("All download endpoints failed for " + destination.getFileName());
        for (URI uri : downloadUrls.candidates(originalUrl)) {
            try {
                Files.deleteIfExists(destination);
                http.download(uri, destination, maximumBytes);
                if (expectedDigest != null && !digest(destination, algorithm).equalsIgnoreCase(expectedDigest)) {
                    throw new IOException(algorithm + " mismatch from " + uri.getHost());
                }
                return;
            } catch (IOException exception) {
                Files.deleteIfExists(destination);
                failure.addSuppressed(new IOException(uri.getHost() + ": " + exception.getMessage(), exception));
            }
        }
        throw failure;
    }

    private static void extractProcessedAudio(Path pack, Path destination, String songId) throws IOException {
        String expected = "assets/musicmc/sounds/music/" + songId + ".ogg";
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            ZipEntry entry = zip.getEntry(expected);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Resource pack is missing " + expected);
            }
            copyZipEntry(zip, entry, destination, MAX_PROCESSED_AUDIO_BYTES);
        }
    }

    private static void copyZipEntry(ZipFile zip, ZipEntry entry, Path destination, long maximumBytes) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".part-" + UUID.randomUUID());
        try (InputStream input = zip.getInputStream(entry); OutputStream output = Files.newOutputStream(temporary)) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maximumBytes) {
                    throw new IOException("Extracted file exceeds size limit");
                }
                output.write(buffer, 0, read);
            }
        } catch (IOException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
        try {
            HttpFileClient.moveAtomically(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String sha1(Path path) throws IOException {
        return digest(path, "SHA-1");
    }

    private static String digest(Path path, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(algorithm + " is unavailable", impossible);
        }
    }
}
