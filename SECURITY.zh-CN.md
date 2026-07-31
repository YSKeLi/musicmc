# 安全说明

[English](SECURITY.md) | [简体中文](SECURITY.zh-CN.md)

- Minecraft 服务器只保存用于签发投稿凭证的 P-256 私钥，不保存 GitHub Token、对象存储凭证、网易云 Cookie 或 Cloudflare 凭证。
- `upload-ticket-public-keys.json` 中的公钥可以公开提交；私钥禁止进入仓库。
- 网页没有 Secret。Worker 保存仓库级 GitHub Token，并校验固定 CORS 来源、请求大小、签名、重复名称、nonce、玩家并发和全服队列。
- Actions 会再次验签，检查 ZIP 路径、压缩比、文件数量、大小、类型和时长，通过后才运行 FFmpeg。
- `NETEASE_COOKIE` 只能保存为 Actions 仓库 Secret。适配器不会打印 Cookie，也不会把 Cookie 发给音频 CDN 主机。
- S3 写入凭证只能保存为 Actions Secret；客户端只获得带版本的公开读取地址。
- 日志和 Issues 不能包含 Token、Cookie、私钥或带签名凭证的完整投稿链接。

ZIP 投稿会短时间保存在公开的 GitHub 预发布中，处理结束后由工作流清理。如果不能接受任何短期公开暂存，应把 bundled Worker 替换为自有的私有接收服务。

按照[部署指南](docs/DEPLOYMENT.zh-CN.md)分阶段轮换上传密钥。任何曾粘贴到聊天、提交到仓库、打印到日志或以其他方式暴露的凭证，都应立即吊销并替换。

安全问题请私下报告给仓库所有者，并提供受影响版本、复现步骤和影响；不要附带真实凭证。
