# MusicMC

[English](README.md) | [简体中文](README.zh-CN.md)

MusicMC 是一个面向 Spigot 1.21.11 的自托管自定义音乐唱片系统，包含 Java 插件、静态上传网页、Cloudflare Worker，以及用于校验音频、生成资源包、发布歌曲 Release 和更新公开目录的 GitHub Actions。

本仓库是部署模板，不包含正在运行的服务地址、生产歌曲目录、私钥、个人访问令牌，也不会默认启用 GitHub Pages。只有管理员完成配置并手动运行部署工作流后，才会创建外部服务。

## 功能

- `/music upload <名称>` 生成短期有效的 P-256 签名上传链接。
- 玩家无需 GitHub 账号，只需上传包含一个音频文件的 `<名称>.zip`。
- 支持 MP3、M4A、AAC、WAV、FLAC、OGG 和 OPUS，并由 Actions 自动校验和转码。
- `/getcd <名称>` 发放自定义唱片，并支持歌曲名称补全。
- 唱片机声音默认在 25 格内线性衰减，到范围边缘时静音。
- 目录同步支持 GitHub API、Raw、jsDelivr 和配置的下载代理回退。
- 玩家上传次数不限；重复歌曲名和重复凭证仍会被拒绝。

## 仓库结构

```text
src/                              Spigot 插件和测试
github-upload-service/docs/       静态上传页和歌曲列表
github-upload-service/worker/     Cloudflare 上传 Worker
github-upload-service/scripts/    音频校验和发布脚本
github-upload-service/config/     可替换的上传签名公钥
scripts/                           初始化和安全检查工具
.github/workflows/                CI 和手动部署工作流
catalog.json                      空白公开目录模板
```

## 构建

需要 Java 21。

```powershell
.\mvnw.cmd clean verify
```

Linux 或 macOS：

```bash
./mvnw clean verify
```

插件输出到 `target/MusicMC-1.0.0.jar`。默认产物包含 `OWNER/REPOSITORY` 和 `https://example.com/musicmc/` 占位符，投入生产服务器前必须完成自己的部署配置。

## 自托管部署

安装 Git、Java 21、Node.js 22、GitHub CLI 和 Wrangler，然后登录：

```powershell
gh auth login
npx wrangler login
.\scripts\bootstrap.ps1 -Repository OWNER/REPOSITORY
```

初始化脚本会生成全新签名密钥、部署 Worker、设置当前仓库的 Secrets 和 Variables、启用 Pages、推送公钥，并显式启动手动 Pages 工作流。仅克隆或更新本仓库不会部署任何服务。

详细说明见[部署文档](docs/DEPLOYMENT.zh-CN.md)、[架构说明](docs/ARCHITECTURE.zh-CN.md)和[迁移说明](docs/MIGRATION.zh-CN.md)。

## 玩家命令

| 命令 | 权限 | 作用 |
| --- | --- | --- |
| `/music upload <名称>` | `musicmc.upload` | 创建一次性上传链接；名称不写音频扩展名 |
| `/music list [页码]` | 无 | 查看已发布歌曲 |
| `/music status` | 无 | 查看目录和待处理上传状态 |
| `/music pack` | 无 | 重新发送当前资源包 |
| `/music sync` | `musicmc.admin` | 立即同步歌曲目录 |
| `/getcd <名称>` | `musicmc.getcd` | 获取唱片并自动补全歌曲名称 |

玩家教程提供 [English](docs/PLAYER_GUIDE.md) 和[简体中文](docs/PLAYER_GUIDE.zh-CN.md)版本。

## 自动化安全

- `CI` 会在 push 和 pull request 时测试代码，但不会创建外部服务。
- `Deploy upload portal` 和 `Deploy upload Worker` 只能手动运行。
- `Process music submission` 只有在已配置服务创建符合格式的 Issue 后才运行。
- `Release plugin` 只有管理员显式推送 `v*` 标签后才运行。

## 许可证

MusicMC 使用 [MIT License 英文法律文本](LICENSE)，并提供[非官方中文参考译文](LICENSE.zh-CN.md)。
