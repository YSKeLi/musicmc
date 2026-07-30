package dev.yskeli.musicmc.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public record SongCatalog(
        @SerializedName("schema_version") int schemaVersion,
        @SerializedName("updated_at_epoch") long updatedAtEpoch,
        List<Song> songs) {

    public SongCatalog validated() {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported catalog schema: " + schemaVersion);
        }
        if (songs == null) {
            throw new IllegalArgumentException("Catalog has no songs array");
        }
        songs.forEach(Song::validated);
        return this;
    }
}
