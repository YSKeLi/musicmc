# Deployment

[English](DEPLOYMENT.md) | [简体中文](DEPLOYMENT.zh-CN.md)

## Prerequisites

- A GitHub repository with Actions, Issues, Releases, and Pages enabled.
- Java 21 and Spigot 1.21.11 for the Minecraft server.
- Node.js 22+, GitHub CLI, npm, and Wrangler for bootstrap.
- A Cloudflare Worker for direct browser upload. It does not proxy song downloads.
- Optional: a NetEase Cookie and/or public S3-compatible object storage.

## GitHub token

Until a repository GitHub App is adopted, use a fine-grained token restricted to this repository with Contents and Issues read/write. It is stored only as the Worker secret `GITHUB_TOKEN` and repository secret `MUSICMC_AUTOMATION_TOKEN`. Never put it in `config.yml`, source files, Pages variables, or the plugin directory.

## Automated setup

Authenticate `gh` and Wrangler, then run:

```powershell
.\scripts\bootstrap.ps1 -Repository OWNER/REPOSITORY
```

The script creates or reuses the signing key, updates the public-key registry, deploys the Worker, installs secrets, creates labels, enables Pages, and prints the portal URL and private-key path. It sets `MUSICMC_ENABLE_PAGES=true`; without that explicit repository variable the Pages workflow is skipped, so a source-only fork cannot create or replace a live site. Copy only the generated private key to:

```text
plugins/MusicMC/upload-ticket-private.pem
```

Set `upload.key-id` in the server `config.yml` to the key ID printed by bootstrap. Build the plugin with `./mvnw --batch-mode clean verify`, install `target/MusicMC-2.0.0.jar`, and restart the server. Do not use `/reload` for plugin upgrades.

## Required deployment values

Worker secrets:

```text
GITHUB_TOKEN
UPLOAD_PUBLIC_KEY                 # legacy single-key fallback
UPLOAD_PUBLIC_KEYS                # JSON key registry
```

Repository secrets:

```text
MUSICMC_AUTOMATION_TOKEN
CLOUDFLARE_API_TOKEN
CLOUDFLARE_ACCOUNT_ID
NETEASE_COOKIE                    # optional
```

Repository variables:

```text
MUSICMC_WORKER_NAME
MUSICMC_UPLOAD_API_URL
MUSICMC_PAGES_ORIGIN
MUSICMC_PORTAL_URL
```

Pages is deployed from `.github/workflows/pages.yml`. Catalog commits trigger it automatically, so `songs.html` follows published songs without a manual deployment.

## NetEase Cookie

Add `NETEASE_COOKIE` as an Actions repository secret. Do not add it to the Worker. The workflow passes it only to `netease-adapter.cjs`; ZIP submissions still work when the secret is absent. Replace the secret when the account session expires. The adapter can still fail because of account entitlements or NetEase risk controls.

## S3-compatible storage

GitHub Releases are the default. To use Cloudflare R2, Alibaba OSS, Tencent COS, or another S3-compatible provider, set:

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

The bucket objects must be publicly readable through `MUSICMC_S3_PUBLIC_BASE_URL`; credentials are needed only by Actions for writes. Do not configure a public URL that routes back to the Minecraft computer.

## Key rotation

1. Run bootstrap with `-RotateUploadKey -UploadKeyId key-YYYYMMDD`.
2. Push the updated public-key registry and redeploy the Worker before changing the server.
3. Copy the new private key to the plugin directory and set the matching `upload.key-id`.
4. Restart the server.
5. Keep the old public key in the registry for at least the ticket lifetime, then remove it and redeploy.

## Updating

Push source changes, wait for CI, run the Worker deploy workflow when Worker code or bindings changed, and replace the plugin JAR followed by a full restart. Catalog-only changes trigger Pages automatically.
