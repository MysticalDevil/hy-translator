# Hy 翻译器

Hy Translator 是一个 Android 离线翻译应用，底层使用 `llama.cpp`
和腾讯 Hy-MT2-1.8B GGUF 模型。当前项目正在标准化为教学用 Android
示例：薄 Activity、Compose UDF、纯 domain 模型、repository 边界、前台下载通知，
以及隔离的 native/OCR/ASR adapter。

## 当前状态

Debug 版当前可用：

- 离线翻译主界面，支持源语言/目标语言选择。
- GGUF 模型选择和按需下载。
- 前台模型下载服务和进度通知。
- Android 16+ 使用 `Notification.ProgressStyle`，旧系统使用标准进度通知。
- 实时文本翻译开关。
- OCR 可从相机/相册进入，当前 runtime adapter 仍是 ML Kit。
- ASR/OCR AI 资源状态展示和按需下载入口。
- 已移除 `android:configChanges`，支持 Activity 重建后的输入状态保留。

尚未完成：

- ASR runtime 尚未接入。`VoiceInputRepository` 已存在，当前
  `SherpaOnnxVoiceInputRepository` 会返回用户可见的占位错误，后续在这里接入
  sherpa-onnx streaming Zipformer。
- OCR runtime 尚未切换到 PaddleOCR。`OcrTextRepository` 已存在，后续 PP-OCRv5
  mobile 可以替换当前 ML Kit adapter，而不改变 Route/UI。
- 下载状态已经迁到 domain 类型，但 service 进度仍通过 service 级 flow 暴露；
  持久化、可恢复的进度流仍在计划中。
- Hilt 仍在计划中。当前使用 `HyTranslatorApplication` 和手写
  `AppContainer` 作为迁移台阶。

完整标准化计划见 [PLAN.md](PLAN.md)。

## 环境要求

- Android Studio 或 Android Gradle 工具链。
- Android SDK，compile platform 为 API 37。
- Android 13 (API 33) 或更新设备。
- 首次下载模型和 AI 资源时需要网络。
- 设备需有足够空间保存所选翻译模型。

当前开发机使用的 Android SDK 路径：

```bash
/home/omega/Android/Sdk
```

## 构建和运行

构建 debug APK：

```bash
./gradlew :app:assembleDebug
```

安装到已连接设备：

```bash
/home/omega/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

启动：

```bash
/home/omega/Android/Sdk/platform-tools/adb shell am start -n org.devil.hytranslator/.MainActivity
```

## 验证

当前标准化过程中使用的本地最小质量门：

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest
```

真机 UI/instrumented 测试：

```bash
./gradlew :app:connectedDebugAndroidTest
```

Native 模块构建/测试：

```bash
./gradlew :lib:assembleDebug :lib:testDebugUnitTest
```

除非明确要求，当前流程不运行 Markdown lint。

## 架构阅读入口

把这个项目当教学材料阅读时，建议从这里开始：

1. `app/src/main/java/org/devil/hytranslator/MainActivity.kt`
   只负责 edge-to-edge、主题和承载 `TranslatorRoute`。
2. `HyTranslatorApplication` 创建 `DefaultAppContainer`。
   这是引入 Hilt 前的临时 DI 入口。
3. `TranslatorRoute` lifecycle-aware 收集 UI state，持有 Android 权限 launcher，
   并把平台回调转成 UI 事件。
4. `TranslatorScreen` 基本是无状态 Compose screen，只接收 state 和 callbacks，
   不创建 repository，也不访问 data singleton。
5. `TranslatorViewModel` 暴露 `TranslatorUiState`，消费 `TranslatorEvent`。
6. domain 模型和 repository contract 位于
   `app/src/main/java/org/devil/hytranslator/domain`。
7. Android 和外部实现细节放在 `data`、`service`、`platform` 和 `lib`。

### 依赖方向

```text
MainActivity
  -> TranslatorRoute
    -> TranslatorViewModel
      -> domain repository interfaces
        <- data/service/platform implementations
```

当前通过代码审查和搜索检查维持的规则：

- `domain` 不依赖 Android resource、Android framework class 或
  `InferenceEngine.State`。
- UI 不直接读取 `data.Languages` 或 `data.ModelOptions`。
- URL 放在 resources 中，不硬编码到 Kotlin。
- OCR 和 ASR runtime 是 adapter，不是 UI 实现细节。

## 重要包结构

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

Native 推理隔离在 `lib/`。App 会先把 native inference state 映射成 app 自己的
domain state，再暴露给 UI。

## Native 构建说明

`:lib` 模块支持 llama.cpp 预编译缓存：

- 本地生成产物位于 `lib/src/main/prebuilt/<abi>/`。
- 如果必需 native 库存在，CMake 会直接链接这些 prebuilt artifact。
- 如果产物缺失，构建路径可以触发 llama.cpp 编译。
- prebuilt cache 是本地构建产物，不是 app/domain contract。

## 架构决策

ADR 位于 [docs/adr](docs/adr)：

- [ADR 0001：保持 Domain 纯净](docs/adr/0001-keep-domain-pure.md)
- [ADR 0002：先使用 AppContainer，再迁移 Hilt](docs/adr/0002-use-appcontainer-before-hilt.md)
- [ADR 0003：前台下载和 Domain 进度模型](docs/adr/0003-foreground-downloads-domain-progress.md)
- [ADR 0004：Native 预编译缓存](docs/adr/0004-native-prebuilt-cache.md)

## 许可证

MIT License。本项目使用 `llama.cpp` 和腾讯 Hy-MT2-1.8B GGUF 模型产物，
请同时遵守它们各自的许可证。
