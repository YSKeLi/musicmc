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
  if (submission.song.length < 1 || submission.song.length > 48 || submission.song.includes("\n")) {
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
  const lines = [
    "### Upload ticket", "", submission.ticket, "",
    "### Song name", "", submission.song, "",
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
