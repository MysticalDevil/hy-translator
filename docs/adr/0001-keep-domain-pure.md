# ADR 0001: Keep Domain Pure

## Status

Accepted.

## Context

The app coordinates Android UI, foreground services, native llama.cpp
inference, OCR, ASR, file storage, network downloads, and notification
rendering. Without a strict boundary, platform types such as Android
resources, `Context`, `Bitmap`, `Uri`, and native `InferenceEngine.State`
can leak into business state and make the code hard to test or teach.

## Decision

Domain models and repository contracts must stay pure Kotlin:

- no Android resource ids;
- no Android framework classes;
- no native inference state classes;
- no UI text formatting;
- no direct network, notification, service, or filesystem APIs.

Adapters map external state into app-owned domain models before
presentation consumes it. Examples:

- `TranslationEngineState` maps native inference state for app use.
- `ModelDownloadState` and `AiAssetDownloadState` are domain states even
  when produced by foreground services.
- `OcrTextRepository` is intentionally under `platform/ocr` because it
  accepts Android image types.

## Consequences

Tests can fake domain repositories without Android runtime. The UI layer
can reason over stable app-owned state. Platform adapters become
replaceable, which matters for switching ML Kit OCR to PaddleOCR and
adding sherpa-onnx ASR.

The tradeoff is more mapping code. That is intentional: explicit mapping
is easier to teach and audit than implicit cross-layer coupling.
