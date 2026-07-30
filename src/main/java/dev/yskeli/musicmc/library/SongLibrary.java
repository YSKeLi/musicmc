package dev.yskeli.musicmc.library;

import dev.yskeli.musicmc.model.Song;
import dev.yskeli.musicmc.model.SongNames;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SongLibrary {
    private volatile Map<String, Song> byId = Map.of();
    private volatile Map<String, Song> byName = Map.of();

    public synchronized void replace(Collection<Song> songs) {
        Map<String, Song> ids = new LinkedHashMap<>();
        Map<String, Song> names = new LinkedHashMap<>();
        for (Song song : songs.stream().sorted(Comparator.comparing(Song::displayName)).toList()) {
            if (ids.putIfAbsent(song.songId(), song) != null) {
                throw new IllegalArgumentException("Duplicate song ID: " + song.songId());
            }
            if (names.putIfAbsent(song.normalizedName(), song) != null) {
                throw new IllegalArgumentException("Duplicate song name: " + song.displayName());
            }
        }
        byId = Map.copyOf(ids);
        byName = Map.copyOf(names);
    }

    public Optional<Song> findById(String songId) {
        return Optional.ofNullable(byId.get(songId));
    }

    public Optional<Song> findByName(String displayName) {
        return Optional.ofNullable(byName.get(SongNames.normalize(displayName)));
    }

    public List<Song> songs() {
        return new ArrayList<>(byId.values());
    }

    public int size() {
        return byId.size();
    }
}
