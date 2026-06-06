package org.devil.hytranslator.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OcrWorkflowControllerTest {
    @Test
    fun showAndHide_emitExpectedStates() = runTest {
        val states = mutableListOf<OcrFlow>()
        val controller = controller(states)

        controller.showSourcePicker()
        controller.showCamera()
        controller.hide()

        assertEquals(
            listOf(
                OcrFlow.SourcePicker,
                OcrFlow.CameraActive,
                OcrFlow.Hidden,
            ),
            states,
        )
    }

    @Test
    fun processRecognition_whenRecognitionSucceeds_emitsProcessingThenResult() = runTest {
        val states = mutableListOf<OcrFlow>()
        val controller = controller(states)

        controller.processRecognition(failedMessage = "OCR failed") {
            "recognized text"
        }
        advanceUntilIdle()

        assertEquals(OcrFlow.Processing, states.first())
        assertEquals(OcrFlow.Result("recognized text"), states.last())
    }

    @Test
    fun processRecognition_whenRecognitionFails_emitsProcessingThenError() = runTest {
        val states = mutableListOf<OcrFlow>()
        val controller = controller(states)

        controller.processRecognition(failedMessage = "OCR failed") {
            error("decode failed")
        }
        advanceUntilIdle()

        assertEquals(OcrFlow.Processing, states.first())
        assertEquals(OcrFlow.Error("decode failed"), states.last())
    }

    private fun TestScope.controller(
        states: MutableList<OcrFlow>,
    ): OcrWorkflowController =
        OcrWorkflowController(
            scope = this,
            recognizeBitmap = { "text" },
            recognizeUri = { _, _ -> "text" },
            updateOcrFlow = states::add,
        )
}
