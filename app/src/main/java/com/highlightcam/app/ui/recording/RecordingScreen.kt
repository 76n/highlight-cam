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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.highlightcam.app.R
import com.highlightcam.app.camera.CameraPreviewManager
import com.highlightcam.app.detection.DebugInfo
import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.domain.RecorderState
import com.highlightcam.app.navigation.Routes
import com.highlightcam.app.ui.setup.CameraEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

private val AmberColor = Color(0xFFFFB347)
private val GreenColor = Color(0xFF00FF88)

@Suppress("LongMethod")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RecordingScreen(
    navController: NavController,
    viewModel: RecordingViewModel = hiltViewModel(),
) {
    val recorderState by viewModel.recorderState.collectAsState()
    val clipsSaved by viewModel.clipsSaved.collectAsState()
    val goalZone by viewModel.goalZone.collectAsState()
    val candidateDetected by viewModel.candidateDetected.collectAsState()
    val modelAvailable by viewModel.modelAvailable.collectAsState()
    val debugMode by viewModel.debugModeEnabled.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    val soundOnSave by viewModel.soundOnSave.collectAsState()
    val lowStorage by viewModel.lowStorageWarning.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDebugPanel by remember { mutableStateOf(false) }
    var showVisionDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val view = LocalView.current
    val cameraPreviewManager =
        remember {
            EntryPointAccessors
                .fromApplication(context.applicationContext, CameraEntryPoint::class.java)
                .cameraPreviewManager()
        }

    val permissionsState =
        rememberMultiplePermissionsState(
            listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
        )
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
                    val sound = MediaActionSound()
                    sound.play(MediaActionSound.START_VIDEO_RECORDING)
                } catch (_: Exception) {
                }
            }
        }
    }

    LaunchedEffect(candidateDetected) {
        if (candidateDetected) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    LaunchedEffect(recorderState) {
        if (recorderState is RecorderState.Error) {
            snackbarHostState.showSnackbar((recorderState as RecorderState.Error).message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (allGranted) {
                RecordingContent(
                    recorderState = recorderState,
                    clipsSaved = clipsSaved,
                    goalZone = goalZone,
                    candidateDetected = candidateDetected,
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
                )
            } else {
                PermissionRequestScreen(
                    onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() },
                )
            }
        }
    }

    if (showVisionDialog) {
        VisionOffDialog(onDismiss = { showVisionDialog = false })
    }

    if (showDebugPanel && debugMode) {
        DebugPanel(debugInfo = debugInfo, onDismiss = { showDebugPanel = false })
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun RecordingContent(
    recorderState: RecorderState,
    clipsSaved: Int,
    goalZone: GoalZone?,
    candidateDetected: Boolean,
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
) {
    val isRecording =
        recorderState is RecorderState.Recording || recorderState is RecorderState.SavingClip
    val isSaving = recorderState is RecorderState.SavingClip
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            cameraPreviewManager.currentSurfaceProvider?.let { sp ->
                cameraPreviewManager.bindToLifecycle(lifecycleOwner, sp)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(cameraPreviewManager)

        if (isRecording && goalZone != null) {
            GoalZoneOverlay(zone = goalZone, isCandidate = candidateDetected)
        }

        TopBar(
            recorderState = recorderState,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToLibrary = onNavigateToLibrary,
        )

        if (!modelAvailable && isRecording) {
            VisionOffBadge(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 52.dp),
                onClick = onShowVisionDialog,
            )
        }

        if (lowStorage && isRecording) {
            LowStorageBanner(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = if (!modelAvailable) 80.dp else 52.dp),
            )
        }

        SavingBanner(visible = isSaving)

        BottomControls(
            isRecording = isRecording,
            clipsSaved = clipsSaved,
            onToggleRecording = { if (isRecording) onStopRecording() else onStartRecording() },
            onManualSave = onManualSave,
            onLongPressRecord = if (debugMode) onShowDebugPanel else null,
        )
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
        cameraPreviewManager.currentSurfaceProvider?.let { sp ->
            cameraPreviewManager.bindToLifecycle(lifecycleOwner, sp)
        }
    }
}

