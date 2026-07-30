# Migrating to the MusicMC Monorepo

[English](MIGRATION.md) | [简体中文](MIGRATION.zh-CN.md)

The monorepo stores the plugin, portal, Worker, Actions, and root `catalog.json`. The source template contains no production catalog or service URL; operators import data only from systems they control.

## Required changes

1. Copy the latest `catalog.json` from the old deployment, or start with an empty catalog.
2. Create a GitHub fine-grained PAT restricted to the new self-hosted repository.
3. Change Worker `GITHUB_REPOSITORY` to the new `OWNER/REPOSITORY`.
4. Generate a new P-256 key pair and update the server private key, repository public key, and Worker `UPLOAD_PUBLIC_KEY` together.
5. Configure the new repository's Actions Secrets, Variables, and Issue labels.
6. Have an administrator manually run the Pages and Worker deployment workflows.
7. Rebuild the plugin with the new repository and portal values.

## Existing songs

Absolute `pack_url`, `manifest_url`, and `source_archive_url` values may continue pointing to old releases. Do not remove old assets until migration and every checksum are verified. To become independent of the old repository, copy release assets first, then update URLs and hashes.

## Recommended sequence

1. Create the public fork without enabling a deployment.
2. Import the catalog and generate new keys.
3. Configure repository Secrets, Variables, and labels.
4. Manually deploy Worker and Pages.
5. Build and install the Spigot plugin.
6. Submit one short audio file as an end-to-end test.
7. Retire old services only after the release, catalog commit, server sync, and `/getcd` all succeed.
