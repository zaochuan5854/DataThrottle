package com.datathrottle.ui.test.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.datathrottle.R
import com.datathrottle.core.TestState

@Composable
fun TestReportDialog(
    state: TestState,
    onDismiss: () -> Unit
) {
    val verified = state.isThrottlingVerified == true
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (verified) Icons.Default.CheckCircle else Icons.Default.ReportProblem,
                contentDescription = null,
                tint = if (verified) Color(0xFF00E676) else Color(0xFFEF4444),
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.test_report_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        if (verified) R.string.test_report_status_verified
                        else R.string.test_report_status_failed
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (verified) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                HorizontalDivider(modifier = Modifier.alpha(0.2f))
                Text(
                    text = stringResource(R.string.test_report_speed, state.averageSpeedKbps),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (state.targetKbps > 0f) {
                    Text(
                        text = stringResource(R.string.test_report_target, state.targetKbps),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!verified) {
                    Text(
                        text = stringResource(
                            if (state.shapingExemptTransport) R.string.test_report_transport_exempt
                            else R.string.test_report_failed_hint
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        }
    )
}
