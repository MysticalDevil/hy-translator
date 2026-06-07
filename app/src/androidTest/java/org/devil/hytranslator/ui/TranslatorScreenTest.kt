package org.devil.hytranslator.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.devil.hytranslator.R
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetState
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.Language
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.domain.model.ModelStatus
import org.devil.hytranslator.domain.model.VoiceInputState
import org.devil.hytranslator.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslatorScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun translatorScreen_emptyStateShowsPrimaryControlsAndAssetPrompts() {
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                TestTranslatorScreen(
                    inputText = "",
                    outputText = "",
                    sourceLang = autoLanguage,
                    targetLang = english,
                    isSwapEnabled = false,
                    isTranslating = false,
                    isLiveTranslateEnabled = false,
                    asrAssetState = AiAssetState.NotDownloaded,
                    ocrAssetState = AiAssetState.NotDownloaded,
                    modelStatus = ModelStatus.NotDownloaded,
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.model_download_title))
            .assertExists()
        composeRule.onNodeWithText(
            string(
                R.string.asset_not_downloaded,
                string(R.string.asset_asr_zipformer),
            ),
        ).assertExists()
        composeRule.onNodeWithText(
            string(
                R.string.asset_not_downloaded,
                string(R.string.asset_ocr_ppocrv5),
            ),
        ).assertExists()
        composeRule.onNodeWithContentDescription(
            string(R.string.cd_live_translate_toggle),
        ).assertExists()
        composeRule.onNodeWithContentDescription(
            string(R.string.cd_voice_input_toggle),
        ).assertExists()
        composeRule.onNodeWithContentDescription(
            string(R.string.cd_ocr_button),
        ).assertExists()
    }

    @Test
    fun translatorScreen_readyStateShowsTranslationResultAndCopyAction() {
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                TestTranslatorScreen(
                    inputText = "hello",
                    outputText = "bonjour",
                    sourceLang = english,
                    targetLang = french,
                    isSwapEnabled = true,
                    isTranslating = false,
                    isLiveTranslateEnabled = true,
                    asrAssetState = AiAssetState.Ready,
                    ocrAssetState = AiAssetState.Ready,
                    modelStatus = ModelStatus.Ready,
                )
            }
        }

        composeRule.onNodeWithText("bonjour").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.cd_copy_result))
            .assertExists()
        composeRule.onNodeWithText(string(R.string.model_current, q4Model.name))
            .assertExists()
    }

    @Test
    fun modelPickerDialog_showsModelsRecommendationAndClearAction() {
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                ModelPickerDialog(
                    models = models,
                    currentModel = q4Model,
                    recommendedModel = q6Model,
                    onSelect = {},
                    onDismiss = {},
                    onClearAllModels = {},
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.model_select_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText("Hy-MT2-1.8B Q4_K_M").assertExists()
        composeRule.onNodeWithText("Hy-MT2-1.8B Q6_K").assertExists()
        composeRule.onNodeWithText(string(R.string.model_recommended)).assertExists()
        composeRule.onNodeWithText(string(R.string.model_clear_all)).assertExists()
    }

    @Test
    fun translatorScreen_voiceRuntimeErrorShowsMessage() {
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                TestTranslatorScreen(
                    inputText = "",
                    outputText = "",
                    sourceLang = english,
                    targetLang = french,
                    isSwapEnabled = true,
                    isTranslating = false,
                    isLiveTranslateEnabled = false,
                    voiceInputState = VoiceInputState.Error("runtime missing"),
                    asrAssetState = AiAssetState.Ready,
                    ocrAssetState = AiAssetState.Ready,
                    modelStatus = ModelStatus.Ready,
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.voice_input_error, "runtime missing"))
            .assertExists()
    }

    @Test
    fun translatorScreen_modelErrorShowsRetryMessage() {
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                TestTranslatorScreen(
                    inputText = "",
                    outputText = "",
                    sourceLang = english,
                    targetLang = french,
                    isSwapEnabled = true,
                    isTranslating = false,
                    isLiveTranslateEnabled = false,
                    asrAssetState = AiAssetState.Ready,
                    ocrAssetState = AiAssetState.Ready,
                    modelStatus = ModelStatus.Error("Notifications permission denied"),
                )
            }
        }

        composeRule.onNodeWithText(
            string(R.string.model_error, "Notifications permission denied"),
        ).assertExists()
        composeRule.onNodeWithText(string(R.string.action_retry)).assertExists()
    }

    @Test
    fun translatorScreen_assetErrorShowsTargetAssetMessage() {
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                TestTranslatorScreen(
                    inputText = "",
                    outputText = "",
                    sourceLang = english,
                    targetLang = french,
                    isSwapEnabled = true,
                    isTranslating = false,
                    isLiveTranslateEnabled = false,
                    asrAssetState = AiAssetState.NotDownloaded,
                    ocrAssetState = AiAssetState.Error("Notifications permission denied"),
                    modelStatus = ModelStatus.Ready,
                )
            }
        }

        composeRule.onNodeWithText(
            string(
                R.string.asset_error,
                string(R.string.asset_ocr_ppocrv5),
                "Notifications permission denied",
            ),
        ).assertExists()
        composeRule.onNodeWithTag(TranslatorTestTags.OcrAssetDownload).assertExists()
    }

    @Test
    fun translatorScreen_assetDownloadStatesRemainIndependent() {
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                TestTranslatorScreen(
                    inputText = "",
                    outputText = "",
                    sourceLang = english,
                    targetLang = french,
                    isSwapEnabled = true,
                    isTranslating = false,
                    isLiveTranslateEnabled = false,
                    asrAssetState = AiAssetState.Downloading(null),
                    ocrAssetState = AiAssetState.NotDownloaded,
                    modelStatus = ModelStatus.Ready,
                )
            }
        }

        composeRule.onNodeWithTag(TranslatorTestTags.AsrAssetStatus)
            .assertExists()
        composeRule.onNodeWithTag(TranslatorTestTags.OcrAssetStatus)
            .assertExists()
        composeRule.onNodeWithText(
            string(R.string.asset_downloading, string(R.string.asset_asr_zipformer)),
        ).assertExists()
        composeRule.onNodeWithText(
            string(R.string.asset_not_downloaded, string(R.string.asset_ocr_ppocrv5)),
        ).assertExists()
    }

    @Test
    fun translatorScreen_downloadProgressDisplaysMeasuredSizes() {
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                TestTranslatorScreen(
                    inputText = "",
                    outputText = "",
                    sourceLang = english,
                    targetLang = french,
                    isSwapEnabled = true,
                    isTranslating = false,
                    isLiveTranslateEnabled = false,
                    asrAssetState = AiAssetState.Downloading(
                        DownloadProgress.Downloading(
                            downloaded = 25_000_000L,
                            total = 100_000_000L,
                        ),
                    ),
                    ocrAssetState = AiAssetState.NotDownloaded,
                    modelStatus = ModelStatus.Downloading,
                    downloadProgress = DownloadProgress.Downloading(
                        downloaded = 500_000_000L,
                        total = 1_500_000_000L,
                    ),
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.model_downloading, 500.0f, 1500.0f))
            .assertExists()
        composeRule.onNodeWithTag(TranslatorTestTags.ModelDownloadProgress)
            .assertExists()
        composeRule.onNodeWithTag(TranslatorTestTags.AsrAssetProgress)
            .assertExists()
    }

    @Test
    fun translatorScreen_languageSwapButtonEmitsCallback() {
        var swapRequests = 0
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                TestTranslatorScreen(
                    inputText = "",
                    outputText = "",
                    sourceLang = english,
                    targetLang = french,
                    isSwapEnabled = true,
                    isTranslating = false,
                    isLiveTranslateEnabled = false,
                    asrAssetState = AiAssetState.Ready,
                    ocrAssetState = AiAssetState.Ready,
                    modelStatus = ModelStatus.Ready,
                    onSwapLanguages = { swapRequests += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(TranslatorTestTags.LanguageSwap)
            .performClick()

        org.junit.Assert.assertEquals(1, swapRequests)
    }

    @Test
    fun translatorScreen_assetDownloadButtonsEmitMatchingAsset() {
        val requestedAssets = mutableListOf<AiAsset>()
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                TestTranslatorScreen(
                    inputText = "",
                    outputText = "",
                    sourceLang = english,
                    targetLang = french,
                    isSwapEnabled = true,
                    isTranslating = false,
                    isLiveTranslateEnabled = false,
                    asrAssetState = AiAssetState.NotDownloaded,
                    ocrAssetState = AiAssetState.NotDownloaded,
                    modelStatus = ModelStatus.Ready,
                    onDownloadAiAsset = requestedAssets::add,
                )
            }
        }

        composeRule.onNodeWithTag(TranslatorTestTags.AsrAssetDownload)
            .performClick()
        composeRule.onNodeWithTag(TranslatorTestTags.OcrAssetDownload)
            .performClick()

        org.junit.Assert.assertEquals(
            listOf(
                AiAsset.AsrStreamingZipformer,
                AiAsset.OcrPpOcrV5Mobile,
            ),
            requestedAssets,
        )
    }

    @Test
    fun translatorScreen_assetDownloadCancelButtonsEmitMatchingAsset() {
        val cancelledAssets = mutableListOf<AiAsset>()
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                TestTranslatorScreen(
                    inputText = "",
                    outputText = "",
                    sourceLang = english,
                    targetLang = french,
                    isSwapEnabled = true,
                    isTranslating = false,
                    isLiveTranslateEnabled = false,
                    asrAssetState = AiAssetState.Downloading(null),
                    ocrAssetState = AiAssetState.Downloading(null),
                    modelStatus = ModelStatus.Ready,
                    onCancelAiAssetDownload = cancelledAssets::add,
                )
            }
        }

        composeRule.onNodeWithTag(TranslatorTestTags.AsrAssetCancel)
            .performClick()
        composeRule.onNodeWithTag(TranslatorTestTags.OcrAssetCancel)
            .performClick()

        org.junit.Assert.assertEquals(
            listOf(
                AiAsset.AsrStreamingZipformer,
                AiAsset.OcrPpOcrV5Mobile,
            ),
            cancelledAssets,
        )
    }

    @Test
    fun translatorScreen_modelDownloadingCancelEmitsCallback() {
        var cancelRequests = 0
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                TestTranslatorScreen(
                    inputText = "",
                    outputText = "",
                    sourceLang = english,
                    targetLang = french,
                    isSwapEnabled = true,
                    isTranslating = false,
                    isLiveTranslateEnabled = false,
                    asrAssetState = AiAssetState.Ready,
                    ocrAssetState = AiAssetState.Ready,
                    modelStatus = ModelStatus.Downloading,
                    onCancelDownload = { cancelRequests += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(TranslatorTestTags.ModelDownloadCancel)
            .performClick()

        org.junit.Assert.assertEquals(1, cancelRequests)
    }

    @Test
    fun translatorScreen_highlightsNotificationTargetAsset() {
        composeRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                TestTranslatorScreen(
                    inputText = "",
                    outputText = "",
                    sourceLang = english,
                    targetLang = french,
                    isSwapEnabled = true,
                    isTranslating = false,
                    isLiveTranslateEnabled = false,
                    asrAssetState = AiAssetState.NotDownloaded,
                    ocrAssetState = AiAssetState.NotDownloaded,
                    highlightedAiAsset = AiAsset.OcrPpOcrV5Mobile,
                    modelStatus = ModelStatus.Ready,
                )
            }
        }

        composeRule.onNodeWithTag(TranslatorTestTags.OcrAssetHighlighted)
            .assertExists()
        composeRule.onNodeWithTag(TranslatorTestTags.AsrAssetStatus)
            .assertExists()
    }

    private fun string(resId: Int, vararg formatArgs: Any): String =
        composeRule.activity.getString(resId, *formatArgs)

    @Composable
    private fun TestTranslatorScreen(
        inputText: String,
        outputText: String,
        sourceLang: Language,
        targetLang: Language,
        isSwapEnabled: Boolean,
        isTranslating: Boolean,
        isLiveTranslateEnabled: Boolean,
        voiceInputState: VoiceInputState = VoiceInputState.Idle,
        asrAssetState: AiAssetState,
        ocrAssetState: AiAssetState,
        highlightedAiAsset: AiAsset? = null,
        modelStatus: ModelStatus,
        downloadProgress: DownloadProgress? = null,
        onSwapLanguages: () -> Unit = {},
        onDownloadAiAsset: (AiAsset) -> Unit = {},
        onCancelAiAssetDownload: (AiAsset) -> Unit = {},
        onCancelDownload: () -> Unit = {},
    ) {
        TranslatorScreen(
            inputText = inputText,
            onInputTextChange = {},
            outputText = outputText,
            sourceLang = sourceLang,
            sourceLanguages = languages,
            onSourceLangChange = {},
            targetLang = targetLang,
            targetLanguages = targetLanguages,
            onTargetLangChange = {},
            isSwapEnabled = isSwapEnabled,
            onSwapLanguages = onSwapLanguages,
            onTranslate = {},
            onCancel = {},
            isTranslating = isTranslating,
            isLiveTranslateEnabled = isLiveTranslateEnabled,
            onLiveTranslateToggle = {},
            voiceInputState = voiceInputState,
            asrAssetState = asrAssetState,
            ocrAssetState = ocrAssetState,
            ocrFlow = OcrFlow.Hidden,
            highlightedAiAsset = highlightedAiAsset,
            onVoiceInputToggle = {},
            onDownloadAiAsset = onDownloadAiAsset,
            onCancelAiAssetDownload = onCancelAiAssetDownload,
            onStartOcr = {},
            onOcrBitmapCaptured = {},
            onOcrDismiss = {},
            onOcrRequestCamera = {},
            onOcrRequestGallery = {},
            onOcrTextConfirm = {},
            onOcrRetry = {},
            modelStatus = modelStatus,
            downloadProgress = downloadProgress,
            selectedModel = q4Model,
            onSwitchModel = {},
            onDownload = {},
            onCancelDownload = onCancelDownload,
        )
    }

    private companion object {
        val autoLanguage = Language("auto", "Auto", "Auto")
        val english = Language("en", "English", "English")
        val french = Language("fr", "French", "French")
        val languages = listOf(autoLanguage, english, french)
        val targetLanguages = listOf(english, french)

        val q4Model = ModelOption(
            key = "Q4_K_M",
            name = "Hy-MT2-1.8B Q4_K_M",
            description = "Recommended 1.1GB, speed-quality balance",
            filename = "Hy-MT2-1.8B-Q4_K_M.gguf",
            sizeGb = 1.1f,
            memoryRequirementGb = 2.2f,
        )
        val q6Model = ModelOption(
            key = "Q6_K",
            name = "Hy-MT2-1.8B Q6_K",
            description = "Balanced 1.5GB, good quality",
            filename = "Hy-MT2-1.8B-Q6_K.gguf",
            sizeGb = 1.5f,
            memoryRequirementGb = 2.8f,
        )
        val models = listOf(q4Model, q6Model)
    }
}
