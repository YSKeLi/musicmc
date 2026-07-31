# MusicMC 投稿服务

[English](README.md) | [简体中文](README.zh-CN.md)

这个目录包含静态投稿网页、Cloudflare Worker、GitHub Actions 音源适配器、目录发布器、滚动资源包构建器及其测试。

```text
docs/       GitHub Pages 投稿页和歌曲目录
worker/     验证服务器签名凭证并直接接收浏览器上传
actions/    只在 GitHub Actions 中运行的音源适配器
scripts/    校验、转码、分片、打包、发布和迁移工具
tests/      网页和处理流程的自动测试
config/     示例公钥和多公钥注册表
```

Worker 不传输已发布的音频。玩家客户端下载资源包时，会直接访问 GitHub Releases 或配置的 S3 兼容对象存储，因此不会占用 Minecraft 服务器的 FRP 流量。

公开源码中的配置都是示例值。执行 `scripts/deploy.ps1` 后，脚本会把实际运行所需文件发布到独立仓库，把投稿网页发布到独立 Pages 仓库，并生成只指向执行者服务的 `config.js`。

所需绑定、Secrets、可选网易云 Cookie 和 S3 兼容存储配置见[部署指南](../docs/DEPLOYMENT.zh-CN.md)。
