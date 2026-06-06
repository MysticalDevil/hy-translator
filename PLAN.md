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

## 当前优先级纠偏：先完成 App 可用性

前期已经完成了一批架构铺垫、文档和测试入口，但这些不能替代产品可用性。
后续执行顺序必须优先保证真实用户流程可用，再继续做 Hilt、文档、主界面视觉重构等外围目标。

当前最高优先级：

1. **通知和下载交互闭环**
   - 模型下载和 AI 资源下载共用一套可复用的下载状态、通知渲染和用户动作模型。
   - 通知动作和 App UI 必须双向绑定：通知取消/失败/完成能反映到 UI，UI 取消/重试/切换模型能反映到通知。
   - 下载状态不能只依赖 service companion object 内存流；需要可恢复的持久化 job/progress 状态。
   - 后台、切后台、划掉 recent task、进程被系统杀死后的行为必须明确并可验证。

2. **OCR 真正切换到 PaddleOCR PP-OCRv5 mobile**
   - 当前 OCR runtime adapter 仍是 ML Kit，只是有了 `OcrTextRepository` 替换边界。
   - 必须接入 PP-OCRv5 mobile det + rec + `ppocr_keys_ocrv5.txt`。
   - OCR 模型资源必须按需下载、校验、加载，不打包进 APK。
   - 相机/相册 OCR smoke path 要在真机验证。

3. **ASR 真正接入 sherpa-onnx streaming Zipformer**
   - 当前 `VoiceInputRepository` 是显式未接入占位，只返回 UI 可见错误。
   - 必须接入 sherpa-onnx streaming Zipformer runtime、JNI/native libs 和 `AudioRecord` 采集。
   - partial/final result 要实时写入输入框，并能配合实时翻译开关触发输出。
   - `RECORD_AUDIO` 权限和监听状态必须进入统一 UI state。

4. **端到端可用性验收**
   - 每个核心流程完成后都要 build、安装到真机、跑 connected tests 或等价 `adb am instrument`。
   - 最终收尾必须包含 debug APK 构建、真机部署、自动 UI/UX 验证和截图/布局树检查。

低优先级直到上述完成前不得抢占：

- Hilt 迁移。
- README/ADR 继续扩展。
- 主界面模仿 Google Translator 的视觉重构。
- 非必要的构建入口整理。

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
  `State` 类型；模型下载和 AI 资源下载的基础状态已改由 DataStore 持久化并通过 controller 暴露。
- 下载通知已有前台服务和进度样式，AI 资源下载通知已按 ASR/OCR 拆成稳定 notification id；
  但模型下载、AI 资源下载、UI 状态和通知动作仍未完全统一；
  通知取消/完成/失败与 UI 的双向绑定和可恢复状态仍是当前最高优先级缺口。
- OCR 资源层已有 `AiAsset.OcrPpOcrV5Mobile` 和 `OcrTextRepository` adapter 边界，
  但实际 OCR runtime 仍是 ML Kit，PaddleOCR PP-OCRv5 mobile 尚未接入。
- ASR 资源层和 `VoiceInputRepository` adapter 边界已存在，但 sherpa-onnx streaming
  Zipformer runtime、`AudioRecord` 和 partial/final result 流尚未接入。
- `app` 已有 ViewModel 单元测试、OCR workflow 单元测试、Compose UI 测试和
  Activity 重建 instrumented 测试；`:lib` 当前测试仍偏 smoke/基础覆盖，后续需继续增强。
- Manifest 已移除 `MainActivity` 的 `configChanges`，并新增配置变化重建测试；
  后续继续覆盖更多 UI 状态和进程死亡恢复。

## 代码审查未完成项

以下结论来自当前源码 review，后续实现必须优先清掉这些具体缺口。

### 下载、通知和后台任务

