# 迁移到 MusicMC 2

[English](MIGRATION.md) | [简体中文](MIGRATION.zh-CN.md)

1. 备份仓库、`plugins/MusicMC` 和旧插件 JAR。
2. 停止服务器，不要用 `/reload` 升级。
3. 替换 JAR，并合并新 `config.yml`，特别是 `upload.key-id`。
4. 部署新版 Worker 和 Pages 工作流。
5. 启动服务器，由管理员执行 `/music sync`。

Java 插件能读取目录 v1，并在内存中迁移到 v2；下一次成功发布时，发布脚本也会把 v1 写成 v2。旧唱片继续有效，因为物品中的 `song_id` 保持不变；旧 `pack_sha1` 标签会被忽略。

MusicMC 2 不再把资源包、源 ZIP 或解压后的 OGG 下载到 `plugins/MusicMC/songs`。完成备份并确认新目录和至少一张旧唱片正常后，旧目录才可以自行归档或清理；验证之前不要删除。

命令名称不再需要满足 Windows ZIP 文件名规则，ZIP 文件名也不需要和命令一致。仍建议使用 `/music upload abc`，不要添加音频后缀。

目录 v2 新增 `bundles`、歌曲 `revision`、`command_name`、`enabled`、分片声音和结构化来源信息。替换歌曲会保留 ID 并递增版本。
