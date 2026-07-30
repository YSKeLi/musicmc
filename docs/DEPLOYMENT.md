# MusicMC Deployment Guide

[English](DEPLOYMENT.md) | [简体中文](DEPLOYMENT.zh-CN.md)

This guide deploys MusicMC from a public fork: the Spigot plugin, static portal, Cloudflare Worker, GitHub Actions, and song catalog.

The source repository enables no Pages site or Worker and contains no production URL. Deployment occurs only when an administrator explicitly runs the bootstrap script or a manual deployment workflow.

## 1. Requirements

Install Git, Java 21, Node.js 22 with npm, GitHub CLI, and Cloudflare Wrangler. You also need a public GitHub repository and a Cloudflare account.

```powershell
gh auth login
npx wrangler login
```

The Minecraft server does not need an HTTP port, FRP website forwarding, a music-platform API, or any GitHub/Cloudflare token.

## 2. Credentials

Create a GitHub fine-grained PAT restricted to your MusicMC repository with:

- Contents: Read and write;
- Issues: Read and write;
- Metadata: Read-only.

The same value is stored as Worker Secret `GITHUB_TOKEN` and Actions Secret `MUSICMC_AUTOMATION_TOKEN`. Do not place it in source code, plugin configuration, screenshots, chat, or a remote URL.

For automated Worker redeployment, create a Cloudflare API Token with Workers Scripts: Edit and store it as `CLOUDFLARE_API_TOKEN`. Store the Cloudflare Account ID as `CLOUDFLARE_ACCOUNT_ID`.

MusicMC also generates a P-256 key pair:

- `upload-ticket-private.pem` stays only on the Minecraft server;
- `upload-ticket-public.pem` is committed to the self-hosted repository and installed as Worker binding `UPLOAD_PUBLIC_KEY`.

## 3. Bootstrap

Run from the repository root:

```powershell
.\scripts\bootstrap.ps1 -Repository OWNER/REPOSITORY
```

The script:

1. verifies required tools and authentication;
2. generates a new signing key pair;
3. reads tokens through hidden prompts;
4. tests and deploys the Worker;
5. configures Worker Secrets and variables;
6. configures repository Secrets, Variables, and labels;
7. enables GitHub Pages in Actions mode;
8. commits the public key and pushes it;
9. explicitly starts the manual Pages workflow.

The private key is written to `.local/upload-ticket-private.pem`, which is ignored by Git.

For a custom Worker domain already bound in Cloudflare:

```powershell
.\scripts\bootstrap.ps1 `
  -Repository OWNER/REPOSITORY `
  -UploadApiUrl https://upload.example.com
```

## 4. Repository configuration

Bootstrap creates these Actions Secrets:

```text
MUSICMC_AUTOMATION_TOKEN
CLOUDFLARE_API_TOKEN
CLOUDFLARE_ACCOUNT_ID
```

It also creates these Actions Variables:

```text
MUSICMC_WORKER_NAME
MUSICMC_UPLOAD_API_URL
MUSICMC_PAGES_ORIGIN
MUSICMC_PORTAL_URL
```

`MUSICMC_PAGES_ORIGIN` contains only scheme and host for CORS. `MUSICMC_PORTAL_URL` includes the full project path. `MUSICMC_UPLOAD_API_URL` is the Worker or custom-domain URL without a trailing slash.

## 5. Workflows

| Workflow | Trigger | Effect |
| --- | --- | --- |
| `CI` | push / pull request | Tests every component and scans for secrets |
| `Deploy upload portal` | manual | Deploys GitHub Pages |
| `Deploy upload Worker` | manual | Deploys the Worker and restores bindings |
| `Process music submission` | matching Issue | Validates, transcodes, publishes, and updates the catalog |
| `Release plugin` | explicit `v*` tag | Builds and publishes the plugin JAR |

Normal source pushes never deploy Pages or Worker.

## 6. Build and install the plugin

The default build is intentionally neutral:

```powershell
.\mvnw.cmd clean verify
```

For your deployment, inject your repository and portal URL:

```powershell
.\mvnw.cmd clean verify `
  -Dmusicmc.github.repository=OWNER/REPOSITORY `
  -Dmusicmc.portal.url=https://OWNER.example/REPOSITORY/
```

Place the JAR in the Spigot 1.21.11 server's `plugins` directory and copy `.local/upload-ticket-private.pem` to `plugins/MusicMC/upload-ticket-private.pem`. Fully restart the server; do not use `/reload`.

## 7. Manual Worker deployment

```powershell
cd github-upload-service\worker
npm ci
npm test
npx wrangler login
npx wrangler deploy --name <WORKER_NAME> `
  --var "ALLOWED_ORIGIN:https://portal.example.com" `
  --var "GITHUB_REPOSITORY:OWNER/REPOSITORY" `
  --var "GITHUB_BRANCH:main"
npx wrangler secret put GITHUB_TOKEN --name <WORKER_NAME>
npx wrangler secret put UPLOAD_PUBLIC_KEY --name <WORKER_NAME>
```

Set `MUSICMC_UPLOAD_API_URL`, then manually run `Deploy upload portal`.

## 8. Runtime API use

| Caller | API | Credential |
| --- | --- | --- |
| Minecraft plugin | GitHub Contents / Raw / jsDelivr | none |
| Upload portal | Worker `/upload` | signed server ticket |
| Worker | GitHub REST API | repository-scoped PAT |
| Actions | Releases, Issues, Git push | Actions token or configured PAT |
| Actions | Cloudflare Workers API | Cloudflare API Token |

## 9. Rotation and troubleshooting

Rotate a GitHub PAT by updating both `MUSICMC_AUTOMATION_TOKEN` and Worker `GITHUB_TOKEN`, then revoke the old token. Rotate signing keys with:

```powershell
.\scripts\bootstrap.ps1 -Repository OWNER/REPOSITORY -RotateUploadKey
```

- `Failed to fetch`: verify Worker DNS, HTTPS, custom-domain binding, and `ALLOWED_ORIGIN`.
- Worker 403: portal origin or ticket signature is wrong.
- Worker 503: a required binding is missing.
- Issue does not trigger processing: verify workflow state, PAT repository access, and the `[Music Upload] ` title prefix.
- Catalog timeout: adjust mirrors and timeouts in `plugins/MusicMC/config.yml`.
