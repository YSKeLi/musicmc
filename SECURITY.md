# Security

[English](SECURITY.md) | [简体中文](SECURITY.zh-CN.md)

- The Minecraft server stores only its P-256 private upload-signing key. It never stores a GitHub token, object-storage credential, NetEase Cookie, or Cloudflare credential.
- Public keys are intentionally committed in `upload-ticket-public-keys.json`; private keys are excluded from the repository.
- The portal has no secret. The Worker holds a repository-scoped GitHub token and enforces exact CORS origin, request size, signature, duplicate-name, nonce, per-player concurrency, and global queue checks.
- Actions re-verifies tickets, validates archive paths and compression ratios, limits file count, size, type, and duration, and invokes FFmpeg only after validation.
- `NETEASE_COOKIE` belongs only in an Actions repository secret. The adapter never prints it or passes it to media CDN hosts.
- S3 write credentials belong only in Actions secrets. Public clients receive only versioned read URLs.
- Logs and Issues must not contain tokens, Cookies, private keys, or signed portal URLs.

Temporary ZIP submissions are stored in a public GitHub prerelease until processing cleanup runs. Use an external private ingestion service instead of the bundled Worker if even short-lived public staging is unacceptable.

Rotate upload keys using the staged procedure in [Deployment](docs/DEPLOYMENT.md). Revoke and replace any credential that was pasted into chat, committed, printed in a log, or otherwise exposed.

Report vulnerabilities privately to the repository owner. Include affected version, reproduction steps, and impact; do not attach real credentials.
