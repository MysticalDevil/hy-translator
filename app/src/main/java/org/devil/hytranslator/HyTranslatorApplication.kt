package org.devil.hytranslator

import android.app.Application
import org.devil.hytranslator.data.ModelOptions
import org.devil.hytranslator.data.repository.BundledAiAssetInstaller
import org.devil.hytranslator.data.repository.BundledModelInstaller
import org.devil.hytranslator.di.AppContainer
import org.devil.hytranslator.di.DefaultAppContainer
import kotlin.concurrent.thread

class HyTranslatorApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        thread(start = true, name = "bundled-ai-assets") {
            BundledAiAssetInstaller(this).installIfPresent()
            BundledModelInstaller(this).installIfPresent(ModelOptions.recommend(this))
        }
        appContainer = DefaultAppContainer(this)
    }
}
