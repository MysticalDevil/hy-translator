package org.devil.hytranslator.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.devil.hytranslator.R
import org.devil.hytranslator.data.Language
import org.devil.hytranslator.data.Languages
import org.devil.hytranslator.service.DownloadProgress
import org.devil.hytranslator.theme.InputTextStyle
import org.devil.hytranslator.theme.OutputTextStyle

@Composable
fun TranslatorScreen(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    outputText: String,
    sourceLang: Language,
    onSourceLangChange: (Language) -> Unit,
    targetLang: Language,
    onTargetLangChange: (Language) -> Unit,
    onTranslate: () -> Unit,
    onCancel: () -> Unit,
    isTranslating: Boolean,
    modelStatus: ModelStatus,
    downloadProgress: DownloadProgress?,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showCopyToast by remember { mutableStateOf(false) }
    var swapRotation by remember { mutableStateOf(0f) }
    val clipboardManager = LocalClipboardManager.current

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
            languages = Languages.sourceLanguages(),
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
            languages = Languages.targetLanguages(),
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
                    onSourceLangChange(targetLang)
                    onTargetLangChange(sourceLang)
                },
                swapEnabled = !Languages.isSourceOnly(sourceLang.code),
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
                    onTranslate = onTranslate,
                    onCancel = onCancel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                            clipboardManager.setText(AnnotatedString(outputText))
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
                    onDownload = onDownload,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

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
    val displayName = if (language.nameResId != null) {
        stringResource(language.nameResId)
    } else {
        language.name
    }

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
    onTranslate: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Box {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 56.dp)
                    .defaultMinSize(minHeight = 160.dp),
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
                Text(
                    text = "${text.length}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isTranslating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onCancel) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                } else if (text.isNotEmpty()) {
                    TextButton(onClick = onTranslate) {
                        Text(
                            text = stringResource(R.string.action_translate),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutputCard(
    outputText: String,
    targetLang: Language,
    isTranslating: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = if (targetLang.nameResId != null) {
        stringResource(targetLang.nameResId)
    } else {
        targetLang.name
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(
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
private fun StatusBanner(
    modelStatus: ModelStatus,
    downloadProgress: DownloadProgress?,
    onDownload: () -> Unit,
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
                        text = stringResource(R.string.model_download_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.model_download_btn))
                    }
                }

                is ModelStatus.Downloading -> {
                    val downloadedMb = when (downloadProgress) {
                        is DownloadProgress.Downloading -> downloadProgress.downloaded.toFloat() / 1_000_000f
                        is DownloadProgress.Started -> downloadProgress.existing.toFloat() / 1_000_000f
                        else -> 0f
                    }
                    val totalMb = when (downloadProgress) {
                        is DownloadProgress.Downloading -> downloadProgress.total.toFloat() / 1_000_000f
                        is DownloadProgress.Started -> downloadProgress.total.toFloat() / 1_000_000f
                        else -> 1130f
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
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
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
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                is ModelStatus.Error -> {
                    Text(
                        text = stringResource(R.string.model_error, modelStatus.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                is ModelStatus.Ready -> {}
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
                        val displayName = if (lang.nameResId != null) {
                            stringResource(lang.nameResId)
                        } else {
                            lang.name
                        }
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
