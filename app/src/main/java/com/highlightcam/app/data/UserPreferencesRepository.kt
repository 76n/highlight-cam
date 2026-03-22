package com.highlightcam.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.domain.GoalZoneSet
import com.highlightcam.app.domain.NormalizedPoint
import com.highlightcam.app.domain.RecordingConfig
import com.highlightcam.app.domain.VideoQuality
import com.highlightcam.app.tracking.AutoFollowConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        private object Keys {
            val GOAL_A_P1_X = floatPreferencesKey("goal_a_p1_x")
            val GOAL_A_P1_Y = floatPreferencesKey("goal_a_p1_y")
            val GOAL_A_P2_X = floatPreferencesKey("goal_a_p2_x")
            val GOAL_A_P2_Y = floatPreferencesKey("goal_a_p2_y")
            val GOAL_A_P3_X = floatPreferencesKey("goal_a_p3_x")
            val GOAL_A_P3_Y = floatPreferencesKey("goal_a_p3_y")
            val GOAL_A_P4_X = floatPreferencesKey("goal_a_p4_x")
            val GOAL_A_P4_Y = floatPreferencesKey("goal_a_p4_y")
            val GOAL_B_P1_X = floatPreferencesKey("goal_b_p1_x")
            val GOAL_B_P1_Y = floatPreferencesKey("goal_b_p1_y")
            val GOAL_B_P2_X = floatPreferencesKey("goal_b_p2_x")
            val GOAL_B_P2_Y = floatPreferencesKey("goal_b_p2_y")
            val GOAL_B_P3_X = floatPreferencesKey("goal_b_p3_x")
            val GOAL_B_P3_Y = floatPreferencesKey("goal_b_p3_y")
            val GOAL_B_P4_X = floatPreferencesKey("goal_b_p4_x")
            val GOAL_B_P4_Y = floatPreferencesKey("goal_b_p4_y")
            val SEGMENT_DURATION = intPreferencesKey("segment_duration")
            val BUFFER_SEGMENTS = intPreferencesKey("buffer_segments")
            val SECONDS_AFTER_EVENT = intPreferencesKey("seconds_after_event")
            val VIDEO_QUALITY = stringPreferencesKey("video_quality")
            val DEBUG_MODE = booleanPreferencesKey("debug_mode")
            val DETECTION_SENSITIVITY = floatPreferencesKey("detection_sensitivity")
            val SOUND_ON_SAVE = booleanPreferencesKey("sound_on_save")
            val AUTO_FOLLOW_ENABLED = booleanPreferencesKey("auto_follow_enabled")
            val AUTO_FOLLOW_ALPHA = floatPreferencesKey("auto_follow_alpha")
        }

        val goalZoneSet: Flow<GoalZoneSet?> =
            dataStore.data.map { prefs ->
                val a1x = prefs[Keys.GOAL_A_P1_X] ?: return@map null
                val a1y = prefs[Keys.GOAL_A_P1_Y] ?: return@map null
                val a2x = prefs[Keys.GOAL_A_P2_X] ?: return@map null
                val a2y = prefs[Keys.GOAL_A_P2_Y] ?: return@map null
                val a3x = prefs[Keys.GOAL_A_P3_X] ?: return@map null
                val a3y = prefs[Keys.GOAL_A_P3_Y] ?: return@map null
                val a4x = prefs[Keys.GOAL_A_P4_X] ?: return@map null
                val a4y = prefs[Keys.GOAL_A_P4_Y] ?: return@map null

                val goalA =
                    GoalZone(
                        id = "a",
                        label = "Goal A",
                        p1 = NormalizedPoint(a1x, a1y),
                        p2 = NormalizedPoint(a2x, a2y),
                        p3 = NormalizedPoint(a3x, a3y),
                        p4 = NormalizedPoint(a4x, a4y),
                    )

                val goalB =
                    run {
                        val b1x = prefs[Keys.GOAL_B_P1_X] ?: return@run null
                        val b1y = prefs[Keys.GOAL_B_P1_Y] ?: return@run null
                        val b2x = prefs[Keys.GOAL_B_P2_X] ?: return@run null
                        val b2y = prefs[Keys.GOAL_B_P2_Y] ?: return@run null
                        val b3x = prefs[Keys.GOAL_B_P3_X] ?: return@run null
                        val b3y = prefs[Keys.GOAL_B_P3_Y] ?: return@run null
                        val b4x = prefs[Keys.GOAL_B_P4_X] ?: return@run null
                        val b4y = prefs[Keys.GOAL_B_P4_Y] ?: return@run null
                        GoalZone(
                            id = "b",
                            label = "Goal B",
                            p1 = NormalizedPoint(b1x, b1y),
                            p2 = NormalizedPoint(b2x, b2y),
                            p3 = NormalizedPoint(b3x, b3y),
                            p4 = NormalizedPoint(b4x, b4y),
                        )
                    }

                GoalZoneSet(goalA = goalA, goalB = goalB)
            }

        val recordingConfig: Flow<RecordingConfig> =
            dataStore.data.map { prefs ->
                RecordingConfig(
                    segmentDurationSeconds = prefs[Keys.SEGMENT_DURATION] ?: 5,
                    bufferSegments = prefs[Keys.BUFFER_SEGMENTS] ?: 6,
                    secondsAfterEvent = prefs[Keys.SECONDS_AFTER_EVENT] ?: 10,
                    videoQuality =
                        prefs[Keys.VIDEO_QUALITY]?.let {
                            VideoQuality.valueOf(it)
                        } ?: VideoQuality.HD_720,
                )
            }

        val debugModeEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.DEBUG_MODE] ?: false }
        val detectionSensitivity: Flow<Float> = dataStore.data.map { it[Keys.DETECTION_SENSITIVITY] ?: 0.5f }
        val soundOnSave: Flow<Boolean> = dataStore.data.map { it[Keys.SOUND_ON_SAVE] ?: true }

        val autoFollowConfig: Flow<AutoFollowConfig> =
            dataStore.data.map { prefs ->
                AutoFollowConfig(
                    enabled = prefs[Keys.AUTO_FOLLOW_ENABLED] ?: false,
                    smoothingAlpha = prefs[Keys.AUTO_FOLLOW_ALPHA] ?: AutoFollowConfig.DEFAULT_ALPHA,
                )
            }

        suspend fun updateGoalZoneSet(zoneSet: GoalZoneSet) {
            dataStore.edit { prefs ->
                val a = zoneSet.goalA
                prefs[Keys.GOAL_A_P1_X] = a.p1.x
                prefs[Keys.GOAL_A_P1_Y] = a.p1.y
                prefs[Keys.GOAL_A_P2_X] = a.p2.x
                prefs[Keys.GOAL_A_P2_Y] = a.p2.y
                prefs[Keys.GOAL_A_P3_X] = a.p3.x
                prefs[Keys.GOAL_A_P3_Y] = a.p3.y
                prefs[Keys.GOAL_A_P4_X] = a.p4.x
                prefs[Keys.GOAL_A_P4_Y] = a.p4.y
                val b = zoneSet.goalB
                if (b != null) {
                    prefs[Keys.GOAL_B_P1_X] = b.p1.x
                    prefs[Keys.GOAL_B_P1_Y] = b.p1.y
                    prefs[Keys.GOAL_B_P2_X] = b.p2.x
                    prefs[Keys.GOAL_B_P2_Y] = b.p2.y
                    prefs[Keys.GOAL_B_P3_X] = b.p3.x
                    prefs[Keys.GOAL_B_P3_Y] = b.p3.y
                    prefs[Keys.GOAL_B_P4_X] = b.p4.x
                    prefs[Keys.GOAL_B_P4_Y] = b.p4.y
                } else {
                    prefs.remove(Keys.GOAL_B_P1_X)
                    prefs.remove(Keys.GOAL_B_P1_Y)
                    prefs.remove(Keys.GOAL_B_P2_X)
                    prefs.remove(Keys.GOAL_B_P2_Y)
                    prefs.remove(Keys.GOAL_B_P3_X)
                    prefs.remove(Keys.GOAL_B_P3_Y)
                    prefs.remove(Keys.GOAL_B_P4_X)
                    prefs.remove(Keys.GOAL_B_P4_Y)
                }
            }
        }

        suspend fun updateRecordingConfig(config: RecordingConfig) {
            dataStore.edit { prefs ->
                prefs[Keys.SEGMENT_DURATION] = config.segmentDurationSeconds
                prefs[Keys.BUFFER_SEGMENTS] = config.bufferSegments
                prefs[Keys.SECONDS_AFTER_EVENT] = config.secondsAfterEvent
                prefs[Keys.VIDEO_QUALITY] = config.videoQuality.name
            }
        }

        suspend fun updateDebugMode(enabled: Boolean) {
            dataStore.edit { it[Keys.DEBUG_MODE] = enabled }
        }

        suspend fun updateDetectionSensitivity(sensitivity: Float) {
            dataStore.edit { it[Keys.DETECTION_SENSITIVITY] = sensitivity }
        }

        suspend fun updateSoundOnSave(enabled: Boolean) {
            dataStore.edit { it[Keys.SOUND_ON_SAVE] = enabled }
        }

        suspend fun updateAutoFollowEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.AUTO_FOLLOW_ENABLED] = enabled }
        }

        suspend fun updateAutoFollowAlpha(alpha: Float) {
            dataStore.edit { it[Keys.AUTO_FOLLOW_ALPHA] = alpha }
        }
    }