- `ModelDownloadService` 和 `AiAssetDownloadService` 已不再把 companion object
  `MutableStateFlow` 作为 UI 状态来源；当前已新增 DataStore 持久化状态 store。
  Service 非正常销毁时会把活跃下载持久化为 interrupted error；后续仍要补进程被直接杀死后
  App 下次启动时的 job metadata 审计。
- 两套下载 service/notifier 重复实现下载 job、foreground notification、取消动作、
  进度节流和错误展示，后续应收敛为统一 download runtime，再由 model/AI asset
  adapter 提供 job metadata。
- AI 资源下载通知已拆成 ASR/OCR 独立 notification id，取消 action 也携带 asset id；
  但 service 当前仍是单 job，尚未支持真正的多资源并发下载。
- 通知动作已覆盖取消、打开 App 和失败后重试；后续还缺跳转到对应资源/模型状态的显式 deep link
  和通知动作 instrumentation 测试。
- UI 取消、通知取消、模型切换、清理资源和 service 自身失败之间还没有完整双向绑定；
  `TranslatorViewModel` 只是收集 service 内存流并映射 UI 状态。
- 模型下载完成后由 service 发出 Completed，ViewModel 再加载模型并补发完成通知；
  这个链路仍依赖 App 进程存活。进程死亡或 Activity 不存在时不会恢复“下载完成后加载/提示”的语义。
- `onSelectModel()` 和 `onClearAllModels()` 只取消模型下载，尚未定义 AI 资源下载、
  OCR/ASR runtime 和相关通知在清理/切换时的行为。
- 还缺少下载恢复、通知动作、通知权限拒绝、前台服务被系统重启、
  recent task 划掉后的自动化或手动验收用例。

### OCR

- `AppContainer` 仍创建 `MlKitOcrTextRepository`，`OcrEngine` 仍使用
  ML Kit Chinese Text Recognition；PaddleOCR PP-OCRv5 mobile runtime 尚未接入。
- `AiAsset.OcrPpOcrV5Mobile` 已配置官方 Paddle Lite demo 资源来源，下载器支持
  tar.gz 解包并提取 `PP-OCRv5_mobile_det.nb`、`PP-OCRv5_mobile_rec.nb`、
  `ppocr_keys_ocrv5.txt`。后续仍要接 PaddleOCR runtime，而不是继续使用 ML Kit。
- Gradle 依赖仍包含 ML Kit OCR，尚无 PaddleOCR/Paddle Lite runtime 或 native libs。
- OCR adapter 边界已有，但还缺 PaddleOCR 初始化、模型文件校验、bitmap 预处理、
  det/rec pipeline、typed error 和真机 smoke 验证。

### ASR

- `SherpaOnnxVoiceInputRepository` 已不再是固定占位错误：会校验 Zipformer 模型文件、
  尝试加载 `libsherpa-onnx-jni.so`，并在缺模型/缺 native runtime/缺 AudioRecord streaming
  时返回明确错误。真实 streaming decode 和音频采集仍未接入。
- Manifest 已声明 `RECORD_AUDIO`，Route 层已接入录音权限请求；权限拒绝会进入
  `VoiceInputState.Error`。后续还要补 instrumented 权限测试。
- 已新增官方 sherpa-onnx Android runtime 安装脚本和 Gradle `jniLibs` 本地打包入口；
  运行 `scripts/setup-sherpa-onnx-android.sh` 后可用 `:app:verifySherpaOnnxRuntime` 验证 native libs。
  当前采用官方 release 产物生成流程；如后续需要源码级定制，再按 llama.cpp 类似方式引入 submodule。
- `VoiceInputRepository.start()` 目前只返回单个 `VoiceInputState`，不足以表达 streaming
  partial/final result、音量/监听状态、采集错误和 runtime 关闭事件；需要改为 Flow 或 callback
  驱动的 typed event。
- 现有 `AsrPartialReceived` / `AsrFinalReceived` 只是 UI event，尚未连接真实 `AudioRecord`
  采集和 sherpa 解码结果。

