package com.highlightcam.app.ui.setup

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.highlightcam.app.camera.CameraPreviewManager
import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.domain.NormalizedPoint
import com.highlightcam.app.navigation.Routes
import com.highlightcam.app.ui.components.FloatingChip
import com.highlightcam.app.ui.components.GhostButton
import com.highlightcam.app.ui.components.PrimaryButton
import com.highlightcam.app.ui.theme.HC
import com.highlightcam.app.ui.theme.HCType
import com.highlightcam.app.ui.theme.IconSize
import com.highlightcam.app.ui.theme.Radius
import com.highlightcam.app.ui.theme.Spacing
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.hypot

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CameraEntryPoint {
    fun cameraPreviewManager(): CameraPreviewManager
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SetupScreen(
    navController: NavController,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val cameraPreviewManager =
        remember {
            EntryPointAccessors
                .fromApplication(context.applicationContext, CameraEntryPoint::class.java)
                .cameraPreviewManager()
        }

    val cameraError by cameraPreviewManager.cameraError.collectAsState()
    LaunchedEffect(cameraError) { viewModel.updateCameraError(cameraError) }

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                SetupNavEvent.NavigateToRecording ->
                    navController.navigate(Routes.RECORDING) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                        launchSingleTop = true
                    }
            }
        }
    }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    if (cameraPermission.status.isGranted) {
        SetupContent(
            uiState = uiState,
            cameraPreviewManager = cameraPreviewManager,
            onCanvasTap = viewModel::onCanvasTap,
            onHandleDrag = viewModel::onHandleDrag,
            onAdvanceToConfirm = viewModel::advanceToConfirm,
            onConfirm = viewModel::confirmZones,
            onRedraw = viewModel::redraw,
            onAddGoalB = viewModel::onAddGoalB,
            onSkipGoalB = viewModel::onSkipGoalB,
        )
    } else {
        PermissionDenied(onRequest = { cameraPermission.launchPermissionRequest() })
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun SetupContent(
    uiState: SetupUiState,
    cameraPreviewManager: CameraPreviewManager,
    onCanvasTap: (Float, Float) -> Unit,
    onHandleDrag: (String, Int, Float, Float) -> Unit,
    onAdvanceToConfirm: () -> Unit,
    onConfirm: () -> Unit,
    onRedraw: () -> Unit,
    onAddGoalB: () -> Unit,
    onSkipGoalB: () -> Unit,
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val ctx = LocalContext.current
    val previewView =
        remember {
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }

    DisposableEffect(Unit) {
        cameraPreviewManager.attachPreviewSurface(previewView.surfaceProvider)
        onDispose {
            cameraPreviewManager.detachPreviewSurface()
            viewSize = IntSize.Zero
        }
    }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { viewSize = it }) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        ZoneOverlay(
            state = uiState,
            viewSize = viewSize,
            onCanvasTap = onCanvasTap,
            onHandleDrag = onHandleDrag,
        )

        AnimatedVisibility(
            visible = uiState.step != SetupStep.CONFIRMING,
            enter = fadeIn() + slideInVertically { -it / 3 },
            exit = fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = Spacing.xl),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val instruction =
                    when (uiState.step) {
                        SetupStep.PLACING_A -> "Tap the 4 corners of Goal A"
                        SetupStep.DECIDING_SECOND_GOAL -> "Is the second goal visible in frame?"
                        SetupStep.PLACING_B -> "Now tap the 4 corners of Goal B"
                        SetupStep.FINE_TUNING -> "Drag to adjust"
                        SetupStep.CONFIRMING -> ""
                    }
                FloatingChip { Text(instruction, style = HCType.label, color = HC.white) }
                Spacer(Modifier.height(Spacing.xs))
                SetupStepDots(uiState)
                if (uiState.step == SetupStep.FINE_TUNING) {
                    Spacer(Modifier.height(Spacing.xl))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onRedraw),
                    ) {
                        Icon(
                            Icons.Filled.Replay,
                            contentDescription = null,
                            tint = HC.white60,
                            modifier = Modifier.size(IconSize.s),
                        )
                        Spacer(Modifier.width(Spacing.xxs))
                        Text("Start over", style = HCType.micro, color = HC.white60)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.decidingSecondGoal,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                PrimaryButton("Add Goal B", onClick = onAddGoalB, fixedWidth = 160.dp)
                GhostButton("One goal only", onClick = onSkipGoalB, fixedWidth = 160.dp)
            }
        }

        AnimatedVisibility(
            visible = uiState.step == SetupStep.FINE_TUNING,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xxl),
        ) {
            PrimaryButton("Continue", onClick = onAdvanceToConfirm)
        }
    }

    AnimatedVisibility(
        visible = uiState.step == SetupStep.CONFIRMING,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        ConfirmOverlay(
            goalAPoints = uiState.goalAPoints,
            goalBPoints = uiState.goalBPoints,
            goalBEnabled = uiState.goalBEnabled,
            onConfirm = onConfirm,
            onRedraw = onRedraw,
        )
    }
}

