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
   - 当前 OCR production adapter 已切到 Paddle Lite/PaddleOCR 边界。
   - 必须接入 PP-OCRv5 mobile det + rec + `ppocr_keys_ocrv5.txt`。
   - App 语言范围收敛为简体中文、繁体中文、英文、日文、阿拉伯文、俄文、葡萄牙文、
     德文、韩文；OCR 不能用单个 PP-OCRv5 mobile rec 模型暗示覆盖全部语言。
   - 当前 `PP-OCRv5_mobile_rec.nb` 优先覆盖中/英/日/繁中识别；阿拉伯文、俄文、
     葡萄牙文、德文、韩文需要后续追加对应 PaddleOCR multilingual recognition
     模型和字典，并在识别入口按源语言或自动检测结果选择 rec session。
   - OCR 模型资源必须按需下载、校验、加载，不打包进 APK。
   - 相机/相册 OCR smoke path 要在真机验证。

3. **ASR 真正接入 sherpa-onnx streaming Zipformer**
   - 当前 `VoiceInputRepository` 已接入 sherpa-onnx streaming Zipformer 和 `AudioRecord`。
   - 必须接入 sherpa-onnx streaming Zipformer runtime、JNI/native libs 和 `AudioRecord` 采集。
   - 当前 sherpa-onnx streaming Zipformer 资产只声明支持中文和英文；ASR 层中文不区分
     简体/繁体，统一输出中文文本，简繁转换/目标书写形式属于翻译或文本后处理层。
   - 日文、阿拉伯文、俄文、葡萄牙文、德文、韩文 ASR 不应在 UI/文档中声明已完成，
     除非后续新增对应多语或单语 ASR 模型资产、下载校验和 smoke 测试。
   - partial/final result 要实时写入输入框，并能配合实时翻译开关触发输出。
   - `RECORD_AUDIO` 权限和监听状态必须进入统一 UI state。

4. **端到端可用性验收**
   - 每个核心流程完成后都要 build、安装到真机、跑 connected tests 或等价 `adb am instrument`。
   - 最终收尾必须包含 debug APK 构建、真机部署、自动 UI/UX 验证和截图/布局树检查。

当前不实现但保留长期扩展计划的语言：

- 法语、西班牙语、土耳其语、泰语、意大利语、越南语、马来语、印尼语、菲律宾语、
  印地语、波兰语、捷克语、荷兰语、高棉语、缅甸语、波斯语、古吉拉特语、乌尔都语、
  泰卢固语、马拉地语、希伯来语、孟加拉语、泰米尔语、乌克兰语。
- 这些语言本轮不出现在语言选择 UI，不配置 OCR/ASR asset，不做下载、通知或 smoke 测试。
- 后续扩展必须按“语言进入 UI 前先完成模型资产、校验、真实 smoke 和文档”的顺序推进，
  避免产品层面宣称支持但 runtime 实际不可用。

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
  Paddle Lite OCR 已封装到 `OcrTextRepository` adapter；仍有 CameraX 预览和较大 Screen
  组件待继续拆分。
- domain 模型已移除 Android resource id；`Language`、`ModelOption` 和
  `TranslatorRepository` 状态都使用 app/domain 自己的纯 Kotlin 类型。
- 选中模型持久化已从 SharedPreferences 迁移到 Preferences DataStore，旧 sharedpref
  备份排除规则保留用于历史安装。
- 下载 Service 暴露的状态类型已迁到 domain 层，ViewModel 不再依赖 Service 内部
  `State` 类型；模型下载和 AI 资源下载的基础状态已改由 DataStore 持久化并通过 controller 暴露。
- 下载通知已有前台服务和进度样式，AI 资源下载通知已按 ASR/OCR 拆成稳定 notification id；
  但模型下载、AI 资源下载、UI 状态和通知动作仍未完全统一；
  通知取消 action 到持久化状态的基础真机测试已补齐，通知完成/失败与 UI 的双向绑定和
  可恢复状态仍是当前最高优先级缺口。
