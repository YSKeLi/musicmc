package dev.yskeli.musicmc.admin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdminStateStore {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public AdminStateStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "admin-state.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized Set<String> disabledSongIds() {
        return Set.copyOf(new HashSet<>(yaml.getStringList("disabled-songs")));
    }

    public synchronized void setSongDisabled(String songId, boolean disabled) {
        Set<String> values = new HashSet<>(yaml.getStringList("disabled-songs"));
        if (disabled) values.add(songId); else values.remove(songId);
        yaml.set("disabled-songs", values.stream().sorted().toList());
        save();
    }

    public synchronized boolean isUploadBanned(UUID playerUuid) {
        return yaml.getStringList("upload-bans").contains(playerUuid.toString());
    }

    public synchronized void setUploadBanned(UUID playerUuid, boolean banned) {
        Set<String> values = new HashSet<>(yaml.getStringList("upload-bans"));
        if (banned) values.add(playerUuid.toString()); else values.remove(playerUuid.toString());
        yaml.set("upload-bans", values.stream().sorted().toList());
        save();
    }

    public File dataDirectory() {
        return file.getParentFile();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save MusicMC admin state", exception);
        }
    }
}
