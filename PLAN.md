# Hy Translator Android 标准化计划

本计划目标是把当前 App 标准化成一个适合教学的 Google Android
最佳实践示例：结构清楚、行为可测、后台任务可靠、UI 状态单向流动，
并且能解释每个工程决策为什么存在。

## 目标和验收标准

- 遵循 Google 推荐 Android 架构：UI、domain、data 分层，依赖方向稳定，UI 使用单向数据流。
- App 代码可以作为教学材料：关键模块职责明确，命名一致，入口、状态、事件、数据源都容易追踪。
- 下载、通知、ASR、OCR、native inference 等平台能力被隔离在可替换的
  adapter 中，业务层不直接依赖 Android framework 或 llama native 细节。
- 每个重要用户流程都有最小测试覆盖：模型下载、下载中断恢复、模型选择、翻译、ASR、OCR、权限拒绝、配置变化。
- 构建流程可复现：Gradle task 清晰，native 预编译缓存行为有文档、有验证，不依赖隐式本机状态。

## 当前状态摘要

已有基础：

- 单 Activity + Compose 架构已经成型。
- 下载服务已经能以前台服务保持后台下载，并使用 Android 新通知进度样式做实时进度。
- `:lib` native 模块已经有预编译 llama/ggml 库缓存：存在产物时复用，
  不存在时触发 llama.cpp 编译。
- ViewModel 没有继承 AndroidViewModel，已开始使用 `StateFlow` 暴露状态。

主要差距：

- `MainActivity` 和 `TranslatorRoute` 已不再手动创建 repository、controller、notifier；
  当前通过 `HyTranslatorApplication` + `AppContainer` 作为过渡 DI 入口，最终仍应收敛到 Hilt。
- `TranslatorViewModel` 同时负责翻译、模型下载、模型加载、
  通知状态观察、语言列表和清理逻辑，职责仍偏宽；ASR runtime 启停已通过
  `VoiceInputRepository` 抽象隔离。
- UI 层已不再直接引用 `data.Languages`、`data.ModelOptions`；OCR 权限、相册 launcher
  由 Route 处理，OCR 状态转换集中到 `OcrWorkflowController`，图片解码、EXIF 旋转和
  ML Kit OCR 已封装到 `OcrTextRepository` adapter；仍有 CameraX 预览和较大 Screen
  组件待继续拆分。
- domain 模型已移除 Android resource id；`Language`、`ModelOption` 和
  `TranslatorRepository` 状态都使用 app/domain 自己的纯 Kotlin 类型。
- 选中模型持久化已从 SharedPreferences 迁移到 Preferences DataStore，旧 sharedpref
  备份排除规则保留用于历史安装。
- 下载 Service 暴露的状态类型已迁到 domain 层，ViewModel 不再依赖 Service 内部
  `State` 类型；但进度仍通过 companion object 静态 `StateFlow` 暴露，后续需要持久化进度流。
- `app` 已有 ViewModel 单元测试、OCR workflow 单元测试、Compose UI 测试和
  Activity 重建 instrumented 测试；`:lib` 当前测试仍偏 smoke/基础覆盖，后续需继续增强。
- Manifest 已移除 `MainActivity` 的 `configChanges`，并新增配置变化重建测试；
  后续继续覆盖更多 UI 状态和进程死亡恢复。

## 目标架构

推荐包结构：

```text
app/
  core/
    designsystem/
    model/
    notification/
    permissions/
  feature/translate/
    presentation/
    domain/
    data/
  feature/model/
    presentation/
    domain/
    data/
  feature/ocr/
    presentation/
    domain/
    data/
  feature/speech/
    presentation/
    domain/
    data/
  platform/
    download/
    camera/
    filesystem/
    nativebridge/
```

分层原则：

- `presentation` 只处理 UI state、UI event、Compose 和 Android lifecycle。
- `domain` 只放纯 Kotlin 模型、use case、repository interface 和业务规则。
- `data` 实现 repository，负责 DataStore、文件、网络、OkHttp、sherpa-onnx、
  PaddleOCR、native adapter。
- `platform` 封装 Android framework 能力，例如通知、前台服务、JobScheduler、CameraX、权限、文件路径。
- `:lib` 保持为 native inference boundary；App domain 只能依赖 app
  自己定义的 `TranslationEngine` 抽象。

Compose 状态模型：

