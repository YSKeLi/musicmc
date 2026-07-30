package dev.yskeli.musicmc.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public record PluginSettings(
        List<String> catalogUrls,
        List<String> downloadProxyPrefixes,
        String issueUrl,
        Duration syncInterval,
        Duration connectTimeout,
        Duration requestTimeout,
        Path privateKeyPath,
        Duration ticketLifetime,
        boolean forceResourcePacks,
        String resourcePackPrompt,
        float playbackRangeBlocks,
        float playbackPitch) {

    private static final String DOWNLOAD_PROXY = "https://gh.jasonzeng.dev/";

    public static PluginSettings load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        Path configuredKey = Path.of(config.getString("upload.private-key-file", "upload-ticket-private.pem"));
        Path keyPath = configuredKey.isAbsolute()
                ? configuredKey.normalize()
                : plugin.getDataFolder().toPath().resolve(configuredKey).normalize();

        LinkedHashSet<String> catalogUrls = new LinkedHashSet<>();
        catalogUrls.add(requireText(config, "github.catalog-url"));
        config.getStringList("github.catalog-fallback-urls").stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(catalogUrls::add);

        LinkedHashSet<String> downloadProxyPrefixes = new LinkedHashSet<>();
        config.getStringList("github.download-proxy-prefixes").stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(downloadProxyPrefixes::add);
        if (downloadProxyPrefixes.isEmpty()) {
            downloadProxyPrefixes.add(DOWNLOAD_PROXY);
        }

        return new PluginSettings(
                List.copyOf(catalogUrls),
                List.copyOf(downloadProxyPrefixes),
                requireText(config, "github.issue-url"),
                Duration.ofSeconds(bounded(config.getLong("sync.interval-seconds", 120), 30, 3600)),
                Duration.ofSeconds(bounded(config.getLong("sync.connect-timeout-seconds", 15), 3, 120)),
                Duration.ofSeconds(bounded(config.getLong("sync.request-timeout-seconds", 60), 10, 300)),
                keyPath,
                Duration.ofMinutes(bounded(config.getLong("upload.ticket-expiration-minutes", 15), 1, 60)),
                config.getBoolean("resource-packs.force", false),
                config.getString("resource-packs.prompt", "MusicMC song resources"),
                (float) bounded(config.getDouble("playback.range-blocks", 25.0), 1.0, 256.0),
                (float) bounded(config.getDouble("playback.pitch", 1.0), 0.5, 2.0));
    }

    private static String requireText(FileConfiguration config, String path) {
        String value = config.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required configuration value: " + path);
        }
        return value.trim();
    }

    private static long bounded(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double bounded(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
