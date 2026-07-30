# Contributing

[English](CONTRIBUTING.md) | [简体中文](CONTRIBUTING.zh-CN.md)

Use Java 21 and Node.js 22. Before opening a pull request, run:

```powershell
.\mvnw.cmd clean verify
node --test github-upload-service/tests/catalog.test.mjs github-upload-service/tests/portal.test.mjs
python -m unittest discover -s github-upload-service/tests -v
cd github-upload-service/worker
npm ci
npm test
```

Do not commit generated JARs, `node_modules`, Worker bundles, audio files, tokens or private keys. Keep changes scoped and add tests for ticket parsing, catalog behavior, upload validation and playback changes.
