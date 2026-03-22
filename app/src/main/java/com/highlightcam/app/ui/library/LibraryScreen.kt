@file:Suppress("DEPRECATION")

package com.highlightcam.app.ui.library

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import com.highlightcam.app.R
import com.highlightcam.app.navigation.Routes
import com.highlightcam.app.ui.components.GhostButton
import com.highlightcam.app.ui.components.HCIconButton
import com.highlightcam.app.ui.components.PrimaryButton
import com.highlightcam.app.ui.theme.HC
import com.highlightcam.app.ui.theme.HCType
import com.highlightcam.app.ui.theme.Radii
import com.highlightcam.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("LongMethod")
@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    var playerClip by remember { mutableStateOf<LibraryClip?>(null) }
    var selectedClip by remember { mutableStateOf<LibraryClip?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (playerClip != null) {
        FullScreenPlayer(clip = playerClip!!, onBack = { playerClip = null })
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(HC.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.s24, vertical = Spacing.s24),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HCIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = { navController.popBackStack() })
                Spacer(Modifier.width(Spacing.s16))
                Text("Highlights", style = HCType.heading, color = HC.white, modifier = Modifier.weight(1f))
                HCIconButton(Icons.Filled.Sort, onClick = { viewModel.setSortOrder(nextSort(sortOrder)) })
            }

            when (val state = uiState) {
                is LibraryUiState.Loading -> LoadingState()
                is LibraryUiState.Empty ->
                    EmptyState(
                        onStartRecording = {
                            navController.navigate(Routes.RECORDING) { popUpTo(Routes.LIBRARY) { inclusive = true } }
                        },
                    )
                is LibraryUiState.Error -> ErrorState(state.message)
                is LibraryUiState.Loaded ->
                    ClipGrid(
                        clips = state.clips,
                        onClipTap = { playerClip = it },
                        onClipLongPress = { selectedClip = it },
                    )
            }
        }
    }

    if (selectedClip != null) {
        ClipDetailSheet(
            clip = selectedClip!!,
            onDismiss = { selectedClip = null },
            onShare = {
                shareClip(navController.context, it)
                selectedClip = null
            },
            onDelete = { showDeleteConfirm = true },
        )
    }

    if (showDeleteConfirm && selectedClip != null) {
        DeleteConfirmDialog(
            onConfirm = {
                viewModel.deleteClip(selectedClip!!)
                showDeleteConfirm = false
                selectedClip = null
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

private fun nextSort(current: SortOrder): SortOrder =
    when (current) {
        SortOrder.NEWEST -> SortOrder.OLDEST
        SortOrder.OLDEST -> SortOrder.LONGEST
        SortOrder.LONGEST -> SortOrder.NEWEST
    }

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        CircularProgressIndicator(color = HC.green)
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(message, color = HC.red, style = HCType.body)
    }
}

@Composable
private fun EmptyState(onStartRecording: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SoccerPitchCanvas()
            Spacer(Modifier.height(Spacing.s32))
            Text("No highlights yet", style = HCType.title, color = HC.white)
            Spacer(Modifier.height(Spacing.s8))
            Text(
                stringResource(R.string.library_empty_body),
                style = HCType.body,
                color = HC.white60,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.s64),
            )
            Spacer(Modifier.height(Spacing.s40))
            PrimaryButton("Start Recording", onClick = onStartRecording, fixedWidth = 180.dp)
        }
    }
}

