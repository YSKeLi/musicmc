package dev.yskeli.musicmc.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.yskeli.musicmc.model.Song;
import java.util.List;
import org.junit.jupiter.api.Test;

class SongLibraryTest {
    @Test
    void findsSongsByNormalizedDisplayName() {
        Song song = song("song_1_abcdef", "Hello World");
        SongLibrary library = new SongLibrary();
        library.replace(List.of(song));

        assertEquals(song, library.findByName("  ＨＥＬＬＯ ＷＯＲＬＤ ").orElseThrow());
        assertTrue(library.findById(song.songId()).isPresent());
    }

    @Test
    void rejectsDuplicateNormalizedNames() {
        SongLibrary library = new SongLibrary();
        assertThrows(IllegalArgumentException.class,
                () -> library.replace(List.of(song("song_1_abcdef", "Test"), song("song_2_abcdef", "ＴＥＳＴ"))));
    }

    private static Song song(String id, String name) {
        return new Song(
                id,
                name,
                "TestPlayer",
                "00000000-0000-0000-0000-000000000001",
                "tester",
                60,
                "musicmc:music." + id,
                "release-1",
                "musicmc-" + id + ".zip",
                "0123456789abcdef0123456789abcdef01234567",
                "https://github.com/example/repo/releases/download/release-1/musicmc.zip",
                "https://github.com/example/repo/releases/download/release-1/manifest.json",
                "https://github.com/user-attachments/files/1/song.zip",
                null,
                null,
                null,
                "0123456789abcdef0123456789abcdef",
                1);
    }
}
