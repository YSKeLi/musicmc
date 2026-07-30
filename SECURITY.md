# Security Policy

## Supported version

Security fixes are applied to the latest `main` branch and the latest tagged release.

## Reporting

Do not open a public Issue containing credentials, private keys, server logs with tokens, or an unpatched vulnerability. Contact the repository owner privately through the security advisory feature.

## Secrets

The repository must never contain:

- `upload-ticket-private.pem`;
- GitHub personal access tokens;
- Cloudflare API tokens or Global API Keys;
- `.dev.vars`, `.env`, `.secrets`, `.local` or Wrangler credential files.

CI runs `scripts/check-repository.py` to reject common token formats, private-key PEM blocks and generated/private directories.

Required deployment values are stored as follows:

| Value | Storage |
| --- | --- |
| Upload P-256 private key | Minecraft server only |
| Upload P-256 public key | Repository and Worker binding |
| GitHub fine-grained PAT | Worker secret and GitHub Actions secret |
| Cloudflare API token | GitHub Actions secret |
| Cloudflare Account ID | GitHub Actions secret |
| Worker URL and Pages URLs | GitHub Actions variables |

Rotate a credential immediately when it has been pasted into chat, committed, printed in CI, included in a command-line URL, or shared outside the intended administrator group.
