package org.devil.hytranslator.domain.model

data class Language(
    val code: String,
    val name: String,
    val englishName: String = name,
)
