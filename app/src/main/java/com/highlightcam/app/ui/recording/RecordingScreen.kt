@file:Suppress("DEPRECATION")

package com.highlightcam.app.ui.recording

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.MediaActionSound
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.highlightcam.app.R
import com.highlightcam.app.camera.CameraPreviewManager
import com.highlightcam.app.detection.DebugInfo
import com.highlightcam.app.domain.GoalZoneSet
import com.highlightcam.app.domain.RecorderState
import com.highlightcam.app.navigation.Routes
import com.highlightcam.app.ui.components.FloatingChip
import com.highlightcam.app.ui.components.HCIconButton
import com.highlightcam.app.ui.components.OverlayState
import com.highlightcam.app.ui.components.PolygonOverlay
import com.highlightcam.app.ui.components.PrimaryButton
import com.highlightcam.app.ui.components.ZoneDisplay
import com.highlightcam.app.ui.setup.CameraEntryPoint
import com.highlightcam.app.ui.theme.HC
import com.highlightcam.app.ui.theme.HCType
import com.highlightcam.app.ui.theme.Radii
import com.highlightcam.app.ui.theme.Spacing
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

private enum class ChipMode { READY, RECORDING, LOW_STORAGE }

@Suppress("LongMethod")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RecordingScreen(
    navController: NavController,
    viewModel: RecordingViewModel = hiltViewModel(),
) {
    val recorderState by viewModel.recorderState.collectAsState()
    val clipsSaved by viewModel.clipsSaved.collectAsState()
    val goalZoneSet by viewModel.goalZoneSet.collectAsState()
    val candidateDetected by viewModel.candidateDetected.collectAsState()
    val candidateGoalZoneId by viewModel.candidateGoalZoneId.collectAsState()
    val modelAvailable by viewModel.modelAvailable.collectAsState()
    val debugMode by viewModel.debugModeEnabled.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    val soundOnSave by viewModel.soundOnSave.collectAsState()
    val lowStorage by viewModel.lowStorageWarning.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDebugPanel by remember { mutableStateOf(false) }
    var showVisionDialog by remember { mutableStateOf(false) }
    var showLowStorageDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val view = LocalView.current
    val cameraPreviewManager =
        remember {
            EntryPointAccessors
                .fromApplication(context.applicationContext, CameraEntryPoint::class.java)
                .cameraPreviewManager()
        }

    val permissionsState = rememberMultiplePermissionsState(listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    val allGranted = permissionsState.permissions.all { it.status.isGranted }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(clipsSaved) {
        if (clipsSaved > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            if (soundOnSave) {
                try {
                    MediaActionSound().play(MediaActionSound.START_VIDEO_RECORDING)
                } catch (_: Exception) {
                }
            }
        }
    }

    LaunchedEffect(candidateDetected) {
        if (candidateDetected) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    LaunchedEffect(recorderState) {
        if (recorderState is RecorderState.Error) {
            snackbarHostState.showSnackbar((recorderState as RecorderState.Error).message)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (allGranted) {
            RecordingContent(
                recorderState = recorderState,
                clipsSaved = clipsSaved,
                goalZoneSet = goalZoneSet,
                candidateDetected = candidateDetected,
                candidateGoalZoneId = candidateGoalZoneId,
                modelAvailable = modelAvailable,
                debugMode = debugMode,
                lowStorage = lowStorage,
                cameraPreviewManager = cameraPreviewManager,
                onStartRecording = viewModel::startRecording,
                onStopRecording = viewModel::stopRecording,
                onManualSave = viewModel::requestManualSave,
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToLibrary = { navController.navigate(Routes.LIBRARY) },
                onShowVisionDialog = { showVisionDialog = true },
                onShowDebugPanel = { showDebugPanel = true },
                onShowLowStorageDialog = { showLowStorageDialog = true },
            )
        } else {
            PermissionRequestScreen(onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() })
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showVisionDialog) VisionOffDialog(onDismiss = { showVisionDialog = false })
    if (showLowStorageDialog) LowStorageDialog(onDismiss = { showLowStorageDialog = false })
    if (showDebugPanel && debugMode) DebugPanel(debugInfo = debugInfo, onDismiss = { showDebugPanel = false })
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun RecordingContent(
    recorderState: RecorderState,
    clipsSaved: Int,
    goalZoneSet: GoalZoneSet?,
    candidateDetected: Boolean,
    candidateGoalZoneId: String?,
    modelAvailable: Boolean,
    debugMode: Boolean,
    lowStorage: Boolean,
    cameraPreviewManager: CameraPreviewManager,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onManualSave: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onShowVisionDialog: () -> Unit,
    onShowDebugPanel: () -> Unit,
    onShowLowStorageDialog: () -> Unit,
) {
    val isRecording = recorderState is RecorderState.Recording || recorderState is RecorderState.SavingClip
    val isSaving = recorderState is RecorderState.SavingClip
    val startedAt = (recorderState as? RecorderState.Recording)?.startedAt ?: 0L
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            cameraPreviewManager.currentSurfaceProvider?.let {
                cameraPreviewManager.bindToLifecycle(lifecycleOwner, it)
            }
        }
    }

    val chipMode =
        when {
            lowStorage -> ChipMode.LOW_STORAGE
            isRecording -> ChipMode.RECORDING
            else -> ChipMode.READY
        }

    fun zoneState(zoneId: String): OverlayState =
        when {
            isSaving && candidateGoalZoneId == zoneId -> OverlayState.SAVE
            candidateDetected && candidateGoalZoneId == zoneId -> OverlayState.CANDIDATE
            else -> OverlayState.IDLE
        }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(cameraPreviewManager)

        if (isRecording && goalZoneSet != null) {
            val zoneColors = mapOf("a" to HC.green, "b" to HC.blue)
            PolygonOverlay(
                zones =
                    goalZoneSet.activeZones.map { zone ->
                        ZoneDisplay(zone, zoneColors[zone.id] ?: HC.green, zoneState(zone.id))
                    },
            )
        }

        AnimatedVisibility(
            visible = isSaving,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = HC.green,
                trackColor = Color.Transparent,
            )
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = Spacing.s20, top = Spacing.s20),
        ) {
            StatusChip(
                mode = chipMode,
                modifier = if (lowStorage) Modifier.clickable(onClick = onShowLowStorageDialog) else Modifier,
            )
            AnimatedVisibility(
                visible = isRecording,
                enter = fadeIn() + slideInVertically { -it / 3 },
                exit = fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(Spacing.s8))
                    Timecode(startedAt)
                }
            }
            AnimatedVisibility(
                visible = !modelAvailable && isRecording,
                enter = fadeIn() + slideInVertically { -it / 3 },
                exit = fadeOut(),
            ) {
                FloatingChip(
                    dotColor = HC.amber,
                    modifier = Modifier.padding(top = Spacing.s8).clickable(onClick = onShowVisionDialog),
                ) {
                    Text("AUDIO ONLY", style = HCType.label, color = HC.amber)
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(end = Spacing.s20, top = Spacing.s20),
            verticalArrangement = Arrangement.spacedBy(Spacing.s8),
        ) {
            HCIconButton(Icons.Filled.Settings, onClick = onNavigateToSettings)
            HCIconButton(Icons.Filled.GridView, onClick = onNavigateToLibrary)
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = Spacing.s32),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(44.dp + Spacing.s24))
                RecordButton(
                    isRecording = isRecording,
                    onClick = { if (isRecording) onStopRecording() else onStartRecording() },
                    onLongClick = if (debugMode) onShowDebugPanel else null,
                )
                Spacer(Modifier.width(Spacing.s24))
                Box(Modifier.size(44.dp)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isRecording,
                        enter = fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it },
                    ) {
                        HCIconButton(Icons.Filled.BookmarkBorder, onClick = onManualSave)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.s12))
            SavedCount(count = clipsSaved)
        }
    }
}

