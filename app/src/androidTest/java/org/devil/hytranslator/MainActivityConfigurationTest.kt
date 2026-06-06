package org.devil.hytranslator

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import org.devil.hytranslator.ui.TranslatorTestTags
import org.junit.Rule
import org.junit.Test

class MainActivityConfigurationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun inputText_survivesActivityRecreation() {
        composeRule.onNodeWithTag(TranslatorTestTags.TranslationInput)
            .performTextInput("hello after recreate")

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(TranslatorTestTags.TranslationInput)
            .assertIsDisplayed()
        composeRule.onNodeWithText("hello after recreate")
            .assertIsDisplayed()
    }
}
