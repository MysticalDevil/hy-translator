package org.devil.hytranslator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.devil.hytranslator.data.Languages
import org.devil.hytranslator.service.DownloadProgress
import org.devil.hytranslator.service.ModelDownloader
import org.devil.hytranslator.service.TranslatorEngine
import org.devil.hytranslator.theme.MyApplicationTheme
import org.devil.hytranslator.ui.ModelStatus
import org.devil.hytranslator.ui.TranslatorScreen

class MainActivity : ComponentActivity() {

    private val translator by lazy { TranslatorEngine(this) }
    private val downloader by lazy { ModelDownloader(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TranslatorApp()
                }
            }
        }
    }

    @Composable
    private fun TranslatorApp() {
        var inputText by remember { mutableStateOf("") }
        var outputText by remember { mutableStateOf("") }
        var sourceLang by remember { mutableStateOf(Languages.all[0]) }
        var targetLang by remember { mutableStateOf(Languages.all[2]) }
        var isTranslating by remember { mutableStateOf(false) }
        var modelStatus by remember { mutableStateOf<ModelStatus>(ModelStatus.NotDownloaded) }
        var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }
        var generationFlow by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        LaunchedEffect(Unit) {
            if (downloader.isModelDownloaded()) {
                loadModel { modelStatus = it }
            }
        }

        TranslatorScreen(
            inputText = inputText,
            onInputTextChange = { inputText = it },
            outputText = outputText,
            sourceLang = sourceLang,
            onSourceLangChange = { lang ->
                sourceLang = lang
                if (lang.code == targetLang.code) {
                    targetLang = Languages.targetLanguages().first { it.code != lang.code }
                }
            },
            targetLang = targetLang,
            onTargetLangChange = { lang ->
                targetLang = lang
                if (lang.code == sourceLang.code && !Languages.isSourceOnly(sourceLang.code)) {
                    sourceLang = Languages.sourceLanguages().first { it.code != lang.code }
                }
            },
            onTranslate = {
                if (inputText.isNotBlank() && translator.isModelReady()) {
                    isTranslating = true
                    outputText = ""
                    generationFlow = lifecycleScope.launch {
                        translator.translate(
                            text = inputText,
                            sourceLang = sourceLang,
                            targetLang = targetLang,
                        ).collect { token ->
                            outputText += token
                        }
                        isTranslating = false
                        generationFlow = null
                    }
                }
            },
            onCancel = {
                generationFlow?.cancel()
                isTranslating = false
                generationFlow = null
            },
            isTranslating = isTranslating,
            modelStatus = modelStatus,
            downloadProgress = downloadProgress,
            onDownload = {
                modelStatus = ModelStatus.Downloading
                downloadProgress = null
                lifecycleScope.launch {
                    downloader.download().collect { progress ->
                        downloadProgress = progress
                        when (progress) {
                            is DownloadProgress.Completed -> {
                                loadModel { modelStatus = it }
                            }
                            is DownloadProgress.Error -> {}
                            else -> {}
                        }
                    }
                }
            },
            modifier = Modifier.systemBarsPadding(),
        )
    }

    private fun loadModel(onStatus: (ModelStatus) -> Unit) {
        onStatus(ModelStatus.Loading)
        lifecycleScope.launch {
            try {
                translator.loadModel(downloader.getModelPath())
                onStatus(ModelStatus.Ready)
            } catch (e: Exception) {
                onStatus(ModelStatus.Error(e.message ?: "模型加载失败"))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        translator.destroy()
    }
}
