package com.datathrottle.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datathrottle.BuildConfig
import com.datathrottle.R
import com.datathrottle.data.AppTheme
import com.datathrottle.ui.settings.components.LanguageSelectionDialog
import com.datathrottle.ui.settings.components.SettingsItemRow
import com.datathrottle.ui.settings.components.ThemeSelectionDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    hasSecureSettings: Boolean,
    hasNotification: Boolean,
    isIgnoringBattery: Boolean,
    appTheme: AppTheme,
    onBack: () -> Unit,
    onOpenTest: () -> Unit,
    onRecheckPermission: () -> Unit,
    onShowResetDialog: () -> Unit,
    onSelectTheme: (AppTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val localeManager = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(Context.LOCALE_SERVICE) as? LocaleManager
        } else null
    }

    val currentLocaleTag = remember(localeManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = localeManager?.applicationLocales
            if (locales == null || locales.isEmpty) "" else locales[0]?.toLanguageTag() ?: ""
        } else ""
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLocaleTag = currentLocaleTag,
            onSelectLanguage = { tag ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (tag.isEmpty()) {
                        localeManager?.applicationLocales = LocaleList.getEmptyLocaleList()
                    } else {
                        localeManager?.applicationLocales = LocaleList.forLanguageTags(tag)
                    }
                }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = appTheme,
            onSelectTheme = onSelectTheme,
            onDismiss = { showThemeDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_settings),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_close),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // 1. 100 kbps テスト
            SettingsItemRow(
                icon = Icons.Default.Speed,
                iconTint = Color(0xFFF6821F),
                title = stringResource(R.string.settings_item_test_title),
                subtitle = stringResource(R.string.settings_item_test_desc),
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                onClick = onOpenTest
            )

            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )

            // 2. システム設定権限
            val allPermsGranted = hasSecureSettings && hasNotification && isIgnoringBattery
            SettingsItemRow(
                icon = Icons.Default.Security,
                iconTint = if (allPermsGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                title = stringResource(R.string.settings_item_permission_title),
                subtitle = if (allPermsGranted) stringResource(R.string.settings_item_permission_granted) else stringResource(R.string.settings_item_permission_missing),
                trailing = {
                    if (allPermsGranted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Granted",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Missing",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                onClick = onRecheckPermission
            )

            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )

            // 3. 言語設定 (Language)
            val currentLanguageLabel = when {
                currentLocaleTag.startsWith("ja") -> "日本語"
                currentLocaleTag.startsWith("zh") -> "中文"
                currentLocaleTag.startsWith("en") -> "English"
                else -> stringResource(R.string.language_system_default)
            }

            SettingsItemRow(
                icon = Icons.Default.Language,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.settings_item_language),
                subtitle = currentLanguageLabel,
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                onClick = { showLanguageDialog = true }
            )

            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )

            // 4. テーマ設定 (Theme)
            val currentThemeLabel = when (appTheme) {
                AppTheme.SYSTEM -> stringResource(R.string.theme_system_default)
                AppTheme.LIGHT -> stringResource(R.string.theme_light)
                AppTheme.DARK -> stringResource(R.string.theme_dark)
            }

            SettingsItemRow(
                icon = Icons.Default.DarkMode,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.settings_item_theme),
                subtitle = currentThemeLabel,
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                onClick = { showThemeDialog = true }
            )

            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )

            // 5. 設定リセット・初期化
            SettingsItemRow(
                icon = Icons.Default.DeleteOutline,
                iconTint = MaterialTheme.colorScheme.error,
                title = stringResource(R.string.settings_item_reset_title),
                subtitle = stringResource(R.string.settings_item_reset_desc),
                titleColor = MaterialTheme.colorScheme.error,
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                },
                onClick = onShowResetDialog
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 詳細 セクションヘッダー
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_section_details),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 法的事項（バージョン情報）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_item_legal),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${BuildConfig.VERSION_NAME} (${stringResource(R.string.app_name)})",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // リポジトリリンク（将来用）
            /*
            SettingsItemRow(
                icon = Icons.Default.Code,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.settings_item_repository),
                subtitle = "https://github.com/...",
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                onClick = { uriHandler.openUri("https://github.com/...") }
            )
            */

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        }
    }
}
