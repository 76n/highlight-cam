package com.highlightcam.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.highlightcam.app.domain.GoalZoneSet
import com.highlightcam.app.domain.RecordingConfig
import com.highlightcam.app.domain.VideoQuality
import com.highlightcam.app.navigation.Routes
import com.highlightcam.app.ui.components.HCIconButton
import com.highlightcam.app.ui.components.LocalWindowSizeClass
import com.highlightcam.app.ui.theme.HC
import com.highlightcam.app.ui.theme.HCType
import com.highlightcam.app.ui.theme.Radii
import com.highlightcam.app.ui.theme.Spacing

private enum class SettingsSection(val label: String) {
    DETECTION("Detection"),
    CLIP_TIMING("Clip Timing"),
    VIDEO("Video"),
    GOAL_ZONES("Goal Zones"),
    APP("App"),
}

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

    val windowSizeClass = LocalWindowSizeClass.current
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    val onReconfigure: () -> Unit = {
        navController.navigate(Routes.SETUP) {
            popUpTo(Routes.RECORDING) { inclusive = false }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(HC.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(Spacing.s24))
            Row(
                Modifier.padding(horizontal = Spacing.s24),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HCIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = { navController.popBackStack() })
                Spacer(Modifier.width(Spacing.s16))
                Text("Settings", style = HCType.heading, color = HC.white)
            }

            if (isCompact) {
                CompactSettings(
                    sensitivity = sensitivity,
                    config = config,
                    goalZoneSet = goalZoneSet,
                    debugMode = debugMode,
                    soundOnSave = soundOnSave,
                    onUpdateSensitivity = viewModel::updateSensitivity,
                    onUpdateSecondsBefore = { viewModel.updateSecondsBefore(it) },
                    onUpdateSecondsAfter = { viewModel.updateSecondsAfter(it) },
                    onUpdateQuality = viewModel::updateQuality,
                    onReconfigure = onReconfigure,
                    onUpdateDebugMode = viewModel::updateDebugMode,
                    onUpdateSoundOnSave = viewModel::updateSoundOnSave,
                    onAboutClick = { showAboutDialog = true },
                )
            } else {
                ExpandedSettings(
                    sensitivity = sensitivity,
                    config = config,
                    goalZoneSet = goalZoneSet,
                    debugMode = debugMode,
                    soundOnSave = soundOnSave,
                    onUpdateSensitivity = viewModel::updateSensitivity,
                    onUpdateSecondsBefore = { viewModel.updateSecondsBefore(it) },
                    onUpdateSecondsAfter = { viewModel.updateSecondsAfter(it) },
                    onUpdateQuality = viewModel::updateQuality,
                    onReconfigure = onReconfigure,
                    onUpdateDebugMode = viewModel::updateDebugMode,
                    onUpdateSoundOnSave = viewModel::updateSoundOnSave,
                    onAboutClick = { showAboutDialog = true },
                )
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

@Suppress("LongParameterList")
@Composable
private fun CompactSettings(
    sensitivity: Float,
    config: RecordingConfig,
    goalZoneSet: GoalZoneSet?,
    debugMode: Boolean,
    soundOnSave: Boolean,
    onUpdateSensitivity: (Float) -> Unit,
    onUpdateSecondsBefore: (Int) -> Unit,
    onUpdateSecondsAfter: (Int) -> Unit,
    onUpdateQuality: (VideoQuality) -> Unit,
    onReconfigure: () -> Unit,
    onUpdateDebugMode: (Boolean) -> Unit,
    onUpdateSoundOnSave: (Boolean) -> Unit,
    onAboutClick: () -> Unit,
) {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.s24),
        contentPadding = PaddingValues(top = Spacing.s40, bottom = navBarBottom + Spacing.s24),
    ) {
        item {
            DetectionContent(sensitivity, onUpdateSensitivity)
            Spacer(Modifier.height(Spacing.s40))
        }
        item {
            ClipTimingContent(config, onUpdateSecondsBefore, onUpdateSecondsAfter)
            Spacer(Modifier.height(Spacing.s40))
        }
        item {
            VideoContent(config, onUpdateQuality)
            Spacer(Modifier.height(Spacing.s40))
        }
        item {
            GoalZonesContent(goalZoneSet, onReconfigure)
            Spacer(Modifier.height(Spacing.s40))
        }
        item {
            AppContent(debugMode, soundOnSave, onUpdateDebugMode, onUpdateSoundOnSave, onAboutClick)
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun ExpandedSettings(
    sensitivity: Float,
    config: RecordingConfig,
    goalZoneSet: GoalZoneSet?,
    debugMode: Boolean,
    soundOnSave: Boolean,
    onUpdateSensitivity: (Float) -> Unit,
    onUpdateSecondsBefore: (Int) -> Unit,
    onUpdateSecondsAfter: (Int) -> Unit,
    onUpdateQuality: (VideoQuality) -> Unit,
    onReconfigure: () -> Unit,
    onUpdateDebugMode: (Boolean) -> Unit,
    onUpdateSoundOnSave: (Boolean) -> Unit,
    onAboutClick: () -> Unit,
) {
    var selectedSection by remember { mutableStateOf(SettingsSection.DETECTION) }
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(200.dp)
                .fillMaxHeight()
                .padding(start = Spacing.s24, top = Spacing.s24),
        ) {
            SettingsSection.entries.forEach { section ->
                SectionLabel(
                    label = section.label,
                    selected = selectedSection == section,
                    onClick = { selectedSection = section },
                )
            }
        }

        Box(
            Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.s24)
                    .padding(top = Spacing.s24, bottom = navBarBottom + Spacing.s24),
            ) {
                when (selectedSection) {
                    SettingsSection.DETECTION -> DetectionContent(sensitivity, onUpdateSensitivity)
                    SettingsSection.CLIP_TIMING -> ClipTimingContent(config, onUpdateSecondsBefore, onUpdateSecondsAfter)
                    SettingsSection.VIDEO -> VideoContent(config, onUpdateQuality)
                    SettingsSection.GOAL_ZONES -> GoalZonesContent(goalZoneSet, onReconfigure)
                    SettingsSection.APP -> AppContent(debugMode, soundOnSave, onUpdateDebugMode, onUpdateSoundOnSave, onAboutClick)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        if (selected) HC.white10 else Color.Transparent,
        tween(200),
        label = "section_bg",
    )
    val textColor by animateColorAsState(
        if (selected) HC.white else HC.white60,
        tween(200),
        label = "section_text",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.s16, horizontal = Spacing.s12),
    ) {
        Text(label, style = HCType.label, color = textColor)
    }
}

@Composable
private fun DetectionContent(
    sensitivity: Float,
    onUpdateSensitivity: (Float) -> Unit,
) {
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
            onUpdateSensitivity(
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
}

@Composable
private fun ClipTimingContent(
    config: RecordingConfig,
    onUpdateSecondsBefore: (Int) -> Unit,
    onUpdateSecondsAfter: (Int) -> Unit,
) {
    ClipSlider(
        label = stringResource(R.string.settings_seconds_before),
        value = config.totalBufferSeconds,
        range = 5f..30f,
        steps = 4,
        onValueChange = onUpdateSecondsBefore,
    )
    Spacer(Modifier.height(Spacing.s12))
    ClipSlider(
        label = stringResource(R.string.settings_seconds_after),
        value = config.secondsAfterEvent,
        range = 5f..30f,
        steps = 4,
        onValueChange = onUpdateSecondsAfter,
    )
}

@Composable
private fun VideoContent(
    config: RecordingConfig,
    onUpdateQuality: (VideoQuality) -> Unit,
) {
    val qualityIndex = if (config.videoQuality == VideoQuality.HD_720) 0 else 1
    SegmentedControl(
        options = listOf("720p", "1080p"),
        selectedIndex = qualityIndex,
        onSelected = { idx ->
            onUpdateQuality(if (idx == 0) VideoQuality.HD_720 else VideoQuality.FHD_1080)
        },
    )
}

@Composable
private fun GoalZonesContent(
    goalZoneSet: GoalZoneSet?,
    onReconfigure: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onReconfigure)
            .padding(vertical = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TinyZoneCanvas(goalZoneSet?.goalA, HC.green)
        Spacer(Modifier.width(Spacing.s8))
        TinyZoneCanvas(goalZoneSet?.goalB, HC.blue)
        Spacer(Modifier.weight(1f))
        Text("Reconfigure", style = HCType.label, color = HC.green)
        Spacer(Modifier.width(Spacing.s4))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = HC.green,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AppContent(
    debugMode: Boolean,
    soundOnSave: Boolean,
    onUpdateDebugMode: (Boolean) -> Unit,
    onUpdateSoundOnSave: (Boolean) -> Unit,
    onAboutClick: () -> Unit,
) {
    SettingSwitch(
        label = stringResource(R.string.settings_debug_mode),
        checked = debugMode,
        onCheckedChange = onUpdateDebugMode,
    )
    Spacer(Modifier.height(Spacing.s12))
    SettingSwitch(
        label = stringResource(R.string.settings_sound_on_save),
        checked = soundOnSave,
        onCheckedChange = onUpdateSoundOnSave,
    )
    Spacer(Modifier.height(Spacing.s40))
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onAboutClick)
            .padding(vertical = Spacing.s12),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_about), style = HCType.body, color = HC.white60)
        Text("v${BuildConfig.VERSION_NAME}", style = HCType.micro, color = HC.white.copy(alpha = 0.4f))
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
    zone: GoalZone?,
    color: Color,
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
