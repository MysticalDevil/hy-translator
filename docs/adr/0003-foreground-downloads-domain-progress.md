# ADR 0003: Foreground Downloads With Domain Progress

## Status

Accepted, with a planned migration.

## Context

Model downloads and AI asset downloads are user-visible, long-running,
and can continue while the app is backgrounded. Android requires visible
foreground work for this class of immediate transfer on currently
supported devices. Newer Android versions also provide richer progress
notification APIs.

## Decision

Use foreground services for current model and AI asset downloads:

- progress notifications are shown while transfers are active;
- Android 16+ uses `Notification.ProgressStyle` when available;
- older supported systems use standard determinate progress;
- notification rendering stays in notifier adapters;
- UI observes app-owned domain progress states.

Download state types are domain models:

- `DownloadProgress`;
- `ModelDownloadState`;
- `AiAssetDownloadState`.

## Consequences

The UI and tests no longer depend on service-internal state classes.
Downloads stay visible and resilient enough for the current debug app.

The remaining gap is persistence. Current progress is still exposed from
service-level flows. The planned next step is moving resumable progress
and failure state into repository-owned persistent state, and evaluating
User-Initiated Data Transfer jobs where they fit the product behavior.
