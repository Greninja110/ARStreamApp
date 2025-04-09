// File: ARStreamApp/app/src/main/java/com/example/arstream/network/RTSPServer.kt
package com.example.arstream.network

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import com.example.arstream.utils.Logger
import com.example.arstream.utils.PreferenceManager
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import com.pedro.rtplibrary.rtsp.RtspCamera2
import java.nio.ByteBuffer

/**
 * RTSP server for streaming video and data
 */
class RTSPServer(
    private val context: Context,
    private val connectChecker: ConnectCheckerRtsp
) {
    companion object {
        private const val TAG = "RTSPServer"

        // RTSP stream paths
        const val VIDEO_PATH = "/camera"
        const val DEPTH_PATH = "/depth"
        const val AR_DATA_PATH = "/ardata"
    }

    private val preferenceManager = PreferenceManager(context)
    private var rtspCamera: RtspCamera2? = null

    // Connection state
    private var isConnected = false

    /**
     * Initialize the RTSP server with camera preview surface
     */
    fun initialize(surfaceView: android.view.SurfaceView) {
        try {
            rtspCamera = RtspCamera2(surfaceView, connectChecker)
            Logger.d(TAG, "RTSP server initialized with preview surface")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize RTSP server", e)
            throw e
        }
    }

    /**
     * Start RTSP streaming
     */
    fun startStream() {
        if (rtspCamera == null) {
            Logger.e(TAG, "Cannot start stream - RTSP camera not initialized")
            return
        }

        try {
            // Get resolution and bitrate from preferences
            val (width, height) = preferenceManager.getResolutionDimensions()
            val bitrate = preferenceManager.getBitrate()

            // Configure video encoder
            rtspCamera?.prepareVideo(
                width,
                height,
                bitrate,
                30, // frames per second
                0,  // rotation
                true, // hardwareRotation
                MediaFormat.MIMETYPE_VIDEO_AVC // H.264 codec
            )

            // Configure audio encoder
            rtspCamera?.prepareAudio(
                128 * 1024, // 128 kbps audio bitrate
                44100,      // sample rate
                true,       // stereo
                false,      // echo canceler
                false       // noise suppressor
            )

            // Build RTSP URL
            val rtspUrl = "rtsp://${preferenceManager.serverAddress}:${preferenceManager.serverPort}${VIDEO_PATH}"

            // Start streaming
            val result = rtspCamera?.startStream(rtspUrl)

            if (result == true) {
                isConnected = true
                Logger.i(TAG, "RTSP stream started: $rtspUrl")
            } else {
                Logger.e(TAG, "Failed to start RTSP stream")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error starting RTSP stream", e)
        }
    }

    /**
     * Stop RTSP streaming
     */
    fun stopStream() {
        try {
            rtspCamera?.stopStream()
            isConnected = false
            Logger.i(TAG, "RTSP stream stopped")
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping RTSP stream", e)
        }
    }

    /**
     * Check if streaming is active
     */
    fun isStreaming(): Boolean {
        return rtspCamera?.isStreaming ?: false
    }

    /**
     * Send custom data over RTSP (for depth maps and AR data)
     */
    fun sendCustomData(data: ByteArray, streamPath: String) {
        // This is a simplified version - in a real implementation,
        // you would need to properly multiplex this data into the RTSP stream
        // or use a separate connection for this data
        Logger.d(TAG, "Sending ${data.size} bytes of custom data to $streamPath")
    }

    /**
     * Clean up resources
     */
    fun release() {
        try {
            if (isStreaming()) {
                stopStream()
            }
            rtspCamera?.release()
            rtspCamera = null
            Logger.d(TAG, "RTSP server released")
        } catch (e: Exception) {
            Logger.e(TAG, "Error releasing RTSP server", e)
        }
    }
}