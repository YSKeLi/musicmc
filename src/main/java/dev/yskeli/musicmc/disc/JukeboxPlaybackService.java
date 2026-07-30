package dev.yskeli.musicmc.disc;

import dev.yskeli.musicmc.config.PluginSettings;
import dev.yskeli.musicmc.model.Song;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

public final class JukeboxPlaybackService implements Listener {
    private static final String VANILLA_DISC_SOUND = "minecraft:music_disc.13";
    private static final float VANILLA_SOUND_RANGE_BLOCKS = 16.0F;

    private final JavaPlugin plugin;
    private final DiscService discs;
    private final float rangeBlocks;
    private final float packetVolume;
    private final float pitch;
    private final Map<JukeboxKey, Playback> active = new HashMap<>();

    public JukeboxPlaybackService(JavaPlugin plugin, DiscService discs, PluginSettings settings) {
        this.plugin = plugin;
        this.discs = discs;
        this.rangeBlocks = settings.playbackRangeBlocks();
        this.packetVolume = packetVolumeForRange(rangeBlocks);
        this.pitch = settings.playbackPitch();
        Bukkit.getScheduler().runTaskTimer(plugin, this::expireFinishedSongs, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.JUKEBOX || event.getHand() == null) {
            return;
        }
        Jukebox jukebox = (Jukebox) block.getState();
        Optional<Song> inserted = discs.findSong(jukebox.getRecord());
        Optional<Song> heldSong = discs.findSong(event.getItem());
        if (inserted.isEmpty() && heldSong.isEmpty()) {
            return;
        }

        // Let vanilla insert or eject the physical disc, then normalize playback
        // after the block entity has applied that interaction.
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
    public void onChunkLoad(ChunkLoadEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> scanChunk(event.getChunk()), 1L);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        List<JukeboxKey> keys = active.keySet().stream()
                .filter(key -> key.worldId().equals(chunk.getWorld().getUID()))
                .filter(key -> key.chunkX() == chunk.getX() && key.chunkZ() == chunk.getZ())
                .toList();
        keys.forEach(this::stop);
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
        Location source = block.getLocation().add(0.5, 0.5, 0.5);
        active.put(key, new Playback(song, System.nanoTime()));
        block.getWorld().playSound(source, song.soundKey(), SoundCategory.RECORDS, packetVolume, pitch);
    }

    private void stop(JukeboxKey key) {
        Playback playback = active.remove(key);
        if (playback == null) {
            return;
        }
        World world = Bukkit.getWorld(key.worldId());
        if (world == null) {
            return;
        }
        Location source = new Location(world, key.x() + 0.5, key.y() + 0.5, key.z() + 0.5);
        double radiusSquared = rangeBlocks * rangeBlocks;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(source) <= radiusSquared) {
                player.stopSound(playback.song().soundKey(), SoundCategory.RECORDS);
            }
        }
    }

    private void expireFinishedSongs() {
        long now = System.nanoTime();
        List<JukeboxKey> finished = active.entrySet().stream()
                .filter(entry -> (now - entry.getValue().startedNanos()) / 1_000_000_000.0
                        >= entry.getValue().song().durationSeconds())
                .map(Map.Entry::getKey)
                .toList();
        for (JukeboxKey key : finished) {
            stop(key);
            World world = Bukkit.getWorld(key.worldId());
            if (world != null && world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
                Block block = world.getBlockAt(key.x(), key.y(), key.z());
                if (block.getState() instanceof Jukebox jukebox) {
                    jukebox.stopPlaying();
                }
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

    static float packetVolumeForRange(float rangeBlocks) {
        // Positional sounds attenuate linearly to silence at 16 * volume blocks.
        return Math.max(1.0F, rangeBlocks / VANILLA_SOUND_RANGE_BLOCKS);
    }

    private static Optional<Location> jukeboxLocation(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Jukebox jukebox) {
            return Optional.of(jukebox.getLocation());
        }
        return Optional.empty();
    }

    private record Playback(Song song, long startedNanos) {}

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
