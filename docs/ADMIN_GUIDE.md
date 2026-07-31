# Administrator guide

[English](ADMIN_GUIDE.md) | [简体中文](ADMIN_GUIDE.zh-CN.md)

The `musicmc.admin` permission defaults to operators.

- `/music sync`: fetches and validates the catalog immediately. A failed fetch keeps the last valid catalog.
- `/music admin disable <command name>`: locally hides a song and prevents its existing discs from playing on this server.
- `/music admin enable <command name>`: removes the local disable override.
- `/music admin remove <command name>`: an alias for local disable. It intentionally does not delete remote Release assets or rewrite GitHub history.
- `/music admin replace upload <command name>`: creates an administrator-signed ZIP replacement. The publication keeps `song_id` and increments the revision.
- `/music admin replace netease <ID> <command name>`: does the same through the NetEase Actions adapter.
- `/music admin banupload <player>`: persists a local upload ban by player UUID.
- `/music admin unbanupload <player>`: removes that ban.
- `/music admin pending`: lists unexpired tickets issued locally. The GitHub Issues queue is the authoritative processing list.
- `/music admin uploader <player>`: lists catalog songs attributed to a Minecraft player.
- `/music admin storage`: reports local plugin metadata size and free disk space. Song audio is not retained locally.

Local disable and upload-ban state is stored in `plugins/MusicMC/admin-state.yml`. Do not edit it while the server is running.

For permanent cross-server removal, update `catalog.json` through a controlled repository change and publish a new catalog revision. Keep old versioned assets until clients and backups no longer require them.
