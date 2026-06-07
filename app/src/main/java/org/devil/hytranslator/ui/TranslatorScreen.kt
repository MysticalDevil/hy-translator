package org.devil.hytranslator.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.devil.hytranslator.R
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetState
import org.devil.hytranslator.domain.model.DownloadProgress
import org.devil.hytranslator.domain.model.Language
import org.devil.hytranslator.domain.model.ModelOption
import org.devil.hytranslator.domain.model.ModelStatus
import org.devil.hytranslator.domain.model.VoiceInputState
import org.devil.hytranslator.theme.InputTextStyle
import org.devil.hytranslator.theme.OutputTextStyle

@Composable
fun TranslatorScreen(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    outputText: String,
    sourceLang: Language,
    sourceLanguages: List<Language>,
    onSourceLangChange: (Language) -> Unit,
    targetLang: Language,
    targetLanguages: List<Language>,
    onTargetLangChange: (Language) -> Unit,
    isSwapEnabled: Boolean,
    onSwapLanguages: () -> Unit,
    onTranslate: () -> Unit,
    onCancel: () -> Unit,
    isTranslating: Boolean,
    isLiveTranslateEnabled: Boolean,
    onLiveTranslateToggle: (Boolean) -> Unit,
    voiceInputState: VoiceInputState,
    asrAssetState: AiAssetState,
    ocrAssetState: AiAssetState,
    ocrFlow: OcrFlow,
    highlightedAiAsset: AiAsset? = null,
    onVoiceInputToggle: (Boolean) -> Unit,
    onDownloadAiAsset: (AiAsset) -> Unit,
    onCancelAiAssetDownload: (AiAsset) -> Unit,
    onStartOcr: () -> Unit,
    onOcrBitmapCaptured: (Bitmap) -> Unit,
    onOcrDismiss: () -> Unit,
    onOcrRequestCamera: () -> Unit,
    onOcrRequestGallery: () -> Unit,
    onOcrTextConfirm: (String) -> Unit,
    onOcrRetry: () -> Unit,
    modelStatus: ModelStatus,
    downloadProgress: DownloadProgress?,
    selectedModel: ModelOption,
    onSwitchModel: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showCopyToast by remember { mutableStateOf(false) }
    var swapRotation by remember { mutableFloatStateOf(0f) }

    val animatedRotation by animateFloatAsState(
        targetValue = swapRotation,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
    )

    LaunchedEffect(showCopyToast) {
        if (showCopyToast) {
            kotlinx.coroutines.delay(1500)
            showCopyToast = false
        }
    }

    if (showSourcePicker) {
        LanguagePickerDialog(
            title = stringResource(R.string.cd_source_lang_dropdown),
            languages = sourceLanguages,
            currentLang = sourceLang,
            onSelect = { lang ->
                onSourceLangChange(lang)
                showSourcePicker = false
            },
            onDismiss = { showSourcePicker = false },
        )
    }

    if (showTargetPicker) {
        LanguagePickerDialog(
            title = stringResource(R.string.cd_target_lang_dropdown),
            languages = targetLanguages,
            currentLang = targetLang,
            onSelect = { lang ->
                onTargetLangChange(lang)
                showTargetPicker = false
            },
            onDismiss = { showTargetPicker = false },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LanguageBar(
                sourceLang = sourceLang,
                targetLang = targetLang,
                onSourceClick = { showSourcePicker = true },
                onTargetClick = { showTargetPicker = true },
                onSwap = {
                    swapRotation += 180f
                    onSwapLanguages()
                },
                swapEnabled = isSwapEnabled,
                animatedRotation = animatedRotation,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                InputArea(
                    text = inputText,
                    onTextChange = onInputTextChange,
                    isTranslating = isTranslating,
                    modelReady = modelStatus is ModelStatus.Ready,
                    onTranslate = onTranslate,
                    onCancel = onCancel,
                    isLiveTranslateEnabled = isLiveTranslateEnabled,
                    onLiveTranslateToggle = onLiveTranslateToggle,
                    voiceInputState = voiceInputState,
                    asrAssetState = asrAssetState,
                    ocrAssetState = ocrAssetState,
                    onVoiceInputToggle = onVoiceInputToggle,
                    onDownloadAiAsset = onDownloadAiAsset,
                    onOcrClick = onStartOcr,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                AiAssetStatusMessages(
                    asrAssetState = asrAssetState,
                    ocrAssetState = ocrAssetState,
                    highlightedAiAsset = highlightedAiAsset,
                    onDownloadAiAsset = onDownloadAiAsset,
                    onCancelAiAssetDownload = onCancelAiAssetDownload,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                VoiceInputStatusMessage(
                    voiceInputState = voiceInputState,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                AnimatedVisibility(
                    visible = outputText.isNotEmpty() || isTranslating,
                    enter = fadeIn(tween(200)) + expandVertically(
                        tween(200, easing = FastOutSlowInEasing),
                    ),
                    exit = fadeOut(tween(150)) + shrinkVertically(
                        tween(150, easing = FastOutSlowInEasing),
                    ),
                ) {
                    OutputCard(
                        outputText = outputText,
                        targetLang = targetLang,
                        isTranslating = isTranslating,
                        onCopy = {
                            val clipboard = context.getSystemService(
                                android.content.Context.CLIPBOARD_SERVICE,
                            ) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("translation", outputText),
                            )
                            showCopyToast = true
                        },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = modelStatus !is ModelStatus.Ready,
                enter = fadeIn(tween(250)) + expandVertically(
                    tween(250, easing = FastOutSlowInEasing),
                ),
                exit = fadeOut(tween(200)) + shrinkVertically(
                    tween(200, easing = FastOutSlowInEasing),
                ),
            ) {
                StatusBanner(
                    modelStatus = modelStatus,
                    downloadProgress = downloadProgress,
                    selectedModel = selectedModel,
                    onSwitchModel = onSwitchModel,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (modelStatus is ModelStatus.Ready) {
                ModelInfoBar(
                    selectedModel = selectedModel,
                    onSwitchModel = onSwitchModel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        if (ocrFlow == OcrFlow.CameraActive) {
            BackHandler { onOcrDismiss() }
            CameraCapture(
                onCaptured = onOcrBitmapCaptured,
                onDismiss = onOcrDismiss,
            )
        }

        OcrBottomSheet(
            ocrFlow = ocrFlow,
            onDismiss = onOcrDismiss,
            onRequestCamera = onOcrRequestCamera,
            onRequestGallery = onOcrRequestGallery,
            onTextConfirm = onOcrTextConfirm,
            onRetry = onOcrRetry,
        )

        if (showCopyToast) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                ) {
                    Text(
                        text = stringResource(R.string.copied_to_clipboard),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageBar(
    sourceLang: Language,
    targetLang: Language,
    onSourceClick: () -> Unit,
    onTargetClick: () -> Unit,
    onSwap: () -> Unit,
    swapEnabled: Boolean,
    animatedRotation: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LanguageChip(
            language = sourceLang,
            onClick = onSourceClick,
            modifier = Modifier.weight(1f),
        )

        IconButton(
            onClick = onSwap,
            enabled = swapEnabled,
            modifier = Modifier
                .size(40.dp)
                .testTag(TranslatorTestTags.LanguageSwap)
                .graphicsLayer { rotationZ = animatedRotation },
        ) {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = stringResource(R.string.cd_swap_languages),
                tint = if (swapEnabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
                modifier = Modifier.size(24.dp),
            )
        }

        LanguageChip(
            language = targetLang,
            onClick = onTargetClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LanguageChip(
    language: Language,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = languageDisplayName(language)

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun InputArea(
    text: String,
    onTextChange: (String) -> Unit,
    isTranslating: Boolean,
    modelReady: Boolean,
    onTranslate: () -> Unit,
    onCancel: () -> Unit,
    isLiveTranslateEnabled: Boolean,
    onLiveTranslateToggle: (Boolean) -> Unit,
    voiceInputState: VoiceInputState,
    asrAssetState: AiAssetState,
    ocrAssetState: AiAssetState,
    onVoiceInputToggle: (Boolean) -> Unit,
    onDownloadAiAsset: (AiAsset) -> Unit,
    onOcrClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val liveTranslateContentDescription = stringResource(R.string.cd_live_translate_toggle)
    val voiceInputContentDescription = stringResource(R.string.cd_voice_input_toggle)
    val inputContentDescription = stringResource(R.string.cd_translation_input)
    val isVoiceInputListening = voiceInputState is VoiceInputState.Listening
    val asrReady = asrAssetState is AiAssetState.Ready
    val ocrReady = ocrAssetState is AiAssetState.Ready

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Box {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 68.dp, top = 20.dp, bottom = 56.dp)
                    .defaultMinSize(minHeight = 160.dp)
                    .testTag(TranslatorTestTags.TranslationInput)
                    .semantics {
                        contentDescription = inputContentDescription
                    },
                textStyle = InputTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onTranslate() }),
                decorationBox = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.hint_input_text),
                                style = InputTextStyle.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                        innerTextField()
                    }
                },
            )

            if (text.isNotEmpty()) {
                IconButton(
                    onClick = { onTextChange("") },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                        .size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_clear_input),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.height(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${text.length}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (asrReady) {
                                onVoiceInputToggle(!isVoiceInputListening)
                            } else {
                                onDownloadAiAsset(AiAsset.AsrStreamingZipformer)
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .semantics {
                                contentDescription = voiceInputContentDescription
                            },
                    ) {
                        Icon(
                            imageVector = if (isVoiceInputListening) {
                                Icons.Filled.Mic
                            } else {
                                Icons.Filled.MicOff
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (asrReady) 1f else 0.62f,
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { onLiveTranslateToggle(!isLiveTranslateEnabled) },
                        enabled = modelReady,
                        modifier = Modifier
                            .size(40.dp)
                            .semantics {
                                contentDescription = liveTranslateContentDescription
                            },
                    ) {
                        Icon(
                            imageVector = if (isLiveTranslateEnabled) {
                                Icons.Filled.FlashOn
                            } else {
                                Icons.Filled.FlashOff
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (modelReady) 1f else 0.38f,
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onOcrClick,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = stringResource(R.string.cd_ocr_button),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (ocrReady) 1f else 0.62f,
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    if (isTranslating) {
                        TextButton(onClick = onCancel) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    } else if (text.isNotEmpty()) {
                        TextButton(
                            onClick = onTranslate,
                            enabled = modelReady,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface
                                    .copy(alpha = 0.38f),
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.action_translate),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceInputStatusMessage(
    voiceInputState: VoiceInputState,
    modifier: Modifier = Modifier,
) {
    if (voiceInputState !is VoiceInputState.Error) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = stringResource(R.string.voice_input_error, voiceInputState.message),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AiAssetStatusMessages(
    asrAssetState: AiAssetState,
    ocrAssetState: AiAssetState,
    highlightedAiAsset: AiAsset?,
    onDownloadAiAsset: (AiAsset) -> Unit,
    onCancelAiAssetDownload: (AiAsset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleStates = listOf(
        AiAsset.AsrStreamingZipformer to asrAssetState,
        AiAsset.OcrPpOcrV5Mobile to ocrAssetState,
    ).filterNot { (_, state) -> state is AiAssetState.Ready }

    if (visibleStates.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visibleStates.forEach { (asset, state) ->
            AiAssetStatusRow(
                asset = asset,
                state = state,
                highlighted = asset == highlightedAiAsset,
                onDownload = { onDownloadAiAsset(asset) },
                onCancelDownload = { onCancelAiAssetDownload(asset) },
            )
        }
    }
}

@Composable
private fun AiAssetStatusRow(
    asset: AiAsset,
    state: AiAssetState,
    highlighted: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    val assetName = when (asset) {
        AiAsset.AsrStreamingZipformer -> stringResource(R.string.asset_asr_zipformer)
        AiAsset.OcrPpOcrV5Mobile -> stringResource(R.string.asset_ocr_ppocrv5)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (highlighted) asset.highlightedStatusTag else asset.statusTag),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (highlighted) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (state) {
                        is AiAssetState.NotDownloaded -> stringResource(
                            R.string.asset_not_downloaded,
                            assetName,
                        )
                        is AiAssetState.Downloading -> stringResource(
                            R.string.asset_downloading,
                            assetName,
                        )
                        is AiAssetState.Error -> stringResource(
                            R.string.asset_error,
                            assetName,
                            state.message,
                        )
                        AiAssetState.Ready -> assetName
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state is AiAssetState.Downloading) {
                    TextButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.testTag(asset.cancelTag),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                } else {
                    TextButton(
                        onClick = onDownload,
                        modifier = Modifier.testTag(asset.downloadTag),
                    ) {
                        Text(stringResource(R.string.asset_download))
                    }
                }
            }

            val progress = (state as? AiAssetState.Downloading)?.progress
            if (progress is DownloadProgress.Downloading && progress.total > 0L) {
                LinearProgressIndicator(
                    progress = { progress.downloaded.toFloat() / progress.total.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(asset.progressTag),
                )
            } else if (state is AiAssetState.Downloading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(asset.progressTag),
                )
            }
        }
    }
}

private val AiAsset.statusTag: String
    get() = when (this) {
        AiAsset.AsrStreamingZipformer -> TranslatorTestTags.AsrAssetStatus
        AiAsset.OcrPpOcrV5Mobile -> TranslatorTestTags.OcrAssetStatus
    }

private val AiAsset.downloadTag: String
    get() = when (this) {
        AiAsset.AsrStreamingZipformer -> TranslatorTestTags.AsrAssetDownload
        AiAsset.OcrPpOcrV5Mobile -> TranslatorTestTags.OcrAssetDownload
    }

private val AiAsset.cancelTag: String
    get() = when (this) {
        AiAsset.AsrStreamingZipformer -> TranslatorTestTags.AsrAssetCancel
        AiAsset.OcrPpOcrV5Mobile -> TranslatorTestTags.OcrAssetCancel
    }

private val AiAsset.progressTag: String
    get() = when (this) {
        AiAsset.AsrStreamingZipformer -> TranslatorTestTags.AsrAssetProgress
        AiAsset.OcrPpOcrV5Mobile -> TranslatorTestTags.OcrAssetProgress
    }

private val AiAsset.highlightedStatusTag: String
    get() = when (this) {
        AiAsset.AsrStreamingZipformer -> TranslatorTestTags.AsrAssetHighlighted
        AiAsset.OcrPpOcrV5Mobile -> TranslatorTestTags.OcrAssetHighlighted
    }

@Composable
private fun OutputCard(
    outputText: String,
    targetLang: Language,
    isTranslating: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = languageDisplayName(targetLang)

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(
                    onClick = onCopy,
                    enabled = outputText.isNotEmpty(),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_result),
                        tint = if (outputText.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
                    .defaultMinSize(minHeight = 60.dp),
            ) {
                if (isTranslating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.translating),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (outputText.isNotEmpty()) {
                    Text(
                        text = outputText,
                        style = OutputTextStyle.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelInfoBar(
    selectedModel: ModelOption,
    onSwitchModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.model_current,
                modelDisplayName(selectedModel),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        TextButton(
            onClick = onSwitchModel,
            modifier = Modifier.height(32.dp),
        ) {
            Text(
                text = stringResource(R.string.model_switch),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun StatusBanner(
    modelStatus: ModelStatus,
    downloadProgress: DownloadProgress?,
    selectedModel: ModelOption,
    onSwitchModel: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (modelStatus) {
                is ModelStatus.NotDownloaded -> {
                    Text(
                        text = stringResource(R.string.model_download_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.model_download_desc,
                            modelDisplayName(selectedModel),
                            selectedModel.sizeGb,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                R.string.model_download_confirm,
                                modelDisplayName(selectedModel),
                            ),
                        )
                    }
                }

                is ModelStatus.Downloading -> {
                    val downloadedMb = when (downloadProgress) {
                        is DownloadProgress.Downloading -> downloadProgress.downloaded.toFloat() / 1_000_000f
                        is DownloadProgress.Started -> downloadProgress.existing.toFloat() / 1_000_000f
                        else -> 0f
                    }
                    val estimatedTotalMb = selectedModel.sizeGb * 1_000f
                    val totalMb = when (downloadProgress) {
                        is DownloadProgress.Downloading -> downloadProgress.total.toFloat() / 1_000_000f
                        is DownloadProgress.Started -> downloadProgress.total.toFloat() / 1_000_000f
                        else -> estimatedTotalMb
                    }
                    val progress = when (downloadProgress) {
                        is DownloadProgress.Downloading -> {
                            if (downloadProgress.total > 0) {
                                downloadProgress.downloaded.toFloat() / downloadProgress.total.toFloat()
                            } else 0f
                        }
                        is DownloadProgress.Started -> {
                            if (downloadProgress.total > 0) {
                                downloadProgress.existing.toFloat() / downloadProgress.total.toFloat()
                            } else 0f
                        }
                        else -> 0f
                    }

                    Text(
                        text = stringResource(R.string.model_downloading, downloadedMb, totalMb),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TranslatorTestTags.ModelDownloadProgress),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onCancelDownload,
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag(TranslatorTestTags.ModelDownloadCancel),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }

                is ModelStatus.Loading -> {
                    Text(
                        text = stringResource(R.string.model_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface,
                    )
                }

                is ModelStatus.Error -> {
                    Text(
                        text = stringResource(R.string.model_error, modelStatus.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_retry))
                    }
                }

                is ModelStatus.Ready -> {}
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.model_current,
                        modelDisplayName(selectedModel),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSwitchModel) {
                    Text(
                        text = stringResource(R.string.model_switch),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguagePickerDialog(
    title: String,
    languages: List<Language>,
    currentLang: Language,
    onSelect: (Language) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 480.dp),
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    items(languages, key = { it.code }) { lang ->
                        val displayName = languageDisplayName(lang)
                        val isSelected = lang.code == currentLang.code

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(lang) }
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                            .copy(alpha = 0.3f)
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isSelected) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun languageDisplayName(language: Language): String = language.name

@Composable
private fun modelDisplayName(model: ModelOption): String = model.name
