# MusicMC

MusicMC 是一个面向 Spigot 1.21.11 的自定义音乐唱片系统。玩家在游戏内申请一次性上传凭证，通过 GitHub Pages 上传音乐 ZIP；Cloudflare Worker 使用仓库专用令牌创建处理任务，GitHub Actions 自动校验、转码、生成资源包、发布 Release 并更新歌曲目录。Minecraft 服务器不保存 GitHub 令牌，也不需要对外开放网页端口。

本仓库包含完整系统，不需要再拆分插件仓库、网页仓库和上传仓库：

```text
MusicMC/
├─ src/                              Spigot 插件源码与测试
├─ github-upload-service/
│  ├─ docs/                          上传页和歌曲列表
│  ├─ worker/                        Cloudflare Worker
│  ├─ scripts/                       音频校验、转码和目录发布
│  └─ config/                        上传签名公钥
├─ scripts/                           初始化和安全检查脚本
├─ .github/workflows/                 CI、Pages、Worker、歌曲处理和插件发布
└─ catalog.json                       服务器同步的公开歌曲目录
```

## 功能

- `/music upload <歌曲名称>` 生成 15 分钟有效的 P-256 签名上传链接。
- 玩家不需要 GitHub 账号；自动模式由 Worker 代为上传 ZIP 和创建 Issue。
- 支持 MP3、M4A、AAC、WAV、FLAC、OGG 和 OPUS，ZIP 最大 25 MB，歌曲最长 10 分钟。
- GitHub Actions 使用 FFmpeg 转为 OGG Vorbis，并生成 Minecraft 资源包。
- `/getcd <歌曲名称>` 支持歌曲名称补全并发放自定义唱片。
- 唱片声音默认在 25 格内线性衰减，距离越远音量越低。
- 插件每 120 秒同步公开目录，支持 GitHub API、Raw、jsDelivr 和下载代理回退。
- 每名玩家上传次数不限；重复歌曲名和重复凭证仍会被拒绝。

## 玩家流程

```text
/music upload abc
        ↓
打开服务器返回的网页
        ↓
选择 abc.zip（ZIP 内只有一个音乐文件）
        ↓
Worker 创建临时 Release 和处理 Issue
        ↓
Actions 转码并更新 catalog.json
        ↓
服务器自动同步，玩家执行 /getcd abc
```

玩家使用说明见 [docs/PLAYER_GUIDE.zh-CN.md](docs/PLAYER_GUIDE.zh-CN.md)。

## 快速构建

要求 Java 21。

Windows：

```powershell
.\mvnw.cmd clean verify
```

Linux 或 macOS：

```bash
./mvnw clean verify
```

插件输出到 `target/MusicMC-1.0.0.jar`。

## 一键部署

完整自动化部署需要安装 Git、Node.js 22、GitHub CLI 和 Wrangler，并准备：

- 一个只授权当前仓库的 GitHub Fine-grained PAT；
- 一个 Cloudflare API Token；
- Cloudflare Account ID；
- 一个 Cloudflare 账号；自有域名可选。

登录 CLI 后运行：

```powershell
gh auth login
npx wrangler login
.\scripts\bootstrap.ps1 -Repository OWNER/REPOSITORY
```

脚本会生成上传签名密钥、部署 Worker、写入 GitHub Secrets/Variables、创建 Issue 标签、启用 Pages、提交公钥并推送。令牌通过隐藏输入读取，不会写入仓库。

所有权限和手动部署步骤见 [docs/DEPLOYMENT.zh-CN.md](docs/DEPLOYMENT.zh-CN.md)。从旧 `MusicMC-Uploads` 迁移见 [docs/MIGRATION.zh-CN.md](docs/MIGRATION.zh-CN.md)。

## 安装插件

1. 将构建或 Release 中的 JAR 放入服务器 `plugins` 目录。
2. 将初始化脚本生成的私钥复制到 `plugins/MusicMC/upload-ticket-private.pem`。
3. 完整重启服务器，不要使用 `/reload`。
4. 玩家必须允许服务器资源包才能听到自定义音乐。

私钥只存在于 Minecraft 服务器。插件运行时不需要 GitHub、Cloudflare 或其他 API Token。

## 命令

| 命令 | 权限 | 作用 |
| --- | --- | --- |
| `/music upload <名称>` | `musicmc.upload` | 创建一次性上传链接；名称不写音频扩展名 |
| `/music list [页码]` | 无额外权限 | 查看已发布歌曲 |
| `/music status` | 无额外权限 | 查看目录同步与待处理上传状态 |
| `/music pack` | 无额外权限 | 重新发送所有歌曲资源包 |
| `/music sync` | `musicmc.admin` | 立即同步歌曲目录 |
| `/getcd <名称>` | `musicmc.getcd` | 获取唱片并自动补全歌曲名称 |

## 自动化

- `CI`：测试 Java 插件、网页、Python 处理器和 Worker，并执行敏感信息扫描。
- `Deploy upload portal`：将网页和最新 `catalog.json` 发布到 GitHub Pages。
- `Deploy upload Worker`：Worker 代码变更后自动部署，并恢复仓库、CORS、令牌和公钥绑定。
- `Process music submission`：处理 `[Music Upload]` Issue，发布歌曲 Release 和目录提交。
- `Release plugin`：推送 `v*` 标签时构建并发布插件 JAR。

系统设计和信任边界见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，安全策略见 [SECURITY.md](SECURITY.md)。

## License

代码使用 [MIT License](LICENSE)。
