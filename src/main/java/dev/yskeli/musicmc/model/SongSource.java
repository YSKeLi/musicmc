package dev.yskeli.musicmc.model;

import com.google.gson.annotations.SerializedName;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

public record SongSource(
        String kind,
        @SerializedName("provider_id") String providerId,
        @SerializedName("provider_url") String providerUrl,
        @SerializedName("archive_file") String archiveFile,
        @SerializedName("archive_sha256") String archiveSha256,
        @SerializedName("archive_url") String archiveUrl) {

    private static final Pattern KIND_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[a-f0-9]{64}");

    public SongSource validated() {
        if (kind == null || !KIND_PATTERN.matcher(kind).matches()) {
            throw new IllegalArgumentException("Invalid song source kind");
        }
        requireHttpsIfPresent(providerUrl, "provider_url");
        requireHttpsIfPresent(archiveUrl, "archive_url");
        if (archiveSha256 != null && !archiveSha256.isBlank()
                && !SHA256_PATTERN.matcher(archiveSha256.toLowerCase(Locale.ROOT)).matches()) {
            throw new IllegalArgumentException("Invalid source archive SHA-256");
        }
        if (archiveFile != null && (archiveFile.contains("/") || archiveFile.contains("\\"))) {
            throw new IllegalArgumentException("Invalid source archive file");
        }
        return this;
    }

    private static void requireHttpsIfPresent(String value, String field) {
        if (value == null || value.isBlank()) {
            return;
        }
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Invalid " + field);
        }
    }
}