### UI 和状态架构

- `TranslatorViewModel` 仍同时负责翻译、模型下载、AI 资源下载、模型加载/卸载、
  ASR 开关、实时翻译、防抖、通知补发和资源 refresh，职责过宽。
- `TranslatorRoute` 仍承担 OCR permission/launcher、AI 资源下载触发和部分平台编排，
  后续应把平台 capability 收敛到更窄的 controller/use case。
- `TranslatorScreen` 已有拆分，但 asset 状态提示、输入工具栏、模型状态、下载进度等
  仍集中在一个较大的文件中，继续增加 ASR/OCR 后会影响教学可读性。
- 实时翻译已支持输入变化触发输出，但 ASR streaming 接入后还要验证 partial/final
  更新不会造成输出闪烁、取消竞争或过度推理。

### 构建、依赖和测试

- URL 已迁到资源文件，Kotlin 源码中未发现硬编码下载 URL；但 AI asset spec
  仍在 repository 内部硬编码文件名、大小和资源映射，后续可迁到可测试 provider。
- release 构建仍 `isMinifyEnabled = false`，没有 resource shrink、baseline profile、
  release smoke 和签名失败路径文档，不符合最终教学版最佳实践。
- 当前测试集中在 ViewModel、OCR workflow、主屏 Compose 和 Activity 重建；
  缺少真实 repository/downloader、notification action、PaddleOCR、sherpa-onnx、
  `AudioRecord` adapter、process death/recovery 的测试或 smoke harness。

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

- **当前最高优先级，先完成可用性再继续外围架构工作。**
- 抽出统一下载 runtime：
  - 模型下载和 AI 资源下载复用同一套 download job/progress/action 抽象。
  - 下载 job id、asset/model id、目标路径、总大小、已下载大小、错误原因、完成状态持久化。
  - App 启动时从持久化状态恢复 UI，不依赖 service 静态内存状态。
- 通知和 UI 双向绑定：
  - 通知取消必须取消真实下载任务，并把 UI 状态更新为取消/可重试。
  - UI 取消必须撤销 foreground notification。
  - 通知完成必须触发 repository refresh/load，并让 UI 进入 Ready/Loading/Error。
  - 通知失败必须暴露 typed error，UI 显示明确重试入口。
  - 失败通知已提供直接重试动作；后续需要补 action instrumentation 覆盖。
  - 切换模型或清理资源时必须取消相关通知和下载 job。
- 评估并落地 Google 推荐的用户发起数据传输方案：
  - Android 14+ 优先考虑 User-Initiated Data Transfer job。
  - 需要即时前台可见和兼容旧系统时保留 Foreground Service fallback。
- 下载状态模型已从 Service 内部类型迁到 domain 层，并已移除 service 静态状态流；
  当前通过 DataStore 持久化基础下载状态，service 异常销毁会记录 interrupted error；
  后续还需补 job metadata、速度、重试次数和进程被直接杀死后的启动恢复策略。
- 下载进度、速度、剩余大小、错误原因使用统一 domain model，再映射到 UI 和 notification。
- 通知 adapter 只负责平台渲染：
  - Android 16+ 使用 `Notification.ProgressStyle` 和实时更新。
  - 旧系统使用标准 determinate progress notification。
  - 小图标统一使用符合 Android 通知规范的单色 drawable。
  - AI 资源通知使用按资源稳定分配的 notification id，避免 ASR/OCR 状态互相覆盖。
- 明确用户划掉任务后的策略：
  - 如果只是移除 recent task，不应静默破坏已承诺的下载。
  - 如果系统终止进程，下载状态必须可恢复或以明确失败状态呈现。

### Phase 6：OCR、CameraX 和权限

