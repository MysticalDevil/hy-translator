package org.devil.hytranslator.data

import android.app.ActivityManager
import android.content.Context
import org.devil.hytranslator.R

data class ModelOption(
    val key: String,
    val nameResId: Int,
    val descResId: Int,
    val filename: String,
    val sizeGb: Float,
    val memoryRequirementGb: Float,
) {
}

object ModelOptions {
    val all = listOf(
        ModelOption(
            key = "Q2_K",
            nameResId = R.string.model_q2_k_name,
            descResId = R.string.model_q2_k_desc,
            filename = "Hy-MT2-1.8B-Q2_K.gguf",
            sizeGb = 0.7f,
            memoryRequirementGb = 1.4f,
        ),
        ModelOption(
            key = "Q3_K_M",
            nameResId = R.string.model_q3_k_m_name,
            descResId = R.string.model_q3_k_m_desc,
            filename = "Hy-MT2-1.8B-Q3_K_M.gguf",
            sizeGb = 0.8f,
            memoryRequirementGb = 1.6f,
        ),
        ModelOption(
            key = "Q4_K_M",
            nameResId = R.string.model_q4_k_m_name,
            descResId = R.string.model_q4_k_m_desc,
            filename = "Hy-MT2-1.8B-Q4_K_M.gguf",
            sizeGb = 1.1f,
            memoryRequirementGb = 2.2f,
        ),
        ModelOption(
            key = "Q5_K_M",
            nameResId = R.string.model_q5_k_m_name,
            descResId = R.string.model_q5_k_m_desc,
            filename = "Hy-MT2-1.8B-Q5_K_M.gguf",
            sizeGb = 1.3f,
            memoryRequirementGb = 2.6f,
        ),
        ModelOption(
            key = "Q8_0",
            nameResId = R.string.model_q8_0_name,
            descResId = R.string.model_q8_0_desc,
            filename = "Hy-MT2-1.8B-Q8_0.gguf",
            sizeGb = 1.9f,
            memoryRequirementGb = 3.8f,
        ),
    )

    fun getByKey(key: String): ModelOption = all.first { it.key == key }

    fun recommend(context: Context): ModelOption {
        val totalRamGb = getTotalRamGb(context)
        val availableGb = totalRamGb * 0.7f
        return all.reversed().firstOrNull { it.memoryRequirementGb <= availableGb }
            ?: all.first()
    }

    private fun getTotalRamGb(context: Context): Float {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.totalMem / (1024f * 1024f * 1024f)
    }
}

object ModelDownloader {
    const val HF_BASE_URL = "https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF/resolve/main/"
}