@Composable
private fun CameraPreview(cameraPreviewManager: CameraPreviewManager) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also { pv ->
                pv.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                cameraPreviewManager.setSurfaceProvider(pv.surfaceProvider)
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { pv -> cameraPreviewManager.setSurfaceProvider(pv.surfaceProvider) },
    )
    LaunchedEffect(lifecycleOwner) {
        cameraPreviewManager.currentSurfaceProvider?.let { cameraPreviewManager.bindToLifecycle(lifecycleOwner, it) }
    }
}

@Composable
private fun StatusChip(
    mode: ChipMode,
    modifier: Modifier = Modifier,
) {
    val dotPulse = rememberInfiniteTransition(label = "dot")
    val dotAlpha by dotPulse.animateFloat(
        1f,
        0.25f,
        infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "dot_a",
    )

    FloatingChip(modifier = modifier) {
        Crossfade(targetState = mode, animationSpec = tween(200), label = "chip_xf") { state ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (state) {
                    ChipMode.READY -> {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(HC.white))
                        Spacer(Modifier.width(Spacing.s8))
                        Text("READY", style = HCType.label, color = HC.white60)
                    }
                    ChipMode.RECORDING -> {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(HC.red.copy(alpha = dotAlpha)))
                        Spacer(Modifier.width(Spacing.s8))
                        Text("REC", style = HCType.label, color = HC.white)
                    }
                    ChipMode.LOW_STORAGE -> {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(HC.amber))
                        Spacer(Modifier.width(Spacing.s8))
                        Text("LOW STORAGE", style = HCType.label, color = HC.amber)
                    }
                }
            }
        }
    }
}

