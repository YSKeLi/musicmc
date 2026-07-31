# Player guide

[English](PLAYER_GUIDE.md) | [简体中文](PLAYER_GUIDE.zh-CN.md)

## Browse and get discs

- `/music`: opens the song browser. It includes all songs, recent uploads, your uploads, search, paging, and your pending-ticket count.
- `/music browse`: opens the same browser explicitly.
- `/music search <query>`: opens the browser with a song, artist, command-name, or uploader filter.
- `/music list [page]`: prints a compact paged list in chat.
- `/getcd <command name>`: gives you the selected disc and requests only its resource bundle. Tab completion lists valid command names.
- `/music pack`: removes and re-requests all current MusicMC bundles. Use it after a failed or declined resource-pack prompt.
- `/music cancel`: clears your locally issued, unexpired ticket. It does not cancel a GitHub job that is already running.

Accept the resource-pack prompt before putting a MusicMC disc in a jukebox. Audio is audible within 25 blocks and fades with distance. If you walk into range after playback began, MusicMC loads the required bundle and joins you at the next roughly five-second segment.

## Submit a ZIP

1. Run `/music upload <command name>`. Use `abc`, not `abc.mp3`; the command name is what players type in `/getcd abc`.
2. Open the signed link shown in chat.
3. Select a ZIP containing exactly one supported audio file.
4. Submit it and wait for the automatic GitHub job.
5. Check `/music status` and the task link. After the next catalog sync, the song appears in `/music` and `/getcd` completion.

The ZIP filename no longer has to match the command name. MusicMC assigns a safe internal filename automatically. Supported audio formats are MP3, M4A, AAC, WAV, FLAC, OGG, and OPUS. The ZIP limit is 25 MB, extracted audio limit is 100 MB, and duration limit is 10 minutes.

## Submit a NetEase song

1. Find the numeric song ID in a `music.163.com/song?id=...` link.
2. Run `/music netease <ID> <command name>`.
3. Open the signed link and submit the task. No local ZIP upload is needed.

This route is available only when the administrator configured `NETEASE_COOKIE`. A failed job usually means the Cookie expired, the account cannot play that track, or NetEase risk control rejected the Actions runner. It is safe to create a new submission after the previous task is closed.
If the plugin still reports an old local ticket after the failed Issue closed, run `/music cancel` before retrying.

## Queue rule

Your total number of submissions is unlimited. You may have only one active task at a time, and duplicate command names are rejected.

## Status

`/music status` shows catalog synchronization, whether ticket signing is configured, and your locally issued ticket that has not expired. GitHub remains the authoritative processing queue.
