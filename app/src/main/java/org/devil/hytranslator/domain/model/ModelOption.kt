package org.devil.hytranslator.domain.model

data class ModelOption(
    val key: String,
    val name: String,
    val description: String,
    val filename: String,
    val sizeGb: Float,
    val memoryRequirementGb: Float,
)
