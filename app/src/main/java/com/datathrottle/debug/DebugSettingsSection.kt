package com.datathrottle.debug

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datathrottle.R
import com.datathrottle.core.DebugFeatures
import com.datathrottle.ui.settings.components.SettingsItemRow

/**
 * The Wi-Fi-as-cellular switch. Visibility follows the runtime feature switch
 * ([DebugFeatures]) rather than the build variant: release hides it by default
 * and tapping the version row in the settings screen reveals it (and hides it
 * again in debug builds).
 */
@Composable
fun DebugSettingsSection() {
    val featuresEnabled by DebugFeatures.enabled.collectAsStateWithLifecycle()
    if (!featuresEnabled) return
    val forceCellular by DebugFlags.forceCellular.collectAsStateWithLifecycle()

    SettingsItemRow(
        icon = Icons.Default.BugReport,
        iconTint = MaterialTheme.colorScheme.tertiary,
        title = stringResource(R.string.debug_force_cellular_title),
        subtitle = stringResource(R.string.debug_force_cellular_desc),
        trailing = {
            Switch(
                checked = forceCellular,
                onCheckedChange = { DebugFlags.setForceCellular(it) }
            )
        },
        onClick = { DebugFlags.setForceCellular(!forceCellular) }
    )
}
