# 部署指南

[English](DEPLOYMENT.md) | [简体中文](DEPLOYMENT.zh-CN.md)

## 前置条件

- 一个启用 Actions、Issues、Releases 和 Pages 的 GitHub 仓库。
- Minecraft 服务器使用 Java 21 和 Spigot 1.21.11。
- 初始化电脑安装 Node.js 22+、GitHub CLI、npm 和 Wrangler。
- 浏览器自动上传需要 Cloudflare Worker；Worker 不转发歌曲下载流量。
- 可选：网易云 Cookie 和公开的 S3 兼容对象存储。

## GitHub 令牌

在以后改用仓库级 GitHub App 之前，使用只授权当前仓库、具有 Contents 和 Issues 读写权限的 Fine-grained token。它只保存为 Worker Secret `GITHUB_TOKEN` 和仓库 Secret `MUSICMC_AUTOMATION_TOKEN`，不能写入 `config.yml`、源代码、Pages 变量或插件目录。

## 自动初始化

先登录 `gh` 和 Wrangler，然后执行：

```powershell
.\scripts\bootstrap.ps1 -Repository OWNER/REPOSITORY
```

脚本会创建或复用签名密钥、更新公钥注册表、部署 Worker、写入 Secret、创建标签、启用 Pages，并显示投稿页地址和私钥路径。只把生成的私钥复制到：

```text
plugins/MusicMC/upload-ticket-private.pem
```

把脚本输出的 Key ID 写入服务器 `config.yml` 的 `upload.key-id`。执行 `./mvnw --batch-mode clean verify` 构建插件，安装 `target/MusicMC-2.0.0.jar` 后完整重启服务器。升级插件不要使用 `/reload`。

## 必需配置

Worker Secrets：

```text
GITHUB_TOKEN
UPLOAD_PUBLIC_KEY                 # 旧版单公钥回退
UPLOAD_PUBLIC_KEYS                # JSON 公钥注册表
```

仓库 Secrets：

```text
MUSICMC_AUTOMATION_TOKEN
CLOUDFLARE_API_TOKEN
CLOUDFLARE_ACCOUNT_ID
NETEASE_COOKIE                    # 可选
```

仓库 Variables：

```text
MUSICMC_WORKER_NAME
MUSICMC_UPLOAD_API_URL
MUSICMC_PAGES_ORIGIN
MUSICMC_PORTAL_URL
```

Pages 由 `.github/workflows/pages.yml` 部署。歌曲目录提交会自动触发，因此 `songs.html` 会自动跟随已发布歌曲，不再需要手动部署。

## 网易云 Cookie

把 `NETEASE_COOKIE` 添加为 Actions 仓库 Secret，不要添加到 Worker。工作流只把它交给 `netease-adapter.cjs`；没有该 Secret 时 ZIP 投稿仍正常。账号会话过期后替换 Secret。即使 Cookie 有效，账号播放权限或网易云风控仍可能让任务失败。

## S3 兼容存储

默认使用 GitHub Release。要改用 Cloudflare R2、阿里云 OSS、腾讯云 COS 或其他 S3 兼容服务，设置仓库 Variables：

```text
MUSICMC_STORAGE_BACKEND=s3
MUSICMC_S3_ENDPOINT_URL=https://...
MUSICMC_S3_BUCKET=bucket-name
MUSICMC_S3_PUBLIC_BASE_URL=https://public.example.com
MUSICMC_S3_PREFIX=musicmc              # 可选
MUSICMC_S3_REGION=auto                 # 按服务商填写
```

并设置仓库 Secrets：

```text
MUSICMC_S3_ACCESS_KEY_ID
MUSICMC_S3_SECRET_ACCESS_KEY
```

对象必须能通过 `MUSICMC_S3_PUBLIC_BASE_URL` 公开读取；凭证只给 Actions 写入。不要把公开地址回源到 Minecraft 服务器电脑，否则才会重新占用 FRP 和本机上行流量。

## 密钥轮换

1. 执行 `bootstrap.ps1 -RotateUploadKey -UploadKeyId key-YYYYMMDD`。
2. 先推送更新后的公钥注册表并重新部署 Worker，再修改服务器。
3. 把新私钥复制到插件目录，并设置相同的 `upload.key-id`。
4. 完整重启服务器。
5. 至少保留旧公钥一个凭证有效期，再删除旧项并重新部署。

## 更新

推送源码并等待 CI；Worker 代码或绑定变化时运行 Worker 部署工作流；替换插件 JAR 后完整重启。只修改目录时，Pages 会自动更新。
