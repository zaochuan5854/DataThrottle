package com.datathrottle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
                DataThrottleApp(viewModel = viewModel)
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