@Composable
private fun GoalZoneOverlay(
    zone: GoalZone,
    isCandidate: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "zone_shimmer")
    val shimmerWidth by transition.animateFloat(
        initialValue = 2f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(animation = tween(300), repeatMode = RepeatMode.Reverse),
        label = "zone_stroke_width",
    )
    val targetColor = if (isCandidate) AmberColor else GreenColor.copy(alpha = 0.4f)
    val strokeColor by animateColorAsState(targetColor, tween(200), label = "zone_color")
    val strokeDp = if (isCandidate) shimmerWidth else 1.5f
    val strokeWidthPx = with(LocalDensity.current) { strokeDp.dp.toPx() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(zone.xFraction * size.width, zone.yFraction * size.height),
            size = Size(zone.widthFraction * size.width, zone.heightFraction * size.height),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(width = strokeWidthPx),
        )
    }
}

@Composable
private fun VisionOffBadge(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(AmberColor.copy(alpha = 0.2f))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            stringResource(R.string.recording_vision_off),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.05.sp),
            color = AmberColor,
        )
    }
}

@Composable
private fun LowStorageBanner(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(AmberColor.copy(alpha = 0.2f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            stringResource(R.string.recording_low_storage),
            style = MaterialTheme.typography.labelSmall,
            color = AmberColor,
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
private fun TopBar(
    recorderState: RecorderState,
    onNavigateToSettings: () -> Unit,
    onNavigateToLibrary: () -> Unit,
) {
    val isRecording = recorderState is RecorderState.Recording
    val startedAt = (recorderState as? RecorderState.Recording)?.startedAt ?: 0L
    Row(
        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecIndicator(isActive = isRecording)
        Spacer(modifier = Modifier.width(8.dp))
        ElapsedTimer(isRecording = isRecording, startedAt = startedAt)
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onNavigateToSettings) {
            Icon(
                ImageVector.vectorResource(R.drawable.ic_notification),
                stringResource(R.string.settings),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onNavigateToLibrary) {
            Icon(
                ImageVector.vectorResource(R.drawable.ic_notification),
                stringResource(R.string.library),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun RecIndicator(isActive: Boolean) {
    val transition = rememberInfiniteTransition(label = "rec_pulse")
    val alpha by transition.animateFloat(1f, 0.4f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "rec_alpha")
    val displayAlpha = if (isActive) alpha else 1f
    val dotColor = if (isActive) Color(0xFFFF3B3B) else Color.Gray
    Box(modifier = Modifier.size(10.dp).drawBehind { drawCircle(dotColor.copy(alpha = displayAlpha), size.minDimension / 2f) })
}

@Composable
private fun ElapsedTimer(
    isRecording: Boolean,
    startedAt: Long,
) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(isRecording, startedAt) {
        if (isRecording && startedAt > 0) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                delay(1000)
            }
        } else {
            elapsedSeconds = 0
        }
    }
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    Text(
        if (isRecording) "%02d:%02d".format(minutes, seconds) else stringResource(R.string.recording_timer_idle),
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 16.sp, letterSpacing = 0.05.sp),
        color = Color.White,
    )
}

@Composable
private fun SavingBanner(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 56.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 48.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.clip(
                    RoundedCornerShape(24.dp),
                ).background(Color(0xFF1E1E1E).copy(alpha = 0.9f)).padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.recording_saving_clip), style = MaterialTheme.typography.bodySmall, color = Color.White)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = GreenColor,
                    trackColor = Color(0xFF2C2C2C),
                )
            }
        }
    }
}

@Composable
private fun BottomControls(
    isRecording: Boolean,
    clipsSaved: Int,
    onToggleRecording: () -> Unit,
    onManualSave: () -> Unit,
    onLongPressRecord: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 32.dp),
        Arrangement.Bottom,
        Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(visible = clipsSaved > 0, enter = fadeIn(), exit = fadeOut()) {
            Text(
                stringResource(R.string.recording_clips_saved, clipsSaved),
                style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.05.sp),
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Spacer(Modifier.width(56.dp))
            RecordButton(isRecording, onToggleRecording, onLongPressRecord)
            SaveButtonSlot(isRecording, onManualSave)
        }
    }
}

