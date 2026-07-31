# Deployment

[English](DEPLOYMENT.md) | [简体中文](DEPLOYMENT.zh-CN.md)

The public MusicMC source repository is a template. It does not contain or point to the maintainer's live catalog, portal, Worker, Cookie, keys, or Minecraft server. The deployment script creates or updates separate runtime services for the person running it.

## Prerequisites

- PowerShell 7, Git, and GitHub CLI (`gh`).
- Java 21 for building the Spigot 1.21.11 plugin.
- Node.js 22+ with npm and npx.
- Python 3.
- A GitHub account and a Cloudflare account with Workers enabled.

The script invokes Wrangler through npx, so a global Wrangler installation is not required.

## Credentials collected first

`scripts/deploy.ps1` asks for and validates all credentials before it creates, deletes, or updates an external service:

1. `GH_TOKEN`: a GitHub token that can create repositories and manage repository contents, workflows, Actions secrets/variables, Issues, Releases, and Pages. Deleting an existing user-site repository also requires repository deletion permission.
2. `CLOUDFLARE_API_TOKEN`: a token scoped to the target account with Workers Scripts edit permission.
3. `CLOUDFLARE_ACCOUNT_ID`: the target Cloudflare account ID.
4. `NETEASE_COOKIE`: optional; press Enter to keep NetEase importing disabled.

Secret input is hidden. Tokens and the Cookie are never written to source files, the plugin JAR, Pages, or `config.yml`. The GitHub token is installed as the runtime repository and Worker secrets because those services need it after deployment.

You may set `GH_TOKEN`, `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, and optionally `NETEASE_COOKIE` as process environment variables for non-interactive deployment. Do not put them in a checked-in `.env` file.

## Automatic deployment

Run from the source checkout:

```powershell
./scripts/deploy.ps1 -Mode Automatic
```

The GitHub login is read from the validated token. No account name or production URL is hard-coded. Automatic mode plans these resources:

| Resource | Default |
| --- | --- |
| Runtime repository | `<user>/MusicMC-Uploads` |
| Primary Pages repository | `<user>/<user>.github.io` |
| Primary portal | `https://<user>.github.io/` |
| Worker name | `musicmc-upload` |
| New Workers account subdomain | `<user>-musicmc` |
| Desired new Worker URL | `https://musicmc-upload.<user>-musicmc.workers.dev` |

If `<user>/<user>.github.io` already exists, the script asks for one of these actions:

- `DELETE`: type the complete repository name, review the final plan, then permanently delete and recreate it for MusicMC.
- `FALLBACK`: preserve the existing site and use `<user>/<user>mcmusic.github.io`. GitHub treats this as a project site, so its URL is `https://<user>.github.io/<user>mcmusic.github.io/`.
- `ABORT`: make no external changes.

If the fallback repository already exists, choose `UPDATE`, `NEW`, or `ABORT`. `NEW` finds an unused numbered repository. If the Worker name already exists, choose `REPLACE`, `NEW`, or `ABORT` in the same way.

A Cloudflare account has one shared `workers.dev` account subdomain. If the account already has one, MusicMC preserves it to avoid breaking other Workers; therefore the final Worker URL may use that existing subdomain rather than `<user>-musicmc`.

After discovery, the script prints the exact repository names, portal URL, Worker URL, deletion decision, and catalog-migration decision. No external mutation occurs until you type `DEPLOY`.

## Manual deployment

Manual mode uses explicitly supplied repositories and portal URL while still automating setup, secret installation, Worker deployment, Pages publishing, and plugin building:

```powershell
./scripts/deploy.ps1 -Mode Manual `
  -Repository OWNER/MUSIC-RUNTIME `
  -PortalRepository OWNER/PORTAL `
  -PortalUrl https://owner.github.io/portal/ `
  -WorkerName musicmc-upload
```

Use `-MigrateCatalogNow` when an existing schema-v1 catalog should be committed as schema v2 immediately. Without it, the catalog is validated but left unchanged; the plugin and portal can still read schema v1.

Use plan mode to test names and URLs without requesting credentials or changing services:

```powershell
./scripts/deploy.ps1 -Mode Automatic -GitHubOwner example `
  -CloudflareSubdomain example-musicmc -PlanOnly -NonInteractive
```