@Composable
private fun SetupStepDots(uiState: SetupUiState) {
    val steps = SetupStep.entries.filter { it != SetupStep.CONFIRMING }
    val currentIndex = steps.indexOf(uiState.step)
    val goalBSkipped = !uiState.goalBEnabled && uiState.step.ordinal > SetupStep.DECIDING_SECOND_GOAL.ordinal

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        steps.forEachIndexed { i, step ->
            val color =
                when {
                    step == SetupStep.PLACING_B && goalBSkipped -> HC.white20
                    i < currentIndex -> HC.green
                    i == currentIndex -> HC.white
                    else -> HC.white20
                }
            val isFilled = i <= currentIndex || (step == SetupStep.PLACING_B && goalBSkipped)
            if (isFilled) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(color))
            } else {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .then(
                            Modifier.drawBehind {
                                drawCircle(
                                    color = HC.white20,
                                    radius = size.minDimension / 2f,
                                    style = Stroke(width = 1.dp.toPx()),
                                )
                            },
                        ),
                )
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun ZoneOverlay(
    state: SetupUiState,
    viewSize: IntSize,
    onCanvasTap: (Float, Float) -> Unit,
    onHandleDrag: (String, Int, Float, Float) -> Unit,
) {
    val w = viewSize.width.toFloat()
    val h = viewSize.height.toFloat()
    if (w <= 0f || h <= 0f) return

    var dragTarget by remember { mutableStateOf<Triple<String, Int, Offset>?>(null) }

    val tapOrDragModifier =
        when (state.step) {
            SetupStep.PLACING_A, SetupStep.PLACING_B ->
                Modifier.pointerInput(state.step, viewSize) {
                    detectTapGestures { offset ->
                        if (w > 0f && h > 0f) onCanvasTap(offset.x / w, offset.y / h)
                    }
                }
            SetupStep.FINE_TUNING, SetupStep.CONFIRMING ->
                Modifier.pointerInput(state.step, viewSize) {
                    detectDragGestures(
                        onDragStart = { offset -> dragTarget = findClosestHandle(offset, state, w, h) },
                        onDrag = { change, _ ->
                            dragTarget?.let { (goalId, idx, _) ->
                                onHandleDrag(goalId, idx, change.position.x / w, change.position.y / h)
                            }
                            change.consume()
                        },
                        onDragEnd = { dragTarget = null },
                    )
                }
            SetupStep.DECIDING_SECOND_GOAL -> Modifier
        }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        1f,
        1.2f,
        infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse),
        label = "ps",
    )
    val pulseAlpha by pulse.animateFloat(
        1f,
        0.6f,
        infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse),
        label = "pa",
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().then(tapOrDragModifier)) {
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)

        fun drawPolygon(
            points: List<NormalizedPoint>,
            color: Color,
            isActiveGoal: Boolean,
        ) {
            if (points.isEmpty()) return
            val offsets = points.map { Offset(it.x * w, it.y * h) }

            for (i in offsets.indices) {
                val next =
                    if (i + 1 < offsets.size) {
                        offsets[i + 1]
                    } else if (points.size == GoalZone.VERTEX_COUNT) {
                        offsets[0]
                    } else {
                        null
                    }
                next?.let {
                    drawLine(
                        color.copy(alpha = 0.6f),
                        offsets[i],
                        it,
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = dashEffect,
                    )
                }
            }

            offsets.forEachIndexed { idx, pt ->
                val isLast = isActiveGoal && idx == offsets.lastIndex
                val s = if (isLast) pulseScale else 1f
                val a = if (isLast) pulseAlpha else 1f
                drawCircle(color.copy(alpha = 0.25f * a), 10.dp.toPx() * s, pt)
                drawCircle(color.copy(alpha = a), 5.dp.toPx() * s, pt)
            }
        }

        drawPolygon(state.goalAPoints, HC.green, state.step == SetupStep.PLACING_A)
        if (state.goalBEnabled && state.goalBPoints.isNotEmpty()) {
            drawPolygon(state.goalBPoints, HC.blue, state.step == SetupStep.PLACING_B)
        }
    }
}

