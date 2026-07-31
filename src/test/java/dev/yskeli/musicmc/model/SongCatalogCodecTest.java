package dev.yskeli.musicmc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SongCatalogCodecTest {
    @Test
    void migratesLegacyCatalogWithoutDownloadingFiles() {
        String legacy = """
                {
                  "schema_version": 1,
                  "updated_at_epoch": 123,
                  "songs": [{
                    "song_id": "song_7_abcdef",
                    "display_name": "Example",
                    "minecraft_player_name": "Player",
                    "minecraft_player_uuid": "00000000-0000-0000-0000-000000000001",
                    "github_uploader": "uploader",
                    "duration_seconds": 10.0,
                    "sound_key": "musicmc:music.song_7_abcdef",
                    "pack_file": "musicmc-song.zip",
                    "pack_sha1": "0123456789abcdef0123456789abcdef01234567",
                    "pack_url": "https://github.com/example/repo/releases/download/song-7/musicmc-song.zip",
                    "source_archive_file": "source.zip",
                    "source_archive_sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    "source_archive_url": "https://github.com/example/repo/releases/download/song-7/source.zip",
                    "ticket_nonce": "0123456789abcdef0123456789abcdef",
                    "created_at_epoch": 123
                  }]
                }
                """;

        SongCatalog catalog = SongCatalogCodec.parse(legacy);

        assertEquals(2, catalog.schemaVersion());
        assertEquals(1, catalog.bundles().size());
        assertEquals("Example", catalog.songs().getFirst().commandName());
        assertEquals("upload", catalog.songs().getFirst().source().kind());
        assertTrue(catalog.bundles().getFirst().immutable());
    }

    @Test
    void acceptsEmptyVersionTwoCatalog() {
        SongCatalog catalog = SongCatalogCodec.parse(
                "{\"schema_version\":2,\"updated_at_epoch\":0,\"bundles\":[],\"songs\":[]}");
        assertEquals(2, catalog.schemaVersion());
    }
}
