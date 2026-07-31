const MAX_ZIP_BYTES = 25 * 1024 * 1024;

export function readSubmissionParams(url) {
  const params = new URL(url).searchParams;
  return {
    ticket: (params.get("ticket") || "").trim(),
    song: (params.get("song") || "").trim(),
    player: (params.get("player") || "").trim(),
    source: (params.get("source") || "upload").trim().toLocaleLowerCase("und"),
    providerId: (params.get("providerId") || "").trim(),
  };
}

export function validateSubmission(submission) {
  const errors = [];
  if (!submission.ticket.startsWith("MUS1.")) errors.push("ticket");
  if (submission.source !== "netease"
      && (submission.song.length < 1 || submission.song.length > 48 || submission.song.includes("\n"))) {
    errors.push("song");
  }
  if (!/^[A-Za-z0-9_]{3,16}$/.test(submission.player)) errors.push("player");
  if (!new Set(["upload", "netease"]).has(submission.source)) errors.push("source");
  if (submission.source === "netease" && !/^[1-9][0-9]{0,19}$/.test(submission.providerId)) {
    errors.push("providerId");
  }
  return errors;
}

export function buildIssueBody(submission) {
  const songName = submission.song || `netease-${submission.providerId}`;
  const lines = [
    "### Upload ticket", "", submission.ticket, "",
    "### Song name", "", songName, "",
    "### Minecraft player", "", submission.player, "",
    "### Source type", "", submission.source, "",
  ];
  if (submission.source === "netease") {
    lines.push("### NetEase song ID", "", submission.providerId);
  } else {
    lines.push("### Music ZIP file", "",
      "<!-- 把 ZIP 文件拖到这一行下方，等待上传完成后再创建 Issue。 -->");
  }
  return lines.join("\n");
}

export function buildIssueUrl(submission, issueUrl) {
  if (validateSubmission(submission).length > 0) throw new Error("Invalid MusicMC submission parameters");
  const target = new URL(issueUrl);
  if (target.protocol !== "https:" || target.hostname !== "github.com" || !target.pathname.endsWith("/issues/new")) {
    throw new Error("Invalid MusicMC issue URL");
  }
  target.search = new URLSearchParams({
    title: `[Music Upload] ${submission.song}`,
    body: buildIssueBody(submission),
  }).toString();
  return target.toString();
}

export function validateZipFile(file) {
  if (!file) return "请选择 ZIP 文件。";
  if (!file.name.toLocaleLowerCase("und").endsWith(".zip")) return "请选择扩展名为 .zip 的文件。";
  if (file.size < 1 || file.size > MAX_ZIP_BYTES) return "ZIP 文件必须大于 0 字节且不超过 25 MB。";
  return "";
}

export async function submitUpload(apiUrl, submission, file, fetchImpl = fetch) {
  const fileError = validateZipFile(file);
  if (validateSubmission(submission).length > 0 || fileError) {
    throw new Error(fileError || "上传链接无效。");
  }
  return submitApi(new URL("/upload", apiUrl), submission, {
    "Content-Type": file.type || "application/zip",
    "X-MusicMC-Filename": encodeURIComponent(file.name),
  }, file, fetchImpl);
}

export async function submitNetEase(apiUrl, submission, fetchImpl = fetch) {
  if (validateSubmission(submission).length > 0 || submission.source !== "netease") {
    throw new Error("网易云投稿链接无效。");
  }
  return submitApi(new URL("/netease", apiUrl), submission, {
    "Content-Type": "application/json",
  }, JSON.stringify({ song_id: submission.providerId }), fetchImpl);
}

export async function resolveNetEaseMetadata(apiUrl, submission, fetchImpl = fetch, fallbackApiUrl = "") {
  if (!submission.ticket.startsWith("MUS1.")
      || submission.source !== "netease"
      || !/^[1-9][0-9]{0,19}$/.test(submission.providerId)) {
    throw new Error("网易云歌曲查询链接无效。");
  }
  let response;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 6000);
  try {
    response = await fetchImpl(new URL("/netease/metadata", apiUrl), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-MusicMC-Ticket": submission.ticket,
      },
      body: JSON.stringify({ song_id: submission.providerId }),
      signal: controller.signal,
    });
  } catch (error) {
    if (fallbackApiUrl) return resolvePublicNetEaseMetadata(fallbackApiUrl, submission.providerId, fetchImpl);
    throw error;
  } finally {
    clearTimeout(timeout);
  }
  let result = {};
  try { result = await response.json(); } catch { /* HTTP status remains useful. */ }
  if (!response.ok && response.status >= 500 && fallbackApiUrl) {
    return resolvePublicNetEaseMetadata(fallbackApiUrl, submission.providerId, fetchImpl);
  }
  if (!response.ok) throw new Error(result.error || `网易云歌曲查询失败（HTTP ${response.status}）。`);
  return result;
}

async function resolvePublicNetEaseMetadata(apiUrl, providerId, fetchImpl) {
  const endpoint = new URL(apiUrl);
  if (endpoint.protocol !== "https:" || endpoint.hostname !== "apis.netstart.cn") {
    throw new Error("网易云元数据回退接口配置无效。");
  }
  endpoint.searchParams.set("ids", providerId);
  const response = await fetchImpl(endpoint);
  if (!response.ok) throw new Error(`网易云歌曲查询失败（HTTP ${response.status}）。`);
  let body;
  try { body = await response.json(); } catch { throw new Error("网易云歌曲信息响应格式无效。"); }
  const song = Array.isArray(body?.songs) ? body.songs[0] : null;
  if (!song || String(song.id) !== providerId) throw new Error("没有找到这个网易云歌曲 ID。");
  const title = cleanMetadataText(song.name, 96);
  const artists = Array.isArray(song.ar) ? song.ar : song.artists;
  const artist = cleanMetadataText(
    Array.isArray(artists) ? artists.map((item) => item?.name).filter(Boolean).join(" / ") : "", 96);
  if (!title) throw new Error("网易云没有返回有效的歌曲标题。");
  return {
    song_id: providerId,
    title,
    artist,
    command_name: cleanMetadataText(artist ? `${title}-${artist}` : title, 48) || `netease-${providerId}`,
  };
}

function cleanMetadataText(value, maximumLength) {
  const normalized = String(value || "").normalize("NFKC").replace(/[\p{Cc}\p{Cf}]+/gu, " ")
    .replace(/\s+/gu, " ").trim();
  return Array.from(normalized).slice(0, maximumLength).join("");
}

async function submitApi(endpoint, submission, headers, body, fetchImpl) {
  const response = await fetchImpl(endpoint.toString(), {
    method: "POST",
    headers: { ...headers, "X-MusicMC-Ticket": submission.ticket },
    body,
  });
  let result = {};
  try { result = await response.json(); } catch { /* HTTP status remains useful. */ }
  if (!response.ok) throw new Error(result.error || `自动提交失败（HTTP ${response.status}）。`);
  return result;
}
