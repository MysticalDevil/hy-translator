package org.devil.hytranslator.data

import org.devil.hytranslator.domain.model.Language

object Languages {
    val all = listOf(
        Language("auto", "自动检测", "Auto"),
        Language("zh", "简体中文", "Simplified Chinese"),
        Language("zh-Hant", "繁体中文", "Traditional Chinese"),
        Language("en", "英语", "English"),
        Language("ja", "日语", "Japanese"),
        Language("ar", "阿拉伯语", "Arabic"),
        Language("ru", "俄语", "Russian"),
        Language("pt", "葡萄牙语", "Portuguese"),
        Language("de", "德语", "German"),
        Language("ko", "韩语", "Korean"),
    )

    fun isSourceOnly(code: String): Boolean = code == "auto"

    fun sourceLanguages(): List<Language> = all

    fun targetLanguages(): List<Language> = all.filter { it.code != "auto" }
}
