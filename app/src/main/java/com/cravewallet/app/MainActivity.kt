package com.cravewallet.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cravewallet.app.ui.CraveWalletApp
import com.cravewallet.app.ui.theme.CraveWalletTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private var deepLinkId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        deepLinkId = intent?.getStringExtra(EXTRA_SUBSCRIPTION_ID)
        setContent {
            CraveWalletTheme {
                CraveWalletApp(vm, openSubscriptionId = deepLinkId, onConsumedDeepLink = { deepLinkId = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_SUBSCRIPTION_ID)?.let { deepLinkId = it }
    }

    companion object {
        const val EXTRA_SUBSCRIPTION_ID = "subscription_id"
    }
}
