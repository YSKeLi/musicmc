package dev.yskeli.musicmc.disc;

import dev.yskeli.musicmc.library.SongLibrary;
import dev.yskeli.musicmc.model.Song;
import java.util.List;
import java.util.Optional;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscService {
    private final SongLibrary library;
    private final NamespacedKey songIdKey;
    private final NamespacedKey packSha1Key;

    public DiscService(JavaPlugin plugin, SongLibrary library) {
        this.library = library;
        this.songIdKey = new NamespacedKey(plugin, "song_id");
        this.packSha1Key = new NamespacedKey(plugin, "pack_sha1");
    }

    public ItemStack createDisc(Song song) {
        ItemStack item = new ItemStack(Material.MUSIC_DISC_13);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + song.displayName());
        meta.setLore(List.of(
                ChatColor.GRAY + "上传者: " + song.minecraftPlayerName(),
                ChatColor.GRAY + "时长: " + formatDuration(song.durationSeconds()),
                ChatColor.DARK_GRAY + "MusicMC / " + song.songId()));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(songIdKey, PersistentDataType.STRING, song.songId());
        data.set(packSha1Key, PersistentDataType.STRING, song.packSha1());
        item.setItemMeta(meta);
        return item;
    }

    public Optional<Song> findSong(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        String songId = data.get(songIdKey, PersistentDataType.STRING);
        String packSha1 = data.get(packSha1Key, PersistentDataType.STRING);
        if (songId == null || packSha1 == null) {
            return Optional.empty();
        }
        return library.findById(songId).filter(song -> song.packSha1().equalsIgnoreCase(packSha1));
    }

    public boolean isCustomDisc(ItemStack item) {
        return findSong(item).isPresent();
    }

    private static String formatDuration(double seconds) {
        long rounded = Math.round(seconds);
        return "%d:%02d".formatted(rounded / 60, rounded % 60);
    }
}
