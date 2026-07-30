# 贡献指南

[English](CONTRIBUTING.md) | [简体中文](CONTRIBUTING.zh-CN.md)

请使用 Java 21 和 Node.js 22。提交 Pull Request 前运行：

```powershell
.\mvnw.cmd clean verify
node --test github-upload-service/tests/catalog.test.mjs github-upload-service/tests/portal.test.mjs
python -m unittest discover -s github-upload-service/tests -v
cd github-upload-service/worker
npm ci
npm test
```

不要提交生成的 JAR、`node_modules`、Worker 构建产物、音频文件、令牌或私钥。改动应保持聚焦，并为凭证解析、歌曲目录、上传校验和播放行为添加相应测试。
