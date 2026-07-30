package dev.yskeli.musicmc.ticket;

import dev.yskeli.musicmc.model.Song;
import dev.yskeli.musicmc.model.SongNames;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PendingUploadStore {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public PendingUploadStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending-uploads.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        pruneExpired();
    }

    public synchronized void add(PendingUpload pending) {
        pruneExpired();
        String path = "uploads." + pending.nonce();
        yaml.set(path + ".player-uuid", pending.playerUuid().toString());
        yaml.set(path + ".song-name", pending.songName());
        yaml.set(path + ".expires-at", pending.expiresAt().getEpochSecond());
        save();
    }

    public synchronized boolean hasPendingName(String songName) {
        pruneExpired();
        String normalized = SongNames.normalize(songName);
        return all().stream().anyMatch(pending -> SongNames.normalize(pending.songName()).equals(normalized));
    }

    public synchronized List<PendingUpload> forPlayer(UUID playerUuid) {
        pruneExpired();
        return all().stream().filter(pending -> pending.playerUuid().equals(playerUuid)).toList();
    }

    public synchronized void removePublished(List<Song> songs) {
        List<String> publishedNames = songs.stream().map(Song::normalizedName).toList();
        ConfigurationSection section = yaml.getConfigurationSection("uploads");
        if (section == null) {
            return;
        }
        boolean changed = false;
        for (String nonce : new ArrayList<>(section.getKeys(false))) {
            String songName = section.getString(nonce + ".song-name", "");
            if (publishedNames.contains(SongNames.normalize(songName))) {
                yaml.set("uploads." + nonce, null);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    public synchronized void pruneExpired() {
        ConfigurationSection section = yaml.getConfigurationSection("uploads");
        if (section == null) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        boolean changed = false;
        for (String nonce : new ArrayList<>(section.getKeys(false))) {
            if (section.getLong(nonce + ".expires-at", 0) <= now) {
                yaml.set("uploads." + nonce, null);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private List<PendingUpload> all() {
        ConfigurationSection section = yaml.getConfigurationSection("uploads");
        if (section == null) {
            return List.of();
        }
        List<PendingUpload> uploads = new ArrayList<>();
        for (String nonce : section.getKeys(false)) {
            try {
                uploads.add(new PendingUpload(
                        nonce,
                        UUID.fromString(section.getString(nonce + ".player-uuid", "")),
                        section.getString(nonce + ".song-name", ""),
                        Instant.ofEpochSecond(section.getLong(nonce + ".expires-at"))));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Ignoring invalid pending upload entry: " + nonce);
            }
        }
        return uploads;
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save pending uploads", exception);
        }
    }
}
