package dev.yskeli.musicmc;

import dev.yskeli.musicmc.catalog.CatalogSyncService;
import dev.yskeli.musicmc.command.GetCdCommand;
import dev.yskeli.musicmc.command.MusicCommand;
import dev.yskeli.musicmc.config.PluginSettings;
import dev.yskeli.musicmc.disc.DiscService;
import dev.yskeli.musicmc.disc.JukeboxPlaybackService;
import dev.yskeli.musicmc.library.SongLibrary;
import dev.yskeli.musicmc.resource.ResourcePackService;
import dev.yskeli.musicmc.storage.HttpFileClient;
import dev.yskeli.musicmc.storage.DownloadUrlResolver;
import dev.yskeli.musicmc.storage.SongStorage;
import dev.yskeli.musicmc.ticket.PendingUploadStore;
import dev.yskeli.musicmc.ticket.UploadTicketSigner;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MusicMCPlugin extends JavaPlugin {
    private CatalogSyncService catalogSync;
    private JukeboxPlaybackService playback;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            PluginSettings settings = PluginSettings.load(this);
            SongLibrary library = new SongLibrary();
            HttpFileClient http = new HttpFileClient(settings.connectTimeout(), settings.requestTimeout());
            DownloadUrlResolver downloadUrls = new DownloadUrlResolver(settings.downloadProxyPrefixes());
            SongStorage storage = new SongStorage(getDataFolder().toPath(), http, downloadUrls);
            PendingUploadStore pendingUploads = new PendingUploadStore(this);
            Optional<UploadTicketSigner> signer = loadSigner(settings);

            DiscService discs = new DiscService(this, library);
            ResourcePackService resourcePacks = new ResourcePackService(this, settings, library, downloadUrls);
            playback = new JukeboxPlaybackService(this, discs, settings);
            catalogSync = new CatalogSyncService(
                    this,
                    settings,
                    library,
                    storage,
                    http,
                    newSongs -> {
                        pendingUploads.removePublished(newSongs);
                        resourcePacks.broadcastNewSongs(newSongs);
                    });

            MusicCommand musicCommand = new MusicCommand(
                    settings, library, pendingUploads, signer, catalogSync, resourcePacks);
            GetCdCommand getCdCommand = new GetCdCommand(library, discs, resourcePacks);
            registerCommand("music", musicCommand, musicCommand);
            registerCommand("getcd", getCdCommand, getCdCommand);
            getServer().getPluginManager().registerEvents(resourcePacks, this);
            getServer().getPluginManager().registerEvents(playback, this);

            catalogSync.loadCache();
            catalogSync.start();
            Bukkit.getScheduler().runTaskLater(this, playback::scanLoadedChunks, 1L);
            getLogger().info("MusicMC enabled with " + library.size() + " cached songs.");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "MusicMC could not start", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (catalogSync != null) {
            catalogSync.shutdown();
        }
        if (playback != null) {
            playback.shutdown();
        }
    }

    private Optional<UploadTicketSigner> loadSigner(PluginSettings settings) {
        if (!Files.isRegularFile(settings.privateKeyPath())) {
            getLogger().warning("Upload signing key not found at " + settings.privateKeyPath()
                    + "; /music upload is disabled until the key is installed.");
            return Optional.empty();
        }
        try {
            return Optional.of(UploadTicketSigner.load(settings.privateKeyPath(), settings.ticketLifetime()));
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Could not load upload signing key; uploads are disabled", exception);
            return Optional.empty();
        }
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor,
            org.bukkit.command.TabCompleter completer) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing command in plugin.yml: " + name);
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }
}
