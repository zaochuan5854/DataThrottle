package com.datathrottle.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datathrottle.R
import com.datathrottle.core.NetworkType

@Composable
fun StatusDescriptionView(
    isRunning: Boolean,
    networkType: NetworkType,
    limitMbps: Float,
    modifier: Modifier = Modifier
) {
    val formattedLimit = if (limitMbps < 1.0f) String.format("%.1f Mbps", limitMbps) else if (limitMbps % 1.0f == 0f) String.format("%.0f Mbps", limitMbps) else String.format("%.1f Mbps", limitMbps)

    val statusText: String
    val descText: String
    val statusColor: Color
    val descColor: Color

    if (!isRunning) {
        statusText = stringResource(R.string.status_unlimited_disabled)
        descText = stringResource(R.string.status_desc_disabled)
        statusColor = MaterialTheme.colorScheme.onSurface
        descColor = MaterialTheme.colorScheme.onSurface
    } else {
        descColor = MaterialTheme.colorScheme.onSurfaceVariant
        when (networkType) {
            NetworkType.CELLULAR -> {
                statusText = stringResource(R.string.status_limited_to, formattedLimit)
                descText = stringResource(R.string.status_desc_cellular, formattedLimit)
                statusColor = Color(0xFF2563EB)
            }
            NetworkType.WIFI -> {
                statusText = stringResource(R.string.status_unlimited_wifi)
                descText = stringResource(R.string.status_desc_wifi)
                statusColor = Color(0xFF0288D1)
            }
            else -> {
                statusText = stringResource(R.string.status_unlimited)
                descText = stringResource(R.string.status_desc_disabled)
                statusColor = MaterialTheme.colorScheme.primary
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            color = statusColor,
            textAlign = TextAlign.Center
        )
        Text(
            text = descText,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 22.sp
            ),
            color = descColor,
            textAlign = TextAlign.Center
        )
    }
}
