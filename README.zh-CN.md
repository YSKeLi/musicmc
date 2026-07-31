# MusicMC

[English](README.md) | [简体中文](README.zh-CN.md)

MusicMC 是一套面向 Spigot 1.21.11 的开源自定义唱片插件和 GitHub 自动化服务。玩家可以提交 ZIP，或提交网易云歌曲 ID；GitHub Actions 自动校验、转码、分片和发布，服务器按需向玩家发送较小的资源包。

## 主要能力

- 运行于 Spigot 1.21.11 和 Java 21，不要求 Paper，也不要求客户端 Mod。
- 投稿通过自动校验后直接发布，没有人工审核步骤。
- 每名玩家历史投稿总次数不限，同时只能有 1 个正在处理的任务。
- 每个滚动资源包最多合并 20 首歌曲，玩家只加载实际需要的资源包。
- 音频切成约 5 秒一段；玩家中途进入唱片机 25 格范围后，会从下一段加入同步播放。
- Minecraft 服务器只保存歌曲目录元数据。音频由 GitHub Release 或 S3 兼容对象存储直接传给客户端，不经过 Minecraft 服务器和 FRP。
- 提供本地禁用/启用歌曲、禁止玩家投稿、保留旧唱片的音频替换、存储诊断和游戏内歌曲菜单。
- 可选用 GitHub Actions 内部的网易云 Cookie 适配器；Cookie 只保存为仓库 Secret。

## 玩家命令

```text
/music
/music upload <命令名称>
/music netease <网易云歌曲ID> <命令名称>
/music search <关键词>
/music list [页码]
/music status
/music cancel
/music pack
/getcd <命令名称>
```

完整说明见[玩家指南](docs/PLAYER_GUIDE.zh-CN.md)。
管理员另见[管理员指南](docs/ADMIN_GUIDE.zh-CN.md)。

## 构建

```bash
./mvnw --batch-mode clean verify
```

打包结果为 `target/MusicMC-2.0.0.jar`。

## 部署

从[部署指南](docs/DEPLOYMENT.zh-CN.md)开始。仓库已经包含插件、投稿网页、Worker、Actions 处理脚本、目录格式、测试和初始化脚本，不写死任何生产网址、个人服务器、FRP 隧道、私钥、Cookie 或访问令牌。

## 已知限制

- 不安装客户端 Mod 时，后来进入范围的玩家只能从下一音频片段加入，不能从任意采样位置开始。
- 网易云接入并非官方开放接口；Cookie 过期、账号没有播放权限或 GitHub Runner 触发风控时会失败。
- 投稿和下载仍受 GitHub 或所选对象存储的配额限制。

## 许可证

MIT，见 [LICENSE](LICENSE) 和 [LICENSE.zh-CN.md](LICENSE.zh-CN.md)。
