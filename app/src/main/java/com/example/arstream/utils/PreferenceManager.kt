// File: ARStreamApp/app/src/main/java/com/example/arstream/utils/PreferenceManager.kt
package com.example.arstream.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manages application preferences and settings
 */
class PreferenceManager(context: Context) {
    companion object {
        private const val PREFS_NAME = "ar_stream_prefs"
        private const val KEY_SERVER_ADDRESS = "server_address"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_VIDEO_RESOLUTION = "video_resolution"
        private const val KEY_STREAM_QUALITY = "stream_quality"
        private const val KEY_ENABLE_DEPTH = "enable_depth"

        // Default values
        private const val DEFAULT_SERVER_ADDRESS = "192.168.1.100"
        private const val DEFAULT_SERVER_PORT = 8080
        private const val DEFAULT_VIDEO_RESOLUTION = "720p"
        private const val DEFAULT_STREAM_QUALITY = "medium" // low, medium, high
        private const val DEFAULT_ENABLE_DEPTH = true
    }

    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    var serverAddress: String
        get() = preferences.getString(KEY_SERVER_ADDRESS, DEFAULT_SERVER_ADDRESS) ?: DEFAULT_SERVER_ADDRESS
        set(value) = preferences.edit { putString(KEY_SERVER_ADDRESS, value) }

    var serverPort: Int
        get() = preferences.getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT)
        set(value) = preferences.edit { putInt(KEY_SERVER_PORT, value) }

    var videoResolution: String
        get() = preferences.getString(KEY_VIDEO_RESOLUTION, DEFAULT_VIDEO_RESOLUTION) ?: DEFAULT_VIDEO_RESOLUTION
        set(value) = preferences.edit { putString(KEY_VIDEO_RESOLUTION, value) }

    var streamQuality: String
        get() = preferences.getString(KEY_STREAM_QUALITY, DEFAULT_STREAM_QUALITY) ?: DEFAULT_STREAM_QUALITY
        set(value) = preferences.edit { putString(KEY_STREAM_QUALITY, value) }

    var enableDepth: Boolean
        get() = preferences.getBoolean(KEY_ENABLE_DEPTH, DEFAULT_ENABLE_DEPTH)
        set(value) = preferences.edit { putBoolean(KEY_ENABLE_DEPTH, value) }

    /**
     * Get resolution dimensions based on the stored preference
     */
    fun getResolutionDimensions(): Pair<Int, Int> {
        return when (videoResolution) {
            "480p" -> Pair(640, 480)
            "720p" -> Pair(1280, 720)
            "1080p" -> Pair(1920, 1080)
            else -> Pair(1280, 720) // Default to 720p
        }
    }

    /**
     * Get bitrate based on quality setting
     */
    fun getBitrate(): Int {
        return when (streamQuality) {
            "low" -> 1_000_000    // 1 Mbps
            "medium" -> 2_500_000 // 2.5 Mbps
            "high" -> 5_000_000   // 5 Mbps
            else -> 2_500_000     // Default to medium
        }
    }

    /**
     * Reset all preferences to default values
     */
    fun resetToDefaults() {
        preferences.edit {
            putString(KEY_SERVER_ADDRESS, DEFAULT_SERVER_ADDRESS)
            putInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT)
            putString(KEY_VIDEO_RESOLUTION, DEFAULT_VIDEO_RESOLUTION)
            putString(KEY_STREAM_QUALITY, DEFAULT_STREAM_QUALITY)
            putBoolean(KEY_ENABLE_DEPTH, DEFAULT_ENABLE_DEPTH)
        }
    }
}