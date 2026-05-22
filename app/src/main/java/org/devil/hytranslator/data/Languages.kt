package org.devil.hytranslator.data

import org.devil.hytranslator.R

object Languages {
    val all = listOf(
        Language("auto", R.string.lang_auto_detect),
        Language("zh", "中文", "Chinese"),
        Language("en", "英语", "English"),
        Language("fr", "法语", "French"),
        Language("pt", "葡萄牙语", "Portuguese"),
        Language("es", "西班牙语", "Spanish"),
        Language("ja", "日语", "Japanese"),
        Language("tr", "土耳其语", "Turkish"),
        Language("ru", "俄语", "Russian"),
        Language("ar", "阿拉伯语", "Arabic"),
        Language("ko", "韩语", "Korean"),
        Language("th", "泰语", "Thai"),
        Language("it", "意大利语", "Italian"),
        Language("de", "德语", "German"),
        Language("vi", "越南语", "Vietnamese"),
        Language("ms", "马来语", "Malay"),
        Language("id", "印尼语", "Indonesian"),
        Language("tl", "菲律宾语", "Filipino"),
        Language("hi", "印地语", "Hindi"),
        Language("zh-Hant", "繁体中文", "Traditional Chinese"),
        Language("pl", "波兰语", "Polish"),
        Language("cs", "捷克语", "Czech"),
        Language("nl", "荷兰语", "Dutch"),
        Language("km", "高棉语", "Khmer"),
        Language("my", "缅甸语", "Burmese"),
        Language("fa", "波斯语", "Persian"),
        Language("gu", "古吉拉特语", "Gujarati"),
        Language("ur", "乌尔都语", "Urdu"),
        Language("te", "泰卢固语", "Telugu"),
        Language("mr", "马拉地语", "Marathi"),
        Language("he", "希伯来语", "Hebrew"),
        Language("bn", "孟加拉语", "Bengali"),
        Language("ta", "泰米尔语", "Tamil"),
        Language("uk", "乌克兰语", "Ukrainian"),
    )

    fun isSourceOnly(code: String): Boolean = code == "auto"

    fun sourceLanguages(): List<Language> = all

    fun targetLanguages(): List<Language> = all.filter { it.code != "auto" }
}

data class Language(
    val code: String,
    val name: String,
    val englishName: String = name,
    val nameResId: Int? = null,
) {
    constructor(code: String, nameResId: Int) : this(code, "", "", nameResId)
}