- 每个 screen 使用一个稳定的 `UiState`。
- UI 事件统一通过 `onEvent(event)` 或清晰的 event sink 上送。
- Composable 默认无状态；Route 负责收集 `StateFlow`、处理
  permission launcher、navigation 和 lifecycle。
- 所有 Flow 在 UI 使用 lifecycle-aware collection。

DI 默认决策：

- 教学目标下推荐引入 Hilt：`@HiltAndroidApp`、`@HiltViewModel`、
  repository/module/service 注入路径更标准。
- 如果希望先降低迁移风险，可以先落一个手写 `AppContainer`，
  但最终教学版应收敛到 Hilt。

## 分阶段实施

### Phase 0：行为冻结和基线

- 梳理当前用户流程：首次启动、无模型、模型下载、后台下载、
  划掉任务、通知权限拒绝、模型切换、翻译、OCR 拍照、OCR 相册。
- 为当前行为补最小回归测试或手动验收清单，避免架构迁移时改变用户可见行为。
- 明确 Android 版本策略：最低 SDK、目标 SDK、通知权限、
  前台服务类型、Android 16 进度通知样式、旧系统 fallback。

### Phase 1：构建和质量入口标准化

- 建立统一质量入口：`fmt`、`lint`、`test`、`build`、`check`，
  优先用 Gradle task 或项目 task runner 暴露。
- 保留并文档化 native 预编译缓存流程：
  - `lib/src/main/prebuilt/<abi>/` 为本地生成缓存，默认不提交。
  - 缺少静态/动态库时触发 llama.cpp 编译。
  - 产物存在且完整时直接导入链接。
- 增加构建验证：检查每个 ABI 的必需库是否存在、文件类型是否正确、
  CMake 是否进入 prebuilt 路径。
- 清理 Gradle deprecated API，例如 `jniLibs.setSrcDirs(...)`。
- 为 release 构建补标准设置：R8/minify、resource shrink、签名配置文档、
  baseline profile 评估。

### Phase 2：domain/data 边界重建

- domain 模型中的 Android resource id 已移除，当前改为纯 Kotlin 字段；后续如需完整国际化，
  再在 presentation 映射层引入 `UiText`。
- 把语言、模型推荐、模型元数据拆为 domain model + data/provider 实现。
- 继续收敛 `TranslatorRepository` 的 app 层状态模型：
  `TranslationEngineState` 已替代 `InferenceEngine.State`，后续补 typed error 和状态映射测试。
- `ModelRepository` 返回 domain 类型，不暴露裸路径字符串；路径解析放在
  data/filesystem adapter。
- DataStore 已替代 SharedPreferences 保存选中模型；下载状态和用户设置后续继续迁移到
  持久化状态流。
- 把下载、模型文件校验、断点续传拆成明确 data source，repository 只组合业务语义。

### Phase 3：DI 和入口标准化

- 已新增 Application 类和手写 `AppContainer` 作为迁移台阶；后续启用 Hilt。
- 为 repository、OkHttp、DataStore、notifier、download controller、
  native adapter 提供 module。
- `MainActivity` 不再手动 new 依赖，只负责 `setContent`、edge-to-edge、
  permission route 和 navigation host。
- `ModelDownloadService` 通过注入获取依赖，避免在 service 内直接创建 repository。
- ViewModel factory 已独立为 `TranslatorViewModelFactory`，手动组装逻辑集中到
  `DefaultAppContainer`，后续由 Hilt modules 替换。

### Phase 4：Compose UDF 和 UI 拆分

- 新增 `TranslatorUiState`，合并当前多个 Flow：
  - 输入文本、输出文本、语言选择、模型状态、下载进度、错误、loading、OCR 状态。
- 新增 `TranslatorEvent`：
  - 输入变化、翻译、取消、交换语言、选择模型、开始下载、取消下载、OCR 结果确认、错误已消费。
- 拆分 `TranslatorRoute` 和 `TranslatorScreen`：
  - Route 收集 ViewModel state，处理 permission、launcher、lifecycle。
  - Screen 只接收 state 和 event lambda。
- `TranslatorScreen` 已改为通过参数接收语言列表、模型列表相关状态，不再直接访问 data singleton。
- 已新增 `TranslatorRoute`，`MainActivity` 只负责 edge-to-edge、主题和承载 Route；
  OCR 权限、相册 launcher 和图片处理已迁出 Screen，后续继续把 OCR 处理链路下沉到 use case/data source。
