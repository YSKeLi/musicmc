# MusicMC 完整部署说明

[English](DEPLOYMENT.md) | [简体中文](DEPLOYMENT.zh-CN.md)

本说明用于从一个公开 GitHub 仓库部署完整 MusicMC，包括 Spigot 插件、上传网页、Cloudflare Worker、GitHub Actions 和歌曲目录。

源码仓库默认不启用 Pages 或 Worker，也不包含真实服务地址。以下步骤会为执行者自己的 fork 创建服务，只有管理员主动运行初始化脚本或手动工作流时才会发生部署。

## 1. 不需要什么

- 不需要 FRP 提供网站。
- 不需要在 Minecraft 服务器开放 HTTP 端口。
- 不需要音乐平台 API。
- 不需要把 GitHub Token 放进插件或 `config.yml`。
- 玩家不需要 GitHub 账号。

## 2. 前置条件

本地管理电脑需要：

- Git；
- Java 21；
- Node.js 22 和 npm；
- GitHub CLI `gh`；
- Cloudflare Wrangler（脚本通过 `npx wrangler` 调用）；
- 一个公开 GitHub 仓库；
- 一个 Cloudflare 账号。

先登录：

```powershell
gh auth login
npx wrangler login
```

## 3. 必须提供的令牌和参数

### GitHub Fine-grained PAT

这是 Worker 调用 GitHub REST API 的身份，同时可作为 Actions 提交目录的身份。

创建时选择：

- Resource owner：仓库所有者；
- Repository access：**Only select repositories**；
- 只选择当前 MusicMC 仓库；
- Repository permissions：
  - Contents：Read and write；
  - Issues：Read and write；
  - Metadata：GitHub 自动提供 Read-only。

令牌只能访问创建者明确选择的自托管仓库，不能复用只授权其他仓库的令牌。

同一个值会保存到两个受保护位置：

| 名称 | 位置 | 用途 |
| --- | --- | --- |
| `GITHUB_TOKEN` | Cloudflare Worker Secret | 创建临时 Release、上传 ZIP、创建 Issue |
| `MUSICMC_AUTOMATION_TOKEN` | GitHub Actions Secret | 发布歌曲、评论 Issue、提交 `catalog.json` |

不要把令牌写进源码、远程 URL、命令参数、聊天消息或截图。

### Cloudflare API Token

这是 GitHub Actions 自动重新部署 Worker 时使用的 Cloudflare 凭证，不是 Cloudflare Global API Key，也不是上面的 GitHub PAT。

创建一个自定义 Cloudflare API Token，至少授予当前账号的 Workers Scripts：Edit。将值保存为 GitHub Actions Secret：

```text
CLOUDFLARE_API_TOKEN
```

### Cloudflare Account ID

在 Cloudflare Dashboard 的账号首页复制 Account ID，保存为 GitHub Actions Secret：

```text
CLOUDFLARE_ACCOUNT_ID
```

Account ID 不是密码，但作为 Secret 保存可简化工作流配置。

### 上传签名密钥

MusicMC 使用 P-256 ECDSA 密钥对：

- `upload-ticket-private.pem`：只放在 Minecraft 服务器 `plugins/MusicMC`；
- `upload-ticket-public.pem`：提交到仓库，并部署为 Worker 绑定。

私钥负责证明上传链接确实由你的 Minecraft 服务器生成。私钥泄露后必须同时重新生成公私钥并重新部署 Worker。

## 4. 一键初始化

在仓库根目录运行：

```powershell
.\scripts\bootstrap.ps1 -Repository OWNER/REPOSITORY
```

脚本会按顺序完成：

1. 检查 Git、Node、npm、GitHub CLI 和登录状态；
2. 生成 P-256 公私钥；
3. 隐藏读取 GitHub PAT、Cloudflare API Token 和 Account ID；
4. 安装并测试 Worker；
5. 部署 Worker；
6. 设置 Worker 的 `GITHUB_TOKEN` 和 `UPLOAD_PUBLIC_KEY`；
7. 写入 GitHub Actions Secrets 和 Variables；
8. 创建 `music-pending`、`music-processing`、`music-ready` 和 `music-rejected` 标签；
9. 启用 GitHub Pages 的 Actions 发布模式；
10. 提交公钥并推送到 `main`；
11. 显式启动手动 Pages 工作流。

生成的私钥默认位于：

```text
.local/upload-ticket-private.pem
```

`.local` 已加入 `.gitignore`。

### 使用自有 Worker 域名

如果 `workers.dev` 在玩家网络中不可访问，先在 Cloudflare 给 Worker 绑定自有域名，例如 `https://upload.example.com`，然后执行：

```powershell
.\scripts\bootstrap.ps1 `
  -Repository OWNER/REPOSITORY `
  -UploadApiUrl https://upload.example.com
```

仅修改网页 URL 不够，自有域名必须先在 Cloudflare 的 Worker Domains & Routes 中绑定成功。

## 5. GitHub 仓库配置结果

初始化后应存在以下 Actions Secrets：

```text
MUSICMC_AUTOMATION_TOKEN
CLOUDFLARE_API_TOKEN
CLOUDFLARE_ACCOUNT_ID
```

以下 Actions Variables：

```text
MUSICMC_WORKER_NAME
MUSICMC_UPLOAD_API_URL
MUSICMC_PAGES_ORIGIN
MUSICMC_PORTAL_URL
```

其中：

