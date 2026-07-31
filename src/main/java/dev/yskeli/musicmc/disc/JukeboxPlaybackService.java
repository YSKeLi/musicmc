package dev.yskeli.musicmc.disc;

import dev.yskeli.musicmc.config.PluginSettings;
import dev.yskeli.musicmc.model.Song;
import dev.yskeli.musicmc.model.SoundSegment;
import dev.yskeli.musicmc.resource.ResourcePackService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

public final class JukeboxPlaybackService implements Listener {
    private static final String VANILLA_DISC_SOUND = "minecraft:music_disc.13";
    private static final float VANILLA_SOUND_RANGE_BLOCKS = 16.0F;

    private final JavaPlugin plugin;
    private final DiscService discs;
    private final ResourcePackService resourcePacks;
    private final float rangeBlocks;
    private final float packetVolume;
    private final float pitch;
    private final Map<JukeboxKey, Playback> active = new HashMap<>();

    public JukeboxPlaybackService(
            JavaPlugin plugin,
            DiscService discs,
            ResourcePackService resourcePacks,
            PluginSettings settings) {
        this.plugin = plugin;
        this.discs = discs;
        this.resourcePacks = resourcePacks;
        this.rangeBlocks = settings.playbackRangeBlocks();
        this.packetVolume = packetVolumeForRange(rangeBlocks);
        this.pitch = settings.playbackPitch();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPlayback, 1L, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.JUKEBOX || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Jukebox jukebox = (Jukebox) block.getState();
        if (discs.findSong(jukebox.getRecord()).isEmpty() && discs.findSong(event.getItem()).isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> normalizeAndStart(block), 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        stopIfJukebox(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::stopIfJukebox);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::stopIfJukebox);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        List<Location> locations = new ArrayList<>();
        jukeboxLocation(event.getSource()).ifPresent(locations::add);
        jukeboxLocation(event.getDestination()).ifPresent(locations::add);
        if (!locations.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> locations.forEach(location -> {
                Block block = location.getBlock();
                if (block.getType() == Material.JUKEBOX) {
                    normalizeAndStart(block);
                }
            }), 1L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        active.values().forEach(playback -> playback.audience().remove(event.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> scanChunk(event.getChunk()), 1L);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        active.keySet().stream()
                .filter(key -> key.worldId().equals(chunk.getWorld().getUID()))
                .filter(key -> key.chunkX() == chunk.getX() && key.chunkZ() == chunk.getZ())
                .toList()
                .forEach(this::stop);
    }

    public void scanLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        }
    }

    public void shutdown() {
        new ArrayList<>(active.keySet()).forEach(this::stop);
    }

    private void scanChunk(Chunk chunk) {
        for (BlockState tile : chunk.getTileEntities()) {
            if (tile instanceof Jukebox jukebox && discs.isCustomDisc(jukebox.getRecord())) {
                normalizeAndStart(jukebox.getBlock());
            }
        }
    }

    private void normalizeAndStart(Block block) {
        if (block.getType() != Material.JUKEBOX) {
            stop(JukeboxKey.from(block));
            return;
        }
        Jukebox jukebox = (Jukebox) block.getState();
        Optional<Song> song = discs.findSong(jukebox.getRecord());
        if (song.isEmpty()) {
            stop(JukeboxKey.from(block));
            return;
        }
        jukebox.stopPlaying();
        stopVanillaNearby(block.getLocation());
        start(block, song.get());
    }

    private void start(Block block, Song song) {
        JukeboxKey key = JukeboxKey.from(block);
        stop(key);
        active.put(key, new Playback(song, System.nanoTime(), -1, new HashSet<>()));
        tick(key, active.get(key), System.nanoTime());
    }

    private void tickPlayback() {
        long now = System.nanoTime();
        for (Map.Entry<JukeboxKey, Playback> entry : new ArrayList<>(active.entrySet())) {
            tick(entry.getKey(), entry.getValue(), now);
        }
    }

