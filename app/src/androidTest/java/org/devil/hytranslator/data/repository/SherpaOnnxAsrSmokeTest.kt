package org.devil.hytranslator.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.devil.hytranslator.test.R
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class SherpaOnnxAsrSmokeTest {
    @Test
    fun streamingZipformer_transcribesStandardSmokeWav() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelDir = File(
            context.filesDir,
            "ai-assets/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20",
        )
        assumeTrue(
            "ASR model assets are not downloaded on device",
            REQUIRED_MODEL_FILES.all { File(modelDir, it).isFile },
        )

        val wav = downloadSmokeWav(context.cacheDir)
        val audio = Pcm16WavReader.read(wav)
        val text = SherpaOnnxStreamingFileTranscriber().transcribe(
            assetPath = modelDir.absolutePath,
            samples = audio.samples,
            sampleRate = audio.sampleRate,
        )

        assertTrue("Expected non-empty ASR text for ${wav.name}", text.isNotBlank())
    }

    private fun downloadSmokeWav(cacheDir: File): File {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val dir = File(cacheDir, "asr-smoke-audio").apply { mkdirs() }
        val target = File(dir, "sherpa-bilingual-0.wav")
        if (!target.isFile) {
            val url = instrumentationContext.getString(R.string.url_asr_smoke_wav_0)
            URL(url).openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target
    }

    private companion object {
        val REQUIRED_MODEL_FILES = listOf(
            "encoder-epoch-99-avg-1.onnx",
            "decoder-epoch-99-avg-1.onnx",
            "joiner-epoch-99-avg-1.onnx",
            "tokens.txt",
        )
    }
}

private data class Pcm16Audio(
    val sampleRate: Int,
    val samples: FloatArray,
)

private object Pcm16WavReader {
    fun read(file: File): Pcm16Audio {
        val bytes = file.readBytes()
        require(bytes.size >= 44) {
            "WAV file is too short: ${file.name}"
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") {
            "WAV file is missing RIFF header: ${file.name}"
        }
        require(String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") {
            "WAV file is missing WAVE header: ${file.name}"
        }

        var offset = 12
        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = buffer.getInt(offset + 4)
            val chunkDataOffset = offset + 8
            when (chunkId) {
                "fmt " -> {
                    val audioFormat = buffer.getShort(chunkDataOffset).toInt()
                    channels = buffer.getShort(chunkDataOffset + 2).toInt()
                    sampleRate = buffer.getInt(chunkDataOffset + 4)
                    bitsPerSample = buffer.getShort(chunkDataOffset + 14).toInt()
                    require(audioFormat == 1) {
                        "Only PCM WAV is supported: ${file.name}"
                    }
                }

                "data" -> {
                    dataOffset = chunkDataOffset
                    dataSize = chunkSize
                }
            }
            offset = chunkDataOffset + chunkSize + (chunkSize % 2)
        }

        require(sampleRate > 0 && channels == 1 && bitsPerSample == 16) {
            "Expected 16-bit mono PCM WAV: ${file.name}"
        }
        require(dataOffset >= 0 && dataOffset + dataSize <= bytes.size) {
            "WAV file is missing valid data chunk: ${file.name}"
        }

        val sampleCount = dataSize / 2
        val samples = FloatArray(sampleCount)
        for (index in 0 until sampleCount) {
            samples[index] = buffer.getShort(dataOffset + index * 2) / 32768.0f
        }
        return Pcm16Audio(sampleRate = sampleRate, samples = samples)
    }
}
