import {
  buildIssueUrl,
  readSubmissionParams,
  resolveNetEaseMetadata,
  submitNetEase,
  submitUpload,
  validateSubmission,
  validateZipFile,
} from "./portal.mjs";

const submission = readSubmissionParams(window.location.href);
let errors = validateSubmission(submission);
let neteaseReady = submission.source !== "netease";
const form = document.querySelector("#submission-form");
const continueButton = document.querySelector("#continue-button");
const status = document.querySelector("#link-status");
const errorNotice = document.querySelector("#link-error");
const uploadField = document.querySelector("#upload-field");
const neteaseField = document.querySelector("#netease-field");
const neteaseTitleField = document.querySelector("#netease-title-field");
const neteaseArtistField = document.querySelector("#netease-artist-field");
const fileInput = document.querySelector("#music-zip");
const actionHint = document.querySelector("#action-hint");
const uploadError = document.querySelector("#upload-error");
const uploadSuccess = document.querySelector("#upload-success");
const configuredApi = window.MUSICMC_CONFIG?.uploadApiUrl?.trim() || "";
const configuredIssueUrl = window.MUSICMC_CONFIG?.issueUrl?.trim() || "";

const songNameInput = document.querySelector("#song-name");
const playerNameInput = document.querySelector("#player-name");
const sourceNameInput = document.querySelector("#source-name");
const neteaseIdInput = document.querySelector("#netease-id");
const neteaseTitleInput = document.querySelector("#netease-title");
const neteaseArtistInput = document.querySelector("#netease-artist");
const ticketInput = document.querySelector("#upload-ticket");

songNameInput.value = submission.song;
playerNameInput.value = submission.player;
ticketInput.value = submission.ticket;
sourceNameInput.value = submission.source === "netease" ? "网易云音乐" : "ZIP 文件";
neteaseIdInput.value = submission.providerId;
uploadField.hidden = submission.source !== "upload";
neteaseField.hidden = submission.source !== "netease";
neteaseTitleField.hidden = submission.source !== "netease";
neteaseArtistField.hidden = submission.source !== "netease";

if (errors.length > 0) {
  markInvalidLink();
} else if (configuredApi) {
  if (submission.source === "netease") {
    actionHint.textContent = "正在查询网易云歌曲标题和歌手";
    continueButton.disabled = true;
    resolveNetEaseMetadata(configuredApi, submission).then((metadata) => {
      submission.song = String(metadata.command_name || "").trim();
      songNameInput.value = submission.song;
      neteaseTitleInput.value = String(metadata.title || "");
      neteaseArtistInput.value = String(metadata.artist || "");
      neteaseReady = Boolean(submission.song && metadata.song_id === submission.providerId);
      if (!neteaseReady) throw new Error("网易云歌曲信息响应无效。");
      status.textContent = "歌曲信息已确认";
      status.classList.add("ready");
      actionHint.textContent = "请确认歌曲信息后提交，服务器会再次校验网易云音频";
      continueButton.disabled = false;
    }).catch((error) => {
      status.textContent = "歌曲信息查询失败";
      status.classList.add("error");
      showUploadError(error.message || "暂时无法查询网易云歌曲信息，请稍后重试。");
      actionHint.textContent = "请回到 Minecraft 重新生成投稿链接，或稍后重试";
    });
  } else {
    status.textContent = "凭证已载入";
    status.classList.add("ready");
    actionHint.textContent = "文件名不限，MusicMC 会自动规范化";
    fileInput.addEventListener("change", updateFileState);
    updateFileState();
  }
} else if (configuredIssueUrl && submission.source === "upload") {
  status.textContent = "凭证已载入";
  status.classList.add("ready");
  actionHint.textContent = "下一步在 GitHub 的“Music ZIP file”下方添加 ZIP";
  continueButton.textContent = "继续到 GitHub";
  continueButton.disabled = false;
} else {
  status.textContent = "投稿服务未配置";
  status.classList.add("error");
  actionHint.textContent = "网易云投稿需要已部署的自动投稿服务";
  continueButton.textContent = "暂不可用";
  continueButton.disabled = true;
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (errors.length > 0 || (submission.source === "netease" && !neteaseReady)) return;
  if (!configuredApi) {
    continueButton.disabled = true;
    continueButton.textContent = "正在打开 GitHub";
    window.location.assign(buildIssueUrl(submission, configuredIssueUrl));
    return;
  }
  if (submission.source === "upload") {
    const fileError = validateZipFile(fileInput.files[0]);
    if (fileError) return showUploadError(fileError);
  }
  continueButton.disabled = true;
  continueButton.textContent = "正在提交";
  actionHint.textContent = "正在进入处理队列，请勿关闭页面";
  uploadError.hidden = true;
  try {
    const result = submission.source === "netease"
      ? await submitNetEase(configuredApi, submission)
      : await submitUpload(configuredApi, submission, fileInput.files[0]);
    status.textContent = "提交成功";
    const submittedName = result.song_name || submission.song || submission.providerId;
    uploadSuccess.replaceChildren(document.createTextNode(`歌曲“${submittedName}”已进入自动处理队列。`));
    if (result.issue_url) {
      const issueLink = document.createElement("a");
      issueLink.href = result.issue_url;
      issueLink.textContent = ` 查看任务 #${result.issue_number}`;
      uploadSuccess.append(issueLink);
    }
    uploadSuccess.hidden = false;
    actionHint.textContent = "处理完成并同步后即可使用 /getcd 获取";
    continueButton.textContent = "已提交";
    continueButton.disabled = true;
    fileInput.disabled = true;
  } catch (error) {
    showUploadError(error.message || "自动提交失败，请稍后重试。");
    continueButton.textContent = "重新提交";
    continueButton.disabled = false;
    actionHint.textContent = "提交失败后可直接重试";
  }
});

function updateFileState() {
  const fileError = submission.source === "upload" ? validateZipFile(fileInput.files[0]) : "";
  continueButton.disabled = errors.length > 0 || Boolean(fileError);
  if (fileInput.files.length > 0 && fileError) showUploadError(fileError);
  else uploadError.hidden = true;
}

function markInvalidLink() {
  status.textContent = "链接无效";
  status.classList.add("error");
  errorNotice.hidden = false;
  continueButton.disabled = true;
}

function showUploadError(message) {
  uploadError.textContent = message;
  uploadError.hidden = false;
}
