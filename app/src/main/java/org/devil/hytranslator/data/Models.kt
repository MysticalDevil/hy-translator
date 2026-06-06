package org.devil.hytranslator.data

import android.app.ActivityManager
import android.content.Context
import org.devil.hytranslator.domain.model.ModelOption

object ModelOptions {
    val all = listOf(
        ModelOption(
            key = "Q4_K_M",
            name = "Hy-MT2-1.8B Q4_K_M",
            description = "推荐 1.1GB，速度质量平衡",
            filename = "Hy-MT2-1.8B-Q4_K_M.gguf",
            sizeGb = 1.1f,
            memoryRequirementGb = 2.2f,
        ),
        ModelOption(
            key = "Q6_K",
            name = "Hy-MT2-1.8B Q6_K",
            description = "均衡 1.5GB，质量良好",
            filename = "Hy-MT2-1.8B-Q6_K.gguf",
            sizeGb = 1.5f,
            memoryRequirementGb = 2.8f,
        ),
        ModelOption(
            key = "Q8_0",
            name = "Hy-MT2-1.8B Q8_0",
            description = "最大 1.9GB，质量最佳",
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
