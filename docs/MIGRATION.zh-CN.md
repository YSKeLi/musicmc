# 迁移到 MusicMC 单仓库

[English](MIGRATION.md) | [简体中文](MIGRATION.zh-CN.md)

单仓库同时保存插件、网页、Worker、Actions 和根目录 `catalog.json`。源码模板不包含任何生产目录或服务 URL；迁移数据必须由部署者从自己的旧系统导入。

## 必须变更

1. 从旧部署复制最新 `catalog.json`，或者从空目录开始。
2. 创建只授权新自托管仓库的 GitHub Fine-grained PAT。
3. 将 Worker 的 `GITHUB_REPOSITORY` 改为新仓库的 `OWNER/REPOSITORY`。
4. 生成新的 P-256 密钥，并同步更新服务器私钥、仓库公钥和 Worker `UPLOAD_PUBLIC_KEY`。
5. 设置新仓库的 Actions Secrets、Variables 和 Issue 标签。
6. 由管理员手动运行 Pages 和 Worker 部署工作流。
7. 使用新仓库和网页参数重新构建插件。

## 旧歌曲

旧目录中的绝对 `pack_url`、`manifest_url` 和 `source_archive_url` 可以继续指向旧 Release。迁移完成并验证全部校验值之前，不要删除旧资产。希望完全脱离旧仓库时，应先复制 Release 资产，再更新 URL 和哈希。

## 推荐顺序

1. 创建新的公开 fork，但不要启用任何部署；
2. 导入目录并生成新密钥；
3. 配置仓库 Secrets、Variables 和标签；
4. 手动部署 Worker 和 Pages；
5. 构建并安装 Spigot 插件；
6. 使用短音频完成一次端到端测试；
7. 确认 Release、目录提交、服务器同步和 `/getcd` 全部成功后再停用旧服务。
