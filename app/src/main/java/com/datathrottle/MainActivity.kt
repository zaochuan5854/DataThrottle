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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.datathrottle.data.AppTheme
import com.datathrottle.navigation.MainRoute
import com.datathrottle.ui.MainScreen
import com.datathrottle.ui.MainViewModel
import com.datathrottle.ui.loading.LoadingScreen
import com.datathrottle.ui.theme.DataThrottleTheme

class MainActivity : ComponentActivity() {
    private var mainViewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    modifier = Modifier.fillMaxSize(),
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
