package dev.yskeli.musicmc.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.yskeli.musicmc.model.SongCatalog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SongStorageTest {
    @TempDir
    Path dataDirectory;

    @Test
    void cachesOnlyCatalogMetadata() throws Exception {
        SongStorage storage = new SongStorage(dataDirectory);
        String catalog = """
                {"schema_version":2,"updated_at_epoch":1,"bundles":[],"songs":[]}
                """;

        storage.saveCatalog(catalog);

        SongCatalog loaded = storage.loadCachedCatalog();
        assertEquals(2, loaded.schemaVersion());
        assertEquals(catalog, Files.readString(dataDirectory.resolve("catalog-cache.json"), StandardCharsets.UTF_8));
        assertFalse(Files.exists(dataDirectory.resolve("songs")));
    }
}
