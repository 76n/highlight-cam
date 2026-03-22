@file:Suppress("DEPRECATION")

package com.highlightcam.app.ui.recording

import android.Manifest
import android.app.Activity
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
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.TransformOrigin
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
import com.highlightcam.app.domain.VideoQuality
import com.highlightcam.app.navigation.Routes
import com.highlightcam.app.tracking.CropWindow
import com.highlightcam.app.ui.components.FloatingChip
import com.highlightcam.app.ui.components.HCIconButton
import com.highlightcam.app.ui.components.OverlayState
import com.highlightcam.app.ui.components.PolygonOverlay
import com.highlightcam.app.ui.components.PrimaryButton
import com.highlightcam.app.ui.components.ZoneDisplay
import com.highlightcam.app.ui.setup.CameraEntryPoint
import com.highlightcam.app.ui.theme.HC
import com.highlightcam.app.ui.theme.HCType
import com.highlightcam.app.ui.theme.IconSize
import com.highlightcam.app.ui.theme.Radius
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
    val lowStorage by viewModel.lowStorageWarning.collectAsState()
    val cropWindow by viewModel.cropWindow.collectAsState()
    val videoQuality by viewModel.videoQuality.collectAsState()
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
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(clipsSaved) {
        if (clipsSaved > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
                cropWindow = cropWindow,
                videoQuality = videoQuality,
                cameraPreviewManager = cameraPreviewManager,
                onStartRecording = viewModel::startRecording,
                onStopRecording = viewModel::stopRecording,
                onManualSave = viewModel::requestManualSave,
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToLibrary = { navController.navigate(Routes.LIBRARY) },
                onNavigateToSetup = { navController.navigate(Routes.SETUP) },
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
    cropWindow: CropWindow,
    videoQuality: VideoQuality,
    cameraPreviewManager: CameraPreviewManager,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onManualSave: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onShowVisionDialog: () -> Unit,
    onShowDebugPanel: () -> Unit,
    onShowLowStorageDialog: () -> Unit,
) {
    val isRecording = recorderState is RecorderState.Recording || recorderState is RecorderState.SavingClip
    val isSaving = recorderState is RecorderState.SavingClip
    val startedAt =
        when (val state = recorderState) {
            is RecorderState.Recording -> state.startedAt
            else -> 0L
        }
    var rememberedStartedAt by remember { mutableLongStateOf(0L) }
    if (startedAt > 0L) rememberedStartedAt = startedAt

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
        val cropRect = cropWindow.toRect()
        val cropScaleX = 1f / (cropRect.right - cropRect.left)
        val cropScaleY = 1f / (cropRect.bottom - cropRect.top)
        val cropModifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = cropScaleX
                    scaleY = cropScaleY
                    translationX = -cropRect.left * size.width * cropScaleX
                    translationY = -cropRect.top * size.height * cropScaleY
                    transformOrigin = TransformOrigin(0f, 0f)
                    clip = true
                }

        Box(modifier = cropModifier) {
            CameraPreview(cameraPreviewManager, videoQuality)

            if (goalZoneSet != null) {
                val zoneColors = mapOf("a" to HC.green, "b" to HC.blue)
                PolygonOverlay(
                    zones =
                        goalZoneSet.activeZones.map { zone ->
                            ZoneDisplay(zone, zoneColors[zone.id] ?: HC.green, zoneState(zone.id))
                        },
                )
            }
        }

        AnimatedVisibility(
            visible = isSaving,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200)),
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
                    .padding(start = Spacing.l, top = Spacing.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    mode = chipMode,
                    modifier = if (lowStorage) Modifier.clickable(onClick = onShowLowStorageDialog) else Modifier,
                )
                AnimatedVisibility(
                    visible = isRecording,
                    enter = fadeIn(tween(250)),
                    exit = fadeOut(tween(200)),
                ) {
                    Row {
                        Spacer(Modifier.width(Spacing.xs))
                        Timecode(rememberedStartedAt)
                    }
                }
            }
            AnimatedVisibility(
                visible = !modelAvailable && isRecording,
                enter = fadeIn() + slideInVertically { -it / 3 },
                exit = fadeOut(),
            ) {
                FloatingChip(
                    dotColor = HC.amber,
                    modifier = Modifier.padding(top = Spacing.xs).clickable(onClick = onShowVisionDialog),
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
                    .padding(end = Spacing.l, top = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            HCIconButton(Icons.Filled.Settings, onClick = onNavigateToSettings)
            HCIconButton(Icons.Filled.Crop, onClick = onNavigateToSetup)
            HCIconButton(Icons.Filled.GridView, onClick = onNavigateToLibrary)
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RecordButton(
                    isRecording = isRecording,
                    onClick = { if (isRecording) onStopRecording() else onStartRecording() },
                    onLongClick = if (debugMode) onShowDebugPanel else null,
                )
                CaptureButton(
                    enabled = isRecording,
                    onClick = onManualSave,
                )
            }
            Spacer(Modifier.height(Spacing.s))
            SavedCount(count = clipsSaved)
        }
    }
}

