package com.highlightcam.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.domain.GoalZoneSet
import com.highlightcam.app.domain.NormalizedPoint
import com.highlightcam.app.domain.RecordingConfig
import com.highlightcam.app.domain.VideoQuality
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: UserPreferencesRepository

    @Before
    fun setup() {
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = testScope,
                produceFile = { tempFolder.newFile("test_prefs.preferences_pb") },
            )
        repo = UserPreferencesRepository(dataStore)
    }

    @Test
    fun `goalZoneSet is null by default`() =
        testScope.runTest {
            assertNull(repo.goalZoneSet.first())
        }

    @Test
    fun `goalZoneSet round-trip`() =
        testScope.runTest {
            val zoneSet =
                GoalZoneSet(
                    goalA =
                        GoalZone(
                            "a",
                            "Goal A",
                            NormalizedPoint(0.1f, 0.2f),
                            NormalizedPoint(0.3f, 0.2f),
                            NormalizedPoint(0.3f, 0.6f),
                            NormalizedPoint(0.1f, 0.6f),
                        ),
                    goalB =
                        GoalZone(
                            "b",
                            "Goal B",
                            NormalizedPoint(0.7f, 0.2f),
                            NormalizedPoint(0.9f, 0.2f),
                            NormalizedPoint(0.9f, 0.6f),
                            NormalizedPoint(0.7f, 0.6f),
                        ),
                )
            repo.updateGoalZoneSet(zoneSet)
            val saved = repo.goalZoneSet.first()!!
            assertEquals(0.1f, saved.goalA.p1.x, 0.001f)
            assertEquals(0.2f, saved.goalA.p1.y, 0.001f)
            assertEquals(0.9f, saved.goalB.p2.x, 0.001f)
        }

    @Test
    fun `goalZoneSet overwrites previous value`() =
        testScope.runTest {
            repo.updateGoalZoneSet(GoalZoneSet.DEFAULT)
            val updated =
                GoalZoneSet(
                    goalA =
                        GoalZone(
                            "a",
                            "Goal A",
                            NormalizedPoint(0.0f, 0.0f),
                            NormalizedPoint(0.5f, 0.0f),
                            NormalizedPoint(0.5f, 0.5f),
                            NormalizedPoint(0.0f, 0.5f),
                        ),
                    goalB =
                        GoalZone(
                            "b",
                            "Goal B",
                            NormalizedPoint(0.5f, 0.5f),
                            NormalizedPoint(1.0f, 0.5f),
                            NormalizedPoint(1.0f, 1.0f),
                            NormalizedPoint(0.5f, 1.0f),
                        ),
                )
            repo.updateGoalZoneSet(updated)
            val saved = repo.goalZoneSet.first()!!
            assertEquals(0.0f, saved.goalA.p1.x, 0.001f)
            assertEquals(1.0f, saved.goalB.p3.x, 0.001f)
        }

    @Test
    fun `recordingConfig returns defaults when nothing persisted`() =
        testScope.runTest {
            val config = repo.recordingConfig.first()
            assertEquals(3, config.segmentDurationSeconds)
            assertEquals(10, config.bufferSegments)
            assertEquals(10, config.secondsAfterEvent)
            assertEquals(VideoQuality.HD_720, config.videoQuality)
        }

    @Test
    fun `recordingConfig round-trip`() =
        testScope.runTest {
            val config =
                RecordingConfig(
                    segmentDurationSeconds = 5,
                    bufferSegments = 20,
                    secondsAfterEvent = 15,
                    videoQuality = VideoQuality.FHD_1080,
                )
            repo.updateRecordingConfig(config)
            assertEquals(config, repo.recordingConfig.first())
        }

    @Test
    fun `debugMode defaults to false`() =
        testScope.runTest {
            assertFalse(repo.debugModeEnabled.first())
        }

    @Test
    fun `debugMode round-trip`() =
        testScope.runTest {
            repo.updateDebugMode(true)
            assertTrue(repo.debugModeEnabled.first())
        }

    @Test
    fun `detectionSensitivity defaults to 0_5f`() =
        testScope.runTest {
            assertEquals(0.5f, repo.detectionSensitivity.first(), 0.001f)
        }

    @Test
    fun `detectionSensitivity round-trip`() =
        testScope.runTest {
            repo.updateDetectionSensitivity(0.8f)
            assertEquals(0.8f, repo.detectionSensitivity.first(), 0.001f)
        }

    @Test
    fun `soundOnSave defaults to true`() =
        testScope.runTest {
            assertTrue(repo.soundOnSave.first())
        }

    @Test
    fun `soundOnSave round-trip`() =
        testScope.runTest {
            repo.updateSoundOnSave(false)
            assertFalse(repo.soundOnSave.first())
        }
}
