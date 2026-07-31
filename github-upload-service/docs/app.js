import {
  buildIssueUrl,
  readSubmissionParams,
  submitNetEase,
  submitUpload,
  validateSubmission,
  validateZipFile,
} from "./portal.mjs";

const submission = readSubmissionParams(window.location.href);
const errors = validateSubmission(submission);
const form = document.querySelector("#submission-form");
const continueButton = document.querySelector("#continue-button");
const status = document.querySelector("#link-status");
const errorNotice = document.querySelector("#link-error");
const uploadField = document.querySelector("#upload-field");
const neteaseField = document.querySelector("#netease-field");
const fileInput = document.querySelector("#music-zip");
const actionHint = document.querySelector("#action-hint");
const uploadError = document.querySelector("#upload-error");
const uploadSuccess = document.querySelector("#upload-success");
const configuredApi = window.MUSICMC_CONFIG?.uploadApiUrl?.trim() || "";
const configuredIssueUrl = window.MUSICMC_CONFIG?.issueUrl?.trim() || "";

document.querySelector("#song-name").value = submission.song;
document.querySelector("#player-name").value = submission.player;
document.querySelector("#upload-ticket").value = submission.ticket;
document.querySelector("#source-name").value = submission.source === "netease" ? "网易云音乐" : "ZIP 文件";
document.querySelector("#netease-id").value = submission.providerId;
uploadField.hidden = submission.source !== "upload";
neteaseField.hidden = submission.source !== "netease";

if (errors.length === 0) {
  status.textContent = "凭证已载入";
  status.classList.add("ready");
} else {
  status.textContent = "链接无效";
  status.classList.add("error");
  errorNotice.hidden = false;
}

if (configuredApi) {
  actionHint.textContent = submission.source === "netease"
    ? "由服务器队列获取并处理网易云音频"
    : "文件名不限，MusicMC 会自动规范化";
  if (submission.source === "upload") {
    fileInput.addEventListener("change", updateFileState);
  }
  updateFileState();
} else if (configuredIssueUrl) {
  actionHint.textContent = submission.source === "upload"
    ? "下一步在 GitHub 的“Music ZIP file”下方添加 ZIP"
    : "下一步由 GitHub 创建自动处理任务";
  continueButton.textContent = "继续到 GitHub";
  continueButton.disabled = errors.length > 0;
} else {
  actionHint.textContent = "投稿服务尚未配置";
  continueButton.textContent = "暂不可用";
  continueButton.disabled = true;
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (errors.length > 0) return;
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
    uploadSuccess.replaceChildren(document.createTextNode(`歌曲“${submission.song}”已进入自动处理队列。`));
    if (result.issue_url) {
      const issueLink = document.createElement("a");
      issueLink.href = result.issue_url;
      issueLink.textContent = ` 查看任务 #${result.issue_number}`;
      uploadSuccess.append(issueLink);
    }
    uploadSuccess.hidden = false;
    actionHint.textContent = "处理完成并同步后即可使用 /getcd 获取";
    continueButton.textContent = "已提交";
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

function showUploadError(message) {
  uploadError.textContent = message;
  uploadError.hidden = false;
}
