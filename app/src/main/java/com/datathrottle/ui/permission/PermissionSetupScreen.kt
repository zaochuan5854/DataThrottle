package com.datathrottle.ui.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datathrottle.R
import com.datathrottle.core.ShizukuManager
import com.datathrottle.core.ShizukuStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSetupScreen(
    shizukuStatus: ShizukuStatus,
    hasSecureSettings: Boolean,
    hasNotification: Boolean,
    isIgnoringBattery: Boolean,
    onRequestShizukuPermission: () -> Unit,
    onGrantViaShizuku: () -> Unit,
    onRecheckPermission: () -> Unit,
    onProceed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val adbCmd = stringResource(R.string.adb_command)
    val copiedText = stringResource(R.string.command_copied)

    // Notification permission launcher for Android 13+
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRecheckPermission()
    }

    // Battery optimization launcher
    val batteryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRecheckPermission()
    }

    var selectedSecureSettingsTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Banner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.permission_setup_title),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.permission_setup_subtitle),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 1. 通信速度の制御 (WRITE_SECURE_SETTINGS)
                PermissionCard(
                    icon = Icons.Default.Speed,
                    title = stringResource(R.string.perm_secure_settings_title),
                    description = stringResource(R.string.perm_secure_settings_desc),
                    isGranted = hasSecureSettings
                ) {
                    if (!hasSecureSettings) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            PrimaryTabRow(
                                selectedTabIndex = selectedSecureSettingsTab,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Tab(
                                    selected = selectedSecureSettingsTab == 0,
                                    onClick = { selectedSecureSettingsTab = 0 },
                                    text = {
                                        Text(
                                            text = stringResource(R.string.tab_shizuku),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                )
                                Tab(
                                    selected = selectedSecureSettingsTab == 1,
                                    onClick = { selectedSecureSettingsTab = 1 },
                                    text = {
                                        Text(
                                            text = stringResource(R.string.tab_adb),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (selectedSecureSettingsTab == 0) {
                                // Shizuku Route
                                when (shizukuStatus) {
                                    ShizukuStatus.RUNNING -> {
                                        Button(
                                            onClick = onGrantViaShizuku,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.shizuku_btn_grant))
                                        }
                                    }
                                    ShizukuStatus.UNAUTHORIZED -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = onRequestShizukuPermission,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(stringResource(R.string.shizuku_btn_authorize))
                                            }
                                            OutlinedButton(
                                                onClick = { openShizukuApp(context, uriHandler) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(stringResource(R.string.shizuku_btn_open_app))
                                            }
                                        }
                                    }
                                    ShizukuStatus.NOT_RUNNING -> {
                                        Button(
                                            onClick = { openShizukuApp(context, uriHandler) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Launch, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.shizuku_btn_open_app))
                                        }
                                    }
                                    ShizukuStatus.NOT_INSTALLED -> {
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ShizukuManager.SHIZUKU_PACKAGE_NAME}")).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    uriHandler.openUri("https://shizuku.rikka.app/download/")
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.shizuku_btn_install))
                                        }
                                    }
                                }
                            } else {
                                // ADB Route (Engineer / Developer)
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = stringResource(R.string.adb_guide_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = adbCmd,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(adbCmd))
                                                Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.btn_copy_command))
                                        }
                                        OutlinedButton(
                                            onClick = onRecheckPermission,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.btn_check_permission))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. 状態通知の表示 (POST_NOTIFICATIONS)
                PermissionCard(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.perm_notification_title),
                    description = stringResource(R.string.perm_notification_desc),
                    isGranted = hasNotification
                ) {
                    if (!hasNotification) {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_grant_notification))
                        }
                    }
                }

                // 3. バックグラウンド動作の維持 (IGNORE_BATTERY_OPTIMIZATIONS)
                PermissionCard(
                    icon = Icons.Default.BatteryChargingFull,
                    title = stringResource(R.string.perm_battery_title),
                    description = stringResource(R.string.perm_battery_desc),
                    isGranted = isIgnoringBattery
                ) {
                    if (!isIgnoringBattery) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    batteryLauncher.launch(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (e2: Exception) {
                                        // Ignore
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_grant_battery))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Proceed Button (If all 3 permissions are granted)
                val allGranted = hasSecureSettings && hasNotification && isIgnoringBattery
                AnimatedVisibility(visible = allGranted) {
                    Button(
                        onClick = onProceed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_start_app),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isGranted) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isGranted) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = CircleShape,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge
                Surface(
                    color = if (isGranted) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (isGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isGranted) stringResource(R.string.perm_status_granted) else stringResource(R.string.perm_status_missing),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            content()
        }
    }
}

private fun openShizukuApp(context: Context, uriHandler: androidx.compose.ui.platform.UriHandler) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE_NAME)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            uriHandler.openUri("https://shizuku.rikka.app/")
        }
    } catch (e: Exception) {
        uriHandler.openUri("https://shizuku.rikka.app/")
    }
}
