# 安全策略

[English](SECURITY.md) | [简体中文](SECURITY.zh-CN.md)

## 支持版本

安全修复应用于最新 `main` 分支和最新正式版本。

## 报告问题

不要在公开 Issue 中发布凭证、私钥、包含令牌的服务器日志或尚未修复的漏洞。请通过 GitHub Security Advisory 私下联系仓库维护者。

## 敏感信息

仓库不得包含：

- `upload-ticket-private.pem`；
- GitHub Personal Access Token；
- Cloudflare API Token 或 Global API Key；
- `.dev.vars`、`.env`、`.secrets`、`.local` 或 Wrangler 凭证文件。

CI 会运行 `scripts/check-repository.py`，拒绝常见令牌格式、私钥 PEM、生成目录和私有目录。

| 内容 | 保存位置 |
| --- | --- |
| 上传 P-256 私钥 | 仅 Minecraft 服务器 |
| 上传 P-256 公钥 | 自托管仓库和 Worker 绑定 |
| GitHub Fine-grained PAT | Worker Secret 和 GitHub Actions Secret |
| Cloudflare API Token | GitHub Actions Secret |
| Cloudflare Account ID | GitHub Actions Secret |
| Worker 与网页 URL | GitHub Actions Variables |

凭证一旦出现在聊天、提交、CI 输出、命令行 URL 或非管理员群体中，应立即轮换。
