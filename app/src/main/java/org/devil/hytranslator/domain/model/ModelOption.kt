package org.devil.hytranslator.domain.model

data class ModelOption(
    val key: String,
    val nameResId: Int,
    val descResId: Int,
    val filename: String,
    val sizeGb: Float,
    val memoryRequirementGb: Float,
)
