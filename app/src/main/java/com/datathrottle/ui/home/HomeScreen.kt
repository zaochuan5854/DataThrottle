package com.datathrottle.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datathrottle.R
import com.datathrottle.ui.MainUiState
import com.datathrottle.ui.home.components.DrumrollBandwidthPicker
import com.datathrottle.ui.home.components.GiantToggleSwitch
import com.datathrottle.ui.home.components.StatusDescriptionView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: MainUiState,
    onOpenSettings: () -> Unit,
    onToggleService: (Boolean) -> Unit,
    onUpdateLimit: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
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
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = Color(0xFFF6821F),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "DataThrottle",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 40.sp,
                                letterSpacing = (-1).sp,
                                lineHeight = 42.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.hero_subtitle),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 30.sp,
                                letterSpacing = (-0.3).sp,
                                lineHeight = 24.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                DrumrollBandwidthPicker(
                    currentLimit = uiState.bandwidthLimitMbps,
                    onUpdateLimit = onUpdateLimit
                )

                Spacer(modifier = Modifier.width(8.dp))

                GiantToggleSwitch(
                    isRunning = uiState.isServiceRunning,
                    onToggle = onToggleService
                )

                Spacer(modifier = Modifier.width(8.dp))

                StatusDescriptionView(
                    isRunning = uiState.isServiceRunning,
                    networkType = uiState.networkType,
                    limitMbps = uiState.bandwidthLimitMbps
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
