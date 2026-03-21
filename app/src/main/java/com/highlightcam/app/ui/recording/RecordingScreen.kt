@file:Suppress("DEPRECATION")

package com.highlightcam.app.ui.recording

import android.Manifest
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
    val snackbarHostState = remember { SnackbarHostState() }
    var showDebugPanel by remember { mutableStateOf(false) }
    var showVisionDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
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
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 300),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "zone_stroke_width",
    )

    val targetColor = if (isCandidate) AmberColor else GreenColor.copy(alpha = 0.4f)
    val strokeColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(200),
        label = "zone_color",
    )
    val strokeDp = if (isCandidate) shimmerWidth else 1.5f
    val strokeWidthPx = with(LocalDensity.current) { strokeDp.dp.toPx() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val left = zone.xFraction * size.width
        val top = zone.yFraction * size.height
        val w = zone.widthFraction * size.width
        val h = zone.heightFraction * size.height

        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(left, top),
            size = Size(w, h),
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
            text = "Vision off \u2014 audio only",
            style =
                MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.05.sp,
                ),
            color = AmberColor,
        )
    }
}

@Composable
private fun VisionOffDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Visual detection disabled") },
        text = {
            Text(
                "Place yolov8n_float16.tflite in app assets to enable visual detection. " +
                    "Currently running audio-only detection.",
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
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
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecIndicator(isActive = isRecording)
        Spacer(modifier = Modifier.width(8.dp))
        ElapsedTimer(isRecording = isRecording, startedAt = startedAt)
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onNavigateToSettings) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_notification),
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onNavigateToLibrary) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_notification),
                contentDescription = "Library",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun RecIndicator(isActive: Boolean) {
    val transition = rememberInfiniteTransition(label = "rec_pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(animation = tween(600), repeatMode = RepeatMode.Reverse),
        label = "rec_alpha",
    )
    val displayAlpha = if (isActive) alpha else 1f
    val dotColor = if (isActive) Color(0xFFFF3B3B) else Color.Gray

    Box(
        modifier =
            Modifier
                .size(10.dp)
                .drawBehind {
                    drawCircle(color = dotColor.copy(alpha = displayAlpha), radius = size.minDimension / 2f)
                },
    )
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
        text = if (isRecording) "%02d:%02d".format(minutes, seconds) else "--:--",
        style =
            MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                letterSpacing = 0.05.sp,
            ),
        color = Color.White,
    )
}

@Composable
private fun SavingBanner(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 56.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1E1E1E).copy(alpha = 0.9f))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Saving clip\u2026", style = MaterialTheme.typography.bodySmall, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
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
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(visible = clipsSaved > 0, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = "Clips saved: $clipsSaved",
                style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.05.sp),
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.width(56.dp))
            RecordButton(
                isRecording = isRecording,
                onClick = onToggleRecording,
                onLongClick = onLongPressRecord,
            )
            SaveButtonSlot(visible = isRecording, onManualSave = onManualSave)
        }
    }
}

@Composable
private fun SaveButtonSlot(
    visible: Boolean,
    onManualSave: () -> Unit,
) {
    Box(modifier = Modifier.width(56.dp)) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            ManualSaveButton(onClick = onManualSave)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val scale by animateFloatAsState(
        targetValue = if (isRecording) 0.9f else 1f,
        animationSpec = tween(300),
        label = "rec_btn_scale",
    )
    val buttonColor by animateColorAsState(
        targetValue = if (isRecording) Color(0xFFFF3B3B) else Color.Transparent,
        animationSpec = tween(300),
        label = "rec_btn_color",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isRecording) Color(0xFFFF3B3B) else Color.White,
        animationSpec = tween(300),
        label = "rec_btn_border",
    )
    val strokeWidthPx = with(LocalDensity.current) { 3.dp.toPx() }
    val innerCornerPx = with(LocalDensity.current) { if (isRecording) 6.dp.toPx() else 28.dp.toPx() }
    val innerSizeFraction = if (isRecording) 0.42f else 0.75f

    Box(
        modifier =
            Modifier
                .size(72.dp)
                .scale(scale)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = borderColor,
                radius = size.minDimension / 2f,
                style = Stroke(width = strokeWidthPx),
            )
            val innerSize = size.minDimension * innerSizeFraction
            val innerOffset = (size.minDimension - innerSize) / 2f
            drawRoundRect(
                color = if (isRecording) buttonColor else Color.White,
                topLeft = Offset(innerOffset, innerOffset),
                size = Size(innerSize, innerSize),
                cornerRadius = CornerRadius(innerCornerPx),
            )
        }
    }
}

@Composable
private fun ManualSaveButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).padding(start = 8.dp)) {
        Canvas(modifier = Modifier.size(24.dp)) {
            drawRoundRect(
                color = GreenColor,
                topLeft = Offset(size.width * 0.15f, size.height * 0.05f),
                size = Size(size.width * 0.7f, size.height * 0.9f),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            drawRoundRect(
                color = Color(0xFF080808),
                topLeft = Offset(size.width * 0.25f, size.height * 0.05f),
                size = Size(size.width * 0.5f, size.height * 0.35f),
                cornerRadius = CornerRadius(2.dp.toPx()),
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
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
        ) {
            Text(
                "Detection Debug",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))

            DebugRmsBar(current = debugInfo.currentRms, baseline = debugInfo.baselineRms)
            Spacer(modifier = Modifier.height(8.dp))
            DebugLabel("Baseline RMS", "%.4f".format(debugInfo.baselineRms))
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DebugChip("Ball", debugInfo.ballDetected)
                DebugChip("In Zone", debugInfo.ballInZone)
                DebugChip("Model", debugInfo.modelAvailable)
            }
            Spacer(modifier = Modifier.height(8.dp))
            DebugLabel("Players in zone", debugInfo.playerCountInZone.toString())
            DebugLabel("State", debugInfo.stateMachineState)
            if (debugInfo.lastEventReason.isNotEmpty()) {
                DebugLabel("Last event", debugInfo.lastEventReason)
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Inference (last 10)",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            InferenceSparkline(
                times = debugInfo.recentInferenceTimesMs,
                modifier = Modifier.fillMaxWidth().height(40.dp),
            )
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
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2C2C2C)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor),
            )
        }
    }
}

@Composable
private fun DebugChip(
    label: String,
    active: Boolean,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (active) GreenColor.copy(alpha = 0.2f) else Color(0xFF2C2C2C))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) GreenColor else Color.Gray,
        )
    }
}

@Composable
private fun DebugLabel(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Text(
            value,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = Color.White,
        )
    }
}

@Composable
private fun InferenceSparkline(
    times: List<Long>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (times.size < 2) return@Canvas
        val maxTime = times.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: return@Canvas
        val stepX = size.width / (times.size - 1)

        val path = Path()
        times.forEachIndexed { i, time ->
            val x = i * stepX
            val y = size.height * (1f - time / maxTime)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, GreenColor, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun PermissionRequestScreen(onRequestPermissions: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF080808)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Canvas(modifier = Modifier.size(64.dp)) {
                drawCircle(color = GreenColor.copy(alpha = 0.15f), radius = size.minDimension / 2f)
                drawCircle(color = GreenColor, radius = size.minDimension / 4f)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Camera & microphone access required",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "HighlightCam needs camera and microphone permissions to record video clips.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            androidx.compose.material3.Button(
                onClick = onRequestPermissions,
                shape = RoundedCornerShape(12.dp),
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = GreenColor,
                        contentColor = Color(0xFF080808),
                    ),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(
                    "Grant Permissions",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.05.sp),
                )
            }
        }
    }
}
