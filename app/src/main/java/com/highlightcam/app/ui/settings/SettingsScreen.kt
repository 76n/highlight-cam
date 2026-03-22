package com.highlightcam.app.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.highlightcam.app.BuildConfig
import com.highlightcam.app.R
import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.domain.GoalZoneSet
import com.highlightcam.app.domain.VideoQuality
import com.highlightcam.app.navigation.Routes
import kotlinx.coroutines.launch

private val GreenColor = Color(0xFF00FF88)

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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF080808),
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("←", color = Color.White, fontSize = 20.sp)
                    }
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                SectionHeader(stringResource(R.string.settings_section_detection))
                Spacer(modifier = Modifier.height(12.dp))

                val selectedIndex =
                    when {
                        sensitivity <= 0.3f -> 0
                        sensitivity <= 0.6f -> 1
                        else -> 2
                    }

                SegmentedControl(
                    options =
                        listOf(
                            stringResource(R.string.settings_sensitivity_careful),
                            stringResource(R.string.settings_sensitivity_balanced),
                            stringResource(R.string.settings_sensitivity_aggressive),
                        ),
                    selectedIndex = selectedIndex,
                    onSelected = { idx ->
                        val value =
                            when (idx) {
                                0 -> SettingsViewModel.SENSITIVITY_CAREFUL
                                1 -> SettingsViewModel.SENSITIVITY_BALANCED
                                else -> SettingsViewModel.SENSITIVITY_AGGRESSIVE
                            }
                        viewModel.updateSensitivity(value)
                    },
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    when (selectedIndex) {
                        0 -> stringResource(R.string.settings_sensitivity_careful_desc)
                        1 -> stringResource(R.string.settings_sensitivity_balanced_desc)
                        else -> stringResource(R.string.settings_sensitivity_aggressive_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                SectionHeader(stringResource(R.string.settings_section_clip_timing))
                Spacer(modifier = Modifier.height(12.dp))

                LabeledSlider(
                    label = stringResource(R.string.settings_seconds_before),
                    value = config.totalBufferSeconds,
                    range = 5f..30f,
                    steps = 4,
                    onValueChange = { viewModel.updateSecondsBefore(it) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                LabeledSlider(
                    label = stringResource(R.string.settings_seconds_after),
                    value = config.secondsAfterEvent,
                    range = 5f..30f,
                    steps = 4,
                    onValueChange = { viewModel.updateSecondsAfter(it) },
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                SectionHeader(stringResource(R.string.settings_section_video))
                Spacer(modifier = Modifier.height(12.dp))

                val qualityIndex = if (config.videoQuality == VideoQuality.HD_720) 0 else 1
                SegmentedControl(
                    options =
                        listOf(
                            stringResource(R.string.settings_quality_720),
                            stringResource(R.string.settings_quality_1080),
                        ),
                    selectedIndex = qualityIndex,
                    onSelected = { idx ->
                        viewModel.updateQuality(if (idx == 0) VideoQuality.HD_720 else VideoQuality.FHD_1080)
                    },
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                SectionHeader(stringResource(R.string.settings_section_goal_zone))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GoalZoneSetPreview(zoneSet = goalZoneSet, modifier = Modifier.width(120.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            navController.navigate(Routes.SETUP) {
                                popUpTo(Routes.RECORDING) { inclusive = false }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.settings_reconfigure), color = GreenColor)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                SectionHeader(stringResource(R.string.settings_section_app))
                Spacer(modifier = Modifier.height(12.dp))

                SwitchRow(
                    label = stringResource(R.string.settings_debug_mode),
                    checked = debugMode,
                    onCheckedChange = {
                        viewModel.updateDebugMode(it)
                        if (it) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Debug panel accessible via long-press on record button",
                                )
                            }
                        }
                    },
                )
                SwitchRow(
                    label = stringResource(R.string.settings_sound_on_save),
                    checked = soundOnSave,
                    onCheckedChange = viewModel::updateSoundOnSave,
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { showAboutDialog = true }
                            .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_about), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("HighlightCam v${BuildConfig.VERSION_NAME}") },
            text = { Text(stringResource(R.string.settings_about_body)) },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style =
            MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        color = Color.White.copy(alpha = 0.4f),
    )
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
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A1A)),
    ) {
        val segmentWidth = maxWidth / options.size
        val offsetX by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = tween(200, easing = FastOutSlowInEasing),
            label = "segment_offset",
        )

        Box(
            modifier =
                Modifier
                    .offset(x = offsetX)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(GreenColor),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelected(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (index == selectedIndex) Color(0xFF080808) else Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Int) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${value}s",
                color = GreenColor,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range,
            steps = steps,
            colors =
                SliderDefaults.colors(
                    thumbColor = GreenColor,
                    activeTrackColor = GreenColor,
                    inactiveTrackColor = Color(0xFF2C2C2C),
                ),
        )
    }
}

private val GoalBColor = Color(0xFF4FC3F7)

@Composable
private fun GoalZoneSetPreview(
    zoneSet: GoalZoneSet?,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(
        modifier =
            modifier
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A1A)),
    ) {
        fun drawZone(
            zone: GoalZone,
            color: Color,
        ) {
            val pts = zone.toPoints()
            if (pts.size < GoalZone.VERTEX_COUNT) return
            val path =
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(pts[0].x * size.width, pts[0].y * size.height)
                    for (i in 1 until pts.size) lineTo(pts[i].x * size.width, pts[i].y * size.height)
                    close()
                }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        }
        if (zoneSet != null) {
            drawZone(zoneSet.goalA, GreenColor)
            drawZone(zoneSet.goalB, GoalBColor)
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedTrackColor = GreenColor,
                    checkedThumbColor = Color.White,
                ),
        )
    }
}