- `MUSICMC_PAGES_ORIGIN` 只有协议和主机，例如 `https://owner.github.io`，用于 Worker CORS；
- `MUSICMC_PORTAL_URL` 是完整项目页面，例如 `https://owner.github.io/repository/`；
- `MUSICMC_UPLOAD_API_URL` 是 Worker 或自有域名，不带末尾 `/`。

## 6. GitHub Actions

仓库包含五条工作流：

| 工作流 | 触发条件 | 作用 |
| --- | --- | --- |
| `CI` | push / pull request | 测试所有组件并扫描敏感文件 |
| `Deploy upload portal` | 手动运行 | 发布 GitHub Pages |
| `Deploy upload Worker` | 手动运行 | 部署 Worker 并恢复绑定 |
| `Process music submission` | `[Music Upload]` Issue | 校验、转码、发布 Release、更新目录 |
| `Release plugin` | 推送 `v*` 标签 | 构建并发布插件 JAR |

在仓库的 Actions 页面确认这些工作流全部可用。Pages 和 Worker 不会因普通 push 自动部署；初始化脚本会显式配置并启动首次 Pages 部署。

## 7. 构建和发布插件

本地构建中性模板：

```powershell
.\mvnw.cmd clean verify
```

默认 JAR 使用 `OWNER/REPOSITORY` 和 `https://example.com/musicmc/` 占位符。为自己的部署构建时指定仓库和页面：

```powershell
.\mvnw.cmd clean verify `
  -Dmusicmc.github.repository=OWNER/REPOSITORY `
  -Dmusicmc.portal.url=https://OWNER.github.io/REPOSITORY/
```

自动发布版本：

```powershell
git tag v1.0.0
git push origin v1.0.0
```

`Release plugin` 工作流会创建 GitHub Release 并附加已配置的插件 JAR。

## 8. 安装到 Spigot

1. 使用 Java 21 和 Spigot 1.21.11。
2. 把 `MusicMC-*.jar` 放进服务器 `plugins`。
3. 创建 `plugins/MusicMC`。
4. 把 `.local/upload-ticket-private.pem` 复制为：

   ```text
   plugins/MusicMC/upload-ticket-private.pem
   ```

5. 完整重启服务器。
6. 检查控制台是否显示 MusicMC 目录同步完成。

不要把 GitHub PAT 或 Cloudflare Token 放在 Minecraft 服务器。插件只需要私钥和公开目录 URL。

## 9. 手动部署 Worker

不使用初始化脚本时：

```powershell
cd github-upload-service\worker
npm ci
npm test
npx wrangler login
npx wrangler deploy --name <WORKER_NAME> `
  --var "ALLOWED_ORIGIN:https://OWNER.github.io" `
  --var "GITHUB_REPOSITORY:OWNER/REPOSITORY" `
  --var "GITHUB_BRANCH:main"
npx wrangler secret put GITHUB_TOKEN --name <WORKER_NAME>
npx wrangler secret put UPLOAD_PUBLIC_KEY --name <WORKER_NAME>
```

`GITHUB_TOKEN` 提示中粘贴 GitHub Fine-grained PAT。`UPLOAD_PUBLIC_KEY` 提示中粘贴 `github-upload-service/config/upload-ticket-public.pem` 的完整内容。

随后在仓库 Variables 中设置 Worker URL，重新运行 `Deploy upload portal`。

## 10. 正常运行时调用的 API

| 调用方 | API | 是否需要密钥 |
| --- | --- | --- |
| Minecraft 插件 | GitHub Contents API / Raw / jsDelivr | 否 |
| 上传网页 | Cloudflare Worker `/upload` | 否；需要服务器签名票据 |
| Cloudflare Worker | GitHub REST API | 是，Fine-grained PAT |
| GitHub Actions | GitHub Release / Issues / Git push | Actions Secret 或 `github.token` |
| GitHub Actions | Cloudflare Workers API | 是，Cloudflare API Token |

## 11. 轮换密钥

### GitHub PAT

1. 创建新 PAT；
2. 更新 GitHub Secret `MUSICMC_AUTOMATION_TOKEN`；
3. 执行 `npx wrangler secret put GITHUB_TOKEN --name <Worker 名称>`；
4. 撤销旧 PAT；
5. 提交一次测试歌曲。

### 上传签名密钥

```powershell
.\scripts\bootstrap.ps1 -Repository OWNER/REPOSITORY -RotateUploadKey
```

然后把新私钥复制到服务器并完整重启。旧上传链接会立即失效。

## 12. 故障排查

### `Failed to fetch`

浏览器没有拿到 Worker 响应。依次检查 Worker URL、DNS、HTTPS、自有域名绑定和 `ALLOWED_ORIGIN`。如果 `workers.dev` 被网络错误解析，应使用已绑定到 Worker 的自有域名。

### Worker 返回 403

网页来源与 `MUSICMC_PAGES_ORIGIN` 不一致，或上传票据签名无效。

### Worker 返回 503

Worker 缺少 `GITHUB_TOKEN`、`GITHUB_REPOSITORY`、`ALLOWED_ORIGIN` 或 `UPLOAD_PUBLIC_KEY`。

### Issue 创建后 Actions 不运行

确认 PAT 有当前仓库的 Issues/Contents 写权限，工作流已启用，并且 Issue 标题以 `[Music Upload] ` 开头。

### 服务器同步超时

插件会依次尝试配置中的 GitHub API、代理、Raw 和 jsDelivr。根据服务器网络修改 `plugins/MusicMC/config.yml` 的目录镜像和超时时间。
