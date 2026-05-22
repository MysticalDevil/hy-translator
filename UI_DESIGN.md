# Hy Translator - UI 设计文档

## 设计目标

打造一个**简洁、现代、高可读性**的本地翻译应用 UI，视觉风格参考 Google 翻译，但针对纯文本翻译场景做减法优化。

## 设计原则

1. **内容优先** - 翻译文本是核心，UI 元素不抢夺注意力
2. **层次清晰** - 输入/输出/控制三层结构分明
3. **触觉友好** - 所有交互元素满足 48dp 最小触摸目标
4. **无障碍** - 全量 `contentDescription` + 屏幕阅读器优化

---

## 一、色彩方案

### 主色
| Token | 值 | 用途 |
|-------|------|------|
| Primary | `#1A73E8` | 翻译按钮、强调文字、进度指示器 |
| On Primary | `#FFFFFF` | 主色上的文字 |
| Primary Container | `#D3E3FD` | 输出卡片背景（低透明度） |

### 中性色
| Token | 值 | 用途 |
|-------|------|------|
| Surface | `#FFFFFF` | 输入卡片、语言 Chip 背景 |
| Surface Container | `#F8F9FA` | 页面底层背景 |
| Surface Container High | `#F0F1F2` | 状态横幅背景 |
| Outline | `#DADCE0` | 卡片边框、分割线 |
| On Surface | `#202124` | 主要文字 |
| On Surface Variant | `#5F6368` | 次要文字、占位符 |

### 语义色
| Token | 值 | 用途 |
|-------|------|------|
| Error | `#EA4335` | 错误状态、取消翻译 |
| Success | `#34A853` | 下载完成、复制成功 |

> **注意**：原实现使用了 Material 3 动态取色（`colorScheme.primary` 来自壁纸），导致色调偏橙/琥珀色。建议**固定使用 Google Blue `#1A73E8`** 作为主色，避免动态取色的不确定性。

---

## 二、布局结构

```
┌──────────────────────────────┐
│  StatusBar (沉浸式)          │
├──────────────────────────────┤
│                              │
│  ┌────────────────────────┐  │
│  │ [自动检测 ▼]  ⟷  [英语 ▼] │  │  ← LanguageBar（语言栏）
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │                        │  │
│  │  请输入要翻译的文本…    │  │  ← InputArea（输入区）
│  │                        │  │
│  │              [X]      │  │     清除按钮（有内容时显示）
│  │                        │  │
│  │  12            [翻译]  │  │     字符计数 + 翻译按钮
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │ 英语              [📋] │  │  ← OutputCard（输出卡片）
│  │                        │  │
│  │ This is a test.       │  │
│  └────────────────────────┘  │
│                              │
│  [模型下载中 ████████  85%]  │  ← StatusBanner（状态横幅）
│                              │
└──────────────────────────────┘
```

### 布局说明

- **LanguageBar 位于顶部**（与 Google 翻译的底部布局不同，更符合"先选语言再输入"的心理模型）
- **InputArea 占据主要视觉空间**，无输入时最小高度 200dp
- **OutputCard 紧贴输入区下方**，出现时带展开动画
- **StatusBanner 浮动在内容下方**，不阻断交互

---

## 三、组件规范

### 3.1 LanguageBar（语言栏）

```
┌─────────────────────────────────────┐
│                                     │
│  ┌────────────┐    ┌────────────┐   │
│  │ 自动检测 ▼  │ ⟷ │   英语 ▼   │   │
│  └────────────┘    └────────────┘   │
│                                     │
└─────────────────────────────────────┘
```

**样式**：
- 两个 `Surface` 组件作为 Chip，白色背景 (`Surface`)
- 圆角：` shapes.medium` (16dp)
- 边框：无（使用阴影区分层次）
- 阴影：`shadowElevation = 1dp`（仅启用时）
- 内边距：`horizontal = 12.dp, vertical = 10.dp`
- 文字：`bodyMedium`, `FontWeight.Medium`, 颜色 `On Surface`

**Swap 按钮**：
- 类型：`IconButton`
- 尺寸：40dp × 40dp
- 图标：`Icons.Filled.SwapHoriz`
- 颜色：`On Surface Variant`（低调，不抢视觉焦点）
- 无障碍：`contentDescription = "交换源语言和目标语言"`

**交互**：
- 点击 Chip 弹出 `LanguagePickerDialog`
- 源语言为 "自动检测" 时不可交换
- 交换时带 200ms 旋转动画

### 3.2 InputArea（输入区）

```
┌─────────────────────────────────────┐
│                                     │
│  请输入要翻译的文本…                  │  ← 占位符，22sp，On Surface Variant
│                                     │
│  这是一段很长的文字                   │  ← 输入文字，22sp，On Surface
│  可以换行输入                         │     行高 32sp
│                                     │
│                          [X]        │  ← 清除按钮（48dp 触摸目标）
│                                     │
│  12                    [翻译]       │  ← 字符计数 + 翻译按钮
│                                     │
└─────────────────────────────────────┘
```

