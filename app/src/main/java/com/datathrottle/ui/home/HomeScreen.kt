package com.datathrottle.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datathrottle.R
import com.datathrottle.ui.MainUiState
import com.datathrottle.ui.home.components.DrumrollBandwidthPicker
import com.datathrottle.ui.home.components.GiantToggleSwitch
import com.datathrottle.ui.home.components.Obsidian
import com.datathrottle.ui.home.components.ObsidianAmbientBackdrop
import com.datathrottle.ui.home.components.ObsidianGlassCard
import com.datathrottle.ui.home.components.windowOffsetOf
import com.datathrottle.ui.home.components.StatusDescriptionView

/**
 * Home: the "obsidian" field (black + two slow blue glows from the lower-left)
 * with the controls inside a glass card that blurs the wave passing behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: MainUiState,
    onOpenSettings: () -> Unit,
    onToggleService: (Boolean) -> Unit,
    onUpdateLimit: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var backdropPosition by remember { mutableStateOf(IntOffset.Zero) }
    var cardPosition by remember { mutableStateOf(IntOffset.Zero) }

    MaterialTheme(colorScheme = Obsidian.colorScheme) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Obsidian.Base)
        ) {
            ObsidianAmbientBackdrop(
                modifier = Modifier.fillMaxSize(),
                onWindowPosition = { backdropPosition = it }
            )

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
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ObsidianGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { cardPosition = windowOffsetOf(it.positionInWindow()) },
                            fieldAlignment = cardPosition - backdropPosition
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = Color(0xFFF6821F),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "DataThrottle",
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontWeight = FontWeight.Black,
                                            fontSize = 34.sp,
                                            letterSpacing = (-1).sp,
                                            lineHeight = 38.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.hero_subtitle),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp,
                                            letterSpacing = (-0.3).sp,
                                            lineHeight = 26.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            DrumrollBandwidthPicker(
                                currentLimit = uiState.bandwidthLimitMbps,
                                onUpdateLimit = onUpdateLimit,
                                isReady = uiState.settingsLoaded
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            GiantToggleSwitch(
                                isRunning = uiState.isServiceRunning,
                                onToggle = onToggleService
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            StatusDescriptionView(
                                isRunning = uiState.isServiceRunning,
                                networkType = uiState.networkType,
                                limitMbps = uiState.bandwidthLimitMbps
                            )
                        }
                    }
                }
            }
        }
    }
}
