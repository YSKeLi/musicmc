#!/usr/bin/env node

import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";

const outputArgument = process.argv[2];
const repository = String(process.env.GITHUB_REPOSITORY || "").trim();
const uploadApiUrl = String(process.env.MUSICMC_UPLOAD_API_URL || "").trim().replace(/\/$/, "");

if (!outputArgument || !/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repository)) {
  console.error("Usage: GITHUB_REPOSITORY=owner/repo node scripts/render-site-config.mjs <output>");
  process.exit(2);
}
if (uploadApiUrl && new URL(uploadApiUrl).protocol !== "https:") {
  console.error("MUSICMC_UPLOAD_API_URL must use HTTPS.");
  process.exit(2);
}

const output = resolve(outputArgument);
const config = {
  uploadApiUrl,
  issueUrl: `https://github.com/${repository}/issues/new`,
};
mkdirSync(dirname(output), { recursive: true });
writeFileSync(
  output,
  `window.MUSICMC_CONFIG = Object.freeze(${JSON.stringify(config, null, 2)});\n`,
  "utf8",
);
