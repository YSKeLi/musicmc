package dev.yskeli.musicmc.disc;

import dev.yskeli.musicmc.library.SongLibrary;
import dev.yskeli.musicmc.model.Song;
import java.util.ArrayList;
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
    private final NamespacedKey revisionKey;

    public DiscService(JavaPlugin plugin, SongLibrary library) {
        this.library = library;
        this.songIdKey = new NamespacedKey(plugin, "song_id");
        this.revisionKey = new NamespacedKey(plugin, "revision");
    }

    public ItemStack createDisc(Song song) {
        ItemStack item = new ItemStack(Material.MUSIC_DISC_13);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + song.displayLabel());
        List<String> lore = new ArrayList<>();
        if (song.minecraftPlayerName() != null && !song.minecraftPlayerName().isBlank()) {
            lore.add(ChatColor.GRAY + "上传者: " + song.minecraftPlayerName());
        }
        lore.add(ChatColor.GRAY + "时长: " + formatDuration(song.durationSeconds()));
        lore.add(ChatColor.DARK_GRAY + "MusicMC / " + song.songId() + " / v" + song.revision());
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(songIdKey, PersistentDataType.STRING, song.songId());
        data.set(revisionKey, PersistentDataType.INTEGER, song.revision());
        item.setItemMeta(meta);
        return item;
    }

    public Optional<Song> findSong(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String songId = item.getItemMeta().getPersistentDataContainer()
                .get(songIdKey, PersistentDataType.STRING);
        return songId == null ? Optional.empty() : library.findEnabledById(songId);
    }

    public boolean isCustomDisc(ItemStack item) {
        return findSong(item).isPresent();
    }

    private static String formatDuration(double seconds) {
        long rounded = Math.round(seconds);
        return "%d:%02d".formatted(rounded / 60, rounded % 60);
    }
}