- 图片 URI 解码、EXIF 旋转、OCR 调用已从 `TranslatorScreen` 移到
  `OcrTextRepository` adapter；OCR flow 状态转换已移到 `OcrWorkflowController`。
  当前 adapter 实现仍是 ML Kit，后续替换 PaddleOCR 时优先替换该 adapter。
- 为关键 Composable 增加 preview parameter provider 和稳定假数据。
- 已删除不必要的 `configChanges`，通过 `MainActivityConfigurationTest` 验证 Activity
  重建后输入状态保留；后续再引入 SavedStateHandle 覆盖进程死亡恢复。

### Phase 5：后台下载和通知标准化

- 评估并落地 Google 推荐的用户发起数据传输方案：
  - Android 14+ 优先考虑 User-Initiated Data Transfer job。
  - 需要即时前台可见和兼容旧系统时保留 Foreground Service fallback。
- 下载状态模型已从 Service 内部类型迁到 domain 层；后续还需移除 service 静态变量，
  改为 repository 暴露持久化进度流。
- 下载进度、速度、剩余大小、错误原因使用统一 domain model，再映射到 UI 和 notification。
- 通知 adapter 只负责平台渲染：
  - Android 16+ 使用 `Notification.ProgressStyle` 和实时更新。
  - 旧系统使用标准 determinate progress notification。
  - 小图标统一使用符合 Android 通知规范的单色 drawable。
- 明确用户划掉任务后的策略：
  - 如果只是移除 recent task，不应静默破坏已承诺的下载。
  - 如果系统终止进程，下载状态必须可恢复或以明确失败状态呈现。

### Phase 6：OCR、CameraX 和权限

- CameraX 绑定改为 lifecycle-aware、state-driven，镜头切换必须触发重新绑定。
- OCR 处理链路拆成 `DecodeImageUseCase`、`RecognizeTextUseCase`、`ApplyOcrTextUseCase`。
- OCR runtime 已有 `OcrTextRepository` adapter 边界，当前实现为 ML Kit；后续切换为
  PaddleOCR PP-OCRv5 时保持 Route/UI 不变。
- OCR 引擎从 ML Kit 切换为 PaddleOCR PP-OCRv5 mobile：
  - 使用 `PP-OCRv5_mobile_det` 和 `PP-OCRv5_mobile_rec`。
  - 使用 `ppocr_keys_ocrv5.txt` 字典。
  - 第一版只启用 det + rec，不启用 unwarp 和方向分类模型。
  - 模型作为首次使用资源下载，不打包进 APK。
- 权限状态集中建模，UI 只显示当前状态和触发请求事件。
- 相册和相机错误使用 typed error，避免在 UI 层拼接平台异常。

### Phase 7：native inference 边界

- 在 app data 层新增 `NativeTranslationEngineAdapter`，负责把 `:lib` 的
  state/error 映射成 app domain 类型。
- `TranslatorViewModel` 不直接持有 native engine，不直接调用 native unload/destroy。
- `InferenceEngineImpl` 的单例、dispatcher、native library load 和
  destroy 行为补测试或至少补 smoke test。
- native 模块文档化：
  - 支持 ABI。
  - llama.cpp 源码同步方式。
  - prebuilt 缓存生成和复用规则。
  - 失败时如何诊断 CMake/Ninja/NDK 日志。

### Phase 8：测试体系

- ViewModel 单元测试：
  - 使用 fake use case/repository。
  - 验证 UI state 和 one-shot event。
  - 覆盖翻译成功、取消、模型未加载、下载中、下载失败。
- Repository 和 downloader 测试：
  - 使用 fake file system 或临时目录。
  - 使用 MockWebServer 或等价 fake HTTP source 测试 Range、断点续传、大小校验、取消。
- Compose UI 测试：
  - 已新增 `TranslatorScreenTest` 覆盖主屏空状态、翻译结果状态和模型选择弹窗。
  - 后续继续补下载进度展示、权限拒绝状态和语言交换。
- OCR workflow 单元测试：
  - 已覆盖 source picker、camera、hide 的状态转换。
  - 后续补相册 URI、bitmap 识别成功和 typed error 路径。
- Instrumented 测试：
  - 已新增 `MainActivityConfigurationTest`，覆盖 Activity 重建后输入状态保留。
  - 通知权限路径。
  - 前台服务/UIDT fallback。
  - CameraX/OCR smoke path。
