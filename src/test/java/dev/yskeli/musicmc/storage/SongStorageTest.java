package dev.yskeli.musicmc.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.yskeli.musicmc.model.Song;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SongStorageTest {
    @TempDir
    Path dataDirectory;

    @Test
    void retainsOriginalArchiveWithoutReopeningIt() throws Exception {
        String songId = "song_5_abcdef123456";
        String packFile = "musicmc-" + songId + ".zip";
        Path songDirectory = dataDirectory.resolve("songs").resolve(songId);
        Files.createDirectories(songDirectory);

        byte[] originalArchive = {0x50, 0x4b, 0x03, 0x04, (byte) 0xff};
        Path sourceZip = songDirectory.resolve("original-upload.zip");
        Files.write(sourceZip, originalArchive);

        byte[] processedAudio = "processed ogg fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path pack = songDirectory.resolve(packFile);
        createPack(pack, songId, processedAudio);

        Song song = new Song(
                songId,
                "Unicode source filename",
                "TestPlayer",
                "00000000-0000-0000-0000-000000000001",
                "tester",
                60,
                "musicmc:music." + songId,
                "release-1",
                packFile,
                sha1(pack),
                "https://github.com/example/repo/releases/download/release-1/" + packFile,
                "https://github.com/example/repo/releases/download/release-1/manifest.json",
                "https://github.com/user-attachments/files/1/song.zip",
                "musicmc-" + songId + "-source.zip",
                digest(sourceZip, "SHA-256"),
                "https://github.com/example/repo/releases/download/release-1/musicmc-" + songId + "-source.zip",
                "0123456789abcdef0123456789abcdef",
                1);

        SongStorage storage = new SongStorage(
                dataDirectory,
                new HttpFileClient(Duration.ofSeconds(1), Duration.ofSeconds(1)),
                new DownloadUrlResolver(java.util.List.of("https://gh.jasonzeng.dev/")));
        storage.materialize(song);

        assertArrayEquals(originalArchive, Files.readAllBytes(sourceZip));
        assertArrayEquals(processedAudio, Files.readAllBytes(songDirectory.resolve(songId + ".ogg")));
        assertTrue(storage.isReady(song));
    }

    private static void createPack(Path destination, String songId, byte[] audio) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(destination))) {
            output.putNextEntry(new ZipEntry("assets/musicmc/sounds/music/" + songId + ".ogg"));
            output.write(audio);
            output.closeEntry();
        }
    }

    private static String sha1(Path path) throws IOException, NoSuchAlgorithmException {
        return digest(path, "SHA-1");
    }

    private static String digest(Path path, String algorithm) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
