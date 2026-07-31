package dev.yskeli.musicmc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NetEaseSongReferenceTest {
    @Test
    void acceptsNumericIds() {
        assertEquals("254132", NetEaseSongReference.parse(" 254132 "));
    }

    @Test
    void extractsIdsFromOfficialUrls() {
        assertEquals("254132", NetEaseSongReference.parse(
                "https://music.163.com/song?id=254132&uct2=example"));
        assertEquals("254132", NetEaseSongReference.parse(
                "https://music.163.com/song?id=254132&amp;uct2=example"));
        assertEquals("254132", NetEaseSongReference.parse(
                "https://music.163.com/#/song?id=254132"));
    }

    @Test
    void rejectsUntrustedOrInvalidReferences() {
        assertThrows(IllegalArgumentException.class,
                () -> NetEaseSongReference.parse("https://example.com/song?id=254132"));
        assertThrows(IllegalArgumentException.class,
                () -> NetEaseSongReference.parse("https://music.163.com/song?id=abc"));
        assertThrows(IllegalArgumentException.class, () -> NetEaseSongReference.parse("0"));
    }
}
