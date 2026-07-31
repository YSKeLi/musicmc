# 部署指南

[English](DEPLOYMENT.md) | [简体中文](DEPLOYMENT.zh-CN.md)

公开的 MusicMC 源码仓库只是模板，不包含也不指向维护者正在使用的歌曲目录、投稿网页、Worker、Cookie、密钥或 Minecraft 服务器。部署脚本会为执行脚本的人创建或更新独立的实际服务。

## 前置条件

- PowerShell 7、Git 和 GitHub CLI（`gh`）。
- Java 21，用于构建 Spigot 1.21.11 插件。
- Node.js 22+，包含 npm 和 npx。
- Python 3。
- 已启用 Workers 的 GitHub 账号和 Cloudflare 账号。

脚本通过 npx 调用 Wrangler，不要求全局安装 Wrangler。

## 先收集并验证凭据

`scripts/deploy.ps1` 会先询问并验证全部凭据，在此之前不会创建、删除或修改任何外部服务：

1. `GH_TOKEN`：能创建仓库，并能管理仓库 Contents、Workflows、Actions Secrets/Variables、Issues、Releases 和 Pages 的 GitHub 令牌。删除已有的用户主页仓库还需要删除仓库权限。
2. `CLOUDFLARE_API_TOKEN`：只授权目标账号并具有 Workers Scripts 编辑权限的 Cloudflare API Token。
3. `CLOUDFLARE_ACCOUNT_ID`：目标 Cloudflare 账号 ID。
4. `NETEASE_COOKIE`：可选；直接回车表示不启用网易云导入。

Secret 输入不会显示。令牌和 Cookie 不会写进源码、插件 JAR、Pages 或 `config.yml`。部署完成后，GitHub 令牌会作为实际运行仓库和 Worker 的 Secret 保存，因为后续自动投稿需要使用它。

无人值守部署时，可以把 `GH_TOKEN`、`CLOUDFLARE_API_TOKEN`、`CLOUDFLARE_ACCOUNT_ID` 和可选的 `NETEASE_COOKIE` 放入当前进程的环境变量。不要把它们写进会提交的 `.env` 文件。

## 自动部署

在源码目录执行：

```powershell
./scripts/deploy.ps1 -Mode Automatic
```

GitHub 用户名来自已经验证的令牌，脚本没有写死任何账号或生产网址。自动模式默认规划以下资源：

| 资源 | 默认值 |
| --- | --- |
| 实际歌曲仓库 | `<用户名>/MusicMC-Uploads` |
| 首选 Pages 仓库 | `<用户名>/<用户名>.github.io` |
| 首选投稿网页 | `https://<用户名>.github.io/` |
| Worker 名称 | `musicmc-upload` |
| 新建的 Workers 账号子域名 | `<用户名>-musicmc` |
| 期望的新 Worker 地址 | `https://musicmc-upload.<用户名>-musicmc.workers.dev` |

如果 `<用户名>/<用户名>.github.io` 已存在，脚本会让你选择：

- `DELETE`：再次完整输入仓库名，然后查看最终计划；确认部署后永久删除并重建这个仓库给 MusicMC 使用。
- `FALLBACK`：保留原有网站，改用 `<用户名>/<用户名>mcmusic.github.io`。GitHub 会把它视为项目站点，所以实际地址是 `https://<用户名>.github.io/<用户名>mcmusic.github.io/`。
- `ABORT`：退出，不修改任何外部服务。

如果备用仓库也已存在，可选择 `UPDATE`、`NEW` 或 `ABORT`；`NEW` 会查找未占用的带编号仓库。Worker 名称已存在时，同样选择 `REPLACE`、`NEW` 或 `ABORT`。

每个 Cloudflare 账号只有一个由所有 Worker 共用的 `workers.dev` 账号子域名。如果账号已经设置过，MusicMC 会保留它，避免影响其他 Worker；因此最终 Worker 地址可能使用现有子域名，而不是 `<用户名>-musicmc`。

查询完成后，脚本会显示准确的实际歌曲仓库、Pages 仓库、投稿网页、Worker 地址、仓库删除决定和目录迁移决定。只有输入 `DEPLOY` 后，脚本才会修改外部服务。

## 手动部署

手动模式由你指定实际歌曲仓库、Pages 仓库和网页地址，其余的仓库初始化、Secret、Worker、Pages 和插件构建仍然自动完成：

```powershell
./scripts/deploy.ps1 -Mode Manual `
  -Repository OWNER/MUSIC-RUNTIME `
  -PortalRepository OWNER/PORTAL `
  -PortalUrl https://owner.github.io/portal/ `
  -WorkerName musicmc-upload