private fun findClosestHandle(
    offset: Offset,
    state: SetupUiState,
    w: Float,
    h: Float,
): Triple<String, Int, Offset>? {
    var best: Triple<String, Int, Offset>? = null
    var bestDist = Float.MAX_VALUE

    fun check(
        goalId: String,
        points: List<NormalizedPoint>,
    ) {
        points.forEachIndexed { i, pt ->
            val px = pt.x * w
            val py = pt.y * h
            val dist = hypot(offset.x - px, offset.y - py)
            if (dist < bestDist && dist < 88f) {
                bestDist = dist
                best = Triple(goalId, i, Offset(px, py))
            }
        }
    }

    check("a", state.goalAPoints)
    if (state.goalBEnabled) check("b", state.goalBPoints)
    return best
}

@Composable
private fun ConfirmOverlay(
    goalAPoints: List<NormalizedPoint>,
    goalBPoints: List<NormalizedPoint>,
    goalBEnabled: Boolean,
    onConfirm: () -> Unit,
    onRedraw: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(HC.bg.copy(alpha = 0.95f))
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (goalBEnabled) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    ZonePreviewCanvas(goalAPoints, HC.green, "Goal A", Modifier.weight(1f))
                    ZonePreviewCanvas(goalBPoints, HC.blue, "Goal B", Modifier.weight(1f))
                }
            } else {
                ZonePreviewCanvas(goalAPoints, HC.green, "Goal A", Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Single goal mode \u2014 Goal B not configured",
                    style = HCType.micro,
                    color = HC.white60,
                )
            }
            Spacer(Modifier.height(Spacing.xl))
            PrimaryButton("Let's go", onClick = onConfirm)
            Spacer(Modifier.height(Spacing.s))
            GhostButton("Redo", onClick = onRedraw)
        }
    }
}

@Composable
private fun ZonePreviewCanvas(
    points: List<NormalizedPoint>,
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Radius.m))
                .background(HC.bg),
        ) {
            if (points.size >= GoalZone.VERTEX_COUNT) {
                val path =
                    Path().apply {
                        moveTo(points[0].x * size.width, points[0].y * size.height)
                        for (i in 1 until points.size) lineTo(points[i].x * size.width, points[i].y * size.height)
                        close()
                    }
                drawPath(path, color, style = Stroke(2.dp.toPx()))
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(label, style = HCType.micro, color = color)
    }
}

@Composable
private fun PermissionDenied(onRequest: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(HC.bg)
            .safeDrawingPadding()
            .padding(Spacing.xxl),
        Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Canvas(Modifier.size(80.dp)) {
                val sw = 1.5.dp.toPx()
                val c = HC.white20
                val bodyL = size.width * 0.1f
                val bodyT = size.height * 0.3f
                val bodyW = size.width * 0.8f
                val bodyH = size.height * 0.5f
                drawRoundRect(c, Offset(bodyL, bodyT), Size(bodyW, bodyH), CornerRadius(6.dp.toPx()), style = Stroke(sw))
                drawCircle(c, size.width * 0.15f, Offset(size.width / 2, size.height * 0.55f), style = Stroke(sw))
                drawLine(c, Offset(size.width * 0.15f, size.height * 0.85f), Offset(size.width * 0.85f, size.height * 0.15f), sw)
            }
            Spacer(Modifier.height(Spacing.xl))
            Text("Camera access needed", style = HCType.title, color = HC.white)
            Spacer(Modifier.height(Spacing.s))
            Text(
                "HighlightCam needs your camera to define the goal zones.",
                style = HCType.body,
                color = HC.white60,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 260.dp),
            )
            Spacer(Modifier.height(Spacing.xxl))
            PrimaryButton("Allow Camera", onClick = onRequest, fixedWidth = 200.dp)
        }
    }
}
