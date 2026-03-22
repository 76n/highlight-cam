@file:Suppress("DEPRECATION")

package com.highlightcam.app.ui.library

import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.highlightcam.app.ui.components.GhostButton
import com.highlightcam.app.ui.components.HCIconButton
import com.highlightcam.app.ui.theme.HC
import com.highlightcam.app.ui.theme.HCType
import com.highlightcam.app.ui.theme.Radius
import com.highlightcam.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("LongMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var playerClip by remember { mutableStateOf<LibraryClip?>(null) }
    var selectedClip by remember { mutableStateOf<LibraryClip?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (playerClip != null) {
        FullScreenPlayer(clip = playerClip!!, onBack = { playerClip = null })
        return
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDir = LocalLayoutDirection.current

    Box(
        Modifier
            .fillMaxSize()
            .background(HC.bg),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = insets.calculateTopPadding() + Spacing.xl,
                    bottom = insets.calculateBottomPadding() + Spacing.l,
                    start = insets.calculateLeftPadding(layoutDir) + Spacing.l,
                    end = insets.calculateRightPadding(layoutDir) + Spacing.l,
                ),
        ) {
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = Spacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HCIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = { navController.popBackStack() })
                    Spacer(Modifier.width(Spacing.m))
                    Text("Highlights", style = HCType.heading, color = HC.white)
                }
            }

            when (val state = uiState) {
                is LibraryUiState.Loading ->
                    item(key = "loading") { LoadingState() }
                is LibraryUiState.Empty ->
                    item(key = "empty") { EmptyState() }
                is LibraryUiState.Error ->
                    item(key = "error") { ErrorState(state.message) }
                is LibraryUiState.Loaded -> {
                    val rows = state.clips.chunked(2)
                    items(rows.size, key = { rows[it].first().id }) { rowIndex ->
                        val row = rows[rowIndex]
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            row.forEach { clip ->
                                ClipCell(
                                    clip = clip,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .combinedClickable(
                                                onClick = { playerClip = clip },
                                                onLongClick = { selectedClip = clip },
                                            ),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
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
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No highlights yet", style = HCType.heading, color = HC.white)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Highlights will appear here automatically during recording",
            style = HCType.body,
            color = HC.white60,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 260.dp),
        )
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
                .clip(RoundedCornerShape(Radius.m)),
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
                    .padding(Spacing.xs)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
            ) {
                Text(formatDuration(clip.durationMs), style = HCType.micro, color = HC.white)
            }
        }
        Spacer(Modifier.height(Spacing.xs))
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
        windowInsets = WindowInsets.safeDrawing,
    ) {
        Column(Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.xxl)) {
            Text(clip.displayName, style = HCType.title, color = HC.white)
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                "${formatDate(clip.dateAdded)}  ·  ${formatFileSize(clip.sizeBytes)}",
                style = HCType.micro,
                color = HC.white60,
            )
            Spacer(Modifier.height(Spacing.xxl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
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
        onDispose { player.release() }
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
                            .padding(start = if (forward) Spacing.massive else 0.dp, end = if (!forward) Spacing.massive else 0.dp),
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
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(Spacing.l),
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
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.xxl),
                ) {
                    Row(Modifier.fillMaxWidth().padding(bottom = Spacing.xs), Arrangement.SpaceBetween) {
                        Text(formatDuration(currentPosition), style = HCType.micro, color = HC.white)
                        Text(formatDuration(duration), style = HCType.micro, color = HC.white40)
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