- OCR 资源层已有 `AiAsset.OcrPpOcrV5Mobile` 和 `OcrTextRepository` adapter 边界，
  生产入口已切到 Paddle Lite adapter，det + rec 多框链路已接入，并已在真机使用
  PP-OCRv5 mobile 资源和 PaddleOCR 标准图片通过非 skip smoke。
- OCR 中文准确率当前仍不能按“可用”验收：det 后处理仍是概率图连通域矩形框，
  不是 PaddleOCR 标准 DB postprocess 的 contour、unclip 和 rotated/perspective crop；
  中文截图小字、密集笔画和多行场景容易被紧框裁掉或混入邻行。已先补 recognition crop
  padding 降低切字风险，但后续必须实现 DB polygon/unclip/rotated crop，并补中文样本
  断言型 smoke，而不是只验证“输出非空”。
- ASR 资源层和 `VoiceInputRepository` adapter 边界已存在，sherpa-onnx streaming
  Zipformer runtime、`AudioRecord` 和 partial/final result 回写已接入，仍待真机端到端 smoke。
- `app` 已有 ViewModel 单元测试、OCR workflow 单元测试、Compose UI 测试和
  Activity 重建 instrumented 测试；`:lib` 当前测试仍偏 smoke/基础覆盖，后续需继续增强。
- Manifest 已移除 `MainActivity` 的 `configChanges`，并新增配置变化重建测试；
  后续继续覆盖更多 UI 状态和进程死亡恢复。

## 代码审查未完成项

以下结论来自当前源码 review，后续实现必须优先清掉这些具体缺口。

### 下载、通知和后台任务

- `ModelDownloadService` 和 `AiAssetDownloadService` 已不再把 companion object
  `MutableStateFlow` 作为 UI 状态来源；当前已新增 DataStore 持久化状态 store。
  Service 非正常销毁时会把活跃下载持久化为 interrupted error；App 初始化时也会审计
  DataStore 中残留的 downloading 记录并标记为 interrupted error；已新增真机 instrumentation
  验证模型和 AI 资源下载审计会把残留 downloading 状态转成 interrupted error。基础 job id、
  attempt/retry 计数和速度 metadata 已随模型和 ASR/OCR 下载状态持久化。`ModelDownloadService`
  和 `AiAssetDownloadService` 已显式处理 `onTaskRemoved`，把活跃下载标记为 interrupted error
  并撤销前台通知，避免 recent task 移除时静默丢失状态；已新增真机 instrumentation 通过
  `am stack remove` 覆盖模型下载和 OCR 资源下载的真实 task removed 路径。后续仍要补真实
  service 被系统重启恢复验收。
- 两套下载 service/notifier 重复实现下载 job、foreground notification、取消动作、
  进度节流和错误展示，后续应收敛为统一 download runtime，再由 model/AI asset
  adapter 提供 job metadata。Service 内部 repository 创建点已先收敛到默认依赖工厂，
  作为后续 Hilt/service 注入迁移的过渡 seam，并用于不触发真实大文件下载的 service 级测试。
  当前已抽出第一层 `DownloadForegroundRuntime`，复用启动前台通知、收集
  `DownloadProgress`、写入 Downloading/Completed/Error 状态、发布进度通知和处理异常终态；
  模型单任务和 AI asset 多任务的取消/并发策略仍保留在各自 service，下一步再继续收敛
  通知 renderer、job registry 和 UIDT scheduler。通知层已抽出 `DownloadNotificationSupport`
  复用通知权限判断、进度百分比/节流和 Android 16 ProgressStyle 构建；模型与 AI asset
  notifier 仍保留各自文案、action、content intent 和资源上下文。Job 管理已抽出
  `DownloadJobRegistry`，复用按目标保存、移除、取消和活跃状态查询；模型 service 仍保持
  单活跃模型下载策略，AI asset service 仍保持按资源并发下载策略。
- AI 资源下载通知已拆成 ASR/OCR 独立 notification id，取消 action 也携带 asset id；
  AI 资源下载状态已按 asset 独立持久化，ViewModel 也按 ASR/OCR 分别观察状态。
  `AiAssetDownloadService` 已按 asset 管理下载 job，支持 ASR/OCR 并发下载和分别取消。
  已新增真机 instrumentation 验证通知取消 action 只清理目标 asset 的持久化下载状态。
  后续还要补 retry action 和真实 service/recent task 恢复验收。
