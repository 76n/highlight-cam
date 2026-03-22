package com.highlightcam.app.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.highlightcam.app.BuildConfig
import com.highlightcam.app.R
import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.domain.VideoQuality
import com.highlightcam.app.navigation.Routes
import com.highlightcam.app.ui.components.HCIconButton
import com.highlightcam.app.ui.theme.HC
import com.highlightcam.app.ui.theme.HCType
import com.highlightcam.app.ui.theme.Radii
import com.highlightcam.app.ui.theme.Spacing

@Suppress("LongMethod")
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val sensitivity by viewModel.sensitivity.collectAsState()
    val config by viewModel.recordingConfig.collectAsState()
    val goalZoneSet by viewModel.goalZoneSet.collectAsState()
    val debugMode by viewModel.debugMode.collectAsState()
    val soundOnSave by viewModel.soundOnSave.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(HC.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.s24),
        ) {
            item {
                Spacer(Modifier.height(Spacing.s24))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HCIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = { navController.popBackStack() })
                    Spacer(Modifier.width(Spacing.s16))
                    Text("Settings", style = HCType.heading, color = HC.white)
                }
                Spacer(Modifier.height(Spacing.s40))
            }

            item {
                val selectedIndex =
                    when {
                        sensitivity <= 0.3f -> 0
                        sensitivity <= 0.6f -> 1
                        else -> 2
                    }
                SegmentedControl(
                    options = listOf("Careful", "Balanced", "Aggressive"),
                    selectedIndex = selectedIndex,
                    onSelected = { idx ->
                        viewModel.updateSensitivity(
                            when (idx) {
                                0 -> SettingsViewModel.SENSITIVITY_CAREFUL
                                1 -> SettingsViewModel.SENSITIVITY_BALANCED
                                else -> SettingsViewModel.SENSITIVITY_AGGRESSIVE
                            },
                        )
                    },
                )
                Spacer(Modifier.height(Spacing.s8))
                Text(
                    when (selectedIndex) {
                        0 -> stringResource(R.string.settings_sensitivity_careful_desc)
                        1 -> stringResource(R.string.settings_sensitivity_balanced_desc)
                        else -> stringResource(R.string.settings_sensitivity_aggressive_desc)
                    },
                    style = HCType.body,
                    color = HC.white60,
                )
                Spacer(Modifier.height(Spacing.s40))
            }

            item {
                ClipSlider(
                    label = stringResource(R.string.settings_seconds_before),
                    value = config.totalBufferSeconds,
                    range = 5f..30f,
                    steps = 4,
                    onValueChange = { viewModel.updateSecondsBefore(it) },
                )
                Spacer(Modifier.height(Spacing.s12))
                ClipSlider(
                    label = stringResource(R.string.settings_seconds_after),
                    value = config.secondsAfterEvent,
                    range = 5f..30f,
                    steps = 4,
                    onValueChange = { viewModel.updateSecondsAfter(it) },
                )
                Spacer(Modifier.height(Spacing.s40))
            }

            item {
                val qualityIndex = if (config.videoQuality == VideoQuality.HD_720) 0 else 1
                SegmentedControl(
                    options = listOf("720p", "1080p"),
                    selectedIndex = qualityIndex,
                    onSelected = { idx ->
                        viewModel.updateQuality(if (idx == 0) VideoQuality.HD_720 else VideoQuality.FHD_1080)
                    },
                )
                Spacer(Modifier.height(Spacing.s40))
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController.navigate(Routes.SETUP) {
                                popUpTo(Routes.RECORDING) { inclusive = false }
                            }
                        }
                        .padding(vertical = Spacing.s12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TinyZoneCanvas(goalZoneSet?.goalA, HC.green)
                    Spacer(Modifier.width(Spacing.s8))
                    TinyZoneCanvas(goalZoneSet?.goalB, HC.blue)
                    Spacer(Modifier.weight(1f))
                    Text("Reconfigure", style = HCType.label, color = HC.green)
                    Spacer(Modifier.width(Spacing.s4))
                    androidx.compose.material3.Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = HC.green,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.height(Spacing.s40))
            }

            item {
                SettingSwitch(
                    label = stringResource(R.string.settings_debug_mode),
                    checked = debugMode,
                    onCheckedChange = { viewModel.updateDebugMode(it) },
                )
                Spacer(Modifier.height(Spacing.s12))
                SettingSwitch(
                    label = stringResource(R.string.settings_sound_on_save),
                    checked = soundOnSave,
                    onCheckedChange = viewModel::updateSoundOnSave,
                )
                Spacer(Modifier.height(Spacing.s40))
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showAboutDialog = true }
                        .padding(vertical = Spacing.s12),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_about), style = HCType.body, color = HC.white60)
                    Text("v${BuildConfig.VERSION_NAME}", style = HCType.micro, color = HC.white.copy(alpha = 0.4f))
                }
                Spacer(Modifier.height(Spacing.s48))
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("HighlightCam v${BuildConfig.VERSION_NAME}") },
            text = { Text(stringResource(R.string.settings_about_body)) },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(Radii.r12))
                .background(HC.surfaceRaised)
                .padding(Spacing.s4),
    ) {
        val segmentWidth = maxWidth / options.size
        val offsetX by animateDpAsState(
            segmentWidth * selectedIndex,
            spring(dampingRatio = 0.8f, stiffness = 300f),
            label = "seg_x",
        )

        Box(
            Modifier
                .offset(x = offsetX)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(HC.white10),
        )

        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelected(index) },
                    Alignment.Center,
                ) {
                    Text(
                        label,
                        style = if (index == selectedIndex) HCType.title else HCType.label,
                        color = if (index == selectedIndex) HC.white else HC.white.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Int) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, style = HCType.body, color = HC.white)
            Text("${value}s", style = HCType.label.copy(fontFamily = FontFamily.Monospace), color = HC.green)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range,
            steps = steps,
            colors =
                SliderDefaults.colors(
                    thumbColor = HC.white,
                    activeTrackColor = HC.green,
                    inactiveTrackColor = HC.white20,
                ),
            thumb = {
                Box(Modifier.size(20.dp).clip(CircleShape).background(HC.white))
            },
        )
    }
}

@Composable
private fun TinyZoneCanvas(
    zone: com.highlightcam.app.domain.GoalZone?,
    color: androidx.compose.ui.graphics.Color,
) {
    androidx.compose.foundation.Canvas(
        Modifier
            .size(32.dp, 20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(HC.surfaceRaised),
    ) {
        if (zone != null) {
            val pts = zone.toPoints()
            if (pts.size >= GoalZone.VERTEX_COUNT) {
                val path =
                    Path().apply {
                        moveTo(pts[0].x * size.width, pts[0].y * size.height)
                        for (i in 1 until pts.size) lineTo(pts[i].x * size.width, pts[i].y * size.height)
                        close()
                    }
                drawPath(path, color, style = Stroke(1.dp.toPx()))
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically,
    ) {
        Text(label, style = HCType.body, color = HC.white)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedTrackColor = HC.green,
                    checkedThumbColor = HC.white,
                    uncheckedTrackColor = HC.white20,
                    uncheckedThumbColor = HC.white,
                ),
        )
    }
}
