package dev.yskeli.musicmc.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public record Song(
        @SerializedName("song_id") String songId,
        int revision,
        @SerializedName("command_name") String commandName,
        @SerializedName("display_name") String displayName,
        String artist,
        boolean enabled,
        @SerializedName("minecraft_player_name") String minecraftPlayerName,
        @SerializedName("minecraft_player_uuid") String minecraftPlayerUuid,
        @SerializedName("github_uploader") String githubUploader,
        @SerializedName("duration_seconds") double durationSeconds,
        @SerializedName("bundle_id") String bundleId,
        List<SoundSegment> segments,
        SongSource source,
        @SerializedName("ticket_nonce") String ticketNonce,
        @SerializedName("created_at_epoch") long createdAtEpoch) {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,96}");

    public Song validated() {
        require(ID_PATTERN.matcher(requireText(songId, "song_id")).matches(), "Invalid song_id");
        require(revision >= 1, "Invalid song revision");
        requireName(commandName, "command_name", 48);
        requireName(displayName, "display_name", 96);
        require(artist == null || (!artist.contains("\n") && artist.length() <= 96), "Invalid artist");
        require(Double.isFinite(durationSeconds) && durationSeconds > 0 && durationSeconds <= 3600,
                "Invalid duration_seconds");
        require(ID_PATTERN.matcher(requireText(bundleId, "bundle_id")).matches(), "Invalid bundle_id");
        require(segments != null && !segments.isEmpty() && segments.size() <= 1000, "Invalid sound segments");
        segments.forEach(SoundSegment::validated);
        double segmentDuration = segments.stream().mapToDouble(SoundSegment::durationSeconds).sum();
        double tolerance = Math.max(2.0, segments.size() * 0.25);
        require(Math.abs(segmentDuration - durationSeconds) <= tolerance, "Sound segment duration mismatch");
        require(source != null, "Missing song source");
        source.validated();
        if (minecraftPlayerUuid != null && !minecraftPlayerUuid.isBlank()) {
            UUID.fromString(minecraftPlayerUuid);
        }
        return this;
    }

    public String normalizedCommandName() {
        return SongNames.normalize(commandName);
    }

    public String normalizedName() {
        return SongNames.normalize(displayName);
    }

    public String soundKey() {
        return segments.getFirst().soundKey();
    }

    public boolean segmented() {
        return segments.size() > 1;
    }

    public String displayLabel() {
        return artist == null || artist.isBlank() ? displayName : displayName + " - " + artist;
    }

    private static void requireName(String value, String field, int maximumLength) {
        requireText(value, field);
        require(!value.contains("\n") && value.length() <= maximumLength, "Invalid " + field);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + field);
        }
        return value.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
