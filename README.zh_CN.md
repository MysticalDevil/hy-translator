# Hy 翻译器

基于 [llama.cpp](https://github.com/ggerganov/llama.cpp) 和 [腾讯 Hy-MT2-1.8B](https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF) 模型的离线翻译 Android 应用。支持 35 种语言互译，所有计算均在本地完成，无需网络即可翻译。

## 功能

- **35 种语言** — 双向翻译，支持自动语言检测
- **大模型驱动** — 使用 Hy-MT2-1.8B 大语言模型，通过 llama.cpp 推理
- **完全离线** — 翻译全程本地运算，无云端依赖
- **OCR 输入** — 通过 ML Kit 拍照或从相册提取文字
- **多档模型** — 3 种量化级别可选（Q4_K_M、Q6_K、Q8_0）
- **Material You** — 动态取色主题，随壁纸自动适配

## 快速开始

### 环境要求

- Android 13 (API 33) 或更高版本
- 约 2 GB 可用存储用于模型下载
- 相机权限（可选，用于 OCR）

### 安装与使用

1. 从 [Releases](https://github.com/MysticalDevil/hy-translator/releases) 下载最新 APK
2. 安装并启动
3. 选择模型版本（推荐 Q4_K_M，1.1 GB）
4. 下载模型
5. 开始翻译

### 使用指南

1. 在顶部语言栏选择源语言和目标语言
2. 输入或粘贴文本，或点击相机按钮进行 OCR 识别
3. 点击**翻译**
4. 使用复制按钮复制翻译结果

## 架构

```
app/                          # 主应用模块
├── MainActivity.kt           # 单 Activity，薄胶合层（~90 行）
├── domain/
│   ├── model/                # 纯 Kotlin 领域模型
│   │   ├── Language.kt       # 语言数据（代码、名称、英文名）
│   │   ├── ModelOption.kt    # 模型变体定义
│   │   ├── ModelStatus.kt    # 模型生命周期状态（sealed class）
│   │   └── DownloadProgress.kt  # 下载进度事件
│   └── repository/           # 仓库接口（抽象层）
│       ├── TranslatorRepository.kt
│       ├── ModelRepository.kt
│       └── LanguageRepository.kt
├── data/
│   ├── Languages.kt          # 35 种语言定义
│   ├── Models.kt             # 模型目录 + 内存感知推荐
│   └── repository/           # 仓库实现
│       ├── TranslatorRepositoryImpl.kt
│       ├── ModelRepositoryImpl.kt
│       └── LanguageRepositoryImpl.kt
├── service/
│   ├── TranslatorEngine.kt   # 翻译封装（Prompt 构建）
│   ├── ModelDownloader.kt    # GGUF 模型下载（OkHttp + 断点续传）
│   └── OcrEngine.kt          # ML Kit OCR 封装
├── ui/
│   ├── TranslatorScreen.kt   # 主界面（语言栏、输入区、输出卡）
│   ├── TranslatorViewModel.kt  # 状态管理 + 业务编排
│   ├── ModelPickerDialog.kt  # 模型选择弹窗
│   ├── CameraCapture.kt      # CameraX 全屏拍照
│   ├── OcrBottomSheet.kt     # OCR 流程底部弹窗
│   └── OcrFlow.kt            # OCR 状态机
└── theme/
    ├── Theme.kt              # Material 3 主题（Monet 动态取色）
    └── Type.kt               # 字体排版定义

lib/                          # Native 推理引擎库
├── src/main/cpp/
│   ├── CMakeLists.txt        # CMake 构建 + 16KB 页对齐
│   └── ai_chat.cpp           # llama.cpp JNI 桥接
└── src/main/java/com/arm/aichat/
    └── InferenceEngine.kt    # llama.cpp 推理 Kotlin 封装

llama.cpp/                    # Git 子模块
```

### 架构模式

```
presentation (ui/) ──→ domain/ ←── data/
                            ↑
                        core（无依赖）
```

- **domain** — Repository 接口，纯 Kotlin 模型，零 Android 依赖
- **data** — Repository 实现，封装 TranslatorEngine/ModelDownloader
- **presentation** — ViewModel + Compose UI，通过 StateFlow 读取状态

### 核心依赖

| 库 | 用途 |
|----|------|
| [llama.cpp](https://github.com/ggerganov/llama.cpp) | LLM 推理引擎 |
| [Compose](https://developer.android.com/compose) | UI 框架 |
| [CameraX](https://developer.android.com/camera) | 相机拍照 |
| [ML Kit](https://developers.google.com/ml-kit) | OCR 文字识别 |
| [OkHttp](https://square.github.io/okhttp/) | 模型下载 |

### 数据流

```
用户输入 → TranslatorScreen → ViewModel (StateFlow)
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
           TranslatorRepository  ModelRepository  LanguageRepository
                    │               │               │
                    ▼               ▼               ▼
            TranslatorEngine.kt  ModelDownloader  Languages.kt
                    │               │
                    ▼               ▼
            llama.cpp (Native)   HuggingFace
```

## 开发

### 构建

```bash
# 克隆仓库及子模块
git clone --recurse-submodules https://github.com/MysticalDevil/hy-translator.git
cd hy-translator

# 构建 Debug APK
./gradlew assembleDebug
```

### 代码质量

```bash
# 运行 Lint 检查
./gradlew :app:lint

# 运行测试
./gradlew :app:test
```

## 许可证

MIT License — 详见 [LICENSE](LICENSE)。

本项目使用了 [llama.cpp](https://github.com/ggerganov/llama.cpp)（MIT）和 [Hy-MT2-1.8B](https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF)（Apache 2.0）。
