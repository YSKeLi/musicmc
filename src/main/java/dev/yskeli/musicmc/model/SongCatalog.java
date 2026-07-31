package dev.yskeli.musicmc.model;

import com.google.gson.annotations.SerializedName;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record SongCatalog(
        @SerializedName("schema_version") int schemaVersion,
        @SerializedName("updated_at_epoch") long updatedAtEpoch,
        List<ResourceBundle> bundles,
        List<Song> songs) {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    public static SongCatalog empty() {
        return new SongCatalog(CURRENT_SCHEMA_VERSION, 0, List.of(), List.of());
    }

    public SongCatalog validated() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported catalog schema: " + schemaVersion);
        }
        if (bundles == null || songs == null) {
            throw new IllegalArgumentException("Catalog has no bundle or song array");
        }

        Map<String, ResourceBundle> byBundleId = new LinkedHashMap<>();
        for (ResourceBundle bundle : bundles) {
            bundle.validated();
            if (byBundleId.putIfAbsent(bundle.bundleId(), bundle) != null) {
                throw new IllegalArgumentException("Duplicate bundle ID: " + bundle.bundleId());
            }
        }

        Set<String> songIds = new HashSet<>();
        Set<String> commandNames = new HashSet<>();
        Set<String> ticketNonces = new HashSet<>();
        for (Song song : songs) {
            song.validated();
            if (!songIds.add(song.songId())) {
                throw new IllegalArgumentException("Duplicate song ID: " + song.songId());
            }
            if (!commandNames.add(song.normalizedCommandName())) {
                throw new IllegalArgumentException("Duplicate command name: " + song.commandName());
            }
            if (!byBundleId.containsKey(song.bundleId())) {
                throw new IllegalArgumentException("Song references missing bundle: " + song.bundleId());
            }
            if (song.ticketNonce() != null && !song.ticketNonce().isBlank() && !ticketNonces.add(song.ticketNonce())) {
                throw new IllegalArgumentException("Duplicate upload ticket nonce");
            }
        }
        return this;
    }

    public Map<String, ResourceBundle> bundlesById() {
        Map<String, ResourceBundle> result = new LinkedHashMap<>();
        bundles.forEach(bundle -> result.put(bundle.bundleId(), bundle));
        return Map.copyOf(result);
    }
}
