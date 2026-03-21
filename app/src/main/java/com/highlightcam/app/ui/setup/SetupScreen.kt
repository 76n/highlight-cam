package com.highlightcam.app.ui.setup

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.highlightcam.app.navigation.Routes
import com.highlightcam.app.ui.theme.ElectricGreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

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
    LaunchedEffect(cameraError) {
        viewModel.updateCameraError(cameraError)
    }

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                SetupNavEvent.NavigateToRecording -> {
                    navController.navigate(Routes.RECORDING) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            }
        }
    }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermission.status.isGranted) {
        CameraSetupContent(
            uiState = uiState,
            cameraPreviewManager = cameraPreviewManager,
            onDragStart = viewModel::onDragStart,
            onDragUpdate = viewModel::onDragUpdate,
            onDragEnd = viewModel::onDragEnd,
            onConfirm = viewModel::confirmZone,
            onRedraw = viewModel::clearRect,
            onSkip = viewModel::skipZone,
            onKeepCurrent = viewModel::keepCurrentZone,
        )
    } else {
        PermissionRationaleScreen(
            onRequestPermission = { cameraPermission.launchPermissionRequest() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraSetupContent(
    uiState: SetupUiState,
    cameraPreviewManager: CameraPreviewManager,
    onDragStart: (Float, Float) -> Unit,
    onDragUpdate: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onConfirm: (com.highlightcam.app.domain.GoalZone) -> Unit,
    onRedraw: () -> Unit,
    onSkip: () -> Unit,
    onKeepCurrent: () -> Unit,
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(previewView, lifecycleOwner) {
        previewView?.let { view ->
            cameraPreviewManager.bindToLifecycle(lifecycleOwner, view.surfaceProvider)
        }
    }

    val showBottomSheet = uiState.currentDragRect != null && uiState.isRectFinalized
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it },
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                }.also { previewView = it }
            },
            modifier = Modifier.fillMaxSize(),
        )

        ZoneDrawingOverlay(
            rect = uiState.currentDragRect,
            onDragStart = onDragStart,
            onDragUpdate = onDragUpdate,
            onDragEnd = onDragEnd,
            modifier = Modifier.fillMaxSize(),
        )

        TopScrim(
            isReconfiguring = uiState.isReconfiguring,
            onSkip = onSkip,
        )

        uiState.cameraError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    val currentRect = uiState.currentDragRect
    if (showBottomSheet && currentRect != null) {
        ModalBottomSheet(
            onDismissRequest = onRedraw,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            windowInsets = WindowInsets(0),
        ) {
            ConfirmationSheetContent(
                rect = currentRect,
                viewWidth = viewSize.width.toFloat(),
                viewHeight = viewSize.height.toFloat(),
                isReconfiguring = uiState.isReconfiguring,
                onConfirm = {
                    onConfirm(
                        rectToGoalZone(
                            currentRect,
                            viewSize.width.toFloat(),
                            viewSize.height.toFloat(),
                        ),
                    )
                },
                onRedraw = onRedraw,
                onKeepCurrent = onKeepCurrent,
            )
        }
    }
}

@Composable
private fun ZoneDrawingOverlay(
    rect: Rect?,
    onDragStart: (Float, Float) -> Unit,
    onDragUpdate: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strokeColor = ElectricGreen
    val fillColor = ElectricGreen.copy(alpha = 0.12f)

    androidx.compose.foundation.Canvas(
        modifier =
            modifier.pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> onDragStart(offset.x, offset.y) },
                    onDrag = { change, _ ->
                        onDragUpdate(change.position.x, change.position.y)
                        change.consume()
                    },
                    onDragEnd = onDragEnd,
                )
            },
    ) {
        rect?.let { r ->
            val cornerRadius = CornerRadius(4.dp.toPx())
            val strokeWidth = 2.5.dp.toPx()
            val handleRadius = 4.dp.toPx()

            drawRoundRect(
                color = fillColor,
                topLeft = Offset(r.left, r.top),
                size = Size(r.width, r.height),
                cornerRadius = cornerRadius,
            )

            drawRoundRect(
                color = strokeColor,
                topLeft = Offset(r.left, r.top),
                size = Size(r.width, r.height),
                cornerRadius = cornerRadius,
                style = Stroke(width = strokeWidth),
            )

            listOf(
                Offset(r.left, r.top),
                Offset(r.right, r.top),
                Offset(r.left, r.bottom),
                Offset(r.right, r.bottom),
            ).forEach { corner ->
                drawCircle(
                    color = strokeColor,
                    radius = handleRadius,
                    center = corner,
                )
            }
        }
    }
}

@Composable
private fun TopScrim(
    isReconfiguring: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                            ),
                    ),
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "HighlightCam",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.TopStart),
        )

        Text(
            text =
                if (isReconfiguring) {
                    "Reconfigure your goal zone"
                } else {
                    "Draw a rectangle over the goal mouth"
                },
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    letterSpacing = 0.05.em,
                ),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )

        Text(
            text = "Skip — use full frame",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .clickable(onClick = onSkip)
                    .padding(4.dp),
        )
    }
}

@Composable
private fun ConfirmationSheetContent(
    rect: Rect,
    viewWidth: Float,
    viewHeight: Float,
    isReconfiguring: Boolean,
    onConfirm: () -> Unit,
    onRedraw: () -> Unit,
    onKeepCurrent: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ZonePreviewCard(rect = rect, viewWidth = viewWidth, viewHeight = viewHeight)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Looks good", modifier = Modifier.padding(vertical = 4.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onRedraw,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Redraw", modifier = Modifier.padding(vertical = 4.dp))
        }

        if (isReconfiguring) {
            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onKeepCurrent) {
                Text(
                    text = "Keep current zone",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ZonePreviewCard(
    rect: Rect,
    viewWidth: Float,
    viewHeight: Float,
) {
    val aspectRatio = if (viewHeight > 0f) viewWidth / viewHeight else 16f / 9f

    androidx.compose.foundation.Canvas(
        modifier =
            Modifier
                .fillMaxWidth(0.6f)
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A1A)),
    ) {
        if (viewWidth > 0f && viewHeight > 0f) {
            val scaleX = size.width / viewWidth
            val scaleY = size.height / viewHeight

            drawRoundRect(
                color = ElectricGreen.copy(alpha = 0.12f),
                topLeft = Offset(rect.left * scaleX, rect.top * scaleY),
                size = Size(rect.width * scaleX, rect.height * scaleY),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            drawRoundRect(
                color = ElectricGreen,
                topLeft = Offset(rect.left * scaleX, rect.top * scaleY),
                size = Size(rect.width * scaleX, rect.height * scaleY),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

@Composable
private fun PermissionRationaleScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(72.dp)) {
                val strokeWidth = 2.5.dp.toPx()
                val bodyLeft = size.width * 0.1f
                val bodyTop = size.height * 0.3f
                val bodyWidth = size.width * 0.8f
                val bodyHeight = size.height * 0.55f

                drawRoundRect(
                    color = ElectricGreen,
                    topLeft = Offset(bodyLeft, bodyTop),
                    size = Size(bodyWidth, bodyHeight),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = strokeWidth),
                )

                drawCircle(
                    color = ElectricGreen,
                    radius = size.width * 0.14f,
                    center = Offset(bodyLeft + bodyWidth / 2f, bodyTop + bodyHeight / 2f),
                    style = Stroke(width = strokeWidth),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Camera Access Required",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "HighlightCam needs camera access to preview the pitch and detect goals.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRequestPermission,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "Grant Permission",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}
