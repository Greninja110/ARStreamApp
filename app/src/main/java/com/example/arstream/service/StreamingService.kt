// File: ARStreamApp/app/src/main/java/com/example/arstream/service/StreamingService.kt
package com.example.arstream.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.SurfaceView
import androidx.core.app.NotificationCompat
import com.example.arstream.R
import com.example.arstream.ar.ARCoreSession
import com.example.arstream.ar.ARFrameProcessor
import com.example.arstream.ar.ARDataSerializer
import com.example.arstream.network.NetworkManager
import com.example.arstream.ui.MainActivity
import com.example.arstream.utils.Logger
import com.example.arstream.utils.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service for AR processing and streaming
 */
class StreamingService : Service() {
    companion object {
        private const val TAG = "StreamingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ar_stream_channel"
        private const val CHANNEL_NAME = "AR Streaming"
    }

    // Binder for clients
    private val binder = LocalBinder()

    // Coroutine scope
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var processingJob: Job? = null

    // AR components
    private lateinit var arCoreSession: ARCoreSession
    private lateinit var arFrameProcessor: ARFrameProcessor
    private lateinit var arDataSerializer: ARDataSerializer

    // Network component
    private lateinit var networkManager: NetworkManager

    // Preferences
    private lateinit var preferenceManager: PreferenceManager

    // Service state
    private val isProcessing = AtomicBoolean(false)
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    // Network info
    private val _networkInfo = MutableStateFlow<NetworkInfo?>(null)
    val networkInfo: StateFlow<NetworkInfo?> = _networkInfo

    /**
     * Surface view for preview
     */
    private var previewSurface: SurfaceView? = null

