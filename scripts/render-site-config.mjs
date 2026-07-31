#!/usr/bin/env node

import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";

const outputArgument = process.argv[2];
const repository = String(process.env.GITHUB_REPOSITORY || "").trim();
const uploadApiUrl = String(process.env.MUSICMC_UPLOAD_API_URL || "").trim().replace(/\/$/, "");
const configuredCatalogUrl = String(process.env.MUSICMC_CATALOG_URL || "").trim();
const catalogBranch = String(process.env.MUSICMC_CATALOG_BRANCH || "main").trim();

if (!outputArgument || !/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repository)) {
  console.error("Usage: GITHUB_REPOSITORY=owner/repo node scripts/render-site-config.mjs <output>");
  process.exit(2);
}
if (uploadApiUrl && new URL(uploadApiUrl).protocol !== "https:") {
  console.error("MUSICMC_UPLOAD_API_URL must use HTTPS.");
  process.exit(2);
}
if (configuredCatalogUrl && new URL(configuredCatalogUrl).protocol !== "https:") {
  console.error("MUSICMC_CATALOG_URL must use HTTPS.");
  process.exit(2);
}
if (!/^[A-Za-z0-9._/-]{1,200}$/.test(catalogBranch) || catalogBranch.includes("..")) {
  console.error("MUSICMC_CATALOG_BRANCH contains unsupported characters.");
  process.exit(2);
}

const output = resolve(outputArgument);
const [repositoryOwner, repositoryName] = repository.split("/");
const catalogUrls = configuredCatalogUrl
  ? [
      configuredCatalogUrl,
      `https://cdn.jsdelivr.net/gh/${repositoryOwner}/${repositoryName}@${catalogBranch}/catalog.json`,
    ]
  : ["./catalog.json"];
const config = {
  uploadApiUrl,
  issueUrl: `https://github.com/${repository}/issues/new`,
  catalogUrls,
};
mkdirSync(dirname(output), { recursive: true });
writeFileSync(
  output,
  `window.MUSICMC_CONFIG = Object.freeze(${JSON.stringify(config, null, 2)});\n`,
  "utf8",
);
