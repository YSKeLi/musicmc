# MusicMC Player Guide

[English](PLAYER_GUIDE.md) | [简体中文](PLAYER_GUIDE.zh-CN.md)

## Uploading a song

For a song named `abc`:

1. Run `/music upload abc` in server chat. Do not include `.mp3`, `.flac`, or another audio extension.
2. Open the link returned by the server.
3. Create `abc.zip`. The ZIP filename must exactly match the song name in the command.
4. Put exactly one MP3, M4A, AAC, WAV, FLAC, OGG, or OPUS file in the ZIP.
5. When selecting a file, keep the cursor and file picker below **Music ZIP file**.
6. Select `abc.zip` and press **Upload and submit**.
7. Wait for processing. The server synchronizes its catalog every 120 seconds by default.
8. After publication, run `/getcd abc`.

## Limits

- ZIP size: at most 25 MB.
- Contents: exactly one supported audio file.
- Duration: at most 10 minutes.
- Encrypted ZIP files are not supported.
- Song names contain 1 to 48 characters.
- Upload links expire after 15 minutes by default.

## Commands

| Command | Purpose |
| --- | --- |
| `/music upload <name>` | Create a one-time upload link |
| `/music list [page]` | List published songs |
| `/music status` | Show catalog and pending-upload status |
| `/music pack` | Reload current song resource packs |
| `/music sync` | Let an administrator synchronize immediately |
| `/getcd <name>` | Get a disc with song-name completion |

## Playback range

Jukebox music is audible within 25 blocks by default. Volume decreases linearly with distance and reaches silence at 25 blocks.

## Troubleshooting

- Invalid link: run `/music upload <name>` again and use the new complete URL.
- Wrong archive name: `/music upload abc` requires exactly `abc.zip`, not `abc.mp3.zip`.
- Published song is missing: wait for synchronization or ask an administrator to run `/music sync`.
- Disc is silent: accept the server resource pack, stay within 25 blocks, and try `/music pack`.
