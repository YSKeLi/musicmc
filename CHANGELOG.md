# Changelog

[English](CHANGELOG.md) | [简体中文](CHANGELOG.zh-CN.md)

## 2.0.0

- Added catalog schema v2 with rolling resource bundles, immutable revisions, source metadata, enabled state, and legacy v1 migration.
- Removed eager server-side audio downloads; clients now load required bundles directly from external storage.
- Added five-second segmented jukebox playback with 25-block audience tracking and linear attenuation.
- Added automatic ZIP and optional NetEase submissions with no manual approval step.
- Added one-active-job-per-player protection, global queue limits, and unlimited lifetime submissions.
- Added in-game browser, search, recent uploads, player uploads, local administration, and revision-preserving replacement.
- Added GitHub Release and S3-compatible publishing backends, multi-key verification, automatic Pages refresh, and bilingual documentation.
