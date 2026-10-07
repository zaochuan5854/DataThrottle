package com.datathrottle.debug

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datathrottle.R
import com.datathrottle.ui.settings.components.SettingsItemRow

/**
 * Debug build implementation of [DebugSettingsSection]: the Wi-Fi-as-cellular
 * switch. Present only in debug variants; release renders the empty stub.
 */
@Composable
fun DebugSettingsSection() {
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
