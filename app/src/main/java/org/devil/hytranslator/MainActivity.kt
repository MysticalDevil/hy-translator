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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.devil.hytranslator.theme.MyApplicationTheme
import org.devil.hytranslator.ui.ModelPickerDialog
import org.devil.hytranslator.ui.TranslatorScreen
import org.devil.hytranslator.ui.TranslatorViewModel

class MainActivity : ComponentActivity() {

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
        val viewModel: TranslatorViewModel = viewModel()
        var showModelPicker by remember { mutableStateOf(false) }
        var lastTranslateTime by remember { mutableLongStateOf(0L) }

        val inputText by viewModel.inputText.collectAsStateWithLifecycle()
        val outputText by viewModel.outputText.collectAsStateWithLifecycle()
        val sourceLang by viewModel.sourceLang.collectAsStateWithLifecycle()
        val targetLang by viewModel.targetLang.collectAsStateWithLifecycle()
        val isTranslating by viewModel.isTranslating.collectAsStateWithLifecycle()
        val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
        val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
        val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()

        if (showModelPicker) {
            ModelPickerDialog(
                currentModel = selectedModel,
                onSelect = { model ->
                    viewModel.onSelectModel(model)
                    showModelPicker = false
                },
                onDismiss = { showModelPicker = false },
            )
        }

        TranslatorScreen(
            inputText = inputText,
            onInputTextChange = viewModel::onInputTextChange,
            outputText = outputText,
            sourceLang = sourceLang,
            onSourceLangChange = viewModel::onSourceLangChange,
            targetLang = targetLang,
            onTargetLangChange = viewModel::onTargetLangChange,
            onTranslate = {
                val now = System.currentTimeMillis()
                if (now - lastTranslateTime > 500) {
                    lastTranslateTime = now
                    viewModel.onTranslate()
                }
            },
            onCancel = viewModel::onCancel,
            isTranslating = isTranslating,
            modelStatus = modelStatus,
            downloadProgress = downloadProgress,
            selectedModel = selectedModel,
            onSwitchModel = { showModelPicker = true },
            onDownload = viewModel::onDownload,
            modifier = Modifier.systemBarsPadding(),
        )
    }
}
