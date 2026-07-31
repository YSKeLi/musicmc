# 管理员指南

[English](ADMIN_GUIDE.md) | [简体中文](ADMIN_GUIDE.zh-CN.md)

`musicmc.admin` 权限默认授予 OP。

- `/music sync`：立即获取并校验目录。获取失败时继续使用最后一份有效目录。
- `/music admin disable <命令名称>`：在本服务器本地隐藏歌曲，并阻止已有唱片继续播放。
- `/music admin enable <命令名称>`：移除本地禁用覆盖。
- `/music admin remove <命令名称>`：本地禁用的别名，不会删除远程 Release，也不会重写 GitHub 历史。
- `/music admin replace upload <命令名称>`：生成管理员签名的 ZIP 替换任务，发布时保留 `song_id` 并递增版本。
- `/music admin replace netease <ID> <命令名称>`：通过网易云 Actions 适配器替换音频。
- `/music admin banupload <玩家>`：按玩家 UUID 持久化禁止投稿。
- `/music admin unbanupload <玩家>`：解除投稿封禁。
- `/music admin pending`：列出本地签发且尚未过期的凭证；GitHub Issues 才是权威处理队列。
- `/music admin uploader <玩家>`：列出目录中归属于该 Minecraft 玩家的歌曲。
- `/music admin storage`：显示插件本地元数据占用和磁盘剩余空间；歌曲音频不在本地长期保存。

本地禁用和投稿封禁保存在 `plugins/MusicMC/admin-state.yml`。服务器运行时不要手工修改该文件。

需要跨服务器永久下架时，应通过受控仓库修改更新 `catalog.json` 并发布新目录版本。旧版本资源应保留到客户端与备份不再需要后再处理。
