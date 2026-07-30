# 从 MusicMC-Uploads 迁移到单仓库

新的单仓库同时保存插件、网页、Worker、Actions 和根目录 `catalog.json`。旧歌曲 Release 不需要搬运；目录中的绝对 Release URL 仍可继续下载。

## 必须变更

1. 将最新歌曲目录复制到新仓库根目录 `catalog.json`。
2. 把新的 GitHub Fine-grained PAT 授权范围改为新仓库。只授权旧 `MusicMC-Uploads` 的令牌无法使用。
3. 将 Worker 的 `GITHUB_REPOSITORY` 改为新仓库，例如 `YSKeLi/musicmc`。
4. 将 Worker 的 `UPLOAD_PUBLIC_KEY` 与新仓库公钥保持一致。
5. 设置新仓库的 GitHub Actions Secrets 和 Variables。
6. 运行 Pages 和 Worker 部署工作流。
7. 使用新仓库参数重新构建插件，并替换服务器 JAR 和私钥。

## 旧歌曲

旧 `catalog.json` 中的 `pack_url`、`manifest_url` 和 `source_archive_url` 可以继续指向 `MusicMC-Uploads` 的 Release。新歌曲会发布到新单仓库的 Release。不要删除旧仓库或旧 Release，除非已经把资产迁移并更新所有校验值。

## 切换顺序

建议按以下顺序切换，避免上传中断：

1. 推送单仓库代码和旧目录；
2. 配置 Secrets、Variables、标签和 Pages；
3. 部署新 Worker 并验证 `/` 返回 404；
4. 验证 Pages 能读取根目录目录副本；
5. 构建并更新 Spigot 插件；
6. 提交一首短测试音频；
7. 确认新 Release、目录提交、服务器同步和 `/getcd` 全部成功。
