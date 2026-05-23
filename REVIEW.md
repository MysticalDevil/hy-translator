# 🔍 Hy Translator — Code Review

> **Date**: 2026-05-23 · **Round 4**
> **Project**: Android offline translation app using llama.cpp + Tencent Hy-MT2-1.8B

---

## 本轮改动 (R3 → R4)

| 文件 | 变更 | 评价 |
|------|------|------|
| `values-zh/strings.xml` | 同步：新增 `action_retry`/`model_clear_all`/Q6_K，移除 Q2_K/Q3_K_M/Q5_K_M | ✅ |
| `service/TranslatorEngine.kt` | `cancel()` → `unloadModel()`，语义匹配原生 `cleanUp()` | ✅ |
| `domain/repository/TranslatorRepository.kt` | `cancel()` → `unloadModel()` | ✅ |
| `data/repository/TranslatorRepositoryImpl.kt` | `cancel()` → `unloadModel()` | ✅ |
| `ui/TranslatorViewModel.kt` | 调用点重命名；`ModelRepositoryImpl` 构造函数注入 filename | ✅ |
| `data/repository/ModelRepositoryImpl.kt` | 构造函数注入 filename，删除脆弱的 `requireDownloader()` | ✅ |
| `data/Models.kt` | 删除重复的 `object ModelDownloader { HF_BASE_URL }` | ✅ |
| `domain/usecase/*.kt` | **删除** 4 个未使用的 UseCase 文件 | ✅ |
| `ui/ModelPickerDialog.kt` | divider+button 从 `LazyColumn.item{}` 提至父 `Column` | ✅ |
| `ui/CameraCapture.kt` | `onError` 加 `Log.e`，不再静默吞错 | ✅ |

---

## 🔴 剩余严重问题

### 1. 零测试覆盖
仅脚手架占位：`assertEquals(4, 2+2)`、检查 package name。无任何业务逻辑测试。

---

## 🟡 中等问题

| # | 问题 |
|---|------|
| 2 | `LanguageRepository` inline 实现与 `LanguageRepositoryImpl` 共存（二选一即可） |
| 3 | `processBitmapFromUri` 内新建冗余 OcrEngine（与 `remember` 实例重复） |

## 🟢 轻微问题

| # | 问题 |
|---|------|
| 4 | `logging.h` `#ifndef` + `#pragma once` 双重守卫 |

---

## ✅ 已修复 (R4)

| # | 问题 | 说明 |
|---|------|------|
| ✓ | `strings-zh.xml` 同步 | 中英文 key 完全一致 |
| ✓ | `cancel()` 语义修复 | 全链路 `cancel()` → `unloadModel()` |
| ✓ | `ModelRepositoryImpl` 初始化脆弱 | 构造函数注入 filename |
| ✓ | `HF_BASE_URL` 重复 | 从 Models.kt 删除 |
| ✓ | UseCase 死代码 | 4 个文件已删除 |
| ✓ | ModelPickerDialog 布局优化 | divider+button 外提到 Column |
| ✓ | `CameraCapture.onError` | 加 `Log.e` |
| ✓ | 模型列表修正 | Q4_K_M / Q6_K / Q8_0 三个实际存在的文件 |
| ✓ | 下载错误流转 | `DownloadProgress.Error` → `ModelStatus.Error` + Retry 按钮 |
| ✓ | Clear All Models | 模型选择弹窗底部 |
| ✓ | 重复下载守卫 | `downloadJob?.isActive` |

## 有意保留

| # | 问题 | 理由 |
|---|------|------|
| — | 下载取消 `.tmp` 残留 | `.tmp` 支持断点续传，`clearAllModels()` 时一并删除 |

---

## 📊 评分趋势

| 维度 | R2 | R3 | R4 | 趋势 |
|------|-----|-----|-----|------|
| 架构 | ★★★★☆ | ★★★★☆ | ★★★★☆ | — |
| 代码质量 | ★★★☆☆ | ★★★★☆ | ★★★★☆ | → 稳定 |
| 测试 | ★☆☆☆☆ | ★☆☆☆☆ | ★☆☆☆☆ | — |
| Native/JNI | ★★★★☆ | ★★★★☆ | ★★★★☆ | — |
| UI/UX | ★★★★☆ | ★★★★☆ | ★★★★☆ | — |
| 工程化 | ★★★☆☆ | ★★★☆☆ | ★★★★☆ | ↑ 中英 strings 同步 + 文件拆分 |

---

## 🔧 剩余行动项

| # | 优先级 | 问题 | 改动量 |
|---|--------|------|--------|
| 1 | 🔴 | 补充关键路径测试 | 中 |
| 2 | 🟡 | 删除 `LanguageRepositoryImpl` 或统一实现 | 微小 |
| 3 | 🟡 | `processBitmapFromUri` 复用 ocrEngine | 小 |
| 4 | 🟢 | `logging.h` 去重 include guard | 微小 |
