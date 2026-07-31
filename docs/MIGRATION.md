# Migration to MusicMC 2

[English](MIGRATION.md) | [简体中文](MIGRATION.zh-CN.md)

1. Back up the repository, `plugins/MusicMC`, and the old plugin JAR.
2. Stop the server. Do not upgrade with `/reload`.
3. Replace the JAR and merge the new `config.yml` keys, especially `upload.key-id`.
4. Deploy the updated Worker and Pages workflows.
5. Restart and run `/music sync` as an operator.

The Java plugin reads catalog schema v1 and migrates it in memory to v2. The publication scripts also migrate a v1 catalog before the next successful publish. Existing discs keep working because their persistent `song_id` is retained; the old `pack_sha1` item tag is ignored.

MusicMC 2 no longer downloads packs, source ZIP files, or extracted OGG files into `plugins/MusicMC/songs`. After a verified backup and successful migration, that old directory is no longer used. Do not delete it until the new catalog and at least one existing disc have been tested.

Command names no longer need to be valid Windows ZIP filenames, and ZIP filenames no longer need to match the command. `/music upload abc` remains the recommended form.

Catalog v2 adds `bundles`, song `revision`, `command_name`, `enabled`, segmented sounds, and structured source metadata. Replacing a song preserves its ID and advances its revision.
