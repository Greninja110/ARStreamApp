// File: ARStreamApp/app/src/main/java/com/example/arstream/utils/PermissionHelper.kt
package com.example.arstream.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper class for managing app permissions
 */
class PermissionHelper(private val context: Context) {
    companion object {
        const val TAG = "PermissionHelper"
        const val PERMISSION_REQUEST_CODE = 1001

        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET
        )
    }

    /**
     * Check if all required permissions are granted
     */
    fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request all required permissions
     */
    fun requestPermissions(activity: Activity) {
        val permissionsToRequest = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            Logger.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest,
                PERMISSION_REQUEST_CODE
            )
        }
    }

    /**
     * Check if permission requests were denied
     */
    fun shouldShowRationale(activity: Activity): Boolean {
        return REQUIRED_PERMISSIONS.any {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
    }

    /**
     * Open app settings to allow the user to grant permissions
     */
    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        activity.startActivity(intent)
    }
}