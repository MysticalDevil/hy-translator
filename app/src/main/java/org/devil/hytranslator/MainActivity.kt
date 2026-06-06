package org.devil.hytranslator

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.devil.hytranslator.service.NotificationDestination
import org.devil.hytranslator.service.NotificationNavigation
import org.devil.hytranslator.theme.MyApplicationTheme
import org.devil.hytranslator.ui.TranslatorRoute

class MainActivity : ComponentActivity() {
    private var notificationDestination by mutableStateOf<NotificationDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationDestination = intent.notificationDestination()

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TranslatorRoute(
                        notificationDestination = notificationDestination,
                        onNotificationDestinationConsumed = {
                            notificationDestination = null
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationDestination = intent.notificationDestination()
    }

    private fun Intent.notificationDestination(): NotificationDestination? =
        NotificationNavigation.destination(
            target = getStringExtra(NotificationNavigation.EXTRA_TARGET),
            modelKey = getStringExtra(NotificationNavigation.EXTRA_MODEL_KEY),
            aiAssetName = getStringExtra(NotificationNavigation.EXTRA_AI_ASSET),
        )
}