- 通知动作已覆盖取消、打开 App 和失败后重试；通知 content intent 已携带模型/AI
  资源下载目标上下文，模型下载通知会打开模型选择入口，AI 资源通知会高亮对应 ASR/OCR
  资源状态行。已新增模型下载取消 action 到 DataStore Idle 的真机 instrumentation 测试；
  通知目标 extra 到 `NotificationDestination` 的 JVM 回归测试已补齐，并已新增通知 intent
  打开 `MainActivity` 后触发模型选择/AI 资源高亮的真机 instrumentation 测试，覆盖首次打开和
  `onNewIntent` 复用路径；已抽出通知 intent 工厂并新增真机 instrumentation 覆盖
  retry/cancel/content intent 的 action、component 和 extras。后续还缺滚动定位和真机大资源
  网络 smoke。已新增可控 fake repository 的 service action
  真机 instrumentation，覆盖模型和 AI asset 从失败态收到 `ACTION_START` 后生成新的
  attempt/jobId 并由 service 写回错误态，也覆盖 `ACTION_START` 完成后持久化 Completed；
  下载器层已新增本地 HTTP retry 验收，覆盖 5xx 自动重试和连接中断后 Range 断点续传。
- UI 取消、通知取消、模型切换、清理资源和 service 自身失败之间还没有完整双向绑定；
  `TranslatorViewModel` 已收集持久化下载状态并按 AI asset 分别映射 UI 状态；主界面已新增
  模型下载取消按钮，以及 ASR/OCR 资源下载中的独立取消按钮，UI 事件会调用对应 controller 并
  只重置目标状态；service/通知取消后写入的 Idle 状态也会回写 UI，只清理正在下载的目标状态，
  避免初始 Idle 覆盖 Ready/普通空状态。已有 ViewModel 单测和真机 Compose 测试覆盖 UI 取消和
  通知/service Idle 回写。模型下载完成后的自动加载语义仍依赖 App 进程存活。
- 模型下载完成后由 service 持久化 Completed 并显示明确的下载完成通知；ViewModel 在 App
  存活或下次启动观察到 Completed 后加载模型并补发完成通知；已新增 ViewModel 单元测试覆盖
  Completed 加载、重复 Completed 去重、非当前 selected model 的 Completed 隔离和 Error
  状态显示。后续仍要补进程死亡恢复验收，
  确认“下载完成但尚未加载”状态对用户可解释。
- `onSelectModel()` 会取消当前模型下载、停止 ASR runtime，但保留独立的 ASR/OCR
  资源下载；`onClearAllModels()` 会取消模型下载、AI 资源下载和 ASR runtime。
  `onClearAllModels()` 现在也会立即把下载中的 ASR/OCR UI 状态收敛回 NotDownloaded，
  但不会误清已经 Ready 的资源。后续仍要补这些 UI 入口到 service/notification 的真机验收。
- 还缺少前台服务被系统重启的自动化或手动验收用例。
  当前已覆盖 App 启动审计残留 downloading 的基础恢复机制，service 也已实现
  `onTaskRemoved` 中断持久化；真机 task removed 测试已覆盖模型下载和 OCR 资源下载通过
  系统 task 移除后进入 interrupted error。后续还没覆盖真实 service 被系统重启时的端到端用户路径。
  通知权限拒绝现在会阻止下载启动，并把模型或对应 ASR/OCR 资源状态置为可见错误；
  已新增 ViewModel 单测和 Compose 真机测试覆盖该错误展示，后续仍可补真实系统权限弹窗流程。

### OCR

- `AppContainer` 已从 `MlKitOcrTextRepository` 切到 `PaddleLiteOcrTextRepository`；
  生产入口不再继续创建 ML Kit OCR adapter。ML Kit 依赖和旧 adapter 代码已删除。