For unattended deployment, provide credentials as environment variables and add `-NonInteractive`. Permanent deletion additionally requires both `-ReplaceExistingUserSite` and `-AllowRepositoryDeletion`. Replacing an existing Worker requires `-ReplaceExistingWorker`.

## What the script deploys

The runtime repository receives only the files needed by GitHub Actions and the Worker, not local dependency or build directories. Existing `catalog.json` and published release assets are preserved. The deployment includes:

- Issue-triggered ZIP and NetEase processing.
- FFmpeg transcoding and five-second audio segmentation.
- Rolling resource-bundle publishing.
- Catalog v1-to-v2 migration support.
- Cloudflare Worker source and secrets.
- Static upload and song-list portal in its own Pages repository.
- Repository labels, Actions secrets, and variables.
- A server-specific plugin JAR built against the selected runtime repository and portal URL.

If a deployment stops halfway because a network service is unavailable, correct the cause and run the same command again. Repository commits and configuration updates are designed to be repeatable.

## Signing key and plugin installation

The upload signing private key defaults to `.local/upload-ticket-private.pem`, which is ignored by Git. If it exists, the script derives the public key and deploys that public key. If a runtime repository already has a public key but the matching private key is missing, deployment stops instead of silently rotating the server identity.

The final plugin is written to `output/MusicMC-server.jar` unless `-PluginOutputPath` is supplied. Install it as:

```text
plugins/MusicMC.jar
plugins/MusicMC/upload-ticket-private.pem
```

The plugin creates `plugins/MusicMC/config.yml` on first start and adds newly introduced default settings to an older config without replacing existing values. Set `upload.key-id` to the deployment's `UploadKeyId` (default: `default`). Replace the JAR and perform a full server restart; do not use `/reload` for plugin upgrades.

To build only a server-specific JAR without deploying services:

```powershell
./scripts/build-plugin.ps1 `
  -Repository OWNER/MUSIC-RUNTIME `
  -PortalUrl https://owner.github.io/portal/
```

## Installed configuration

Worker secrets:

```text
GITHUB_TOKEN
UPLOAD_PUBLIC_KEYS
```

Runtime repository secrets:

```text
MUSICMC_AUTOMATION_TOKEN
CLOUDFLARE_API_TOKEN
CLOUDFLARE_ACCOUNT_ID
NETEASE_COOKIE                    # optional
```

Runtime repository variables:

```text
MUSICMC_WORKER_NAME
MUSICMC_UPLOAD_API_URL
MUSICMC_PAGES_ORIGIN
MUSICMC_PORTAL_URL
MUSICMC_CATALOG_URL
```

The portal's generated `config.js` points to the separate runtime catalog. The public source repository remains neutral and is never used as the operator's live service by default.

## NetEase Cookie

The optional `NETEASE_COOKIE` is used only by the GitHub Actions adapter. ZIP submissions work without it. Replace the repository secret when the session expires. NetEase access is unofficial and can still fail because of account playback rights, region restrictions, or risk controls on GitHub-hosted runners.

## Optional S3-compatible storage

GitHub Releases are the default public download source. To use Cloudflare R2, Alibaba OSS, Tencent COS, or another S3-compatible provider, set these runtime repository variables after deployment:

```text
MUSICMC_STORAGE_BACKEND=s3
MUSICMC_S3_ENDPOINT_URL=https://...
MUSICMC_S3_BUCKET=bucket-name
MUSICMC_S3_PUBLIC_BASE_URL=https://public.example.com
MUSICMC_S3_PREFIX=musicmc              # optional
MUSICMC_S3_REGION=auto                 # provider-specific
```

and repository secrets:

```text
MUSICMC_S3_ACCESS_KEY_ID
MUSICMC_S3_SECRET_ACCESS_KEY
```

Clients download audio/resource bundles directly from GitHub Releases or the configured object-storage public URL. These downloads do not pass through the Minecraft server or consume its FRP tunnel unless the public storage URL is deliberately routed back to that computer.
