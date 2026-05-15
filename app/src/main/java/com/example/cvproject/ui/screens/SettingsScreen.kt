package com.example.cvproject.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cvproject.ui.theme.F1Colors
import com.example.cvproject.viewmodel.SettingsViewModel
import java.util.Locale
import kotlin.math.round

private const val ENTRY_TRIPWIRE_PRESET = 0.5f
private const val EXIT_TRIPWIRE_PRESET = 0.8f

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
     
    val sceneWidthRange = 1f..500f
    val sceneWidthSteps = ((sceneWidthRange.endInclusive - sceneWidthRange.start) / 1f).toInt() - 1
    val sceneWidth = uiState.sceneWidthMeters.coerceIn(sceneWidthRange.start, sceneWidthRange.endInclusive)
     
    val topBarExtraPadding = 8.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
             
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + topBarExtraPadding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                text = "Settings",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Dark Mode",
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = uiState.themeSelection != "light",
                    onCheckedChange = viewModel::setThemeSelection,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = F1Colors.TextPrimary,
                        checkedTrackColor = F1Colors.Red,
                    ),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Use MPH instead of KM/H",
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = uiState.isMph,
                    onCheckedChange = viewModel::setIsMph,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = F1Colors.TextPrimary,
                        checkedTrackColor = F1Colors.Red,
                    ),
                )
            }

            Text(
                text = "Speed Limit Warning",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    modifier = Modifier.weight(1f),
                    value = uiState.speedWarningThreshold.toFloat(),
                    onValueChange = { viewModel.setSpeedWarningThreshold(it.toInt()) },
                    valueRange = 50f..300f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = F1Colors.Red,
                        activeTickColor = F1Colors.Red,
                        thumbColor = F1Colors.Red,
                    ),
                )
                Text(
                    text = "${uiState.speedWarningThreshold}",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setSceneWidthMeters(10f) },
                ) { Text("City (10m)") }
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setSceneWidthMeters(50f) },
                ) { Text("Highway (50m)") }
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setSceneWidthMeters(150f) },
                ) { Text("Long Hwy (150m)") }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tripwire Distance",
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Slider(
                    modifier = Modifier.weight(1f),
                    value = sceneWidth,
                    onValueChange = { value ->
                        val snapped = round(value)
                        viewModel.setSceneWidthMeters(snapped.coerceIn(sceneWidthRange.start, sceneWidthRange.endInclusive))
                    },
                    valueRange = sceneWidthRange,
                    steps = sceneWidthSteps,
                    colors = SliderDefaults.colors(
                        activeTrackColor = F1Colors.Red,
                        activeTickColor = F1Colors.Red,
                        thumbColor = F1Colors.Red,
                    ),
                )
                Text(
                    text = String.format(Locale.US, "%.0fm", sceneWidth),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Max Concurrent Cars",
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Slider(
                    modifier = Modifier.weight(1f),
                    value = uiState.maxConcurrentVehicles.toFloat(),
                    onValueChange = { value ->
                        viewModel.setMaxConcurrentVehicles(value.toInt())
                    },
                    valueRange = 1f..50f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Text(
                    text = "${uiState.maxConcurrentVehicles}",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            //TAG->ENTRY_TRIPWIRE_FRACTION — adjustable in Settings
            Text(
                text = "Entry Tripwire position",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    modifier = Modifier.weight(1f),
                    value = uiState.entryTripwireFraction,
                    onValueChange = viewModel::setEntryTripwireFraction,
                    valueRange = 0.1f..0.9f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = F1Colors.Red,
                        activeTickColor = F1Colors.Red,
                        thumbColor = F1Colors.Red,
                    ),
                )
                Text(
                    text = String.format(Locale.US, "%.2f", uiState.entryTripwireFraction),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            //TAG->EXIT_TRIPWIRE_FRACTION — adjustable in Settings
            Text(
                text = "Exit Tripwire position",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    modifier = Modifier.weight(1f),
                    value = uiState.exitTripwireFraction,
                    onValueChange = viewModel::setExitTripwireFraction,
                    valueRange = 0.1f..0.95f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = F1Colors.Red,
                        activeTickColor = F1Colors.Red,
                        thumbColor = F1Colors.Red,
                    ),
                )
                Text(
                    text = String.format(Locale.US, "%.2f", uiState.exitTripwireFraction),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.setEntryTripwireFraction(ENTRY_TRIPWIRE_PRESET)
                        viewModel.setExitTripwireFraction(EXIT_TRIPWIRE_PRESET)
                    },
                ) { Text("Reset to Default") }
            }
        }
    }
}