@Composable
private fun Timecode(startedAt: Long) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(startedAt) {
        if (startedAt > 0) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                delay(1000)
            }
        } else {
            elapsedSeconds = 0
        }
    }
    Text(
        "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
        style = HCType.nums,
        color = HC.white,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.9f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "rec_s",
    )
    val bgColor by animateColorAsState(
        if (isRecording) HC.red else Color.Transparent,
        tween(400),
        label = "rec_bg",
    )
    val borderColor by animateColorAsState(
        if (isRecording) HC.red else HC.white.copy(alpha = 0.4f),
        tween(400),
        label = "rec_brd",
    )

    Box(
        Modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(bgColor, size.minDimension / 2f)
            drawCircle(borderColor, size.minDimension / 2f, style = Stroke(2.dp.toPx()))
        }
        Crossfade(targetState = isRecording, animationSpec = tween(200), label = "rec_icon") { recording ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (recording) {
                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(HC.white))
                } else {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(HC.white))
                }
            }
        }
    }
}

@Composable
private fun SavedCount(count: Int) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (count > 0) {
            scale.animateTo(1.3f, spring(dampingRatio = 0.5f, stiffness = 600f))
            scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 600f))
        }
    }
    AnimatedVisibility(visible = count > 0, enter = fadeIn()) {
        Text(
            "$count saved",
            style = HCType.label,
            color = HC.white60,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
    }
}

