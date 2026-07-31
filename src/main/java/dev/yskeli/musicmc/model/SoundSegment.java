package dev.yskeli.musicmc.model;

import com.google.gson.annotations.SerializedName;
import java.util.regex.Pattern;

public record SoundSegment(
        @SerializedName("sound_key") String soundKey,
        @SerializedName("duration_seconds") double durationSeconds) {

    private static final Pattern SOUND_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public SoundSegment validated() {
        if (soundKey == null || !SOUND_PATTERN.matcher(soundKey).matches()) {
            throw new IllegalArgumentException("Invalid sound segment key");
        }
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0 || durationSeconds > 3600) {
            throw new IllegalArgumentException("Invalid sound segment duration");
        }
        return this;
    }
}
