import assert from "node:assert/strict";
import test from "node:test";
import { commandForSong, filterSongs, formatDuration, parseCatalog } from "../docs/catalog.mjs";

const songs = parseCatalog({
  schema_version: 2,
  bundles: [],
  songs: [
    { command_name: "qing-tian", display_name: "晴天", artist: "周杰伦", enabled: true,
      minecraft_player_name: "Steve", duration_seconds: 243.2, source: { kind: "netease" } },
    { command_name: "abc", display_name: "abc", enabled: true,
      minecraft_player_name: "Alex", duration_seconds: 61, source: { kind: "upload" } },
    { command_name: "hidden", display_name: "Hidden", enabled: false, duration_seconds: 1 },
  ],
});

test("parses, sorts, and hides disabled v2 songs", () => {
  assert.deepEqual(songs.map((song) => song.displayName), ["晴天", "abc"]);
  assert.equal(formatDuration(songs.find((song) => song.displayName === "abc").durationSeconds), "1:01");
});

test("builds the exact lowercase getcd command from command_name", () => {
  assert.equal(commandForSong(songs[0].commandName), "/getcd qing-tian");
});

test("filters by song, artist, command, and uploader", () => {
  assert.deepEqual(filterSongs(songs, "周杰伦").map((song) => song.displayName), ["晴天"]);
  assert.deepEqual(filterSongs(songs, "alex").map((song) => song.displayName), ["abc"]);
});

test("keeps v1 catalogs readable during migration", () => {
  assert.equal(parseCatalog({ schema_version: 1, songs: [{ display_name: "old" }] })[0].commandName, "old");
  assert.throws(() => parseCatalog({ schema_version: 3, songs: [] }));
});