- `AiAsset.OcrPpOcrV5Mobile` 已配置官方 Paddle Lite demo 资源来源，下载器支持
  tar.gz 解包并提取 `PP-OCRv5_mobile_det.nb`、`PP-OCRv5_mobile_rec.nb`、
  `ppocr_keys_ocrv5.txt`。
- 已新增官方 Paddle Lite Android runtime 安装脚本和 Gradle 本地打包入口；
  运行 `scripts/setup-paddle-lite-android.sh` 后可用 `:app:verifyPaddleLiteRuntime`
  验证 `PaddlePredictor.jar`、`libpaddle_lite_jni.so` 和 `libc++_shared.so`。
  Kotlin 编译和打包前会先执行 runtime 校验，避免缺少本地 Paddle jar 时出现不可读的
  unresolved import 错误。
- Paddle Lite runtime 已升级到官方 `v2.14-rc` `with_extra` Android Java runtime，
  匹配 PP-OCRv5 mobile 模型的 `opt:v2.14-rc` 和 `hard_swish` 等 extra ops；脚本会从
  Android NDK 拷贝对应 ABI 的 `libc++_shared.so`。
- PaddleOCR adapter 已完成 runtime 初始化、模型文件校验、label 文件校验、det/rec
  predictor 创建和 EXIF 旋转沿用。当前已接入 rec predictor 的整图单行识别路径：
  bitmap resize/normalize、Tensor 输入、`predictor.run()` 和 CTC decode 已有单测覆盖。
- det resize/stride 对齐、BGR NCHW 归一化、概率图连通域后处理、文本框排序和
  axis-aligned crop + rec 多框循环已接入，并有纯 JVM 单测覆盖。recognition crop 已增加
  源图边界内 padding，降低中文笔画被紧框裁切的概率。后续仍缺 DB polygon
  unclip/rotated crop、更精确文本框排序、typed error 收敛。
- OCR 多语支持策略必须从“单资产”升级为“共享 det + 多 rec/字典 session”：
  - 中/英/日/繁中先使用当前 `PP-OCRv5_mobile_rec.nb` 和 `ppocr_keys_ocrv5.txt`。
  - 阿拉伯文、俄文、葡萄牙文、德文、韩文分别追加官方 multilingual rec 模型和字典。
  - UI 选择源语言时传入 OCR repository；自动检测时先用当前通用 session，后续再补脚本/检测器。
  - 每个新增 rec 模型都必须有 asset spec、下载通知、文件校验和最小真机 smoke。
- 已新增标准图片 smoke 入口：`scripts/download-ocr-smoke-images.sh` 下载 PaddleOCR/PaddleX
  demo 图片，`PaddleLiteOcrSmokeTest` 可在设备已有 OCR 模型资源时把标准图片喂给
  Paddle Lite PP-OCRv5 mobile det + rec runtime 做真机 smoke；模型未下载时测试显式 skip。
  已在 Pixel 7 Pro 真机准备 `PP-OCRv5_mobile_det.nb`、`PP-OCRv5_mobile_rec.nb` 和
  `ppocr_keys_ocrv5.txt` 后跑通该 smoke，测试报告为 `tests=1 failures=0 skipped=0`。
  OCR 和 ASR 一样保留“小样本 smoke + 大规模标准数据离线回归”的结构。大规模标准回归数据源
  记录在 `docs/ocr-smoke-data.md`，包括 ICDAR 2015、COCO-Text 和 PaddleOCR dataset index。

### ASR

- `SherpaOnnxVoiceInputRepository` 已接入真实 AudioRecord + sherpa-onnx streaming
  Zipformer session：启动时校验模型文件、加载 `libsherpa-onnx-jni.so`，创建
  `OnlineRecognizer`，后台采集 16 kHz PCM16，向 recognizer streaming 输入 samples，
  并把 partial/final 文本回写 `TranslatorViewModel` 输入框。
- Manifest 已声明 `RECORD_AUDIO`，Route 层已接入录音权限请求；权限拒绝会进入
  `VoiceInputState.Error`。后续还要补 instrumented 权限测试。
