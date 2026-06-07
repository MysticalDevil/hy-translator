package org.devil.hytranslator

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.service.NotificationNavigation
import org.devil.hytranslator.ui.TranslatorTestTags
import org.junit.Rule
import org.junit.Test

class MainActivityNotificationIntentTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun notificationIntent_highlightsTargetAiAsset() {
        launchMainActivity(
            Intent().apply {
                putExtra(
                    NotificationNavigation.EXTRA_TARGET,
                    NotificationNavigation.TARGET_AI_ASSET_DOWNLOAD,
                )
                putExtra(NotificationNavigation.EXTRA_AI_ASSET, AiAsset.OcrPpOcrV5Mobile.name)
            },
        ).use {
            composeRule.onNodeWithTag(TranslatorTestTags.OcrAssetHighlighted)
                .assertIsDisplayed()
        }
    }

    @Test
    fun notificationIntent_opensModelPickerForModelDownloadTarget() {
        launchMainActivity(
            Intent().apply {
                putExtra(
                    NotificationNavigation.EXTRA_TARGET,
                    NotificationNavigation.TARGET_MODEL_DOWNLOAD,
                )
                putExtra(NotificationNavigation.EXTRA_MODEL_KEY, "Q4_K_M")
            },
        ).use {
            composeRule.onNodeWithText(
                context.getString(R.string.model_select_title),
            ).assertIsDisplayed()
        }
    }

    private fun launchMainActivity(intent: Intent): ActivityScenario<MainActivity> =
        ActivityScenario.launch(
            intent.setClassName(
                context.packageName,
                MainActivity::class.java.name,
            ),
        )
}
