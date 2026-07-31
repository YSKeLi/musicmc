package dev.yskeli.musicmc.model;

import com.google.gson.annotations.SerializedName;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public record ResourceBundle(
        @SerializedName("bundle_id") String bundleId,
        int revision,
        @SerializedName("pack_id") String packId,
        @SerializedName("pack_file") String packFile,
        @SerializedName("pack_sha1") String packSha1,
        @SerializedName("pack_url") String packUrl,
        @SerializedName("size_bytes") long sizeBytes,
        boolean immutable) {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,96}");
    private static final Pattern SHA1_PATTERN = Pattern.compile("[a-f0-9]{40}");

    public ResourceBundle validated() {
        if (bundleId == null || !ID_PATTERN.matcher(bundleId).matches()) {
            throw new IllegalArgumentException("Invalid bundle_id");
        }
        if (revision < 1) {
            throw new IllegalArgumentException("Invalid bundle revision");
        }
        UUID.fromString(packId);
        if (packFile == null || packFile.isBlank() || packFile.contains("/") || packFile.contains("\\")) {
            throw new IllegalArgumentException("Invalid pack_file");
        }
        if (packSha1 == null || !SHA1_PATTERN.matcher(packSha1.toLowerCase(Locale.ROOT)).matches()) {
            throw new IllegalArgumentException("Invalid pack_sha1");
        }
        URI uri = URI.create(packUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Invalid pack_url");
        }
        if (sizeBytes < 0 || sizeBytes > 1024L * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid bundle size");
        }
        return this;
    }

    public UUID uuid() {
        return UUID.fromString(packId);
    }
}
