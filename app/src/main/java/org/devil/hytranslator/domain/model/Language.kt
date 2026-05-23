package org.devil.hytranslator.domain.model

data class Language(
    val code: String,
    val name: String,
    val englishName: String = name,
    val nameResId: Int? = null,
) {
    constructor(code: String, nameResId: Int) : this(code, "", "", nameResId)
}
