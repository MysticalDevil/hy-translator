package org.devil.hytranslator.data.repository

import org.devil.hytranslator.data.Languages
import org.devil.hytranslator.domain.model.Language
import org.devil.hytranslator.domain.repository.LanguageRepository

class LanguageRepositoryImpl : LanguageRepository {
    override fun allLanguages(): List<Language> = Languages.all

    override fun sourceLanguages(): List<Language> = Languages.sourceLanguages()

    override fun targetLanguages(): List<Language> = Languages.targetLanguages()

    override fun isSourceOnly(code: String): Boolean = Languages.isSourceOnly(code)
}