- 已新增官方 sherpa-onnx Android runtime 安装脚本和 Gradle `jniLibs` 本地打包入口；
  运行 `scripts/setup-sherpa-onnx-android.sh` 后可用 `:app:verifySherpaOnnxRuntime` 验证 native libs。
  当前采用官方 release 产物生成流程；`k2-fsa/sherpa-onnx` 已作为 submodule 加入，用作
  Kotlin/JNI API 上游参照。
- `VoiceInputRepository.start()` 已改为 callback 驱动，能表达 streaming partial/final result；
  后续仍要把采集错误、runtime 关闭事件和音量/监听状态扩展为 typed event 或 Flow。
- 现有 `AsrPartialReceived` / `AsrFinalReceived` 仍保留为 UI event，但真实 `AudioRecord`
  路径已直接通过 repository callback 连接 `TranslatorViewModel`。
- 已新增标准 WAV 文件 smoke 入口：`scripts/download-asr-smoke-audio.sh` 下载当前
  sherpa-onnx bilingual Zipformer 模型仓库自带 `test_wavs`，`SherpaOnnxAsrSmokeTest`
  可在设备已有 ASR 模型资源时把 WAV 文件喂给 streaming recognizer 做真机解码 smoke。
  大规模标准回归数据源记录在 `docs/asr-smoke-data.md`，包括 LibriSpeech、AISHELL-1
  和 Common Voice。

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
  当前 production adapter 已切到 Paddle Lite/PaddleOCR，仍缺 det 多框 pipeline。
- 为关键 Composable 增加 preview parameter provider 和稳定假数据。
- 已删除不必要的 `configChanges`，通过 `MainActivityConfigurationTest` 验证 Activity
  重建后输入状态保留；后续再引入 SavedStateHandle 覆盖进程死亡恢复。

### Phase 5：后台下载和通知标准化

- **当前最高优先级，先完成可用性再继续外围架构工作。**
- 抽出统一下载 runtime：
  - 模型下载和 AI 资源下载复用同一套 download job/progress/action 抽象。
  - 下载 job id、asset/model id、目标路径、总大小、已下载大小、错误原因、完成状态持久化。
  - App 启动时从持久化状态恢复 UI，不依赖 service 静态内存状态。
  - 已抽出 `DownloadForegroundRuntime` 复用前台执行和 progress 终态处理；后续继续把
    job registry、通知渲染和 UIDT/FGS 调度层纳入统一 runtime。
  - 已抽出 `DownloadJobRegistry` 复用 service 内部 job 保存、取消和 active 查询。
- 通知和 UI 双向绑定：
  - 通知取消必须取消真实下载任务，并把 UI 状态更新为取消/可重试。
  - UI 取消必须撤销 foreground notification。
  - 通知完成必须触发 repository refresh/load，并让 UI 进入 Ready/Loading/Error。
  - 通知失败必须暴露 typed error，UI 显示明确重试入口。
  - 失败通知已提供直接重试动作；通知 content intent 已携带模型/AI 资源目标上下文。
    已补取消 action 到持久化状态的真机 instrumentation；已补 retry/open intent action 和
    extras 覆盖。后续需要补真机大资源网络 smoke。
  - service/通知取消写入 Idle 后，ViewModel 已把正在下载的模型或目标 ASR/OCR 资源回写为
    NotDownloaded，不会影响其他资源状态，也不会用初始 Idle 覆盖 Ready 状态。
  - 切换模型会取消模型下载并停止 ASR runtime；清理模型会取消模型下载、AI 资源下载
    和 ASR runtime，并立即重置正在下载的 AI 资源 UI 状态。后续补真机通知动作验证。
