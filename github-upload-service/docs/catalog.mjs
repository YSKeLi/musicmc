export const CATALOG_URLS = ["./catalog.json"];

export function parseCatalog(value) {
  if (!value || !new Set([1, 2]).has(value.schema_version) || !Array.isArray(value.songs)) {
    throw new Error("Invalid MusicMC catalog");
  }
  return value.songs
    .filter((song) => song && song.enabled !== false
      && typeof song.display_name === "string" && song.display_name.trim())
    .map((song) => ({
      commandName: String(song.command_name || song.display_name).trim(),
      displayName: song.display_name.trim(),
      artist: typeof song.artist === "string" ? song.artist.trim() : "",
      uploader: typeof song.minecraft_player_name === "string" ? song.minecraft_player_name : "",
      durationSeconds: Number.isFinite(song.duration_seconds) ? song.duration_seconds : null,
      sourceKind: typeof song.source?.kind === "string" ? song.source.kind : "upload",
    }))
    .sort((left, right) => left.displayName.localeCompare(right.displayName, "zh-CN"));
}

export function commandForSong(commandName) {
  return `/getcd ${commandName}`;
}

export function filterSongs(songs, query) {
  const normalized = query.trim().toLocaleLowerCase("zh-CN");
  if (!normalized) return songs;
  return songs.filter((song) =>
    `${song.displayName}\n${song.artist}\n${song.commandName}\n${song.uploader}`
      .toLocaleLowerCase("zh-CN").includes(normalized));
}

export function formatDuration(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) return "";
  const rounded = Math.round(seconds);
  return `${Math.floor(rounded / 60)}:${String(rounded % 60).padStart(2, "0")}`;
}
