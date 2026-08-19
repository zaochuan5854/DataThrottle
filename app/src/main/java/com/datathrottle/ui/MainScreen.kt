package com.datathrottle.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.datathrottle.R
import com.datathrottle.core.NetworkType
import com.datathrottle.core.ShizukuStatus
import com.datathrottle.core.TestState
import com.datathrottle.core.TestStatus
import com.datathrottle.data.AppTheme
import com.datathrottle.ui.home.HomeScreen
import com.datathrottle.ui.permission.PermissionSetupScreen
import com.datathrottle.ui.settings.SettingsScreen
import com.datathrottle.ui.test.TestScreen
import com.datathrottle.ui.test.components.TestErrorDialog
import com.datathrottle.ui.test.components.TestReportDialog
import com.datathrottle.ui.theme.DataThrottleTheme

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()
    val showTestReport by viewModel.showTestReport.collectAsStateWithLifecycle()
    val showTestError by viewModel.showTestError.collectAsStateWithLifecycle()
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("MainScreen", "Notification permission: $isGranted")
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshState()
        onPauseOrDispose {}
    }

    MainContent(
        uiState = uiState,
        testState = testState,
        appTheme = appTheme,
        showTestReport = showTestReport,
        showTestError = showTestError,
        onToggleService = viewModel::toggleService,
        onUpdateLimit = viewModel::updateBandwidthLimit,
        onStartTest = viewModel::start100KbpsTest,
        onCancelTest = viewModel::cancel100KbpsTest,
        onDismissReport = viewModel::dismissTestReport,
        onDismissError = viewModel::dismissTestError,
        onSafetyReset = viewModel::safetyReset,
        onRequestShizukuPermission = viewModel::requestShizukuPermission,
        onGrantViaShizuku = viewModel::grantViaShizuku,
        onRecheckPermission = viewModel::refreshState,
        onSelectTheme = viewModel::setAppTheme,
        modifier = modifier
    )
}

enum class Screen {
    HOME,
    SETTINGS,
    TEST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    uiState: MainUiState,
    testState: TestState,
    appTheme: AppTheme = AppTheme.SYSTEM,
    showTestReport: Boolean,
    showTestError: Boolean,
    onToggleService: (Boolean) -> Unit,
    onUpdateLimit: (Float) -> Unit,
    onStartTest: () -> Unit,
    onCancelTest: () -> Unit,
    onDismissReport: () -> Unit,
    onDismissError: () -> Unit,
    onSafetyReset: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onGrantViaShizuku: () -> Unit,
    onRecheckPermission: () -> Unit,
    onSelectTheme: (AppTheme) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val isMediumOrLarger = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var showResetDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = currentScreen != Screen.HOME) {
        currentScreen = if (currentScreen == Screen.TEST) Screen.SETTINGS else Screen.HOME
    }

    if (showTestReport) {
        TestReportDialog(
            state = testState,
            onDismiss = onDismissReport
        )
    }

    if (showTestError) {
        TestErrorDialog(
            errorMessage = testState.errorMessage,
            onDismiss = onDismissError
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.reset_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.reset_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        onSafetyReset()
                        currentScreen = Screen.HOME
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_confirm_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (!uiState.hasAllPermissions) {
        PermissionSetupScreen(
            shizukuStatus = uiState.shizukuStatus,
            hasSecureSettings = uiState.hasSecureSettingsPermission,
            hasNotification = uiState.hasNotificationPermission,
            isIgnoringBattery = uiState.isIgnoringBatteryOptimizations,
            onRequestShizukuPermission = onRequestShizukuPermission,
            onGrantViaShizuku = onGrantViaShizuku,
            onRecheckPermission = onRecheckPermission,
            onProceed = { /* Automatically transitions via hasAllPermissions */ },
            modifier = modifier
        )
    } else {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                val isForward = (initialState == Screen.HOME && targetState == Screen.SETTINGS) ||
                                (initialState == Screen.HOME && targetState == Screen.TEST) ||
                                (initialState == Screen.SETTINGS && targetState == Screen.TEST)
                if (isForward) {
                    slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it } togetherWith
                        slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { -it }
                } else {
                    slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { -it } togetherWith
                        slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it }
                }
            },
            label = "ScreenTransition",
            modifier = modifier.fillMaxSize()
        ) { targetScreen ->
            when (targetScreen) {
                Screen.HOME -> {
                    HomeScreen(
                        uiState = uiState,
                        onOpenSettings = { currentScreen = Screen.SETTINGS },
                        onToggleService = onToggleService,
                        onUpdateLimit = onUpdateLimit
                    )
                }
                Screen.SETTINGS -> {
                    SettingsScreen(
                        hasSecureSettings = uiState.hasSecureSettingsPermission,
                        hasNotification = uiState.hasNotificationPermission,
                        isIgnoringBattery = uiState.isIgnoringBatteryOptimizations,
                        appTheme = appTheme,
                        onBack = { currentScreen = Screen.HOME },
                        onOpenTest = { currentScreen = Screen.TEST },
                        onRecheckPermission = onRecheckPermission,
                        onShowResetDialog = { showResetDialog = true },
                        onSelectTheme = onSelectTheme
                    )
                }
                Screen.TEST -> {
                    TestScreen(
                        state = testState,
                        onBack = { currentScreen = Screen.SETTINGS },
                        onStartTest = onStartTest,
                        onCancelTest = onCancelTest,
                        isMediumOrLarger = isMediumOrLarger
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun MainScreenPreview() {
    DataThrottleTheme {
        MainContent(
            uiState = MainUiState(
                networkType = NetworkType.CELLULAR,
                isServiceRunning = true,
                hasSecureSettingsPermission = true,
                hasNotificationPermission = true,
                isIgnoringBatteryOptimizations = true,
                bandwidthLimitMbps = 1.2f,
                shizukuStatus = ShizukuStatus.RUNNING
            ),
            testState = TestState(
                status = TestStatus.RUNNING,
                bytesRead = 8500L,
                totalBytes = 17400L,
                progress = 0.5f,
                elapsedTimeMs = 1700L,
                currentSpeedKbps = 100.0f
            ),
            showTestReport = false,
            showTestError = false,
            onToggleService = {},
            onUpdateLimit = {},
            onStartTest = {},
            onCancelTest = {},
            onDismissReport = {},
            onDismissError = {},
            onSafetyReset = {},
            onRequestShizukuPermission = {},
            onGrantViaShizuku = {},
            onRecheckPermission = {}
        )
    }
}
