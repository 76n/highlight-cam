package com.highlightcam.app.ui.recording

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.highlightcam.app.data.SessionRepository
import com.highlightcam.app.data.UserPreferencesRepository
import com.highlightcam.app.detection.DebugInfo
import com.highlightcam.app.detection.HighlightDetectionEngine
import com.highlightcam.app.detection.TFLiteDetector
import com.highlightcam.app.domain.DetectionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModelStorageTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionRepository: SessionRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var mockEngine: HighlightDetectionEngine
    private lateinit var mockDetector: TFLiteDetector
    private lateinit var viewModel: RecordingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = SessionRepository()

        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(testDispatcher + SupervisorJob()),
            ) {
                tmpFolder.newFile("test_prefs.preferences_pb")
            }
        userPreferencesRepository = UserPreferencesRepository(dataStore)

        mockEngine = mock(HighlightDetectionEngine::class.java)
        whenever(mockEngine.eventFlow).thenReturn(MutableSharedFlow<DetectionEvent>())
        whenever(mockEngine.debugInfo).thenReturn(MutableStateFlow(DebugInfo()))

        mockDetector = mock(TFLiteDetector::class.java)
        whenever(mockDetector.modelAvailable).thenReturn(MutableStateFlow(false))

        viewModel =
            RecordingViewModel(
                mock(Context::class.java),
                sessionRepository,
                userPreferencesRepository,
                mockEngine,
                mockDetector,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `low storage warning is initially false`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            assertFalse(viewModel.lowStorageWarning.value)
        }

    @Test
    fun `sound on save defaults to true`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            assert(viewModel.soundOnSave.value)
        }
}
