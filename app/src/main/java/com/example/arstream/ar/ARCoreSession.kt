// File: ARStreamApp/app/src/main/java/com/example/arstream/ar/ARCoreSession.kt
package com.example.arstream.ar

import android.app.Activity
import android.content.Context
import com.example.arstream.utils.Logger
import com.google.ar.core.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the ARCore session and related functionality
 */
class ARCoreSession(private val context: Context) {
    companion object {
        private const val TAG = "ARCoreSession"
    }

    private var session: Session? = null
    private val isSessionCreated = AtomicBoolean(false)
    private val depthSupported = AtomicBoolean(false)

    // AR configuration
    private val config = Config(null)

    /**
     * Creates an ARCore session and sets up the configuration
     */
    fun createSession(activity: Activity) {
        if (isSessionCreated.get()) {
            Logger.d(TAG, "Session already created")
            return
        }

        try {
            Logger.d(TAG, "Creating ARCore session")

            // Check ARCore availability
            when (ArCoreApk.getInstance().checkAvailability(context)) {
                ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                    Logger.e(TAG, "ARCore is not supported on this device")
                    throw Exception("ARCore is not supported on this device")
                }
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> {
                    Logger.w(TAG, "ARCore is not installed or is too old")

                    // Request ARCore installation or update
                    val installStatus = ArCoreApk.getInstance().requestInstall(
                        activity, true
                    )

                    if (installStatus != ArCoreApk.InstallStatus.INSTALLED) {
                        Logger.e(TAG, "ARCore installation required: $installStatus")
                        return
                    }
                }
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                    Logger.i(TAG, "ARCore is supported and installed")
                }
                else -> {
                    Logger.w(TAG, "ARCore availability unknown")
                }
            }

            // Create the session
            session = Session(context)

            // Configure the session
            configureSession()

            isSessionCreated.set(true)
            Logger.d(TAG, "ARCore session created successfully")

        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create ARCore session", e)
            throw e
        }
    }

    /**
     * Configure the ARCore session with needed features
     */
    private fun configureSession() {
        session?.let { session ->
            // Configure camera to use auto focus
            config.setFocusMode(Config.FocusMode.AUTO)

            // Configure to use the back camera
            config.setCameraDirection(Config.CameraDirection.BACK)

            // Enable plane detection
            config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL)

            // Check and enable depth if supported
            if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                config.setDepthMode(Config.DepthMode.AUTOMATIC)
                depthSupported.set(true)
                Logger.i(TAG, "Depth mode supported and enabled")
            } else {
                Logger.w(TAG, "Depth mode not supported on this device")
                depthSupported.set(false)
            }

            // Apply configuration
            session.configure(config)

            Logger.d(TAG, "ARCore session configured")
        } ?: run {
            Logger.e(TAG, "Cannot configure session - session is null")
        }
    }

    /**
     * Resume the ARCore session
     */
    fun resumeSession(activity: Activity) {
        if (!isSessionCreated.get()) {
            createSession(activity)
            return
        }

        try {
            session?.resume()
            Logger.d(TAG, "ARCore session resumed")
        } catch (e: CameraNotAvailableException) {
            Logger.e(TAG, "Camera not available", e)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to resume ARCore session", e)
        }
    }

    /**
     * Pause the ARCore session
     */
    fun pauseSession() {
        session?.pause()
        Logger.d(TAG, "ARCore session paused")
    }

    /**
     * Close the ARCore session
     */
    fun closeSession() {
        session?.close()
        session = null
        isSessionCreated.set(false)
        Logger.d(TAG, "ARCore session closed")
    }

    /**
     * Updates the ARCore session and returns the current frame
     */
    fun update(): Frame? {
        return try {
            session?.update()
        } catch (e: CameraNotAvailableException) {
            Logger.e(TAG, "Camera not available during update", e)
            null
        } catch (e: Exception) {
            Logger.e(TAG, "Error during ARCore update", e)
            null
        }
    }

    /**
     * Get the AR session
     */
    fun getSession(): Session? = session

    /**
     * Check if depth is supported
     */
    fun isDepthSupported(): Boolean = depthSupported.get()

    /**
     * Check if session is created and valid
     */
    fun isSessionValid(): Boolean = isSessionCreated.get() && session != null
}