@Composable
private fun CameraPreview(
    cameraPreviewManager: CameraPreviewManager,
    quality: VideoQuality = VideoQuality.FHD_1080,
) {
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
    LaunchedEffect(lifecycleOwner, quality) {
        cameraPreviewManager.currentSurfaceProvider?.let {
            cameraPreviewManager.bindToLifecycle(lifecycleOwner, it, quality)
        }
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
                        Spacer(Modifier.width(Spacing.xs))
                        Text("READY", style = HCType.label, color = HC.white60)
                    }
                    ChipMode.RECORDING -> {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(HC.red.copy(alpha = dotAlpha)))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("REC", style = HCType.label, color = HC.white)
                    }
                    ChipMode.LOW_STORAGE -> {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(HC.amber))
                        Spacer(Modifier.width(Spacing.xs))
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
        style = HCType.label.copy(fontFamily = FontFamily.Monospace),
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
        if (isPressed) 0.93f else 1f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "rec_s",
    )
    val bgColor by animateColorAsState(
        if (isRecording) HC.red else HC.white10,
        tween(200),
        label = "rec_bg",
    )
    val borderColor by animateColorAsState(
        if (isRecording) HC.red else HC.white40,
        tween(200),
        label = "rec_brd",
    )

    Box(
        Modifier
            .size(56.dp)
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
                    Box(Modifier.size(20.dp).clip(RoundedCornerShape(Radius.s)).background(HC.white))
                } else {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(HC.white))
                }
            }
        }
    }
}

@Composable
private fun CaptureButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        if (enabled) HC.green else HC.white10,
        tween(200),
        label = "cap_bg",
    )
    val iconTint by animateColorAsState(
        if (enabled) HC.white else HC.white40,
        tween(200),
        label = "cap_tint",
    )
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(bgColor)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Flag,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(IconSize.l),
        )
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
    AnimatedVisibility(visible = count > 0, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
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
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l).padding(bottom = Spacing.xxl)) {
            Text(stringResource(R.string.debug_title), style = HCType.title, color = HC.white, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Spacing.m))
            DebugRmsBar(debugInfo.currentRms, debugInfo.baselineRms)
            Spacer(Modifier.height(Spacing.xs))
            DebugLabel("Baseline RMS", "%.4f".format(debugInfo.baselineRms))
            Spacer(Modifier.height(Spacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DebugChip("Ball", debugInfo.ballDetected)
                DebugChip("In Zone", debugInfo.ballInZone)
                DebugChip("Model", debugInfo.modelAvailable)
            }
            Spacer(Modifier.height(Spacing.xs))
            DebugLabel("Players in zone", debugInfo.playerCountInZone.toString())
            DebugLabel("State", debugInfo.stateMachineState)
            if (debugInfo.lastEventReason.isNotEmpty()) DebugLabel("Last event", debugInfo.lastEventReason)
            Spacer(Modifier.height(Spacing.s))
            Text("Inference (last 10)", style = HCType.micro, color = HC.white60)
            Spacer(Modifier.height(Spacing.xxs))
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
        Spacer(Modifier.height(Spacing.xxs))
        Box(Modifier.fillMaxWidth().height(Spacing.xs).clip(RoundedCornerShape(Spacing.xxs)).background(HC.surfaceRaised)) {
            Box(Modifier.fillMaxWidth(fraction).height(Spacing.xs).clip(RoundedCornerShape(Spacing.xxs)).background(barColor))
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
            .clip(RoundedCornerShape(Radius.s))
            .background(if (active) HC.greenDim else HC.surfaceRaised)
            .padding(horizontal = Spacing.s, vertical = Spacing.xxs),
    ) {
        Text(label, style = HCType.micro, color = if (active) HC.green else HC.white60)
    }
}

@Composable
private fun DebugLabel(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs), Arrangement.SpaceBetween) {
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
            modifier = Modifier.padding(Spacing.xxl),
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
            Spacer(Modifier.height(Spacing.xl))
            Text(
                stringResource(R.string.permission_camera_mic_title),
                style = HCType.title,
                color = HC.white,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.s))
            Text(
                stringResource(R.string.permission_camera_mic_body),
                style = HCType.body,
                color = HC.white60,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xxl))
            PrimaryButton(stringResource(R.string.permission_grant), onClick = onRequestPermissions, fixedWidth = 200.dp)
        }
    }
}