    private void tick(JukeboxKey key, Playback playback, long now) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
            stop(key);
            return;
        }
        Block block = world.getBlockAt(key.x(), key.y(), key.z());
        if (!(block.getState() instanceof Jukebox jukebox)
                || discs.findSong(jukebox.getRecord()).map(Song::songId)
                        .filter(playback.song().songId()::equals).isEmpty()) {
            stop(key);
            return;
        }

        double elapsedSeconds = (now - playback.startedNanos()) / 1_000_000_000.0;
        int segmentIndex = segmentIndexAt(playback.song().segments(), elapsedSeconds * pitch);
        if (segmentIndex < 0) {
            stop(key);
            jukebox.stopPlaying();
            return;
        }

        Location source = new Location(world, key.x() + 0.5, key.y() + 0.5, key.z() + 0.5);
        Set<UUID> inRange = new HashSet<>();
        double radiusSquared = rangeBlocks * rangeBlocks;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(source) <= radiusSquared) {
                inRange.add(player.getUniqueId());
                resourcePacks.ensureLoaded(player, playback.song());
            }
        }

        SoundSegment oldSegment = playback.currentSegment() >= 0
                ? playback.song().segments().get(playback.currentSegment()) : null;
        for (UUID playerId : new HashSet<>(playback.audience())) {
            if (!inRange.contains(playerId)) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && oldSegment != null) {
                    player.stopSound(oldSegment.soundKey(), SoundCategory.RECORDS);
                }
                playback.audience().remove(playerId);
            }
        }

        if (segmentIndex != playback.currentSegment()) {
            if (oldSegment != null) {
                for (UUID playerId : playback.audience()) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        player.stopSound(oldSegment.soundKey(), SoundCategory.RECORDS);
                    }
                }
            }
            playback.audience().clear();
            SoundSegment current = playback.song().segments().get(segmentIndex);
            for (UUID playerId : inRange) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && resourcePacks.hasLoaded(player, playback.song())) {
                    player.playSound(source, current.soundKey(), SoundCategory.RECORDS, packetVolume, pitch);
                    playback.audience().add(playerId);
                }
            }
            playback.currentSegment(segmentIndex);
        }
    }

    private void stop(JukeboxKey key) {
        Playback playback = active.remove(key);
        if (playback == null || playback.currentSegment() < 0) {
            return;
        }
        String soundKey = playback.song().segments().get(playback.currentSegment()).soundKey();
        for (UUID playerId : playback.audience()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.stopSound(soundKey, SoundCategory.RECORDS);
            }
        }
    }

    private void stopIfJukebox(Block block) {
        if (block.getType() == Material.JUKEBOX) {
            stop(JukeboxKey.from(block));
        }
    }

    private void stopVanillaNearby(Location location) {
        double radiusSquared = rangeBlocks * rangeBlocks;
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= radiusSquared) {
                player.stopSound(VANILLA_DISC_SOUND, SoundCategory.RECORDS);
            }
        }
    }

    static int segmentIndexAt(List<SoundSegment> segments, double elapsedSeconds) {
        if (elapsedSeconds < 0) {
            return 0;
        }
        double cursor = 0;
        for (int index = 0; index < segments.size(); index++) {
            cursor += segments.get(index).durationSeconds();
            if (elapsedSeconds < cursor) {
                return index;
            }
        }
        return -1;
    }

    static float packetVolumeForRange(float rangeBlocks) {
        return Math.max(1.0F, rangeBlocks / VANILLA_SOUND_RANGE_BLOCKS);
    }

    private static Optional<Location> jukeboxLocation(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof Jukebox jukebox ? Optional.of(jukebox.getLocation()) : Optional.empty();
    }

    private static final class Playback {
        private final Song song;
        private final long startedNanos;
        private int currentSegment;
        private final Set<UUID> audience;

        private Playback(Song song, long startedNanos, int currentSegment, Set<UUID> audience) {
            this.song = song;
            this.startedNanos = startedNanos;
            this.currentSegment = currentSegment;
            this.audience = audience;
        }

        Song song() {
            return song;
        }

        long startedNanos() {
            return startedNanos;
        }

        int currentSegment() {
            return currentSegment;
        }

        void currentSegment(int value) {
            currentSegment = value;
        }

        Set<UUID> audience() {
            return audience;
        }
    }

    private record JukeboxKey(UUID worldId, int x, int y, int z) {
        static JukeboxKey from(Block block) {
            return new JukeboxKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        int chunkX() {
            return x >> 4;
        }

        int chunkZ() {
            return z >> 4;
        }
    }
}
