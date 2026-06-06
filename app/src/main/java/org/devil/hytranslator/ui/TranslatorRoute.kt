package org.devil.hytranslator.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.devil.hytranslator.HyTranslatorApplication
import org.devil.hytranslator.R
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetState
import org.devil.hytranslator.platform.ocr.OcrProcessor

@Composable
fun TranslatorRoute(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as HyTranslatorApplication).appContainer
    val scope = rememberCoroutineScope()
    val viewModel: TranslatorViewModel = viewModel(
        factory = appContainer.translatorViewModelFactory,
    )
    var showModelPicker by remember { mutableStateOf(false) }
    var lastTranslateTime by remember { mutableLongStateOf(0L) }
    var pendingModelDownload by remember { mutableStateOf(false) }
    var pendingAiAssetDownload by remember { mutableStateOf<AiAsset?>(null) }
    var ocrProcessor by remember { mutableStateOf<OcrProcessor?>(null) }
    var ocrFlow by remember { mutableStateOf<OcrFlow>(OcrFlow.Hidden) }

    fun getOcrProcessor(): OcrProcessor =
        ocrProcessor ?: OcrProcessor(context.applicationContext).also { ocrProcessor = it }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ocrFailedMessage = stringResource(R.string.ocr_failed)
    val ocrWorkflowController = remember(scope) {
        OcrWorkflowController(
            scope = scope,
            recognizeBitmap = { bitmap ->
                getOcrProcessor().recognize(bitmap)
            },
            recognizeUri = { uri, failedMessage ->
                getOcrProcessor().recognize(uri, failedMessage)
            },
            updateOcrFlow = { nextFlow -> ocrFlow = nextFlow },
        )
    }

    DisposableEffect(Unit) {
        onDispose { ocrProcessor?.close() }
    }

    LaunchedEffect(viewModel) {
        viewModel.initialize()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val aiAsset = pendingAiAssetDownload
        pendingAiAssetDownload = null
        if (aiAsset != null) {
            viewModel.onEvent(TranslatorEvent.DownloadAiAsset(aiAsset))
        } else if (pendingModelDownload) {
            viewModel.onEvent(TranslatorEvent.DownloadModel)
        }
        pendingModelDownload = false
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            ocrWorkflowController.showCamera()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            ocrWorkflowController.processUri(uri, ocrFailedMessage)
        }
    }

    val requestCamera: () -> Unit = {
        ocrWorkflowController.hide()
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val requestGallery: () -> Unit = {
        ocrWorkflowController.hide()
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    val startDownload: () -> Unit = {
        if (shouldRequestNotificationPermission(context)) {
            pendingModelDownload = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onEvent(TranslatorEvent.DownloadModel)
        }
    }

    val startAiAssetDownload: (AiAsset) -> Unit = { asset ->
        if (shouldRequestNotificationPermission(context)) {
            pendingAiAssetDownload = asset
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onEvent(TranslatorEvent.DownloadAiAsset(asset))
        }
    }

    if (showModelPicker) {
        ModelPickerDialog(
            models = viewModel.allModels,
            currentModel = uiState.selectedModel,
            recommendedModel = viewModel.recommendedModel,
            onSelect = { model ->
                viewModel.onEvent(TranslatorEvent.ModelSelected(model))
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false },
            onClearAllModels = {
                viewModel.onEvent(TranslatorEvent.ClearAllModels)
            },
        )
    }

    TranslatorScreen(
        inputText = uiState.inputText,
        onInputTextChange = { text ->
            viewModel.onEvent(TranslatorEvent.InputChanged(text))
        },
        outputText = uiState.outputText,
        sourceLang = uiState.sourceLang,
        sourceLanguages = viewModel.sourceLanguages,
        onSourceLangChange = { language ->
            viewModel.onEvent(TranslatorEvent.SourceLanguageChanged(language))
        },
        targetLang = uiState.targetLang,
        targetLanguages = viewModel.targetLanguages,
        onTargetLangChange = { language ->
            viewModel.onEvent(TranslatorEvent.TargetLanguageChanged(language))
        },
        isSwapEnabled = viewModel.isSwapEnabled(),
        onSwapLanguages = {
            viewModel.onEvent(TranslatorEvent.SwapLanguages)
        },
        onTranslate = {
            val now = System.currentTimeMillis()
            if (now - lastTranslateTime > TRANSLATE_CLICK_DEBOUNCE_MS) {
                lastTranslateTime = now
                viewModel.onEvent(TranslatorEvent.Translate)
            }
        },
        onCancel = {
            viewModel.onEvent(TranslatorEvent.CancelTranslation)
        },
        isTranslating = uiState.isTranslating,
        isLiveTranslateEnabled = uiState.isLiveTranslateEnabled,
        onLiveTranslateToggle = { enabled ->
            viewModel.onEvent(TranslatorEvent.LiveTranslateToggled(enabled))
        },
        voiceInputState = uiState.voiceInputState,
        asrAssetState = uiState.asrAssetState,
        ocrAssetState = uiState.ocrAssetState,
        ocrFlow = ocrFlow,
        onVoiceInputToggle = { enabled ->
            viewModel.onEvent(TranslatorEvent.VoiceInputToggled(enabled))
        },
        onDownloadAiAsset = startAiAssetDownload,
        onStartOcr = {
            if (uiState.ocrAssetState is AiAssetState.Ready) {
                ocrWorkflowController.showSourcePicker()
            } else {
                startAiAssetDownload(AiAsset.OcrPpOcrV5Mobile)
            }
        },
        onOcrBitmapCaptured = { bitmap ->
            ocrWorkflowController.processBitmap(bitmap, ocrFailedMessage)
        },
        onOcrDismiss = ocrWorkflowController::hide,
        onOcrRequestCamera = requestCamera,
        onOcrRequestGallery = requestGallery,
        onOcrTextConfirm = { text ->
            viewModel.onEvent(TranslatorEvent.InputChanged(text))
            ocrWorkflowController.hide()
        },
        onOcrRetry = ocrWorkflowController::showSourcePicker,
        modelStatus = uiState.modelStatus,
        downloadProgress = uiState.downloadProgress,
        selectedModel = uiState.selectedModel,
        onSwitchModel = { showModelPicker = true },
        onDownload = startDownload,
        modifier = modifier.systemBarsPadding(),
    )
}

private fun shouldRequestNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED

private const val TRANSLATE_CLICK_DEBOUNCE_MS = 500L
