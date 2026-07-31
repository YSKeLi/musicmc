#!/usr/bin/env node

import { createPrivateKey, createPublicKey } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";

const [privateArgument, publicArgument] = process.argv.slice(2);
if (!privateArgument || !publicArgument) {
  console.error("Usage: node scripts/derive-public-key.mjs <private.pem> <public.pem>");
  process.exit(2);
}

const privateKey = createPrivateKey(readFileSync(resolve(privateArgument), "utf8"));
const publicKey = createPublicKey(privateKey).export({ type: "spki", format: "pem" });
const output = resolve(publicArgument);
mkdirSync(dirname(output), { recursive: true });
writeFileSync(output, publicKey, { encoding: "utf8", mode: 0o644 });
console.log(`Public key: ${output}`);
