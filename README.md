# Hy Translator

Offline translation app for Android, powered by [llama.cpp](https://github.com/ggerganov/llama.cpp) and the [Tencent Hy-MT2-1.8B](https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF) model. Supports 35 languages with full privacy — everything runs locally on your device, no network required for translation.

## Features

- **35 languages** — bidirectional translation with auto language detection
- **AI-powered** — uses Hy-MT2-1.8B large language model via llama.cpp
- **100% offline** — all translation runs locally, no cloud dependency
- **OCR input** — capture text from camera or gallery via ML Kit
- **Model variants** — choose from 5 quantization levels (Q2_K through Q8_0)
- **Material You** — dynamic color theming that adapts to your wallpaper

## Getting Started

### Prerequisites

- Android 13 (API 33) or higher
- ~2 GB free storage for model download
- Camera permission (optional, for OCR)

### Download & Install

1. Download the latest APK from [Releases](https://github.com/MysticalDevil/hy-translator/releases)
2. Install and launch
3. Select a model variant (Q4_K_M recommended for most devices)
4. Download the model (~1.1 GB)
5. Start translating

### Quick Start

1. Choose source and target languages from the top bar
2. Type or paste text, or use the camera button for OCR
3. Tap **Translate**
4. Copy the result with the copy button

## Architecture

```
app/                          # Main application module
├── MainActivity.kt           # Single Activity + state management
├── data/
│   ├── Languages.kt          # Language definitions (35 languages)
│   └── Models.kt             # Model variants + memory recommendation
├── service/
│   ├── TranslatorEngine.kt   # Translation wrapper (prompt building)
│   ├── ModelDownloader.kt    # GGUF model download (OkHttp + resume)
│   └── OcrEngine.kt          # ML Kit OCR wrapper
├── ui/
│   ├── TranslatorScreen.kt   # Main UI (LanguageBar, InputArea, OutputCard)
│   ├── CameraCapture.kt      # CameraX full-screen capture
│   ├── OcrBottomSheet.kt     # OCR flow bottom sheet
│   ├── OcrFlow.kt            # OCR state machine
│   └── ModelStatus.kt        # Model lifecycle states
└── theme/
    ├── Theme.kt              # Material 3 theme (Monet dynamic color)
    └── Type.kt               # Typography definitions

lib/                          # Native inference library
├── src/main/cpp/
│   ├── CMakeLists.txt        # CMake build + 16KB page alignment
│   └── ai_chat.cpp           # JNI bridge to llama.cpp
└── src/main/java/com/arm/aichat/
    └── InferenceEngine.kt    # Kotlin wrapper for llama.cpp inference

llama.cpp/                    # Git submodule
```

### Key Dependencies

| Library | Purpose |
|---------|---------|
| [llama.cpp](https://github.com/ggerganov/llama.cpp) | LLM inference engine |
| [Compose](https://developer.android.com/compose) | UI framework |
| [CameraX](https://developer.android.com/camera) | Camera capture |
| [ML Kit](https://developers.google.com/ml-kit) | OCR text recognition |
| [OkHttp](https://square.github.io/okhttp/) | Model download |

### Data Flow

```
User Input → TranslatorScreen
                │
                ▼
         MainActivity (State)
                │
    ┌───────────┼───────────┐
    ▼           ▼           ▼
 InputArea   OutputCard   StatusBanner
    │           │           │
    │    ┌──────┘           │
    ▼    ▼                  ▼
 TranslatorEngine    ModelDownloader
    │                    │
    ▼                    ▼
llama.cpp (native)   HuggingFace
```

## Development

### Build

```bash
# Clone with submodules
git clone --recurse-submodules https://github.com/MysticalDevil/hy-translator.git
cd hy-translator

# Build debug APK
./gradlew assembleDebug
```

### Code Quality

```bash
# Run lint
./gradlew :app:lint

# Run tests
./gradlew :app:test
```

## License

MIT License — see [LICENSE](LICENSE) for details.

This project uses [llama.cpp](https://github.com/ggerganov/llama.cpp) (MIT) and [Hy-MT2-1.8B](https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF) (Apache 2.0).
