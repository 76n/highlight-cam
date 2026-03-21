package com.highlightcam.app.ui.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LibraryClip(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val dateAdded: Long,
    val sizeBytes: Long,
    val filePath: String?,
)

sealed class LibraryUiState {
    data object Loading : LibraryUiState()

    data object Empty : LibraryUiState()

    data class Loaded(val clips: List<LibraryClip>) : LibraryUiState()

    data class Error(val message: String) : LibraryUiState()
}

enum class SortOrder { NEWEST, OLDEST, LONGEST }

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
        val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

        private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
        val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

        private var allClips: List<LibraryClip> = emptyList()

        init {
            loadClips()
        }

        fun loadClips() {
            viewModelScope.launch {
                _uiState.value = LibraryUiState.Loading
                try {
                    allClips = queryMediaStore()
                    if (allClips.isEmpty()) {
                        _uiState.value = LibraryUiState.Empty
                    } else {
                        _uiState.value = LibraryUiState.Loaded(sortClips(allClips))
                    }
                } catch (e: Exception) {
                    _uiState.value = LibraryUiState.Error(e.message ?: "Failed to load clips")
                }
            }
        }

        fun setSortOrder(order: SortOrder) {
            _sortOrder.value = order
            if (allClips.isNotEmpty()) {
                _uiState.value = LibraryUiState.Loaded(sortClips(allClips))
            }
        }

        fun deleteClip(clip: LibraryClip) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.delete(clip.uri, null, null)
                }
                loadClips()
            }
        }

        private fun sortClips(clips: List<LibraryClip>): List<LibraryClip> =
            when (_sortOrder.value) {
                SortOrder.NEWEST -> clips.sortedByDescending { it.dateAdded }
                SortOrder.OLDEST -> clips.sortedBy { it.dateAdded }
                SortOrder.LONGEST -> clips.sortedByDescending { it.durationMs }
            }

        @Suppress("InlinedApi")
        private suspend fun queryMediaStore(): List<LibraryClip> =
            withContext(Dispatchers.IO) {
                val clips = mutableListOf<LibraryClip>()
                val projection =
                    arrayOf(
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.DURATION,
                        MediaStore.Video.Media.DATE_ADDED,
                        MediaStore.Video.Media.SIZE,
                        MediaStore.Video.Media.DATA,
                    )

                val selection: String
                val selectionArgs: Array<String>
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
                    selectionArgs = arrayOf("${Environment.DIRECTORY_MOVIES}/HighlightCam/%")
                } else {
                    @Suppress("DEPRECATION")
                    selection = "${MediaStore.Video.Media.DATA} LIKE ?"
                    selectionArgs = arrayOf("%/HighlightCam/%")
                }

                appContext.contentResolver
                    .query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        "${MediaStore.Video.Media.DATE_ADDED} DESC",
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            clips.add(
                                LibraryClip(
                                    id = id,
                                    uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                                    displayName = cursor.getString(nameCol) ?: "Unknown",
                                    durationMs = cursor.getLong(durationCol),
                                    dateAdded = cursor.getLong(dateCol),
                                    sizeBytes = cursor.getLong(sizeCol),
                                    filePath = cursor.getString(dataCol),
                                ),
                            )
                        }
                    }
                clips
            }
    }
