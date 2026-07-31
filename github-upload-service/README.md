# MusicMC upload service

[English](README.md) | [简体中文](README.zh-CN.md)

This directory contains the static portal, Cloudflare Worker, GitHub Actions source adapter, catalog publisher, rolling resource-bundle builder, and their tests.

```text
docs/       GitHub Pages portal and song catalog
worker/     browser upload and GitHub Issue creation
actions/    pinned NetEase adapter used only by Actions
scripts/    validation, transcoding, bundling, and catalog v2 tools
config/     public signing keys only
tests/      portal and Python tests
```

The Worker accepts `/upload` for ZIP streams and `/netease` for JSON song IDs. It never serves music files. Actions processes every `music-pending` Issue automatically; no approval label is used.

Run tests from the repository root:

```bash
node --test github-upload-service/tests/*.test.mjs
python3 -m unittest discover -s github-upload-service/tests -v
cd github-upload-service/worker && npm ci && npm test
```

See [Deployment](../docs/DEPLOYMENT.md) for bindings, secrets, optional NetEase Cookie, and S3-compatible storage.
