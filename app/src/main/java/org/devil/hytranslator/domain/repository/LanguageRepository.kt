package org.devil.hytranslator.domain.repository

import org.devil.hytranslator.domain.model.Language

interface LanguageRepository {
    fun allLanguages(): List<Language>
    fun sourceLanguages(): List<Language>
    fun targetLanguages(): List<Language>
    fun isSourceOnly(code: String): Boolean
}
