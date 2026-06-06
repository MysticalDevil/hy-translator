package org.devil.hytranslator

import android.app.Application
import org.devil.hytranslator.di.AppContainer
import org.devil.hytranslator.di.DefaultAppContainer

class HyTranslatorApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
    }
}
