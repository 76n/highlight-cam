package com.highlightcam.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.domain.RecordingConfig
import com.highlightcam.app.domain.VideoQuality
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
            val GOAL_ZONE_X = floatPreferencesKey("goal_zone_x")
            val GOAL_ZONE_Y = floatPreferencesKey("goal_zone_y")
            val GOAL_ZONE_W = floatPreferencesKey("goal_zone_w")
            val GOAL_ZONE_H = floatPreferencesKey("goal_zone_h")
            val SEGMENT_DURATION = intPreferencesKey("segment_duration")
            val BUFFER_SEGMENTS = intPreferencesKey("buffer_segments")
            val SECONDS_AFTER_EVENT = intPreferencesKey("seconds_after_event")
            val VIDEO_QUALITY = stringPreferencesKey("video_quality")
            val DEBUG_MODE = booleanPreferencesKey("debug_mode")
            val DETECTION_SENSITIVITY = floatPreferencesKey("detection_sensitivity")
            val SOUND_ON_SAVE = booleanPreferencesKey("sound_on_save")
        }

        val goalZone: Flow<GoalZone?> =
            dataStore.data.map { prefs ->
                val x = prefs[Keys.GOAL_ZONE_X]
                val y = prefs[Keys.GOAL_ZONE_Y]
                val w = prefs[Keys.GOAL_ZONE_W]
                val h = prefs[Keys.GOAL_ZONE_H]
                if (x != null && y != null && w != null && h != null) {
                    GoalZone(x, y, w, h)
                } else {
                    null
                }
            }

        val recordingConfig: Flow<RecordingConfig> =
            dataStore.data.map { prefs ->
                RecordingConfig(
                    segmentDurationSeconds = prefs[Keys.SEGMENT_DURATION] ?: 3,
                    bufferSegments = prefs[Keys.BUFFER_SEGMENTS] ?: 10,
                    secondsAfterEvent = prefs[Keys.SECONDS_AFTER_EVENT] ?: 10,
                    videoQuality =
                        prefs[Keys.VIDEO_QUALITY]?.let {
                            VideoQuality.valueOf(it)
                        } ?: VideoQuality.HD_720,
                )
            }

        val debugModeEnabled: Flow<Boolean> =
            dataStore.data.map { prefs ->
                prefs[Keys.DEBUG_MODE] ?: false
            }

        val detectionSensitivity: Flow<Float> =
            dataStore.data.map { prefs ->
                prefs[Keys.DETECTION_SENSITIVITY] ?: 0.5f
            }

        val soundOnSave: Flow<Boolean> =
            dataStore.data.map { prefs ->
                prefs[Keys.SOUND_ON_SAVE] ?: true
            }

        suspend fun updateGoalZone(zone: GoalZone) {
            dataStore.edit { prefs ->
                prefs[Keys.GOAL_ZONE_X] = zone.xFraction
                prefs[Keys.GOAL_ZONE_Y] = zone.yFraction
                prefs[Keys.GOAL_ZONE_W] = zone.widthFraction
                prefs[Keys.GOAL_ZONE_H] = zone.heightFraction
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
            dataStore.edit { prefs ->
                prefs[Keys.DEBUG_MODE] = enabled
            }
        }

        suspend fun updateDetectionSensitivity(sensitivity: Float) {
            dataStore.edit { prefs ->
                prefs[Keys.DETECTION_SENSITIVITY] = sensitivity
            }
        }

        suspend fun updateSoundOnSave(enabled: Boolean) {
            dataStore.edit { prefs ->
                prefs[Keys.SOUND_ON_SAVE] = enabled
            }
        }
    }
