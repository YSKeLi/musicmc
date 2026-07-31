# Changelog

## 2.1.0 - 2026-07-31

- NetEase submissions now require only a song ID and support `/music netease url <URL>`.
- The signed submission page resolves and displays the NetEase title, artist, ID, and disc command name.
- Trusted NetEase CDN HTTP URLs are safely upgraded to HTTPS.
- Workflow labels are bootstrapped automatically and failed Issues retain a more specific NetEase error.

[English](CHANGELOG.md) | [简体中文](CHANGELOG.zh-CN.md)

## 2.0.0

- Added catalog schema v2 with rolling resource bundles, immutable revisions, source metadata, enabled state, and legacy v1 migration.
- Removed eager server-side audio downloads; clients now load required bundles directly from external storage.
- Added five-second segmented jukebox playback with 25-block audience tracking and linear attenuation.
- Added automatic ZIP and optional NetEase submissions with no manual approval step.
- Added one-active-job-per-player protection, global queue limits, and unlimited lifetime submissions.
- Added in-game browser, search, recent uploads, player uploads, local administration, and revision-preserving replacement.
- Added GitHub Release and S3-compatible publishing backends, multi-key verification, automatic Pages refresh, and bilingual documentation.
- Added guided automatic/manual deployment that creates separate runtime, Pages, and Worker services without assigning the source repository as a live service.
- Added server-specific plugin builds, safe v1 catalog migration, existing-site/Worker collision handling, and missing-default config upgrades.
- Forced the NetEase media lookup through the supported EAPI path and added adapter regression tests.