**容器**：
- 类型：`Surface`
- 背景：`Surface` (#FFFFFF)
- 圆角：`shapes.large` (20dp)
- 边框：`outlineVariant` 色，0.5dp（可选，建议无边界更干净）
- 阴影：`shadowElevation = 2dp`
- 外边距：`horizontal = 16.dp, vertical = 8.dp`

**文本框**：
- 类型：`OutlinedTextField`（去掉边框）或 `BasicTextField`
- 字体：`22sp`，行高 `32sp`
- 最小行数：无内容时 4 行，有输出时 2 行
- 最大行数：8 行（超出可滚动）
- 占位符："请输入要翻译的文本…"，22sp，`On Surface Variant`
- 键盘：`ImeAction.Done`（触发翻译）

**清除按钮**（有内容时显示）：
- 位置：文本框右上角
- 图标：`Icons.Filled.Close`
- 尺寸：48dp 触摸目标（图标 20dp）
- 颜色：`On Surface Variant`
- 点击：清空输入文本

**底部操作栏**：
- 字符计数：`labelSmall`，`On Surface Variant`，左对齐
- 翻译按钮：
  - 无内容时：隐藏或禁用
  - 有内容时：`TextButton`，文字 "翻译"，`Primary` 色
  - 翻译中：变为 "取消"，`Error` 色，左侧带 14dp 进度指示器

### 3.3 OutputCard（输出卡片）

```
┌─────────────────────────────────────┐
│ 英语                          [📋]  │  ← 目标语言标签 + 复制按钮
│                                     │
│ This is a very long piece of text   │  ← 翻译结果，22sp，On Surface
│ that spans multiple lines in the    │     行高 32sp
│ output area.                        │
│                                     │
└─────────────────────────────────────┘
```

**容器**：
- 类型：`Surface`
- 背景：`Primary Container` 20% 透明度 (`#D3E3FD` @ 20%)
- 圆角：`shapes.large` (20dp)
- 顶部装饰：2dp 高的 `Primary` 色横条（作为视觉锚点）
- 阴影：`shadowElevation = 1dp`
- 外边距：`horizontal = 16.dp, top = 8.dp`
- 内边距：`16.dp`

**头部**：
- 目标语言标签：`labelLarge`, `FontWeight.SemiBold`, `Primary` 色
- 复制按钮：
  - 类型：`IconButton`
  - 图标：`Icons.Filled.ContentCopy`
  - 尺寸：48dp 触摸目标（图标 20dp）
  - 颜色：`Primary`
  - 无障碍：`contentDescription = "复制翻译结果"`
  - 点击反馈：Toast "已复制"

**翻译结果**：
- 字体：`22sp`，行高 `32sp`
- 颜色：`On Surface`
- 翻译中状态：显示 `CircularProgressIndicator`（16dp）代替结果

**展开动画**：
- 进入：`fadeIn(200ms) + expandVertically(200ms)`
- 退出：`fadeOut(150ms) + shrinkVertically(150ms)`

### 3.4 StatusBanner（状态横幅）

**容器**：
- 类型：`Card`
- 背景：`Surface Container High` (#F0F1F2)
- 圆角：`shapes.large` (20dp)
- 外边距：`horizontal = 16.dp, vertical = 4.dp`
- 无边框

**状态样式**：

| 状态 | 内容 | 操作 |
|------|------|------|
| 未下载 | 标题"需要下载模型" + 描述"首次使用需下载翻译模型(~1.1GB)" | `FilledTonalButton` "下载" |
| 下载中 | "正在下载... 856MB / 1130MB" + `LinearProgressIndicator` | 无 |
| 加载中 | "正在加载模型..." + `LinearProgressIndicator` | 无 |
| 错误 | 错误消息，红色文字 | 重试按钮（如果有） |
| 就绪 | 横幅隐藏 | — |

**展开动画**：
- 进入：`fadeIn + expandVertically`
- 退出：`fadeOut + shrinkVertically`

### 3.5 LanguagePickerDialog（语言选择器）

**容器**：
- 类型：`AlertDialog`
- 宽度：屏幕宽度的 92%
- 最大高度：480dp

**内容**：
- 语言列表垂直排列
- 每项高度：48dp
- 选中项：背景 `Primary Container`，文字 `FontWeight.Bold`
- 未选中：背景 `Surface`
- 分割线：无（使用间距区分）

**滚动**：`verticalScroll(rememberScrollState())`

---

## 四、交互设计

### 4.1 翻译流程

```
用户输入文本
    │
    ▼
┌─────────────┐
│ 键盘 Done 键  │ ──→ 触发翻译
│ 或点击[翻译]   │
└─────────────┘
    │
    ▼
显示 OutputCard（展开动画）
    │
    ▼
CircularProgressIndicator 旋转
    │
    ▼
翻译完成，结果淡入显示
```

### 4.2 语言交换

```
点击 Swap 按钮
    │
    ▼
源语言 ←→ 目标语言交换
    │
    ▼
如果源语言为"自动检测"：禁用 Swap（灰度显示）
```

### 4.3 复制反馈

```
点击复制按钮
    │
    ▼
剪贴板写入文本
    │
    ▼
显示 Toast："已复制"（1.5秒）
```

---

## 五、与 Google 翻译的差异

| 维度 | Google 翻译 | Hy Translator |
|------|-----------|---------------|
| 语言栏位置 | 底部 | **顶部**（更符合先选语言再输入的习惯） |
| 输入区样式 | 无边界大文本框 | **卡片式**（更好界定操作区域） |
| 翻译触发 | 自动（实时） | **手动**（本地模型推理慢，避免频繁触发） |
| 字符计数 | 无 | **有**（本地模型上下文有限，需提示用户） |
| 底部导航 | 对话/麦克风/相机 | **无**（纯文本翻译，功能聚焦） |
| 颜色 | 动态取色/壁纸色 | **固定 Google Blue**（品牌一致性） |
| 粘贴按钮 | 有（智能显示） | **可选**（剪贴板检测实现复杂，V2 可加） |
| 语言列表 | 底部 Sheet | **居中 Dialog**（减少层级，直接操作） |

---

## 六、无障碍规范

### 6.1 ContentDescription 全量清单

| 元素 | 中文 | 英文 |
|------|------|------|
| 源语言 Chip | "选择源语言，当前：中文" | "Select source language, currently Chinese" |
| 目标语言 Chip | "选择目标语言，当前：英语" | "Select target language, currently English" |
| Swap 按钮 | "交换源语言和目标语言" | "Swap source and target languages" |
| 输入文本框 | "输入要翻译的文本" | "Enter text to translate" |
| 清除按钮 | "清除输入文本" | "Clear input text" |
| 翻译按钮 | "翻译" | "Translate" |
| 取消翻译 | "取消翻译" | "Cancel translation" |
| 复制按钮 | "复制翻译结果" | "Copy translation result" |
| 下载按钮 | "下载翻译模型" | "Download translation model" |

### 6.2 进度指示器

- 下载进度：`progressBarRangeInfo = ProgressBarRangeInfo(current, 0f..1f)`
- 加载状态：`contentDescription = "正在加载模型"`
- 翻译中：`contentDescription = "正在翻译"`

### 6.3 焦点顺序

```
源语言 Chip → 目标语言 Chip → Swap 按钮 → 输入文本框 → 清除按钮 → 翻译按钮 → 复制按钮
```

---

## 七、响应式与适配

### 7.1 横屏适配

- 语言栏保持水平排列
- 输入/输出区并排显示（左右分栏）
- 最小宽度 600dp 触发分栏布局

### 7.2 大屏幕（平板）

- 最大内容宽度 600dp，居中显示
- 语言栏 Chip 宽度增加，文字更舒展

### 7.3 字体缩放

- 支持系统字体缩放（1.0x ~ 2.0x）
- 输入/输出文字最小 18sp（保证可读性）
- 按钮文字最小 14sp

---

## 八、动画规范

| 动画 | 时长 | 缓动 |
|------|------|------|
| 输出卡片展开 | 200ms | `easeOutCubic` |
| 输出卡片收起 | 150ms | `easeInCubic` |
| 状态横幅展开 | 250ms | `easeOutCubic` |
| 状态横幅收起 | 200ms | `easeInCubic` |
| 语言交换旋转 | 200ms | `easeInOutCubic` |
| Toast 淡入淡出 | 150ms | `linear` |

---

## 九、实现检查清单

### 9.1 必须实现

- [ ] 固定主色为 `#1A73E8`，禁用动态取色
- [ ] LanguageBar 使用白色 Chip + 无阴影/微阴影
- [ ] InputArea 使用 22sp 大字号输入框
- [ ] OutputCard 使用 `Primary Container` 20% 透明背景 + 顶部蓝色装饰条
- [ ] 清除按钮（有内容时显示，48dp 触摸目标）
- [ ] 全量 `contentDescription`
- [ ] 展开/收起动画

### 9.2 建议实现

- [ ] 粘贴按钮（检测剪贴板非空时显示）
- [ ] 翻译完成 Toast 提示
- [ ] 复制成功 Toast 提示
- [ ] 横屏分栏布局
- [ ] 键盘 Done 键触发翻译
- [ ] 输入框空时显示更大的占位符（类似 Google 翻译的"翻译文字"大字）

### 9.3 可选优化

- [ ] 语言列表支持搜索过滤
- [ ] 最近使用语言置顶
- [ ] 翻译历史记录
- [ ] 深色模式配色

---

## 十、参考截图

### 当前实现截图

![当前 UI](current_ui.png)

**问题识别**：
1. 整体色调偏橙（Material 动态取色导致）
2. 输入卡片边框突兀
3. 字符计数和翻译按钮布局拥挤
4. 输出卡片背景色过深
5. Swap 按钮过小，不明显

### Google 翻译参考

![Google Translate](google_translate.png)

**借鉴点**：
1. 白色/浅米色背景，干净无压迫感
2. 语言 Chip 白色背景 + 微阴影
3. 输入区无边界，大字占位符
4. 底部语言栏布局（可选调整）

---

*文档版本: 1.0*
*日期: 2026-05-22*
*作者: OpenCode*