@Composable
private fun SaveButtonSlot(
    visible: Boolean,
    onManualSave: () -> Unit,
) {
    Box(
        Modifier.width(56.dp),
    ) { AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) { ManualSaveButton(onManualSave) } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val scale by animateFloatAsState(if (isRecording) 0.9f else 1f, tween(300), label = "rec_btn_scale")
    val buttonColor by animateColorAsState(if (isRecording) Color(0xFFFF3B3B) else Color.Transparent, tween(300), label = "rec_btn_color")
    val borderColor by animateColorAsState(if (isRecording) Color(0xFFFF3B3B) else Color.White, tween(300), label = "rec_btn_border")
    val strokeWidthPx = with(LocalDensity.current) { 3.dp.toPx() }
    val innerCornerPx = with(LocalDensity.current) { if (isRecording) 6.dp.toPx() else 28.dp.toPx() }
    val innerSizeFraction = if (isRecording) 0.42f else 0.75f
    Box(
        Modifier.size(72.dp).scale(scale).clip(CircleShape).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(borderColor, size.minDimension / 2f, style = Stroke(strokeWidthPx))
            val innerSize = size.minDimension * innerSizeFraction
            val innerOffset = (size.minDimension - innerSize) / 2f
            drawRoundRect(
                if (isRecording) buttonColor else Color.White,
                Offset(innerOffset, innerOffset),
                Size(innerSize, innerSize),
                CornerRadius(innerCornerPx),
            )
        }
    }
}

@Composable
private fun ManualSaveButton(onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp).padding(start = 8.dp)) {
        Canvas(Modifier.size(24.dp)) {
            drawRoundRect(
                GreenColor,
                Offset(size.width * 0.15f, size.height * 0.05f),
                Size(size.width * 0.7f, size.height * 0.9f),
                CornerRadius(3.dp.toPx()),
            )
            drawRoundRect(
                Color(0xFF080808),
                Offset(size.width * 0.25f, size.height * 0.05f),
                Size(size.width * 0.5f, size.height * 0.35f),
                CornerRadius(2.dp.toPx()),
            )
        }
    }
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
        containerColor = Color(0xFF121212),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.debug_title),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            DebugRmsBar(debugInfo.currentRms, debugInfo.baselineRms)
            Spacer(Modifier.height(8.dp))
            DebugLabel("Baseline RMS", "%.4f".format(debugInfo.baselineRms))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DebugChip("Ball", debugInfo.ballDetected)
                DebugChip("In Zone", debugInfo.ballInZone)
                DebugChip("Model", debugInfo.modelAvailable)
            }
            Spacer(Modifier.height(8.dp))
            DebugLabel("Players in zone", debugInfo.playerCountInZone.toString())
            DebugLabel("State", debugInfo.stateMachineState)
            if (debugInfo.lastEventReason.isNotEmpty()) DebugLabel("Last event", debugInfo.lastEventReason)
            Spacer(Modifier.height(12.dp))
            Text("Inference (last 10)", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(4.dp))
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
            current > spikeThreshold -> Color(0xFFFF3B3B)
            current > baseline * 2f -> AmberColor
            else -> GreenColor
        }
    Column {
        Text("RMS Level", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF2C2C2C))) {
            Box(Modifier.fillMaxWidth(fraction).height(8.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
        }
    }
}

@Composable private fun DebugChip(
    label: String,
    active: Boolean,
) {
    Box(
        Modifier.clip(
            RoundedCornerShape(8.dp),
        ).background(if (active) GreenColor.copy(alpha = 0.2f) else Color(0xFF2C2C2C)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) GreenColor else Color.Gray)
    }
}

@Composable private fun DebugLabel(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = Color.White)
    }
}

@Composable private fun InferenceSparkline(
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
        drawPath(path, GreenColor, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun PermissionRequestScreen(onRequestPermissions: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF080808)), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Canvas(Modifier.size(64.dp)) {
                drawCircle(GreenColor.copy(alpha = 0.15f), size.minDimension / 2f)
                drawCircle(GreenColor, size.minDimension / 4f)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.permission_camera_mic_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.permission_camera_mic_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            androidx.compose.material3.Button(
                onRequestPermissions,
                Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = GreenColor,
                        contentColor = Color(0xFF080808),
                    ),
            ) {
                Text(stringResource(R.string.permission_grant), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.05.sp))
            }
        }
    }
}