- 评估并落地 Google 推荐的用户发起数据传输方案：
  - Android 14+ 优先考虑 User-Initiated Data Transfer job。
  - 需要即时前台可见和兼容旧系统时保留 Foreground Service fallback。
  - 已按 Android 官方文档复核：用户点击下载模型/ASR/OCR 资源、需要长时间进度通知、
    中断会损害体验，符合 UIDT 适用条件；UIDT 需要 API 34+ `JobService`、
    `RUN_USER_INITIATED_JOBS`、`setUserInitiated(true)`、`setNotification(...)` 和
    `jobFinished(...)`，且当前没有 Jetpack 兼容库。迁移顺序应先抽统一 download runtime，
    再添加 API 34+ UIDT scheduler，Android 13 及以下继续使用当前 dataSync FGS fallback。
  - 已新增 `DownloadStartPolicy` 和 `DownloadStartScheduler`：API 34+ 选择 UIDT job，
    Android 13 fallback 到当前 dataSync FGS，并已补 JVM 测试覆盖选择规则；manifest 已声明
    `RUN_USER_INITIATED_JOBS` 和两个 `JobService`。当前 UIDT JobService 已直接复用
    `DownloadForegroundRuntime` 执行真实下载、通过 `setNotification(...)` 发布 UIDT 通知，
    不再委托 FGS 作为主执行机制；Android 13 及以下仍走现有 dataSync FGS fallback。
    已新增真机 manifest 测试验证 UIDT 权限和 `BIND_JOB_SERVICE` 声明。已新增真机
    JobScheduler schedule/run/timeout smoke，使用 fake repository 覆盖模型和 OCR asset 的
    UIDT job 能通过 `cmd jobscheduler run -f` 执行并持久化 Completed 状态，也能通过
    `cmd jobscheduler timeout` 触发 interrupted error 持久化，不触发真实大文件下载。
- 下载状态模型已从 Service 内部类型迁到 domain 层，并已移除 service 静态状态流；
  当前通过 DataStore 持久化基础下载状态，service 异常销毁会记录 interrupted error；
  App 初始化会把上次进程直接结束后残留的 downloading 记录审计为 interrupted error；
  当前已持久化 job id、attempt 和 bytes-per-second 元数据，progress 更新不会递增 attempt
  或改变 job id，失败后重新开始会递增 attempt 并生成新 job id；后续还需补更完整的启动恢复策略。
- 下载进度、速度、剩余大小、错误原因使用统一 domain model，再映射到 UI 和 notification。
- 通知 adapter 只负责平台渲染：
  - Android 16+ 使用 `Notification.ProgressStyle` 和实时更新。
  - 旧系统使用标准 determinate progress notification。
  - 小图标统一使用符合 Android 通知规范的单色 drawable。
  - AI 资源通知使用按资源稳定分配的 notification id，避免 ASR/OCR 状态互相覆盖。
  - 已抽出共享通知 support，统一 `POST_NOTIFICATIONS` gate、进度节流和
    `Notification.ProgressStyle` segment/point 构建；后续继续收敛通知 action renderer。
- 明确用户划掉任务后的策略：
  - 如果移除 recent task，当前策略是取消活跃下载、持久化 interrupted error、撤销前台通知，
    下次打开 App 时通过持久化状态显示可重试错误。
  - 如果系统终止进程，下载状态必须可恢复或以明确失败状态呈现。

### Phase 6：OCR、CameraX 和权限

- **当前第二优先级：完成真实 PaddleOCR runtime，而不是只保留 ML Kit adapter。**
- CameraX 绑定改为 lifecycle-aware、state-driven，镜头切换必须触发重新绑定。
- OCR 处理链路拆成 `DecodeImageUseCase`、`RecognizeTextUseCase`、`ApplyOcrTextUseCase`。
- OCR runtime 已有 `OcrTextRepository` adapter 边界，当前 production 实现为
  Paddle Lite/PaddleOCR，后续补齐 det 多框 pipeline 时保持 Route/UI 不变。
- OCR 引擎从 ML Kit 切换为 PaddleOCR PP-OCRv5 mobile：
  - 使用 `PP-OCRv5_mobile_det` 和 `PP-OCRv5_mobile_rec`。
  - 使用 `ppocr_keys_ocrv5.txt` 字典。
  - 第一版只启用 det + rec，不启用 unwarp 和方向分类模型。
  - 模型作为首次使用资源下载，不打包进 APK。
  - 已配置官方 Paddle Lite demo tar.gz 资源，并在下载器中支持单文件解包。
  - 已新增 Paddle Lite Android runtime 本地安装/验证入口，产物位于 ignored
    `third_party/paddle-lite-android`。
