package dev.yskeli.musicmc.catalog;

import dev.yskeli.musicmc.config.PluginSettings;
import dev.yskeli.musicmc.library.SongLibrary;
import dev.yskeli.musicmc.model.Song;
import dev.yskeli.musicmc.model.SongCatalog;
import dev.yskeli.musicmc.model.SongCatalogCodec;
import dev.yskeli.musicmc.storage.HttpFileClient;
import dev.yskeli.musicmc.storage.SongStorage;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class CatalogSyncService {
    private static final long MAX_CATALOG_BYTES = 5L * 1024 * 1024;

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final SongLibrary library;
    private final SongStorage storage;
    private final HttpFileClient http;
    private final Consumer<List<Song>> updateListener;
    private final AtomicBoolean syncing = new AtomicBoolean();
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "MusicMC catalog sync");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Future<?> currentSync;
    private volatile String preferredCatalogUrl;

    public CatalogSyncService(
            JavaPlugin plugin,
            PluginSettings settings,
            SongLibrary library,
            SongStorage storage,
            HttpFileClient http,
            Consumer<List<Song>> updateListener) {
        this.plugin = plugin;
        this.settings = settings;
        this.library = library;
        this.storage = storage;
        this.http = http;
        this.updateListener = updateListener;
        this.preferredCatalogUrl = settings.catalogUrls().getFirst();
    }

    public void loadCache() {
        try {
            library.replace(storage.loadCachedCatalog());
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Could not load cached MusicMC catalog", exception);
        }
    }

    public void start() {
        long intervalTicks = settings.syncInterval().toSeconds() * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> syncNow(null), 20L, intervalTicks);
        syncNow(null);
    }

    public boolean syncNow(CommandSender requester) {
        if (shuttingDown.get()) {
            return false;
        }
        if (!syncing.compareAndSet(false, true)) {
            if (requester != null) {
                requester.sendMessage(ChatColor.YELLOW + "MusicMC 正在同步目录");
            }
            return false;
        }
        if (requester != null) {
            requester.sendMessage(ChatColor.GRAY + "MusicMC 开始同步歌曲目录...");
        }
        try {
            currentSync = executor.submit(() -> synchronize(requester));
        } catch (RejectedExecutionException exception) {
            syncing.set(false);
            return false;
        }
        return true;
    }

    public boolean isSyncing() {
        return syncing.get();
    }

    public void shutdown() {
        shuttingDown.set(true);
        Future<?> task = currentSync;
        if (task != null) {
            task.cancel(true);
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("MusicMC catalog worker did not stop within 5 seconds");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            syncing.set(false);
        }
    }

    private void synchronize(CommandSender requester) {
        try {
            CatalogDownload download = downloadCatalog();
            SongCatalog catalog = download.catalog();
            storage.saveCatalog(download.rawCatalog());

            java.util.Map<String, Integer> previousRevisions = library.allSongs().stream()
                    .collect(java.util.stream.Collectors.toMap(Song::songId, Song::revision));
            List<Song> newSongs = catalog.songs().stream()
                    .filter(Song::enabled)
                    .filter(song -> song.revision() > previousRevisions.getOrDefault(song.songId(), 0))
                    .toList();

            if (shuttingDown.get()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (shuttingDown.get()) {
                    return;
                }
                library.replace(catalog);
                updateListener.accept(newSongs);
                if (requester != null) {
                    requester.sendMessage(ChatColor.GREEN + "MusicMC 同步完成：" + library.size() + " 首可用歌曲");
                }
            });
        } catch (Exception exception) {
            if (!shuttingDown.get()) {
                plugin.getLogger().log(Level.WARNING, "MusicMC catalog synchronization failed", exception);
            }
            if (requester != null && !shuttingDown.get()) {
                Bukkit.getScheduler().runTask(plugin, () -> requester.sendMessage(
                        ChatColor.RED + "MusicMC 同步失败，请查看服务器日志"));
            }
        } finally {
            syncing.set(false);
        }
    }

    private CatalogDownload downloadCatalog() throws IOException, InterruptedException {
        LinkedHashSet<String> endpoints = new LinkedHashSet<>();
        endpoints.add(preferredCatalogUrl);
        endpoints.addAll(settings.catalogUrls());

        IOException failure = new IOException("All MusicMC catalog endpoints failed");
        int attempt = 0;
        for (String endpoint : endpoints) {
            attempt++;
            try {
                URI uri = URI.create(endpoint);
                String accept = isGitHubContentsApi(uri)
                        ? "application/vnd.github.raw+json"
                        : "application/json";
                String rawCatalog = http.getText(uri, MAX_CATALOG_BYTES, accept);
                SongCatalog catalog = SongCatalogCodec.parse(rawCatalog);

                if (!endpoint.equals(preferredCatalogUrl)) {
                    plugin.getLogger().info("MusicMC catalog endpoint switched to " + uri.getHost());
                }
                preferredCatalogUrl = endpoint;
                return new CatalogDownload(rawCatalog, catalog);
            } catch (InterruptedException exception) {
                throw exception;
            } catch (Exception exception) {
                failure.addSuppressed(exception);
                if (attempt < endpoints.size()) {
                    plugin.getLogger().warning("MusicMC catalog endpoint unavailable ("
                            + endpointHost(endpoint) + ", " + exception.getClass().getSimpleName()
                            + "); trying fallback");
                }
            }
        }
        throw failure;
    }

    private static boolean isGitHubContentsApi(URI uri) {
        return "api.github.com".equalsIgnoreCase(uri.getHost())
                && uri.getPath().contains("/contents/");
    }

    private static String endpointHost(String endpoint) {
        try {
            String host = URI.create(endpoint).getHost();
            return host == null ? "invalid URL" : host;
        } catch (IllegalArgumentException ignored) {
            return "invalid URL";
        }
    }

    private record CatalogDownload(String rawCatalog, SongCatalog catalog) {}
}
