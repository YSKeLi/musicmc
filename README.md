# MusicMC

[English](README.md) | [简体中文](README.zh-CN.md)

MusicMC is a self-hosted custom music-disc system for Spigot 1.21.11. It combines a Java plugin, a static upload portal, a Cloudflare Worker, and GitHub Actions that validate audio, build resource packs, publish immutable song releases, and update a public catalog.

This repository is a deployment template. It contains no live service URL, production song catalog, private key, personal access token, or enabled Pages deployment. Deployment workflows are manual until an administrator explicitly configures a fork.

## Features

- `/music upload <name>` creates a short-lived P-256 signed upload link.
- Players upload one supported audio file inside `<name>.zip` without needing a GitHub account.
- GitHub Actions validate and transcode MP3, M4A, AAC, WAV, FLAC, OGG, or OPUS audio.
- `/getcd <name>` gives a custom disc and supports song-name completion.
- Jukebox audio fades linearly to silence over 25 blocks by default.
- Catalog synchronization retries GitHub API, Raw, jsDelivr, and configured download proxies.
- There is no per-player upload count limit; duplicate names and reused tickets are rejected.

## Repository layout

```text
src/                              Spigot plugin and tests
github-upload-service/docs/       Static portal and song list
github-upload-service/worker/     Cloudflare upload Worker
github-upload-service/scripts/    Audio validation and publishing
github-upload-service/config/     Replaceable ticket public key
scripts/                           Bootstrap and security tools
.github/workflows/                CI and manual deployment workflows
catalog.json                      Empty public catalog template
```

## Build

Java 21 is required.

```powershell
.\mvnw.cmd clean verify
```

On Linux or macOS:

```bash
./mvnw clean verify
```

The plugin is written to `target/MusicMC-1.0.0.jar`. The default artifact contains `OWNER/REPOSITORY` and `https://example.com/musicmc/` placeholders; configure your deployment before installing it on a production server.

## Self-hosting

Install Git, Java 21, Node.js 22, GitHub CLI, and Wrangler, then authenticate:

```powershell
gh auth login
npx wrangler login
.\scripts\bootstrap.ps1 -Repository OWNER/REPOSITORY
```

The bootstrap script generates a fresh signing key pair, deploys a Worker, writes repository-scoped Secrets and Variables, enables Pages, pushes the public key, and explicitly starts the manual Pages workflow. Nothing deploys merely because this source repository is cloned or updated.

See [Deployment](docs/DEPLOYMENT.md), [Architecture](docs/ARCHITECTURE.md), and [Migration](docs/MIGRATION.md).

## Player commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/music upload <name>` | `musicmc.upload` | Create a one-time upload link; omit the audio extension |
| `/music list [page]` | none | List published songs |
| `/music status` | none | Show catalog and pending-upload status |
| `/music pack` | none | Re-send current resource packs |
| `/music sync` | `musicmc.admin` | Synchronize the catalog immediately |
| `/getcd <name>` | `musicmc.getcd` | Get a disc with song-name completion |

The player guide is available in [English](docs/PLAYER_GUIDE.md) and [简体中文](docs/PLAYER_GUIDE.zh-CN.md).

## Automation safety

- `CI` runs on pushes and pull requests but creates no external service.
- `Deploy upload portal` and `Deploy upload Worker` are manual-only workflows.
- `Process music submission` runs only after a configured service creates a matching Issue.
- `Release plugin` runs only when an administrator explicitly pushes a `v*` tag.

## License

MusicMC is released under the [MIT License](LICENSE). An [unofficial Chinese translation](LICENSE.zh-CN.md) is provided for reference.
