package dev.yskeli.musicmc.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SongCatalogCodec {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private SongCatalogCodec() {}

    public static SongCatalog parse(String rawCatalog) {
        JsonObject root = JsonParser.parseString(rawCatalog).getAsJsonObject();
        int schemaVersion = integer(root, "schema_version", 0);
        if (schemaVersion == SongCatalog.CURRENT_SCHEMA_VERSION) {
            return GSON.fromJson(root, SongCatalog.class).validated();
        }
        if (schemaVersion == 1) {
            return migrateV1(root).validated();
        }
        throw new IllegalArgumentException("Unsupported catalog schema: " + schemaVersion);
    }

    private static SongCatalog migrateV1(JsonObject root) {
        List<ResourceBundle> bundles = new ArrayList<>();
        List<Song> songs = new ArrayList<>();
        JsonArray legacySongs = root.has("songs") && root.get("songs").isJsonArray()
                ? root.getAsJsonArray("songs") : new JsonArray();
        for (JsonElement value : legacySongs) {
            JsonObject legacy = value.getAsJsonObject();
            String songId = text(legacy, "song_id");
            String displayName = text(legacy, "display_name");
            String packSha1 = text(legacy, "pack_sha1");
            String bundleId = "legacy_" + songId;
            String packId = UUID.nameUUIDFromBytes(
                    ("musicmc:" + songId + ':' + packSha1).getBytes(StandardCharsets.UTF_8)).toString();
            bundles.add(new ResourceBundle(
                    bundleId,
                    1,
                    packId,
                    text(legacy, "pack_file"),
                    packSha1,
                    text(legacy, "pack_url"),
                    0,
                    true));

            double duration = number(legacy, "duration_seconds", 0);
            String archiveFile = optionalText(legacy, "source_archive_file");
            String archiveSha256 = optionalText(legacy, "source_archive_sha256");
            String archiveUrl = optionalText(legacy, "source_archive_url");
            String providerUrl = optionalText(legacy, "source_attachment_url");
            songs.add(new Song(
                    songId,
                    1,
                    displayName,
                    displayName,
                    "",
                    true,
                    optionalText(legacy, "minecraft_player_name"),
                    optionalText(legacy, "minecraft_player_uuid"),
                    optionalText(legacy, "github_uploader"),
                    duration,
                    bundleId,
                    List.of(new SoundSegment(text(legacy, "sound_key"), duration)),
                    new SongSource("upload", optionalText(legacy, "issue_number"), providerUrl,
                            archiveFile, archiveSha256, archiveUrl),
                    optionalText(legacy, "ticket_nonce"),
                    longNumber(legacy, "created_at_epoch", 0)));
        }
        return new SongCatalog(
                SongCatalog.CURRENT_SCHEMA_VERSION,
                longNumber(root, "updated_at_epoch", 0),
                List.copyOf(bundles),
                List.copyOf(songs));
    }

    private static String text(JsonObject object, String name) {
        String value = optionalText(object, name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing catalog field: " + name);
        }
        return value;
    }

    private static String optionalText(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        return value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static int integer(JsonObject object, String name, int fallback) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? fallback : value.getAsInt();
    }

    private static long longNumber(JsonObject object, String name, long fallback) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? fallback : value.getAsLong();
    }

    private static double number(JsonObject object, String name, double fallback) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? fallback : value.getAsDouble();
    }
}
