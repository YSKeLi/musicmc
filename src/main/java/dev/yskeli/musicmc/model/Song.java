package dev.yskeli.musicmc.model;

import com.google.gson.annotations.SerializedName;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record Song(
        @SerializedName("song_id") String songId,
        @SerializedName("display_name") String displayName,
        @SerializedName("minecraft_player_name") String minecraftPlayerName,
        @SerializedName("minecraft_player_uuid") String minecraftPlayerUuid,
        @SerializedName("github_uploader") String githubUploader,
        @SerializedName("duration_seconds") double durationSeconds,
        @SerializedName("sound_key") String soundKey,
        @SerializedName("release_tag") String releaseTag,
        @SerializedName("pack_file") String packFile,
        @SerializedName("pack_sha1") String packSha1,
        @SerializedName("pack_url") String packUrl,
        @SerializedName("manifest_url") String manifestUrl,
        @SerializedName("source_attachment_url") String sourceAttachmentUrl,
        @SerializedName("source_archive_file") String sourceArchiveFile,
        @SerializedName("source_archive_sha256") String sourceArchiveSha256,
        @SerializedName("source_archive_url") String sourceArchiveUrl,
        @SerializedName("ticket_nonce") String ticketNonce,
        @SerializedName("created_at_epoch") long createdAtEpoch) {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,96}");
    private static final Pattern SHA1_PATTERN = Pattern.compile("[a-f0-9]{40}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[a-f0-9]{64}");
    private static final Pattern SOUND_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public Song validated() {
        require(ID_PATTERN.matcher(requireText(songId, "song_id")).matches(), "Invalid song_id");
        require(!requireText(displayName, "display_name").contains("\n"), "Invalid display_name");
        require(displayName.length() <= 48, "display_name is too long");
        require(durationSeconds > 0 && durationSeconds <= 3600, "Invalid duration_seconds");
        require(SOUND_PATTERN.matcher(requireText(soundKey, "sound_key")).matches(), "Invalid sound_key");
        require(SHA1_PATTERN.matcher(requireText(packSha1, "pack_sha1").toLowerCase(Locale.ROOT)).matches(),
                "Invalid pack_sha1");
        require(isSafeFileName(requireText(packFile, "pack_file")), "Invalid pack_file");
        requireHttps(packUrl, "pack_url");
        requireHttps(sourceAttachmentUrl, "source_attachment_url");
        if (hasSourceArchive()) {
            require(isSafeFileName(requireText(sourceArchiveFile, "source_archive_file")),
                    "Invalid source_archive_file");
            require(SHA256_PATTERN.matcher(requireText(sourceArchiveSha256, "source_archive_sha256")
                    .toLowerCase(Locale.ROOT)).matches(), "Invalid source_archive_sha256");
            requireHttps(sourceArchiveUrl, "source_archive_url");
        } else {
            require(isBlank(sourceArchiveFile) && isBlank(sourceArchiveSha256) && isBlank(sourceArchiveUrl),
                    "Incomplete source archive metadata");
        }
        if (manifestUrl != null && !manifestUrl.isBlank()) {
            requireHttps(manifestUrl, "manifest_url");
        }
        if (minecraftPlayerUuid != null && !minecraftPlayerUuid.isBlank()) {
            UUID.fromString(minecraftPlayerUuid);
        }
        return this;
    }

    public String normalizedName() {
        return SongNames.normalize(displayName);
    }

    public boolean hasSourceArchive() {
        return !isBlank(sourceArchiveFile) || !isBlank(sourceArchiveSha256) || !isBlank(sourceArchiveUrl);
    }

    public String sourceDownloadUrl() {
        return hasSourceArchive() ? sourceArchiveUrl : sourceAttachmentUrl;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + field);
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isSafeFileName(String value) {
        return Objects.equals(PathNames.fileName(value), value) && !value.equals(".") && !value.equals("..");
    }

    private static void requireHttps(String value, String field) {
        URI uri = URI.create(requireText(value, field));
        require("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null, "Invalid " + field);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static final class PathNames {
        private static String fileName(String value) {
            int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
            return slash >= 0 ? value.substring(slash + 1) : value;
        }
    }
}
