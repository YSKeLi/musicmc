package dev.yskeli.musicmc.resource;

import dev.yskeli.musicmc.config.PluginSettings;
import dev.yskeli.musicmc.library.SongLibrary;
import dev.yskeli.musicmc.model.ResourceBundle;
import dev.yskeli.musicmc.model.Song;
import dev.yskeli.musicmc.storage.DownloadUrlResolver;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
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
    private final Map<UUID, ResourceBundle> bundlesByPackId = new HashMap<>();
    private final Map<UUID, Set<UUID>> requestedByPlayer = new HashMap<>();
    private final Map<UUID, Set<UUID>> loadedByPlayer = new HashMap<>();
    private final Map<UUID, Map<UUID, Integer>> requestAttemptByPlayer = new HashMap<>();
    private final Map<UUID, Map<String, UUID>> bundleVersionByPlayer = new HashMap<>();
    private final List<BiConsumer<Player, ResourceBundle>> loadListeners = new ArrayList<>();

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
        clearPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearPlayer(event.getPlayer());
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        UUID packId = event.getID();
        ResourceBundle bundle = bundlesByPackId.get(packId);
        if (bundle == null) {
            return;
        }
        Set<UUID> requested = requestedByPlayer.getOrDefault(event.getPlayer().getUniqueId(), Set.of());
        if (!requested.contains(packId)) {
            return;
        }
        String status = event.getStatus().name();
        if (status.equals("SUCCESSFULLY_LOADED")) {
            loadedByPlayer.computeIfAbsent(event.getPlayer().getUniqueId(), ignored -> new HashSet<>()).add(packId);
            loadListeners.forEach(listener -> listener.accept(event.getPlayer(), bundle));
        } else if (status.equals("DECLINED") || status.equals("DISCARDED")) {
            markUnloaded(event.getPlayer(), packId);
            event.getPlayer().sendMessage(ChatColor.RED + "歌曲资源包未加载：" + bundle.bundleId());
        } else if (status.startsWith("FAILED") || status.equals("INVALID_URL")) {
            markUnloaded(event.getPlayer(), packId);
            int currentAttempt = requestAttemptByPlayer
                    .getOrDefault(event.getPlayer().getUniqueId(), Map.of())
                    .getOrDefault(packId, 0);
            if (sendPack(event.getPlayer(), bundle, currentAttempt + 1)) {
                event.getPlayer().sendMessage(ChatColor.YELLOW + "资源包下载失败，正在尝试备用地址："
                        + bundle.bundleId());
                return;
            }
            event.getPlayer().sendMessage(ChatColor.RED + "歌曲资源包加载失败：" + bundle.bundleId());
        }
    }

    public void addLoadListener(BiConsumer<Player, ResourceBundle> listener) {
        loadListeners.add(listener);
    }

    public boolean ensureLoaded(Player player, Song song) {
        if (hasLoaded(player, song)) {
            return true;
        }
        sendSong(player, song);
        return false;
    }

    public boolean sendSong(Player player, Song song) {
        ResourceBundle bundle = library.bundleFor(song).orElse(null);
        return bundle != null && sendBundle(player, bundle);
    }

    public void sendAll(Player player) {
        Set<String> sent = new LinkedHashSet<>();
        for (Song song : library.songs()) {
            if (sent.add(song.bundleId())) {
                sendSong(player, song);
            }
        }
    }

    public void resendAll(Player player) {
        removeSentPacks(player);
        clearPlayer(player);
        sendAll(player);
    }

    public void broadcastNewSongs(List<Song> songs) {
        if (songs.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(ChatColor.GREEN + "MusicMC 有 " + songs.size() + " 首歌曲新增或更新，可按需获取唱片。");
        }
    }

    public boolean hasLoaded(Player player, Song song) {
        return library.bundleFor(song)
                .map(ResourceBundle::uuid)
                .map(packId -> loadedByPlayer.getOrDefault(player.getUniqueId(), Set.of()).contains(packId))
                .orElse(false);
    }

    private boolean sendBundle(Player player, ResourceBundle bundle) {
        UUID packId = bundle.uuid();
        bundlesByPackId.put(packId, bundle);
        Map<String, UUID> versions = bundleVersionByPlayer
                .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        UUID oldPackId = versions.put(bundle.bundleId(), packId);
        if (oldPackId != null && !oldPackId.equals(packId)) {
            player.removeResourcePack(oldPackId);
            Set<UUID> requestedVersions = requestedByPlayer.get(player.getUniqueId());
            Set<UUID> loadedVersions = loadedByPlayer.get(player.getUniqueId());
            if (requestedVersions != null) {
                requestedVersions.remove(oldPackId);
            }
            if (loadedVersions != null) {
                loadedVersions.remove(oldPackId);
            }
        }
        Set<UUID> requested = requestedByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>());
        if (!requested.add(packId)) {
            return true;
        }
        if (sendPack(player, bundle, 0)) {
            return true;
        }
        requested.remove(packId);
        player.sendMessage(ChatColor.RED + "无法发送歌曲资源包：" + bundle.bundleId());
        return false;
    }

    private boolean sendPack(Player player, ResourceBundle bundle, int startAttempt) {
        List<URI> candidates = downloadUrls.candidates(bundle.packUrl());
        for (int attempt = startAttempt; attempt < candidates.size(); attempt++) {
            try {
                player.addResourcePack(
                        bundle.uuid(),
                        candidates.get(attempt).toASCIIString(),
                        HexFormat.of().parseHex(bundle.packSha1()),
                        settings.resourcePackPrompt(),
                        settings.forceResourcePacks());
                requestAttemptByPlayer
                        .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                        .put(bundle.uuid(), attempt);
                return true;
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Could not send resource bundle "
                        + bundle.bundleId() + ": " + exception.getMessage());
            }
        }
        return false;
    }

    private void removeSentPacks(Player player) {
        for (UUID packId : bundleVersionByPlayer.getOrDefault(player.getUniqueId(), Map.of()).values()) {
            player.removeResourcePack(packId);
        }
    }

    private void markUnloaded(Player player, UUID packId) {
        loadedByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).remove(packId);
    }

    private void clearPlayer(Player player) {
        requestedByPlayer.remove(player.getUniqueId());
        loadedByPlayer.remove(player.getUniqueId());
        requestAttemptByPlayer.remove(player.getUniqueId());
        bundleVersionByPlayer.remove(player.getUniqueId());
    }
}
