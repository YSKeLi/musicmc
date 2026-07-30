#!/usr/bin/env node

import { generateKeyPairSync } from "node:crypto";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";

const args = process.argv.slice(2);
const force = args.includes("--force");
const paths = args.filter((value) => value !== "--force");

if (paths.length !== 2) {
  console.error("Usage: node scripts/generate-keypair.mjs <private.pem> <public.pem> [--force]");
  process.exit(2);
}

const privatePath = resolve(paths[0]);
const publicPath = resolve(paths[1]);
if (!force && (existsSync(privatePath) || existsSync(publicPath))) {
  console.error("Key output already exists. Use --force only when rotating the complete deployment.");
  process.exit(2);
}

const { privateKey, publicKey } = generateKeyPairSync("ec", {
  namedCurve: "prime256v1",
  privateKeyEncoding: { type: "pkcs8", format: "pem" },
  publicKeyEncoding: { type: "spki", format: "pem" },
});

mkdirSync(dirname(privatePath), { recursive: true });
mkdirSync(dirname(publicPath), { recursive: true });
writeFileSync(privatePath, privateKey, { encoding: "utf8", mode: 0o600 });
writeFileSync(publicPath, publicKey, { encoding: "utf8", mode: 0o644 });
console.log(`Private key: ${privatePath}`);
console.log(`Public key:  ${publicPath}`);
