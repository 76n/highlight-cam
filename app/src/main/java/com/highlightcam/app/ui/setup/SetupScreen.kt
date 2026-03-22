package com.highlightcam.app.ui.setup

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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

private val GoalAColor = Color(0xFF00FF88)
private val GoalBColor = Color(0xFF4FC3F7)

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
                    navController.navigate(Routes.RECORDING) { popUpTo(Routes.SETUP) { inclusive = true } }
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
            onUseDefaults = viewModel::useDefaults,
            onKeepCurrent = viewModel::keepCurrentZones,
        )
    } else {
        PermissionRationale(onRequest = { cameraPermission.launchPermissionRequest() })
    }
}

@Suppress("LongParameterList", "LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupContent(
    uiState: SetupUiState,
    cameraPreviewManager: CameraPreviewManager,
    onCanvasTap: (Float, Float) -> Unit,
    onHandleDrag: (String, Int, Float, Float) -> Unit,
    onAdvanceToConfirm: () -> Unit,
    onConfirm: () -> Unit,
    onRedraw: () -> Unit,
    onUseDefaults: () -> Unit,
    onKeepCurrent: () -> Unit,
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(previewView, lifecycleOwner) {
        previewView?.let { cameraPreviewManager.bindToLifecycle(lifecycleOwner, it.surfaceProvider) }
    }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { viewSize = it }) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                }.also { previewView = it }
            },
            modifier = Modifier.fillMaxSize(),
        )

        ZoneOverlay(
            state = uiState,
            viewSize = viewSize,
            onCanvasTap = onCanvasTap,
            onHandleDrag = onHandleDrag,
        )

        TopScrim(uiState = uiState, onUseDefaults = onUseDefaults)

        if (uiState.step == SetupStep.FINE_TUNING) {
            Button(
                onClick = onAdvanceToConfirm,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoalAColor, contentColor = Color(0xFF080808)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Done adjusting", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
    }

    if (uiState.step == SetupStep.CONFIRMING) {
        ModalBottomSheet(
            onDismissRequest = onRedraw,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            ConfirmSheet(
                isReconfiguring = uiState.isReconfiguring,
                onConfirm = onConfirm,
                onRedraw = onRedraw,
                onKeepCurrent = onKeepCurrent,
            )
        }
    }
}

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
                Modifier.pointerInput(state.step) {
                    detectTapGestures { offset -> onCanvasTap(offset.x / w, offset.y / h) }
                }
            SetupStep.FINE_TUNING, SetupStep.CONFIRMING ->
                Modifier.pointerInput(state.goalAPoints, state.goalBPoints) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragTarget = findClosestHandle(offset, state, w, h)
                        },
                        onDrag = { change, _ ->
                            dragTarget?.let { (goalId, idx, _) ->
                                onHandleDrag(goalId, idx, change.position.x / w, change.position.y / h)
                            }
                            change.consume()
                        },
                        onDragEnd = { dragTarget = null },
                    )
                }
        }

    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().then(tapOrDragModifier)) {
        fun drawPolygon(
            points: List<NormalizedPoint>,
            color: Color,
            isActive: Boolean,
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
                    drawLine(color, offsets[i], it, strokeWidth = 2.dp.toPx(), pathEffect = if (isActive) null else dash)
                }
            }

            val handleRadius = 6.dp.toPx()
            val outerRadius = 15.dp.toPx()
            offsets.forEach { pt ->
                drawCircle(color.copy(alpha = 0.3f), outerRadius, pt)
                drawCircle(color, handleRadius, pt)
            }
        }

        val isPlacingA = state.step == SetupStep.PLACING_A
        val isPlacingB = state.step == SetupStep.PLACING_B

        drawPolygon(state.goalAPoints, GoalAColor, isPlacingA)
        drawPolygon(state.goalBPoints, GoalBColor, isPlacingB)
    }
}

private fun findClosestHandle(
    offset: Offset,
    state: SetupUiState,
    w: Float,
    h: Float,
): Triple<String, Int, Offset>? {
    val threshold = 44f
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
            if (dist < bestDist && dist < threshold * 2) {
                bestDist = dist
                best = Triple(goalId, i, Offset(px, py))
            }
        }
    }

    check("a", state.goalAPoints)
    check("b", state.goalBPoints)
    return best
}

@Composable
private fun TopScrim(
    uiState: SetupUiState,
    onUseDefaults: () -> Unit,
) {
    val instruction =
        when (uiState.step) {
            SetupStep.PLACING_A -> "Tap 4 corners of Goal A (${uiState.goalAPoints.size}/4)"
            SetupStep.PLACING_B -> "Tap 4 corners of Goal B (${uiState.goalBPoints.size}/4)"
            SetupStep.FINE_TUNING -> "Drag corners to fine-tune"
            SetupStep.CONFIRMING -> "Confirm your goal zones"
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            "HighlightCam",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            instruction,
            style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.05.em),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )
        if (uiState.step == SetupStep.PLACING_A && uiState.goalAPoints.isEmpty()) {
            Text(
                "Use defaults",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopEnd).clickable(onClick = onUseDefaults).padding(4.dp),
            )
        }
    }
}

@Composable
private fun ConfirmSheet(
    isReconfiguring: Boolean,
    onConfirm: () -> Unit,
    onRedraw: () -> Unit,
    onKeepCurrent: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth().padding(
                horizontal = 24.dp,
            ).padding(top = 16.dp, bottom = 24.dp).windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Both goals configured", style = MaterialTheme.typography.titleSmall, color = Color.White)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GoalAColor, contentColor = Color(0xFF080808)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Looks good", modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRedraw, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text("Redraw", modifier = Modifier.padding(vertical = 4.dp))
        }
        if (isReconfiguring) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onKeepCurrent) { Text("Keep current zones", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(72.dp)) {
                drawCircle(GoalAColor.copy(alpha = 0.15f), size.minDimension / 2f)
                drawCircle(GoalAColor, size.minDimension / 4f)
            }
            Spacer(Modifier.height(24.dp))
            Text("Camera Access Required", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(12.dp))
            Text(
                "HighlightCam needs camera access to preview the pitch and detect goals.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = GoalAColor, contentColor = Color(0xFF080808)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Grant Permission", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
    }
}