- **当前第二优先级：完成真实 PaddleOCR runtime，而不是只保留 ML Kit adapter。**
- CameraX 绑定改为 lifecycle-aware、state-driven，镜头切换必须触发重新绑定。
- OCR 处理链路拆成 `DecodeImageUseCase`、`RecognizeTextUseCase`、`ApplyOcrTextUseCase`。
- OCR runtime 已有 `OcrTextRepository` adapter 边界，当前实现为 ML Kit；后续切换为
  PaddleOCR PP-OCRv5 时保持 Route/UI 不变。
- OCR 引擎从 ML Kit 切换为 PaddleOCR PP-OCRv5 mobile：
  - 使用 `PP-OCRv5_mobile_det` 和 `PP-OCRv5_mobile_rec`。
  - 使用 `ppocr_keys_ocrv5.txt` 字典。
  - 第一版只启用 det + rec，不启用 unwarp 和方向分类模型。
  - 模型作为首次使用资源下载，不打包进 APK。
  - 已配置官方 Paddle Lite demo tar.gz 资源，并在下载器中支持单文件解包。
- OCR 可用性验收：
  - 未下载 OCR 资源时点击 OCR 只触发资源下载，不进入不可用 camera/gallery 流。
  - OCR 资源下载完成后自动 refresh，用户再次点击 OCR 可进入相机/相册。
  - 相机拍照 OCR、相册 OCR 至少各有一次真机 smoke 验证。
  - PaddleOCR 初始化失败、模型缺失、识别失败要进入 typed error 和 UI 重试路径。
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
  - 已新增下载状态持久化记录的 JVM 映射测试，覆盖模型/AI 资源下载状态恢复；
    后续还需补真实 downloader 和 service notification action 测试。
  - 已新增 AI asset spec JVM 测试，覆盖 OCR PP-OCRv5 mobile 必需文件、URL resource
    和 tar.gz entry 配置。
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

- **当前第三优先级：完成真实 sherpa-onnx streaming ASR，而不是占位错误。**
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
  - 已新增 `scripts/setup-sherpa-onnx-android.sh` 安装官方 Android release runtime，
    产物位于 ignored `third_party/sherpa-onnx-android/jniLibs`。
- ASR 可用性验收：
  - 未下载 ASR 资源时点击麦克风只触发资源下载或明确下载入口。
  - ASR 资源 ready 后点击麦克风请求 `RECORD_AUDIO` 权限并进入 Listening；权限拒绝路径已进入
    UI error，后续补设备测试。
  - partial result 实时更新输入框。
  - final result 固化输入框，并在实时翻译开启时触发输出。
  - 通知/后台/切屏不应破坏正在进行的资源下载。
  - sherpa runtime 初始化失败、麦克风权限拒绝、音频采集失败必须进入 typed error。
  - 当前已覆盖缺模型、缺 native runtime、权限拒绝的错误状态；音频采集和 decode 错误待接入后补。
- 录音链路使用 `AudioRecord` adapter：
  - 采集 16 kHz mono PCM。
  - UI 通过麦克风 icon button 触发。
  - `RECORD_AUDIO` 权限由 Route 层处理；当前已完成权限声明、请求和拒绝状态映射。
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

1. 先完成 Phase 5：统一下载状态、通知复用、通知动作和 UI 双向绑定、后台恢复。
2. 再完成 Phase 6：把 OCR runtime 从 ML Kit 切换到 PaddleOCR PP-OCRv5 mobile，并真机验证。
3. 再完成 Phase 10：接入 sherpa-onnx streaming Zipformer、AudioRecord、partial/final result。
4. 然后补 Phase 8 缺失测试：下载恢复、通知权限、OCR/ASR smoke、错误路径。
5. 再继续 Phase 3 Hilt、Phase 7 native adapter 深化、Phase 1 构建入口整理。
6. Phase 9 文档只随实现同步更新，不再抢占实现优先级。
7. Phase 11 主界面体验重构最低优先级，只在核心能力和测试稳定后执行。

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