@Composable
private fun VisionOffDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recording_vision_off_title)) },
        text = { Text(stringResource(R.string.recording_vision_off_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun LowStorageDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Low storage") },
        text = { Text(stringResource(R.string.recording_low_storage)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugPanel(
    debugInfo: DebugInfo,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HC.surface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.safeDrawing,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.s20).padding(bottom = Spacing.s32)) {
            Text(stringResource(R.string.debug_title), style = HCType.title, color = HC.white, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Spacing.s16))
            DebugRmsBar(debugInfo.currentRms, debugInfo.baselineRms)
            Spacer(Modifier.height(Spacing.s8))
            DebugLabel("Baseline RMS", "%.4f".format(debugInfo.baselineRms))
            Spacer(Modifier.height(Spacing.s12))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                DebugChip("Ball", debugInfo.ballDetected)
                DebugChip("In Zone", debugInfo.ballInZone)
                DebugChip("Model", debugInfo.modelAvailable)
            }
            Spacer(Modifier.height(Spacing.s8))
            DebugLabel("Players in zone", debugInfo.playerCountInZone.toString())
            DebugLabel("State", debugInfo.stateMachineState)
            if (debugInfo.lastEventReason.isNotEmpty()) DebugLabel("Last event", debugInfo.lastEventReason)
            Spacer(Modifier.height(Spacing.s12))
            Text("Inference (last 10)", style = HCType.micro, color = HC.white60)
            Spacer(Modifier.height(Spacing.s4))
            InferenceSparkline(debugInfo.recentInferenceTimesMs, Modifier.fillMaxWidth().height(40.dp))
        }
    }
}

@Composable
private fun DebugRmsBar(
    current: Float,
    baseline: Float,
) {
    val spikeThreshold = baseline * 2.8f
    val fraction = (current / (spikeThreshold * 1.2f)).coerceIn(0f, 1f)
    val barColor =
        when {
            current > spikeThreshold -> HC.red
            current > baseline * 2f -> HC.amber
            else -> HC.green
        }
    Column {
        Text("RMS Level", style = HCType.micro, color = HC.white60)
        Spacer(Modifier.height(Spacing.s4))
        Box(Modifier.fillMaxWidth().height(Spacing.s8).clip(RoundedCornerShape(Spacing.s4)).background(HC.surfaceRaised)) {
            Box(Modifier.fillMaxWidth(fraction).height(Spacing.s8).clip(RoundedCornerShape(Spacing.s4)).background(barColor))
        }
    }
}

@Composable
private fun DebugChip(
    label: String,
    active: Boolean,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Radii.r8))
            .background(if (active) HC.greenDim else HC.surfaceRaised)
            .padding(horizontal = 10.dp, vertical = Spacing.s4),
    ) {
        Text(label, style = HCType.micro, color = if (active) HC.green else HC.white60)
    }
}

@Composable
private fun DebugLabel(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
        Text(label, style = HCType.micro, color = HC.white60)
        Text(value, style = HCType.micro.copy(fontFamily = FontFamily.Monospace), color = HC.white)
    }
}

@Composable
private fun InferenceSparkline(
    times: List<Long>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (times.size < 2) return@Canvas
        val maxTime = times.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: return@Canvas
        val stepX = size.width / (times.size - 1)
        val path = Path()
        times.forEachIndexed { i, time ->
            val x = i * stepX
            val y = size.height * (1f - time / maxTime)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, HC.green, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun PermissionRequestScreen(onRequestPermissions: () -> Unit) {
    Box(Modifier.fillMaxSize().background(HC.bg).safeDrawingPadding(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.s32),
        ) {
            Canvas(Modifier.size(80.dp)) {
                val sw = 1.5.dp.toPx()
                val c = HC.white20
                drawRoundRect(
                    c,
                    Offset(size.width * 0.1f, size.height * 0.3f),
                    Size(size.width * 0.8f, size.height * 0.5f),
                    CornerRadius(6.dp.toPx()),
                    style = Stroke(sw),
                )
                drawCircle(c, size.width * 0.15f, Offset(size.width / 2, size.height * 0.55f), style = Stroke(sw))
                drawLine(c, Offset(size.width * 0.15f, size.height * 0.85f), Offset(size.width * 0.85f, size.height * 0.15f), sw)
            }
            Spacer(Modifier.height(Spacing.s24))
            Text(
                stringResource(R.string.permission_camera_mic_title),
                style = HCType.title,
                color = HC.white,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.s12))
            Text(
                stringResource(R.string.permission_camera_mic_body),
                style = HCType.body,
                color = HC.white60,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.s32))
            PrimaryButton(stringResource(R.string.permission_grant), onClick = onRequestPermissions, fixedWidth = 200.dp)
        }
    }
}
