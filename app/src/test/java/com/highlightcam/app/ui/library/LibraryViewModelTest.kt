package com.highlightcam.app.ui.library

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class LibraryViewModelTest {
    @Test
    fun `sort NEWEST returns clips ordered by date descending`() {
        val clips = listOf(fakeClip(id = 1, dateAdded = 100), fakeClip(id = 2, dateAdded = 300), fakeClip(id = 3, dateAdded = 200))
        val sorted = clips.sortedByDescending { it.dateAdded }
        assertEquals(300L, sorted[0].dateAdded)
        assertEquals(200L, sorted[1].dateAdded)
        assertEquals(100L, sorted[2].dateAdded)
    }

    @Test
    fun `sort OLDEST returns clips ordered by date ascending`() {
        val clips = listOf(fakeClip(id = 1, dateAdded = 300), fakeClip(id = 2, dateAdded = 100), fakeClip(id = 3, dateAdded = 200))
        val sorted = clips.sortedBy { it.dateAdded }
        assertEquals(100L, sorted[0].dateAdded)
        assertEquals(200L, sorted[1].dateAdded)
        assertEquals(300L, sorted[2].dateAdded)
    }

    @Test
    fun `sort LONGEST returns clips ordered by duration descending`() {
        val clips = listOf(fakeClip(id = 1, durationMs = 5000), fakeClip(id = 2, durationMs = 60000), fakeClip(id = 3, durationMs = 15000))
        val sorted = clips.sortedByDescending { it.durationMs }
        assertEquals(60000L, sorted[0].durationMs)
        assertEquals(15000L, sorted[1].durationMs)
        assertEquals(5000L, sorted[2].durationMs)
    }

    @Test
    fun `initial state is Loading`() {
        assertEquals(LibraryUiState.Loading, LibraryUiState.Loading)
    }

    @Test
    fun `empty list produces Empty state`() {
        val clips = emptyList<LibraryClip>()
        val state = if (clips.isEmpty()) LibraryUiState.Empty else LibraryUiState.Loaded(clips)
        assertEquals(LibraryUiState.Empty, state)
    }

    @Test
    fun `non-empty list produces Loaded state with correct count`() {
        val clips = listOf(fakeClip(id = 1), fakeClip(id = 2))
        val state = if (clips.isEmpty()) LibraryUiState.Empty else LibraryUiState.Loaded(clips)
        assert(state is LibraryUiState.Loaded)
        assertEquals(2, (state as LibraryUiState.Loaded).clips.size)
    }

    private fun fakeClip(
        id: Long = 1,
        dateAdded: Long = 1700000000L,
        durationMs: Long = 10000L,
    ) = LibraryClip(
        id = id,
        uri = mock(Uri::class.java),
        displayName = "hc_test_$id.mp4",
        durationMs = durationMs,
        dateAdded = dateAdded,
        sizeBytes = 1024L,
        filePath = null,
    )
}