@Composable
private fun SoccerPitchCanvas() {
    val lineColor = HC.white10
    androidx.compose.foundation.Canvas(Modifier.size(280.dp, 175.dp)) {
        val s = Stroke(1.dp.toPx())
        drawRect(lineColor, style = s)
        drawLine(lineColor, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
        drawCircle(lineColor, 40.dp.toPx(), center, style = s)
        drawCircle(lineColor, 1.5.dp.toPx(), center)
        val penW = size.width * 0.183f
        val penH = size.height * 0.44f
        val penTop = (size.height - penH) / 2
        drawRect(lineColor, Offset(0f, penTop), Size(penW, penH), style = s)
        drawRect(lineColor, Offset(size.width - penW, penTop), Size(penW, penH), style = s)
        val gaW = size.width * 0.07f
        val gaH = size.height * 0.22f
        val gaTop = (size.height - gaH) / 2
        drawRect(lineColor, Offset(0f, gaTop), Size(gaW, gaH), style = s)
        drawRect(lineColor, Offset(size.width - gaW, gaTop), Size(gaW, gaH), style = s)
        val gW = 4.dp.toPx()
        val gH = size.height * 0.12f
        val gTop = (size.height - gH) / 2
        drawRect(lineColor, Offset(-gW / 2, gTop), Size(gW, gH), style = s)
        drawRect(lineColor, Offset(size.width - gW / 2, gTop), Size(gW, gH), style = s)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipGrid(
    clips: List<LibraryClip>,
    onClipTap: (LibraryClip) -> Unit,
    onClipLongPress: (LibraryClip) -> Unit,
) {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding =
            PaddingValues(
                start = Spacing.s20,
                top = Spacing.s20,
                end = Spacing.s20,
                bottom = Spacing.s20 + navBarBottom,
            ),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(clips, key = { it.id }) { clip ->
            ClipCell(
                clip = clip,
                modifier =
                    Modifier.combinedClickable(
                        onClick = { onClipTap(clip) },
                        onLongClick = { onClipLongPress(clip) },
                    ),
            )
        }
    }
}

@Composable
private fun ClipCell(
    clip: LibraryClip,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var thumbnail by remember(clip.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(clip.uri) { thumbnail = withContext(Dispatchers.IO) { loadThumbnail(context, clip.uri) } }

    Column(modifier = modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Radii.r12)),
        ) {
            if (thumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = thumbnail!!,
                    contentDescription = clip.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                ShimmerBox(Modifier.fillMaxSize())
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(Radii.r100))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(formatDuration(clip.durationMs), style = HCType.micro, color = HC.white)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            formatDate(clip.dateAdded),
            style = HCType.micro,
            color = HC.white60,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        -300f,
        1000f,
        infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "shim_off",
    )
    Box(
        modifier.background(
            Brush.linearGradient(
                listOf(HC.surface, HC.surfaceRaised, HC.surface),
                start = Offset(offset, 0f),
                end = Offset(offset + 300f, 0f),
            ),
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipDetailSheet(
    clip: LibraryClip,
    onDismiss: () -> Unit,
    onShare: (LibraryClip) -> Unit,
    onDelete: (LibraryClip) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = HC.surface,
    ) {
        Column(Modifier.padding(horizontal = Spacing.s20).padding(bottom = Spacing.s32)) {
            Text(clip.displayName, style = HCType.title, color = HC.white)
            Spacer(Modifier.height(Spacing.s4))
            Text(
                "${formatDate(clip.dateAdded)}  ·  ${formatFileSize(clip.sizeBytes)}",
                style = HCType.micro,
                color = HC.white60,
            )
            Spacer(Modifier.height(Spacing.s32))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                GhostButton(
                    stringResource(R.string.library_share),
                    onClick = { onShare(clip) },
                    modifier = Modifier.weight(1f),
                )
                GhostButton(
                    stringResource(R.string.library_delete),
                    onClick = { onDelete(clip) },
                    modifier = Modifier.weight(1f),
                    borderColor = HC.red.copy(alpha = 0.4f),
                    textColor = HC.red,
                )
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_delete_title)) },
        text = { Text(stringResource(R.string.library_delete_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.library_delete), color = HC.red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Suppress("LongMethod")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun FullScreenPlayer(
    clip: LibraryClip,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val player =
        remember {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(clip.uri))
                prepare()
                playWhenReady = true
            }
        }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            player.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var seekEvent by remember { mutableStateOf<Pair<Float, Boolean>?>(null) }

    LaunchedEffect(lastInteraction) {
        delay(3000)
        controlsVisible = false
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentPosition = player.currentPosition
            duration = player.duration.coerceAtLeast(1)
            isPlaying = player.isPlaying
            delay(200)
        }
    }

    LaunchedEffect(seekEvent) {
        if (seekEvent != null) {
            delay(350)
            seekEvent = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        lastInteraction = System.currentTimeMillis()
                    },
                    onDoubleTap = { offset ->
                        lastInteraction = System.currentTimeMillis()
                        controlsVisible = true
                        val forward = offset.x > size.width / 2
                        val seekMs = if (forward) 5000L else -5000L
                        player.seekTo((player.currentPosition + seekMs).coerceIn(0, player.duration))
                        seekEvent = (offset.x.toFloat() / size.width) to forward
                    },
                )
            },
    ) {
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = seekEvent != null,
            enter = scaleIn(initialScale = 0.6f) + fadeIn(),
            exit = scaleOut(targetScale = 1.3f) + fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            seekEvent?.let { (_, forward) ->
                Icon(
                    if (forward) Icons.Filled.Forward5 else Icons.Filled.Replay5,
                    null,
                    tint = HC.white,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .padding(start = if (forward) Spacing.s64 else 0.dp, end = if (!forward) Spacing.s64 else 0.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(500)),
        ) {
            Box(Modifier.fillMaxSize()) {
                HCIconButton(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBack,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(Spacing.s20),
                )

                Box(
                    Modifier
                        .size(64.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(HC.white10)
                        .clickable {
                            if (player.isPlaying) player.pause() else player.play()
                            lastInteraction = System.currentTimeMillis()
                        },
                    Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        null,
                        tint = HC.white,
                        modifier = Modifier.size(36.dp),
                    )
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = Spacing.s20, end = Spacing.s20, bottom = Spacing.s32),
                ) {
                    Row(Modifier.fillMaxWidth().padding(bottom = Spacing.s8), Arrangement.SpaceBetween) {
                        Text(formatDuration(currentPosition), style = HCType.micro, color = HC.white)
                        Text(formatDuration(duration), style = HCType.micro, color = HC.white.copy(alpha = 0.4f))
                    }
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        onValueChange = { frac ->
                            player.seekTo((frac * duration).toLong())
                            lastInteraction = System.currentTimeMillis()
                        },
                        colors =
                            SliderDefaults.colors(
                                thumbColor = Color.Transparent,
                                activeTrackColor = HC.green,
                                inactiveTrackColor = HC.white20,
                            ),
                    )
                }
            }
        }
    }
}

private fun loadThumbnail(
    context: android.content.Context,
    uri: android.net.Uri,
): ImageBitmap? =
    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val frame: Bitmap? = retriever.getFrameAtTime(0)
        retriever.release()
        frame?.asImageBitmap()
    } catch (_: Exception) {
        null
    }

private fun shareClip(
    context: android.content.Context,
    clip: LibraryClip,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, clip.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(intent, "Share clip"))
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatDate(epochSeconds: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(epochSeconds * 1000))
}

private fun formatFileSize(bytes: Long): String =
    when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
