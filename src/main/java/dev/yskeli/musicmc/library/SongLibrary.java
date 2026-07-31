package dev.yskeli.musicmc.library;

import dev.yskeli.musicmc.model.ResourceBundle;
import dev.yskeli.musicmc.model.Song;
import dev.yskeli.musicmc.model.SongCatalog;
import dev.yskeli.musicmc.model.SongNames;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SongLibrary {
    private volatile Map<String, Song> byId = Map.of();
    private volatile Map<String, Song> byCommandName = Map.of();
    private volatile Map<String, ResourceBundle> bundlesById = Map.of();
    private volatile Set<String> locallyDisabled = Set.of();

    public synchronized void replace(SongCatalog catalog) {
        catalog.validated();
        Map<String, Song> ids = new LinkedHashMap<>();
        Map<String, Song> commands = new LinkedHashMap<>();
        for (Song song : catalog.songs().stream()
                .sorted(Comparator.comparing(Song::displayLabel, String.CASE_INSENSITIVE_ORDER))
                .toList()) {
            if (ids.putIfAbsent(song.songId(), song) != null) {
                throw new IllegalArgumentException("Duplicate song ID: " + song.songId());
            }
            if (commands.putIfAbsent(song.normalizedCommandName(), song) != null) {
                throw new IllegalArgumentException("Duplicate command name: " + song.commandName());
            }
        }
        byId = Map.copyOf(ids);
        byCommandName = Map.copyOf(commands);
        bundlesById = catalog.bundlesById();
    }

    public Optional<Song> findById(String songId) {
        return Optional.ofNullable(byId.get(songId));
    }

    public Optional<Song> findEnabledById(String songId) {
        return findById(songId).filter(this::isEnabled);
    }

    public Optional<Song> findByName(String commandName) {
        return Optional.ofNullable(byCommandName.get(SongNames.normalize(commandName))).filter(this::isEnabled);
    }

    public Optional<ResourceBundle> bundleFor(Song song) {
        return Optional.ofNullable(bundlesById.get(song.bundleId()));
    }

    public List<Song> songs() {
        return byId.values().stream().filter(this::isEnabled).toList();
    }

    public List<Song> allSongs() {
        return new ArrayList<>(byId.values());
    }

    public List<ResourceBundle> bundles() {
        return new ArrayList<>(bundlesById.values());
    }

    public int size() {
        return songs().size();
    }

    public synchronized void setLocallyDisabled(Set<String> songIds) {
        locallyDisabled = Set.copyOf(songIds);
    }

    public synchronized void setLocallyDisabled(String songId, boolean disabled) {
        java.util.HashSet<String> updated = new java.util.HashSet<>(locallyDisabled);
        if (disabled) updated.add(songId); else updated.remove(songId);
        locallyDisabled = Set.copyOf(updated);
    }

    public boolean isEnabled(Song song) {
        return song.enabled() && !locallyDisabled.contains(song.songId());
    }
}
