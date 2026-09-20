package io.github.paulneja.archdroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import io.github.paulneja.archdroid.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val requestedRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedRoute.value = intent.getStringExtra(EXTRA_ROUTE)
        enableEdgeToEdge()
        setContent {
            AppRoot(
                requestedRoute = requestedRoute.value,
                onRouteConsumed = { requestedRoute.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedRoute.value = intent.getStringExtra(EXTRA_ROUTE)
    }

    companion object {
        const val EXTRA_ROUTE = "io.github.paulneja.archdroid.ROUTE"
    }
}
