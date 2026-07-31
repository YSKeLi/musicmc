# MusicMC 投稿服务

[English](README.md) | [简体中文](README.zh-CN.md)

本目录包含静态投稿页、Cloudflare Worker、GitHub Actions 来源适配器、目录发布器、滚动资源包构建器和测试。

```text
docs/       GitHub Pages 投稿页与歌曲列表
worker/     浏览器上传和创建 GitHub Issue
actions/    只在 Actions 内运行的固定版本网易云适配器
scripts/    校验、转码、分包和目录 v2 工具
config/     只包含公开签名公钥
tests/      网页与 Python 测试
```

Worker 的 `/upload` 接收 ZIP 流，`/netease` 接收 JSON 歌曲 ID；Worker 不提供音乐下载。Actions 会自动处理每个 `music-pending` Issue，不使用人工批准标签。

在仓库根目录运行测试：

```bash
node --test github-upload-service/tests/*.test.mjs
python3 -m unittest discover -s github-upload-service/tests -v
cd github-upload-service/worker && npm ci && npm test
```

绑定、Secret、可选网易云 Cookie 和 S3 兼容存储见[部署指南](../docs/DEPLOYMENT.zh-CN.md)。
