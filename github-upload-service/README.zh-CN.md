# MusicMC 上传服务

[English](README.md) | [简体中文](README.zh-CN.md)

此目录包含静态上传网页、Cloudflare Worker、公开凭证公钥、音频处理器和测试。它与 Spigot 插件及根目录 `catalog.json` 一起运行在同一个自托管仓库中。

部署和凭证说明见 [`../docs/DEPLOYMENT.zh-CN.md`](../docs/DEPLOYMENT.zh-CN.md)。Worker 不会在源码中保存令牌；`GITHUB_TOKEN` 和 `UPLOAD_PUBLIC_KEY` 必须由部署者配置为 Cloudflare 绑定。

从仓库根目录运行组件测试：

```powershell
node --test github-upload-service/tests/catalog.test.mjs github-upload-service/tests/portal.test.mjs
python -m unittest discover -s github-upload-service/tests -v
cd github-upload-service/worker
npm ci
npm test
```
