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

1. If you know the numeric song ID, run `/music netease <ID>`, for example `/music netease 254132`.
2. If you have a complete URL, run `/music netease url <NetEase song URL>`, for example `/music netease url https://music.163.com/song?id=254132`.
3. Open the signed link. The page uses a signed metadata endpoint to display the NetEase song ID, title, artist, and generated disc command name automatically.
4. Confirm and submit. No local ZIP is needed. After publication, use the displayed `/getcd <disc command name>` command.

The metadata lookup never sends the NetEase Cookie to the page. The Cookie is available only to GitHub Actions when fetching audio. Audio processing requires the administrator to configure `NETEASE_COOKIE`. Failed Issues now report a more specific reason, such as an expired Cookie, missing playback permission, NetEase risk control, or a rejected download URL. It is safe to create a new submission after the previous task is closed.
If the plugin still reports an old local ticket after the failed Issue closed, run `/music cancel` before retrying.

## Queue rule

Your total number of submissions is unlimited. You may have only one active task at a time, and duplicate command names are rejected.

## Status

`/music status` shows catalog synchronization, whether ticket signing is configured, and your locally issued ticket that has not expired. GitHub remains the authoritative processing queue.
