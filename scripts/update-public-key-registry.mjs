#!/usr/bin/env node

import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const [registryArgument, keyId, publicKeyArgument] = process.argv.slice(2);
if (!registryArgument || !/^[A-Za-z0-9_-]{1,32}$/.test(keyId || "") || !publicKeyArgument) {
  console.error("Usage: node scripts/update-public-key-registry.mjs <registry.json> <key-id> <public.pem>");
  process.exit(2);
}
const registryPath = resolve(registryArgument);
const publicKey = readFileSync(resolve(publicKeyArgument), "utf8").trim() + "\n";
if (!publicKey.startsWith("-----BEGIN PUBLIC KEY-----")) {
  console.error("The supplied public key is not an SPKI PEM public key.");
  process.exit(2);
}
const registry = existsSync(registryPath)
  ? JSON.parse(readFileSync(registryPath, "utf8"))
  : {};
registry[keyId] = publicKey;
const sorted = Object.fromEntries(Object.entries(registry).sort(([left], [right]) => left.localeCompare(right)));
writeFileSync(registryPath, `${JSON.stringify(sorted, null, 2)}\n`, "utf8");
console.log(`Registered upload public key: ${keyId}`);
