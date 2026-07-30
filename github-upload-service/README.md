# MusicMC upload service

[English](README.md) | [简体中文](README.zh-CN.md)

This directory contains the static upload portal, Cloudflare Worker, public
ticket key, audio processor and tests. It is designed to run inside the same
repository as the Spigot plugin and root `catalog.json`.

Deployment and credential instructions are maintained in
[`../docs/DEPLOYMENT.md`](../docs/DEPLOYMENT.md). The Worker never
stores a token in source code; `GITHUB_TOKEN` and `UPLOAD_PUBLIC_KEY` are
Cloudflare bindings configured during deployment.

Run the component tests from the repository root:

```powershell
node --test github-upload-service/tests/catalog.test.mjs github-upload-service/tests/portal.test.mjs
python -m unittest discover -s github-upload-service/tests -v
cd github-upload-service/worker
npm ci
npm test
```