- Native smoke test：
  - 加载库。
  - 初始化 engine。
  - 失败路径映射。

### Phase 9：教学文档

- README 已增加当前状态、架构阅读入口、模块职责、运行命令和验证命令。
- 已为关键决策写 ADR：
  - 为什么 domain 不依赖 Android resource/native state。
  - 为什么先使用 `AppContainer`，后续再迁移 Hilt。
  - 为什么当前下载使用 FGS + domain progress，并保留 UIDT job 评估。
  - 为什么 native 预编译缓存默认不作为 app contract。
- 增加“从代码读架构”的教学入口：
  - `MainActivity` 到 `TranslatorRoute`。
  - `TranslatorViewModel` 到 use case。
  - use case 到 repository。
  - repository 到 platform adapter。

### Phase 10：离线 ASR 和 AI 资源管理

- 新增统一 AI 资源层：
  - `AiAsset` 描述 translation/asr/ocr 资源、文件清单、大小、校验、下载 URL 和本地路径。
  - `AiAssetRepository` 暴露资源状态、下载、校验和清理。
  - ASR/OCR 模型首次下载，不进入 APK。
- ASR 资源下载和 runtime 启停已拆开：
  - 资源状态仍由 `AiAssetRepository` lazy 检查和下载。
  - 语音 runtime 通过 `VoiceInputRepository` lazy start/stop。
  - 当前生产实现 `SherpaOnnxVoiceInputRepository` 是显式未接入占位，返回 UI 可见错误；
    后续在该 adapter 内接入 sherpa-onnx streaming runtime。
- ASR 使用 sherpa-onnx streaming Zipformer：
  - 第一版模型为 `sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20`。
  - 支持中英语音输入，输出 partial/final result。
  - Android native runtime 包含 `libsherpa-onnx-jni.so` 和 `libonnxruntime.so`。
  - 第一版设备目标为 `arm64-v8a`。
- 录音链路使用 `AudioRecord` adapter：
  - 采集 16 kHz mono PCM。
  - UI 通过麦克风 icon button 触发。
  - `RECORD_AUDIO` 权限由 Route 层处理。
  - partial result 实时写入输入框，并复用实时翻译开关触发输出。
- 资源下载复用模型下载的后台任务和通知实践：
  - 下载进度、失败、校验失败、完成状态使用统一 domain model。
  - 通知文案从“模型下载”扩展为“AI 资源下载”。
  - ASR/OCR 资源未就绪时，UI 显示下载入口而不是静默失败。

### Phase 11：主界面体验重构（最低优先级）

- 通过 `android-cli` 观察 Google Translator 当前 Android 主界面交互和布局。
- 重构主翻译界面时只做轻量借鉴，不 1:1 复刻：
  - 保留本 App 的模型下载、离线翻译、OCR、实时翻译和语音输入能力。
  - 参考其语言选择、输入/输出区域层级、底部快捷操作和状态表达。
  - 避免复制品牌视觉、图标组合、专有动效或完全相同布局。
- 增加 Compose preview 和截图验收，覆盖空输入、翻译结果、下载中、
  ASR 监听中、OCR 结果确认等状态。
- 该阶段优先级最低：只有在架构、下载、ASR/OCR、native adapter 和测试体系稳定后再做。

## 建议优先级

1. 先做 Phase 0 和 Phase 8 的最小基线测试，锁住当前行为。
2. 再做 Phase 2 和 Phase 4，把 domain 泄漏和 UI 过载问题拆开。
3. 然后做 Phase 3，引入 Hilt，把对象创建路径标准化。
4. 最后做 Phase 5、Phase 6、Phase 7、Phase 10，把后台下载、OCR、ASR 和 native 边界
   变成可教学的完整示例。
5. Phase 11 主界面体验重构最低优先级，只在核心能力和测试稳定后执行。

## 验收命令

每个阶段完成后至少运行：

```bash
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :lib:testDebugUnitTest
```

涉及 native 构建时额外运行：

```bash
./gradlew :lib:assembleDebug
```

涉及 UI 或设备能力时额外运行：

```bash
./gradlew :app:connectedDebugAndroidTest
```

Markdown 文档变更后可按需运行（当前按用户要求不自动运行）：

```bash
rumdl fmt PLAN.md
rumdl check PLAN.md
```
