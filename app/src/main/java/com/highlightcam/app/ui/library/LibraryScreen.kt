@file:Suppress("DEPRECATION")

package com.highlightcam.app.ui.library

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import com.highlightcam.app.R
import com.highlightcam.app.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GreenColor = Color(0xFF00FF88)

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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFF080808))
                .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        LibraryTopBar(
            sortOrder = sortOrder,
            onSortChanged = viewModel::setSortOrder,
            onBack = { navController.popBackStack() },
        )

        when (val state = uiState) {
            is LibraryUiState.Loading -> LoadingState()
            is LibraryUiState.Empty ->
                EmptyState(onStartRecording = {
                    navController.navigate(Routes.RECORDING) {
                        popUpTo(Routes.LIBRARY) { inclusive = true }
                    }
                })
            is LibraryUiState.Error -> ErrorState(state.message)
            is LibraryUiState.Loaded ->
                ClipGrid(
                    clips = state.clips,
                    onClipTap = { playerClip = it },
                    onClipLongPress = { selectedClip = it },
                )
        }
    }

    if (selectedClip != null) {
        ClipDetailSheet(
            clip = selectedClip!!,
            onDismiss = { selectedClip = null },
            onShare = { shareClip(navController.context, it) },
            onDelete = {
                showDeleteConfirm = true
            },
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
private fun LibraryTopBar(
    sortOrder: SortOrder,
    onSortChanged: (SortOrder) -> Unit,
    onBack: () -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Text("←", color = Color.White, fontSize = 20.sp)
        }
        Text(
            stringResource(R.string.library_title),
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { showSortMenu = true }) {
                Text("Sort", color = GreenColor, style = MaterialTheme.typography.labelLarge)
            }
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                SortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (order) {
                                    SortOrder.NEWEST -> stringResource(R.string.library_sort_newest)
                                    SortOrder.OLDEST -> stringResource(R.string.library_sort_oldest)
                                    SortOrder.LONGEST -> stringResource(R.string.library_sort_longest)
                                },
                                color = if (order == sortOrder) GreenColor else Color.White,
                            )
                        },
                        onClick = {
                            onSortChanged(order)
                            showSortMenu = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = GreenColor)
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = Color(0xFFFF3B3B), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptyState(onStartRecording: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            SoccerFieldIllustration()
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                stringResource(R.string.library_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.library_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onStartRecording,
                colors = ButtonDefaults.buttonColors(containerColor = GreenColor, contentColor = Color(0xFF080808)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.library_start_recording), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SoccerFieldIllustration() {
    val lineColor = Color(0xFF1A1A1A)
    androidx.compose.foundation.Canvas(modifier = Modifier.size(200.dp, 130.dp)) {
        val s = Stroke(width = 2.dp.toPx())
        drawRect(color = lineColor, style = s)
        drawLine(lineColor, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 2.dp.toPx())
        drawCircle(color = lineColor, radius = size.height * 0.2f, center = Offset(size.width / 2, size.height / 2), style = s)
        drawRect(lineColor, topLeft = Offset(0f, size.height * 0.25f), size = Size(size.width * 0.18f, size.height * 0.5f), style = s)
        drawRect(
            lineColor,
            topLeft = Offset(size.width * 0.82f, size.height * 0.25f),
            size = Size(size.width * 0.18f, size.height * 0.5f),
            style = s,
        )
        drawRect(lineColor, topLeft = Offset(0f, size.height * 0.35f), size = Size(size.width * 0.08f, size.height * 0.3f), style = s)
        drawRect(
            lineColor,
            topLeft = Offset(size.width * 0.92f, size.height * 0.35f),
            size = Size(size.width * 0.08f, size.height * 0.3f),
            style = s,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipGrid(
    clips: List<LibraryClip>,
    onClipTap: (LibraryClip) -> Unit,
    onClipLongPress: (LibraryClip) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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

    LaunchedEffect(clip.uri) {
        thumbnail =
            withContext(Dispatchers.IO) {
                loadThumbnail(context, clip.uri)
            }
    }

    Column(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
        ) {
            if (thumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = thumbnail!!,
                    contentDescription = clip.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                ShimmerBox(modifier = Modifier.fillMaxSize())
            }

            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    formatDuration(clip.durationMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            formatDate(clip.dateAdded),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmer_offset",
    )
    Box(
        modifier =
            modifier.background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1A1A1A), Color(0xFF2A2A2A), Color(0xFF1A1A1A)),
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
        containerColor = Color(0xFF121212),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(clip.displayName, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${formatDate(clip.dateAdded)}  •  ${formatFileSize(clip.sizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    onShare(clip)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GreenColor, contentColor = Color(0xFF080808)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(stringResource(R.string.library_share))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onDelete(clip) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B3B), contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(stringResource(R.string.library_delete))
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
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.library_delete), color = Color(0xFFFF3B3B))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowRewindButton(true)
                    setShowFastForwardButton(true)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        IconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(8.dp),
        ) {
            Text("←", color = Color.White, fontSize = 24.sp)
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
