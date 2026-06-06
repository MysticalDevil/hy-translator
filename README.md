# Hy Translator

Hy Translator is an Android offline translation app powered by
`llama.cpp` and Tencent Hy-MT2-1.8B GGUF models. The project is being
standardized as a teaching-oriented Android app: thin Activity, Compose
UDF, pure domain models, repository boundaries, foreground download
notifications, and isolated native/OCR/ASR adapters.

## Current Status

Working in the debug build:

- Offline translation UI with source/target language selection.
- GGUF model selection and lazy model download.
- Foreground model download service with progress notifications.
- Android 16+ `Notification.ProgressStyle` when available, standard
  progress fallback on older supported systems.
- Live text translation toggle.
- OCR entry from camera/gallery through the current ML Kit adapter.
- ASR/OCR AI asset status and lazy download entry points.
- Activity recreation support without `android:configChanges`.

Not finished yet:

- ASR runtime is not integrated. `VoiceInputRepository` exists and the
  current `SherpaOnnxVoiceInputRepository` returns a visible placeholder
  error until sherpa-onnx streaming Zipformer is wired in.
- OCR runtime is still ML Kit. `OcrTextRepository` exists so PP-OCRv5
  mobile can replace the current adapter without changing Route/UI code.
- Download progress is modeled in domain types, but service progress is
  still exposed from a service-level flow. Persistent resumable progress
  state remains planned.
- Hilt is planned. The current code uses `HyTranslatorApplication` and a
  hand-written `AppContainer` as a migration step.

See [PLAN.md](PLAN.md) for the full standardization plan.

## Requirements

- Android Studio or Android Gradle tooling.
- Android SDK with API 37 compile platform.
- Android 13 (API 33) or newer device.
- Network access for first-time model and AI asset downloads.
- Enough storage for the selected translation model.

The Android SDK on the development machine used for this project is:

```bash
/home/omega/Android/Sdk
```

## Build And Run

Build the debug APK:

```bash
./gradlew :app:assembleDebug
```

Install on the connected device:

```bash
/home/omega/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch:

```bash
/home/omega/Android/Sdk/platform-tools/adb shell am start -n org.devil.hytranslator/.MainActivity
```

## Verification

Small local gate used during standardization:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest
```

Device UI/instrumented gate:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Native module smoke/build gate:

```bash
./gradlew :lib:assembleDebug :lib:testDebugUnitTest
```

Markdown lint is intentionally not run in the current workflow unless
explicitly requested.

## Architecture Reading Guide

Start here when reading the app as teaching material:

1. `app/src/main/java/org/devil/hytranslator/MainActivity.kt`
   sets edge-to-edge UI, theme, and hosts `TranslatorRoute`.
2. `HyTranslatorApplication` creates `DefaultAppContainer`.
   This is a temporary DI entry point before Hilt.
3. `TranslatorRoute` collects lifecycle-aware UI state, owns Android
   permission launchers, and wires platform callbacks into UI events.
4. `TranslatorScreen` is a mostly stateless Compose screen. It receives
   state and callbacks instead of constructing repositories or accessing
   data singletons.
5. `TranslatorViewModel` exposes `TranslatorUiState` and consumes
   `TranslatorEvent`.
6. Domain models and repository contracts live under
   `app/src/main/java/org/devil/hytranslator/domain`.
7. Android and external implementation details live in `data`,
   `service`, `platform`, and `lib`.

### Dependency Direction

```text
MainActivity
  -> TranslatorRoute
    -> TranslatorViewModel
      -> domain repository interfaces
        <- data/service/platform implementations
```

Rules currently enforced by code review and search checks:

- `domain` does not depend on Android resources, Android framework
  classes, or `InferenceEngine.State`.
- UI does not directly read `data.Languages` or `data.ModelOptions`.
- URLs are stored in resources, not hardcoded in Kotlin.
- OCR and ASR runtime entry points are adapters, not UI implementation
  details.

## Important Packages

```text
app/src/main/java/org/devil/hytranslator/
  data/
    repository/
      ModelRepositoryImpl.kt
      TranslatorRepositoryImpl.kt
      AiAssetRepositoryImpl.kt
      MlKitOcrTextRepository.kt
      SherpaOnnxVoiceInputRepository.kt
  domain/
    model/
    repository/
  platform/
    ocr/OcrTextRepository.kt
  service/
    ModelDownloadService.kt
    AiAssetDownloadService.kt
    ModelDownloadNotifier.kt
    AiAssetDownloadNotifier.kt
  ui/
    TranslatorRoute.kt
    TranslatorScreen.kt
    TranslatorViewModel.kt
    OcrWorkflowController.kt
```

Native inference is isolated in `lib/`. The app maps native inference
state into app-owned domain state before exposing it to UI.

## Native Build Notes

The `:lib` module supports a prebuilt llama.cpp cache:

- Local generated artifacts live under `lib/src/main/prebuilt/<abi>/`.
- If required native libraries exist, CMake links against the prebuilt
  artifacts.
- If they are missing, the build path can trigger llama.cpp compilation.
- The prebuilt cache is local build output and is not the app/domain
  contract.

## Design Decisions

See the ADRs in [docs/adr](docs/adr):

- [ADR 0001: Keep Domain Pure](docs/adr/0001-keep-domain-pure.md)
- [ADR 0002: Use AppContainer Before Hilt](docs/adr/0002-use-appcontainer-before-hilt.md)
- [ADR 0003: Foreground Downloads With Domain Progress](docs/adr/0003-foreground-downloads-domain-progress.md)
- [ADR 0004: Native Prebuilt Cache](docs/adr/0004-native-prebuilt-cache.md)

## License

MIT License. This project uses `llama.cpp` and Tencent Hy-MT2-1.8B GGUF
model artifacts under their respective licenses.
