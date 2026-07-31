package dev.yskeli.musicmc.storage;

import dev.yskeli.musicmc.model.SongCatalog;
import dev.yskeli.musicmc.model.SongCatalogCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class SongStorage {
    private final Path dataDirectory;
    private final Path catalogCache;

    public SongStorage(Path dataDirectory) throws IOException {
        this.dataDirectory = dataDirectory;
        this.catalogCache = dataDirectory.resolve("catalog-cache.json");
        Files.createDirectories(dataDirectory);
    }

    public SongCatalog loadCachedCatalog() throws IOException {
        if (!Files.isRegularFile(catalogCache)) {
            return SongCatalog.empty();
        }
        return SongCatalogCodec.parse(Files.readString(catalogCache, StandardCharsets.UTF_8));
    }

    public void saveCatalog(String rawCatalog) throws IOException {
        SongCatalogCodec.parse(rawCatalog);
        Path temporary = dataDirectory.resolve("catalog-cache.json.part-" + UUID.randomUUID());
        try {
            Files.writeString(temporary, rawCatalog, StandardCharsets.UTF_8);
            HttpFileClient.moveAtomically(temporary, catalogCache);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
