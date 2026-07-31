package dev.yskeli.musicmc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SongNamesTest {
    @Test
    void normalizesWidthCaseAndWhitespace() {
        assertEquals("hello 世界", SongNames.normalize("  ＨＥＬＬＯ 世界  "));
    }

    @Test
    void rejectsControlCharactersAndLongNames() {
        assertThrows(IllegalArgumentException.class, () -> SongNames.validateDisplayName("bad\nname"));
        assertThrows(IllegalArgumentException.class, () -> SongNames.validateDisplayName("x".repeat(49)));
    }

    @Test
    void permitsNamesThatNoLongerNeedToBeZipFilenames() {
        assertEquals("bad/name?", SongNames.validateDisplayName("bad/name?"));
        assertEquals("song.", SongNames.validateDisplayName("song."));
    }
}