```

已有 schema v1 目录时，可增加 `-MigrateCatalogNow`，立即提交为 schema v2。不加该参数时只校验、不改写；插件和歌曲网页仍能读取 schema v1。

只想检查自动命名，不输入令牌也不修改任何服务，可以运行：

```powershell
./scripts/deploy.ps1 -Mode Automatic -GitHubOwner example `
  -CloudflareSubdomain example-musicmc -PlanOnly -NonInteractive
```

无人值守部署需要通过环境变量提供凭据并增加 `-NonInteractive`。永久删除用户主页仓库时，还必须同时增加 `-ReplaceExistingUserSite` 和 `-AllowRepositoryDeletion`；覆盖已有 Worker 需要 `-ReplaceExistingWorker`。

## 脚本会部署什么

实际歌曲仓库只会收到 Actions 和 Worker 运行所需的文件，不会复制本地的依赖和构建目录。已有 `catalog.json` 和已经发布的 Release 文件会保留。部署内容包括：

- 由 Issue 触发的 ZIP 和网易云投稿处理。
- FFmpeg 转码和约五秒一段的音频分片。
- 滚动资源包构建与发布。
- schema v1 到 v2 的目录迁移支持。
- Cloudflare Worker 源码和 Secrets。
- 部署在独立 Pages 仓库中的投稿页和歌曲列表。
- 仓库 Labels、Actions Secrets 和 Variables。
- 按所选实际歌曲仓库和投稿网址构建的服务器专用插件 JAR。

如果因为网络或平台故障只完成了一部分，修复原因后用相同参数重新执行即可。仓库提交和配置写入均按可重复执行设计。

## 签名密钥和插件安装

上传签名私钥默认保存在 `.local/upload-ticket-private.pem`，该目录被 Git 忽略。私钥已存在时，脚本会派生公钥并部署公钥。如果实际歌曲仓库已经有公钥，但本地找不到匹配的私钥，脚本会停止，不会悄悄轮换服务器身份。

最终插件默认输出到 `output/MusicMC-server.jar`，也可用 `-PluginOutputPath` 修改。安装为：

```text
plugins/MusicMC.jar
plugins/MusicMC/upload-ticket-private.pem
```

插件第一次启动会生成 `plugins/MusicMC/config.yml`；升级后还会补充新版本增加的默认配置，但不会覆盖你已有的值。把 `upload.key-id` 设为部署时的 `UploadKeyId`，默认是 `default`。替换 JAR 后必须完整重启服务器，插件升级不要使用 `/reload`。

只构建指定服务使用的插件、不部署任何线上服务时，执行：

```powershell
./scripts/build-plugin.ps1 `
  -Repository OWNER/MUSIC-RUNTIME `
  -PortalUrl https://owner.github.io/portal/
```

## 自动写入的配置

Worker Secrets：

```text
GITHUB_TOKEN
UPLOAD_PUBLIC_KEYS
```

实际歌曲仓库 Secrets：

```text
MUSICMC_AUTOMATION_TOKEN
CLOUDFLARE_API_TOKEN
CLOUDFLARE_ACCOUNT_ID
NETEASE_COOKIE                    # 可选
```

实际歌曲仓库 Variables：

```text
MUSICMC_WORKER_NAME
MUSICMC_UPLOAD_API_URL
MUSICMC_PAGES_ORIGIN
MUSICMC_PORTAL_URL
MUSICMC_CATALOG_URL
```

Pages 中生成的 `config.js` 会指向独立的实际歌曲目录。开源源码仓库保持中立，默认不会被当成任何人的实际服务。

## 网易云 Cookie

可选的 `NETEASE_COOKIE` 只供 GitHub Actions 内的适配器使用；没有 Cookie 时 ZIP 投稿仍然工作。登录状态过期后需要替换仓库 Secret。网易云接入不是官方开放接口，即使 Cookie 有效，也可能因为账号播放权限、地区限制或 GitHub Runner 触发风控而失败。

## 可选的 S3 兼容存储

默认使用 GitHub Releases 提供公开下载。要改用 Cloudflare R2、阿里云 OSS、腾讯云 COS 或其他 S3 兼容服务，部署后设置以下实际歌曲仓库 Variables：

```text
MUSICMC_STORAGE_BACKEND=s3
MUSICMC_S3_ENDPOINT_URL=https://...
MUSICMC_S3_BUCKET=bucket-name
MUSICMC_S3_PUBLIC_BASE_URL=https://public.example.com
MUSICMC_S3_PREFIX=musicmc              # 可选
MUSICMC_S3_REGION=auto                 # 按服务商填写
```

以及仓库 Secrets：

```text
MUSICMC_S3_ACCESS_KEY_ID
MUSICMC_S3_SECRET_ACCESS_KEY
```

客户端会直接从 GitHub Releases 或所配置的对象存储公网地址下载音频资源包，不经过 Minecraft 服务器，也不占用它的 FRP 隧道。只有把公开存储地址主动回源到 Minecraft 电脑时，才会重新消耗该电脑的上行和 FRP 流量。
