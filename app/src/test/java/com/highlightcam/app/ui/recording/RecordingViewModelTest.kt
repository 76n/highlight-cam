package com.highlightcam.app.ui.recording

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.highlightcam.app.data.SessionRepository
import com.highlightcam.app.data.UserPreferencesRepository
import com.highlightcam.app.domain.RecorderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModelTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionRepository: SessionRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var viewModel: RecordingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = SessionRepository()

        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(testDispatcher + SupervisorJob()),
                produceFile = { tmpFolder.newFile("test.preferences_pb") },
            )
        userPreferencesRepository = UserPreferencesRepository(dataStore)
        viewModel =
            RecordingViewModel(
                mock(Context::class.java),
                sessionRepository,
                userPreferencesRepository,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is idle`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            assertEquals(RecorderState.Idle, viewModel.recorderState.value)
        }

    @Test
    fun `idle to recording transition`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            sessionRepository.updateRecorderState(
                RecorderState.Recording(startedAt = 1000L),
            )
            advanceUntilIdle()

            assertEquals(
                RecorderState.Recording(startedAt = 1000L),
                viewModel.recorderState.value,
            )
        }

    @Test
    fun `idle to recording to idle transition`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            sessionRepository.updateRecorderState(
                RecorderState.Recording(startedAt = 1000L),
            )
            advanceUntilIdle()
            assertEquals(
                RecorderState.Recording(startedAt = 1000L),
                viewModel.recorderState.value,
            )

            sessionRepository.updateRecorderState(RecorderState.Idle)
            advanceUntilIdle()
            assertEquals(RecorderState.Idle, viewModel.recorderState.value)
        }

    @Test
    fun `idle to recording to saving to recording transition`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            sessionRepository.updateRecorderState(
                RecorderState.Recording(startedAt = 1000L),
            )
            advanceUntilIdle()
            assertEquals(
                RecorderState.Recording(startedAt = 1000L),
                viewModel.recorderState.value,
            )

            sessionRepository.updateRecorderState(RecorderState.SavingClip(0.5f))
            advanceUntilIdle()
            assertEquals(
                RecorderState.SavingClip(0.5f),
                viewModel.recorderState.value,
            )

            sessionRepository.updateRecorderState(
                RecorderState.Recording(startedAt = 1000L),
            )
            advanceUntilIdle()
            assertEquals(
                RecorderState.Recording(startedAt = 1000L),
                viewModel.recorderState.value,
            )
        }

    @Test
    fun `clips saved count reflects session repository`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            assertEquals(0, viewModel.clipsSaved.value)

            sessionRepository.incrementClipsSaved()
            advanceUntilIdle()
            assertEquals(1, viewModel.clipsSaved.value)

            sessionRepository.incrementClipsSaved()
            advanceUntilIdle()
            assertEquals(2, viewModel.clipsSaved.value)
        }

    @Test
    fun `error state propagates`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            sessionRepository.updateRecorderState(
                RecorderState.Error("Camera disconnected"),
            )
            advanceUntilIdle()

            val state = viewModel.recorderState.value
            assert(state is RecorderState.Error)
            assertEquals(
                "Camera disconnected",
                (state as RecorderState.Error).message,
            )
        }
}