- OCR 可用性验收：
  - 未下载 OCR 资源时点击 OCR 只触发资源下载，不进入不可用 camera/gallery 流。
  - OCR 资源下载完成后自动 refresh，用户再次点击 OCR 可进入相机/相册。
  - 相机拍照 OCR、相册 OCR 至少各有一次真机 smoke 验证。
  - PaddleOCR 初始化失败、模型缺失、识别失败要进入 typed error 和 UI 重试路径。
  - 已新增单行 recognition smoke harness：设备上 OCR 模型存在时，instrumented test 会下载
    PaddleOCR 标准 demo 图片并验证输出非空；模型未下载时该测试 skip。完整 det + rec
    端到端 smoke 已在真机手动安装 app/test APK、准备 OCR 模型资源后通过
    `adb shell am instrument` 验证。
- 权限状态集中建模，UI 只显示当前状态和触发请求事件。
- 相册和相机错误使用 typed error，避免在 UI 层拼接平台异常。

### Phase 7：native inference 边界

- 在 app data 层新增 `NativeTranslationEngineAdapter`，负责把 `:lib` 的
  state/error 映射成 app domain 类型。
- `TranslatorViewModel` 不直接持有 native engine，不直接调用 native unload/destroy。
- `InferenceEngineImpl` 的单例、dispatcher、native library load 和
  destroy 行为补测试或至少补 smoke test。已修复模型加载失败后 Activity 销毁触发
  native `unload()` 空 context 崩溃的问题：Kotlin `destroy()` 不再对 Error 状态调用
  native unload，C++ `unload()` 也对空 context/model/sampler/batch 做幂等保护。
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
    已新增 `ModelDownloader` 本地 HTTP JVM 测试，覆盖下载成功、HTTP error、Range 续传、
    5xx 自动重试和连接中断后的 Range 断点续传；
    已新增 `AiAssetFileDownloader` 本地 HTTP JVM 测试，覆盖 direct 文件下载、tar.gz entry
    解包、Range 续传、5xx 自动重试和连接中断后的 Range 断点续传；
    已新增 `ModelDownloader` 和 `AiAssetFileDownloader` 取消测试，覆盖下载中取消不会
    finalize 部分文件；
    已新增 service notification cancel action 的真机 instrumentation 测试；
    已新增下载恢复 audit 的真机 instrumentation 测试；
    已新增通知 retry/cancel/open intent 工厂的真机 instrumentation 测试；
    已新增 DataStore job id/attempt/speed 元数据真机测试，覆盖模型和 ASR/OCR 资源下载的
    retry 计数、稳定 job id 和速度计算；
    已新增 service `ACTION_START` 真机测试，使用 fake repository 覆盖完成态持久化和
    失败后重试会进入新 attempt/jobId 并由 service 写回终态；
    已新增真机 task removed 测试，覆盖模型下载和 OCR 资源下载的 foreground service 在
    `am stack remove` 后持久化 interrupted error；
    后续还需补真机大资源网络 smoke 和真实 service 重启恢复测试。
  - 已新增 AI asset spec JVM 测试，覆盖 OCR PP-OCRv5 mobile 必需文件、URL resource
    和 tar.gz entry 配置。
- Compose UI 测试：
  - 已新增 `TranslatorScreenTest` 覆盖主屏空状态、翻译结果状态和模型选择弹窗。
  - 已新增通知权限拒绝后的模型/AI 资源错误展示真机 Compose 测试。
  - 已新增下载进度展示、模型/AI 资源进度条和语言交换按钮回调的真机 Compose 测试。
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
  - 当前生产实现 `SherpaOnnxVoiceInputRepository` 已创建 sherpa-onnx streaming
    recognizer 并通过 `AudioRecord` 采集音频。
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
  - 当前已覆盖缺模型、缺 native runtime、权限拒绝和 session 启动失败的错误状态；
    音频采集过程中的运行时错误仍需扩展为 typed event 或 Flow。
  - 已新增文件流 ASR smoke harness：设备上 ASR 模型存在时，instrumented test 会下载
    sherpa-onnx 标准 test WAV 并验证输出非空；模型未下载时该测试 skip。
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