    override fun onCreate() {
        super.onCreate()
        Logger.d(TAG, "StreamingService onCreate")

        // Initialize preferences
        preferenceManager = PreferenceManager(this)

        // Initialize AR components
        arCoreSession = ARCoreSession(this)
        arFrameProcessor = ARFrameProcessor()
        arDataSerializer = ARDataSerializer()

        // Initialize network manager
        networkManager = NetworkManager(this)

        // Monitor network status
        serviceScope.launch {
            networkManager.connectionStatus.collect { status ->
                val currentInfo = _networkInfo.value ?: NetworkInfo("", "", NetworkStatus.DISCONNECTED)

                _networkInfo.value = currentInfo.copy(
                    status = when (status) {
                        NetworkManager.ConnectionStatus.DISCONNECTED -> NetworkStatus.DISCONNECTED
                        NetworkManager.ConnectionStatus.CONNECTING -> NetworkStatus.CONNECTING
                        NetworkManager.ConnectionStatus.CONNECTED -> NetworkStatus.CONNECTED
                        NetworkManager.ConnectionStatus.PARTIAL -> NetworkStatus.PARTIAL
                        NetworkManager.ConnectionStatus.ERROR -> NetworkStatus.ERROR
                    }
                )

                // Update streaming state
                _isStreaming.value = status == NetworkManager.ConnectionStatus.CONNECTED ||
                        status == NetworkManager.ConnectionStatus.PARTIAL
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.d(TAG, "StreamingService onStartCommand")

        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Logger.d(TAG, "StreamingService onBind")
        return binder
    }

    /**
     * Set the surface view for preview
     */
    fun setPreviewSurface(surfaceView: SurfaceView) {
        previewSurface = surfaceView

        // Initialize network manager with surface view
        networkManager.initialize(surfaceView)

        Logger.d(TAG, "Preview surface set")
    }

    /**
     * Start AR processing and streaming
     */
    fun startProcessing() {
        if (isProcessing.getAndSet(true)) {
            Logger.d(TAG, "Processing already active")
            return
        }

        Logger.d(TAG, "Starting AR processing and streaming")

        // Connect to server
        networkManager.connect()

        // Start processing job
        processingJob = serviceScope.launch {
            var frameCount = 0
            val frameInterval = 1000L / 30 // 30 fps target

            while (isProcessing.get()) {
                try {
                    // Get AR frame
                    val frame = arCoreSession.update()

                    if (frame != null) {
                        frameCount++

                        // Extract camera pose
                        val cameraPose = arFrameProcessor.extractCameraPose(frame)
                        val poseJson = arDataSerializer.serializeCameraPose(cameraPose)
                        networkManager.sendARData("camera_pose", poseJson)

                        // Extract planes (every 10 frames to reduce bandwidth)
                        if (frameCount % 10 == 0) {
                            val planes = arFrameProcessor.extractPlanes(frame)
                            val planesJson = arDataSerializer.serializePlanes(planes)
                            networkManager.sendARData("planes", planesJson)
                        }

                        // Extract point cloud (every 30 frames)
                        if (frameCount % 30 == 0) {
                            arFrameProcessor.extractPointCloud(frame)?.let { pointCloud ->
                                val pointCloudData = arDataSerializer.serializePointCloud(pointCloud)
                                networkManager.sendBinaryARData("point_cloud", pointCloudData)
                            }
                        }

                        // Extract depth if enabled (every 5 frames)
                        if (preferenceManager.enableDepth && frameCount % 5 == 0 && arCoreSession.isDepthSupported()) {
                            arFrameProcessor.extractDepthImage(frame)?.let { depthImage ->
                                val depthMap = arFrameProcessor.depthImageToDepthMap(depthImage)
                                if (depthMap != null) {
                                    val depthData = arDataSerializer.serializeDepthMap(
                                        depthMap,
                                        depthImage.width,
                                        depthImage.height
                                    )
                                    networkManager.sendBinaryARData("depth_map", depthData)
                                }
                                depthImage.close()
                            }
                        }

                        // Extract camera intrinsics (once at the beginning)
                        if (frameCount == 1) {
                            val intrinsics = arFrameProcessor.extractCameraIntrinsics(frame)
                            val intrinsicsJson = arDataSerializer.serializeCameraIntrinsics(intrinsics)
                            networkManager.sendARData("camera_intrinsics", intrinsicsJson)
                        }
                    }

                    // Sleep to maintain frame rate
                    delay(frameInterval)
                } catch (e: Exception) {
                    Logger.e(TAG, "Error processing AR frame", e)
                    delay(1000) // Wait a bit before retrying
                }
            }
        }

        updateNotification()
    }

    /**
     * Stop AR processing and streaming
     */
    fun stopProcessing() {
        if (!isProcessing.getAndSet(false)) {
            Logger.d(TAG, "Processing already stopped")
            return
        }

        Logger.d(TAG, "Stopping AR processing and streaming")

        // Cancel processing job
        processingJob?.cancel()
        processingJob = null

        // Disconnect from server
        networkManager.disconnect()

        updateNotification()
    }

    /**
     * Initialize AR session
     */
    fun initializeARSession(activity: MainActivity) {
        try {
            arCoreSession.createSession(activity)
            Logger.d(TAG, "AR session initialized")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize AR session", e)
        }
    }

    /**
     * Resume AR session
     */
    fun resumeARSession(activity: MainActivity) {
        try {
            arCoreSession.resumeSession(activity)
            Logger.d(TAG, "AR session resumed")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to resume AR session", e)
        }
    }

    /**
     * Pause AR session
     */
    fun pauseARSession() {
        try {
            arCoreSession.pauseSession()
            Logger.d(TAG, "AR session paused")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to pause AR session", e)
        }
    }

    /**
     * Create notification channel (required for Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AR Streaming Service Channel"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create notification for foreground service
     */
    private fun createNotification(): Notification {
        createNotificationChannel()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (isProcessing.get()) {
            "AR streaming active"
        } else {
            "AR streaming idle"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AR Stream")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    /**
     * Update the notification with current status
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        Logger.d(TAG, "StreamingService onDestroy")

        // Ensure processing is stopped
        if (isProcessing.get()) {
            stopProcessing()
        }

        // Release resources
        arCoreSession.closeSession()
        networkManager.release()

        super.onDestroy()
    }

    /**
     * Binder class for client to access service
     */
    inner class LocalBinder : Binder() {
        fun getService(): StreamingService = this@StreamingService
    }

    /**
     * Network information data class
     */
    data class NetworkInfo(
        val serverAddress: String,
        val streamUrl: String,
        val status: NetworkStatus
    )

    /**
     * Network status enum
     */
    enum class NetworkStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        PARTIAL,
        ERROR
    }
}