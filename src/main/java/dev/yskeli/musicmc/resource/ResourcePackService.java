package dev.yskeli.musicmc.resource;

import dev.yskeli.musicmc.config.PluginSettings;
import dev.yskeli.musicmc.library.SongLibrary;
import dev.yskeli.musicmc.model.Song;
import dev.yskeli.musicmc.storage.DownloadUrlResolver;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ResourcePackService implements Listener {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final SongLibrary library;
    private final DownloadUrlResolver downloadUrls;
    private final Map<UUID, Song> songsByPackId = new HashMap<>();
    private final Map<UUID, Set<UUID>> requestedByPlayer = new HashMap<>();
    private final Map<UUID, Set<UUID>> loadedByPlayer = new HashMap<>();
    private final Map<UUID, Map<UUID, Integer>> requestAttemptByPlayer = new HashMap<>();

    public ResourcePackService(
            JavaPlugin plugin,
            PluginSettings settings,
            SongLibrary library,
            DownloadUrlResolver downloadUrls) {
        this.plugin = plugin;
        this.settings = settings;
        this.library = library;
        this.downloadUrls = downloadUrls;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendAll(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        requestedByPlayer.remove(event.getPlayer().getUniqueId());
        loadedByPlayer.remove(event.getPlayer().getUniqueId());
        requestAttemptByPlayer.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        UUID packId = event.getID();
        Song song = songsByPackId.get(packId);
        if (song == null) {
            return;
        }
        String status = event.getStatus().name();
        if (status.equals("SUCCESSFULLY_LOADED")) {
            loadedByPlayer.computeIfAbsent(event.getPlayer().getUniqueId(), ignored -> new HashSet<>()).add(packId);
        } else if (status.equals("DECLINED")) {
            loadedByPlayer.computeIfAbsent(event.getPlayer().getUniqueId(), ignored -> new HashSet<>()).remove(packId);
            event.getPlayer().sendMessage(ChatColor.RED + "歌曲资源包加载失败：" + song.displayName());
        } else if (status.startsWith("FAILED") || status.equals("INVALID_URL")) {
            loadedByPlayer.computeIfAbsent(event.getPlayer().getUniqueId(), ignored -> new HashSet<>()).remove(packId);
            int currentAttempt = requestAttemptByPlayer
                    .getOrDefault(event.getPlayer().getUniqueId(), Map.of())
                    .getOrDefault(packId, 0);
            if (sendPack(event.getPlayer(), song, packId, currentAttempt + 1)) {
                event.getPlayer().sendMessage(ChatColor.YELLOW + "代理下载失败，正在尝试 GitHub 直连：" + song.displayName());
                return;
            }
            event.getPlayer().sendMessage(ChatColor.RED + "歌曲资源包加载失败：" + song.displayName());
        }
    }

    public void sendAll(Player player) {
        send(player, library.songs());
    }

    public void resendAll(Player player) {
        requestedByPlayer.remove(player.getUniqueId());
        loadedByPlayer.remove(player.getUniqueId());
        requestAttemptByPlayer.remove(player.getUniqueId());
        sendAll(player);
    }

    public void broadcastNewSongs(List<Song> songs) {
        if (songs.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player, songs);
            player.sendMessage(ChatColor.GREEN + "MusicMC 新增 " + songs.size() + " 首歌曲资源");
        }
    }

    public boolean hasLoaded(Player player, Song song) {
        return loadedByPlayer
                .getOrDefault(player.getUniqueId(), Set.of())
                .contains(packId(song));
    }

    private void send(Player player, List<Song> songs) {
        Set<UUID> requested = requestedByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>());
        for (Song song : songs) {
            UUID packId = packId(song);
            songsByPackId.put(packId, song);
            if (!requested.add(packId)) {
                continue;
            }
            if (!sendPack(player, song, packId, 0)) {
                requested.remove(packId);
                player.sendMessage(ChatColor.RED + "无法发送歌曲资源包：" + song.displayName());
            }
        }
    }

    private boolean sendPack(Player player, Song song, UUID packId, int startAttempt) {
        List<URI> candidates = downloadUrls.candidates(song.packUrl());
        for (int attempt = startAttempt; attempt < candidates.size(); attempt++) {
            try {
                player.addResourcePack(
                        packId,
                        candidates.get(attempt).toASCIIString(),
                        HexFormat.of().parseHex(song.packSha1()),
                        settings.resourcePackPrompt(),
                        settings.forceResourcePacks());
                requestAttemptByPlayer
                        .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                        .put(packId, attempt);
                return true;
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Could not send resource pack URL for "
                        + song.songId() + ": " + exception.getMessage());
            }
        }
        return false;
    }

    public static UUID packId(Song song) {
        String value = "musicmc:" + song.songId() + ':' + song.packSha1();
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
