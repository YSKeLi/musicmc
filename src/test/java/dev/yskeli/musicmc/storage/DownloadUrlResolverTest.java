package dev.yskeli.musicmc.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class DownloadUrlResolverTest {
    private final DownloadUrlResolver resolver =
            new DownloadUrlResolver(List.of("https://gh.jasonzeng.dev/"));

    @Test
    void proxiesReleaseDownloadsAndKeepsGitHubFallback() {
        String original = "https://github.com/example/uploads/releases/download/release-1/song.zip";

        assertEquals(List.of(
                "https://gh.jasonzeng.dev/" + original,
                original), resolver.candidates(original).stream().map(Object::toString).toList());
    }

    @Test
    void doesNotProxyUnsupportedUserAttachments() {
        String original = "https://github.com/user-attachments/files/1/song.zip";

        assertEquals(List.of(original), resolver.candidates(original).stream().map(Object::toString).toList());
    }
}
