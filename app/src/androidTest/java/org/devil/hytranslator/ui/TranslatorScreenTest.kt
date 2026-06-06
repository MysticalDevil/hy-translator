package org.devil.hytranslator.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.devil.hytranslator.domain.model.AiAssetState
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

        composeRule.onNodeWithText("Model download required").assertIsDisplayed()
        composeRule.onNodeWithText("Voice input model required").assertExists()
        composeRule.onNodeWithText("OCR model required").assertExists()
        composeRule.onNodeWithContentDescription("Toggle live translation").assertExists()
        composeRule.onNodeWithContentDescription("Toggle voice input").assertExists()
        composeRule.onNodeWithContentDescription("Open camera to capture text").assertExists()
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
        composeRule.onNodeWithContentDescription("Copy translation result to clipboard")
            .assertExists()
        composeRule.onNodeWithText("Current: Hy-MT2-1.8B Q4_K_M").assertExists()
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

        composeRule.onNodeWithText("Select Model").assertIsDisplayed()
        composeRule.onNodeWithText("Hy-MT2-1.8B Q4_K_M").assertExists()
        composeRule.onNodeWithText("Hy-MT2-1.8B Q6_K").assertExists()
        composeRule.onNodeWithText("Recommended").assertExists()
        composeRule.onNodeWithText("Clear All Models").assertExists()
    }

    @Composable
    private fun TestTranslatorScreen(
        inputText: String,
        outputText: String,
        sourceLang: Language,
        targetLang: Language,
        isSwapEnabled: Boolean,
        isTranslating: Boolean,
        isLiveTranslateEnabled: Boolean,
        asrAssetState: AiAssetState,
        ocrAssetState: AiAssetState,
        modelStatus: ModelStatus,
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
            onSwapLanguages = {},
            onTranslate = {},
            onCancel = {},
            isTranslating = isTranslating,
            isLiveTranslateEnabled = isLiveTranslateEnabled,
            onLiveTranslateToggle = {},
            voiceInputState = VoiceInputState.Idle,
            asrAssetState = asrAssetState,
            ocrAssetState = ocrAssetState,
            ocrFlow = OcrFlow.Hidden,
            onVoiceInputToggle = {},
            onDownloadAiAsset = {},
            onStartOcr = {},
            onOcrBitmapCaptured = {},
            onOcrDismiss = {},
            onOcrRequestCamera = {},
            onOcrRequestGallery = {},
            onOcrTextConfirm = {},
            onOcrRetry = {},
            modelStatus = modelStatus,
            downloadProgress = null,
            selectedModel = q4Model,
            onSwitchModel = {},
            onDownload = {},
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
