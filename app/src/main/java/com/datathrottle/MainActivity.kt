package com.datathrottle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.datathrottle.data.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.datathrottle.navigation.MainRoute
import com.datathrottle.ui.MainScreen
import com.datathrottle.ui.MainViewModel
import com.datathrottle.ui.loading.LoadingScreen
import com.datathrottle.ui.theme.DataThrottleTheme

class MainActivity : ComponentActivity() {
    private var mainViewModel: MainViewModel? = null

    // Debug-only spoof focus policy (see onCreate).
    private val spoofScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate
    )
    private var spoofDisableJob: kotlinx.coroutines.Job? = null

    private companion object {
        /** How long the debug cellular spoof survives focus loss before it is dropped. */
        const val SPOOF_GRACE_MS = 5 * 60 * 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            com.datathrottle.debug.DebugFlags.init(this)
            android.util.Log.d("MainActivity", "focus observer registering")
            // Focus policy (S2-17 follow-up): the cellular spoof must never keep
            // running indefinitely in the background, but disabling it the instant
            // focus is lost made short background trips (notification shade, quick
            // settings) silently revert the network type to Wi-Fi mid-verification.
            // Grace period: the flag survives SPOOF_GRACE_MS after focus loss and
            // is dropped afterwards; returning to the app cancels the pending drop.
            lifecycle.addObserver(
                androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                            if (com.datathrottle.debug.DebugFlags.forceCellular.value) {
                                spoofDisableJob?.cancel()
                                spoofDisableJob = spoofScope.launch {
                                    delay(SPOOF_GRACE_MS)
                                    if (com.datathrottle.debug.DebugFlags.forceCellular.value) {
                                        com.datathrottle.debug.DebugFlags.setForceCellular(false)
                                        android.util.Log.d(
                                            "MainActivity",
                                            "Focus lost for ${SPOOF_GRACE_MS / 60000} min: cellular spoof disabled"
                                        )
                                    }
                                }
                            }
                        }
                        androidx.lifecycle.Lifecycle.Event.ON_START -> {
                            if (spoofDisableJob?.isActive == true) {
                                spoofDisableJob?.cancel()
                                android.util.Log.d("MainActivity", "Focus regained: pending spoof disable cancelled")
                            }
                            spoofDisableJob = null
                        }
                        androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> {
                            spoofDisableJob?.cancel()
                            spoofDisableJob = null
                        }
                        else -> {}
                    }
                }
            )
        }
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            mainViewModel = viewModel
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            val darkTheme = when (appTheme) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }

            DataThrottleTheme(darkTheme = darkTheme) {
                Surface(
                    // Expose Compose testTags as uiautomator resource-ids for ADB automation
                    modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
                    color = MaterialTheme.colorScheme.background
                ) {
                    DataThrottleApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel?.refreshState()
    }
}

@Composable
fun DataThrottleApp(viewModel: MainViewModel) {
    var isLoading by remember { mutableStateOf(true) }

    Crossfade(
        targetState = isLoading,
        label = "AppStartupTransition"
    ) { loading ->
        if (loading) {
            LoadingScreen(
                onLoadingComplete = { isLoading = false }
            )
        } else {
            val backStack = rememberNavBackStack(MainRoute)
            NavDisplay<NavKey>(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                entryProvider = { key ->
                    when (key) {
                        is MainRoute -> NavEntry(key) {
                            MainScreen(viewModel = viewModel)
                        }
                        else -> error("Unknown route: $key")
                    }
                }
            )
        }
    }
}
