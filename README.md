# MusicMC

[English](README.md) | [简体中文](README.zh-CN.md)

MusicMC is an open-source Spigot 1.21.11 plugin and GitHub automation stack for custom music discs. Players can submit a ZIP or a NetEase Cloud Music song ID, GitHub Actions validates and segments the audio, and the server distributes small resource bundles on demand.

## What it does

- Runs on Spigot 1.21.11 with Java 21; it does not require Paper or a client mod.
- Publishes submissions automatically with no manual approval stage.
- Allows unlimited lifetime submissions while limiting each player to one active job.
- Groups up to 20 songs per rolling resource bundle and loads only bundles a player needs.
- Splits audio into roughly five-second segments so players entering the 25-block range can join at the next boundary.
- Keeps only catalog metadata on the Minecraft server. Audio goes directly from GitHub Releases or an S3-compatible public endpoint to each client, never through FRP.
- Supports local song disable/enable, uploader bans, song replacement, storage diagnostics, and an in-game song browser.
- Supports a Cookie-backed NetEase adapter inside GitHub Actions. The Cookie is optional and remains a repository secret.

## Player commands

```text
/music
/music upload <command name>
/music netease <NetEase song ID> <command name>
/music search <query>
/music list [page]
/music status
/music cancel
/music pack
/getcd <command name>
```

See [Player guide](docs/PLAYER_GUIDE.md) or [玩家指南](docs/PLAYER_GUIDE.zh-CN.md).
Administrators should also read [Administrator guide](docs/ADMIN_GUIDE.md).

## Build

```bash
./mvnw --batch-mode clean verify
```

The shaded plugin is written to `target/MusicMC-2.0.0.jar`.

## Deploy

Start with [Deployment](docs/DEPLOYMENT.md) or [部署指南](docs/DEPLOYMENT.zh-CN.md). The repository contains the plugin, portal, Worker, Actions processors, catalog schema, tests, and bootstrap scripts. No production URL, personal server, FRP tunnel, private key, Cookie, or access token is hard-coded.

## Limits

- Without a client mod, a listener can join only at the next audio segment, not at an arbitrary sample position.
- NetEase access is unofficial and can fail because a Cookie expires, an account lacks playback access, or risk controls reject a GitHub-hosted runner.
- GitHub and object-storage quotas still apply to uploads and downloads.

## License

MIT. See [LICENSE](LICENSE) and [LICENSE.zh-CN.md](LICENSE.zh-CN.md).
