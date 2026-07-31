package dev.yskeli.musicmc.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.yskeli.musicmc.model.ResourceBundle;
import dev.yskeli.musicmc.model.Song;
import dev.yskeli.musicmc.model.SongCatalog;
import dev.yskeli.musicmc.model.SongSource;
import dev.yskeli.musicmc.model.SoundSegment;
import java.util.List;
import org.junit.jupiter.api.Test;

class SongLibraryTest {
    @Test
    void findsEnabledSongsByNormalizedCommandName() {
        Song song = song("song_1_abcdef", "Hello World", true);
        SongLibrary library = new SongLibrary();
        library.replace(catalog(song));

        assertEquals(song, library.findByName("  hello world  ").orElseThrow());
        assertTrue(library.findEnabledById(song.songId()).isPresent());
        assertEquals(bundle().bundleId(), library.bundleFor(song).orElseThrow().bundleId());
    }

    @Test
    void keepsDisabledSongsAddressableByIdButHidesThemFromPlayers() {
        Song song = song("song_1_abcdef", "disabled", false);
        SongLibrary library = new SongLibrary();
        library.replace(catalog(song));

        assertTrue(library.findById(song.songId()).isPresent());
        assertFalse(library.findEnabledById(song.songId()).isPresent());
        assertFalse(library.findByName(song.commandName()).isPresent());
        assertEquals(0, library.size());
    }

    @Test
    void rejectsDuplicateNormalizedCommandNames() {
        assertThrows(IllegalArgumentException.class, () -> new SongLibrary().replace(new SongCatalog(
                2, 1, List.of(bundle()), List.of(
                        song("song_1_abcdef", "Test", true),
                        song("song_2_abcdef", "test", true)))));
    }

    private static SongCatalog catalog(Song song) {
        return new SongCatalog(2, 1, List.of(bundle()), List.of(song));
    }

    private static ResourceBundle bundle() {
        return new ResourceBundle(
                "bundle_0001", 1, "00000000-0000-0000-0000-000000000001",
                "musicmc-bundle-0001.zip", "0123456789abcdef0123456789abcdef01234567",
                "https://github.com/example/repo/releases/download/bundle-1/musicmc-bundle-0001.zip",
                1024, true);
    }

    private static Song song(String id, String commandName, boolean enabled) {
        return new Song(
                id, 1, commandName, commandName, "", enabled,
                "TestPlayer", "00000000-0000-0000-0000-000000000001", "tester", 60,
                bundle().bundleId(), List.of(new SoundSegment("musicmc:music." + id, 60)),
                new SongSource("upload", null, null, null, null, null),
                "0123456789abcdef0123456789abcdef", 1);
    }
}
