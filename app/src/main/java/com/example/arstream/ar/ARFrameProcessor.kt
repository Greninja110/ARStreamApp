// File: ARStreamApp/app/src/main/java/com/example/arstream/ar/ARFrameProcessor.kt
package com.example.arstream.ar

import android.media.Image
import com.example.arstream.utils.Logger
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Processes AR frames to extract relevant data for streaming
 */
class ARFrameProcessor {
    companion object {
        private const val TAG = "ARFrameProcessor"
    }

    /**
     * Extract camera image from AR frame
     */
    fun extractCameraImage(frame: Frame): Image? {
        return try {
            frame.acquireCameraImage()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to acquire camera image", e)
            null
        }
    }

    /**
     * Extract depth image from AR frame if available
     */
    fun extractDepthImage(frame: Frame): Image? {
        return try {
            if (frame.hasDepthImage()) {
                frame.acquireDepthImage()
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to acquire depth image", e)
            null
        }
    }

    /**
     * Extract detected planes from AR frame
     */
    fun extractPlanes(frame: Frame): List<PlaneData> {
        val planes = mutableListOf<PlaneData>()

        try {
            for (plane in frame.getUpdatedTrackables(Plane::class.java)) {
                if (plane.trackingState != com.google.ar.core.TrackingState.TRACKING) {
                    continue
                }

                val center = plane.centerPose
                val extentX = plane.extentX
                val extentZ = plane.extentZ

                planes.add(
                    PlaneData(
                        id = plane.hashCode(),
                        centerX = center.tx(),
                        centerY = center.ty(),
                        centerZ = center.tz(),
                        normalX = center.qx(),
                        normalY = center.qy(),
                        normalZ = center.qz(),
                        extentX = extentX,
                        extentZ = extentZ,
                        type = when (plane.type) {
                            Plane.Type.HORIZONTAL_UPWARD_FACING -> "FLOOR"
                            Plane.Type.HORIZONTAL_DOWNWARD_FACING -> "CEILING"
                            Plane.Type.VERTICAL -> "WALL"
                            else -> "OTHER"
                        }
                    )
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error extracting planes", e)
        }

        Logger.d(TAG, "Extracted ${planes.size} planes")
        return planes
    }

    /**
     * Extract camera pose information
     */
    fun extractCameraPose(frame: Frame): CameraPoseData {
        val cameraPose = frame.camera.pose

        return CameraPoseData(
            tx = cameraPose.tx(),
            ty = cameraPose.ty(),
            tz = cameraPose.tz(),
            qx = cameraPose.qx(),
            qy = cameraPose.qy(),
            qz = cameraPose.qz(),
            qw = cameraPose.qw()
        )
    }

    /**
     * Extract point cloud data from AR frame
     */
    fun extractPointCloud(frame: Frame): PointCloudData? {
        val pointCloud = frame.acquirePointCloud() ?: return null

        try {
            val buffer = pointCloud.points
            val pointCount = buffer.remaining() / 4 // Each point has 4 floats

            // Copy points to a new buffer
            val points = FloatArray(pointCount * 3) // Only copy XYZ (not confidence)
            val confidences = FloatArray(pointCount)

            // Position buffer at the beginning
            buffer.rewind()

            // Extract points and confidences
            for (i in 0 until pointCount) {
                points[i * 3] = buffer.get()     // X
                points[i * 3 + 1] = buffer.get() // Y
                points[i * 3 + 2] = buffer.get() // Z
                confidences[i] = buffer.get()    // Confidence
            }

            Logger.d(TAG, "Extracted point cloud with $pointCount points")

            return PointCloudData(
                points = points,
                confidences = confidences,
                pointCount = pointCount
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Error extracting point cloud", e)
            return null
        } finally {
            pointCloud.release()
        }
    }

    /**
     * Extract camera intrinsics for projecting 3D points to 2D
     */
    fun extractCameraIntrinsics(frame: Frame): CameraIntrinsicsData {
        val camera = frame.camera

        // Get camera intrinsics
        val intrinsics = camera.textureIntrinsics

        // Get focal length and principal point
        val focalLength = FloatArray(2)
        intrinsics.getFocalLength(focalLength)

        val principalPoint = FloatArray(2)
        intrinsics.getPrincipalPoint(principalPoint)

        return CameraIntrinsicsData(
            focalLengthX = focalLength[0],
            focalLengthY = focalLength[1],
            principalPointX = principalPoint[0],
            principalPointY = principalPoint[1],
            imageWidth = intrinsics.imageDimensions[0],
            imageHeight = intrinsics.imageDimensions[1]
        )
    }

    /**
     * Convert depth image to depth map (float values in meters)
     */
    fun depthImageToDepthMap(depthImage: Image): FloatArray? {
        try {
            val width = depthImage.width
            val height = depthImage.height
            val depthValues = FloatArray(width * height)

            val plane = depthImage.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixelIndex = y * rowStride + x * pixelStride

                    // Get depth value in millimeters and convert to meters
                    val depthMillimeters = buffer.getShort(pixelIndex).toFloat()
                    val depthMeters = depthMillimeters / 1000.0f

                    depthValues[y * width + x] = depthMeters
                }
            }

            Logger.d(TAG, "Converted depth image to depth map (${width}x${height})")
            return depthValues
        } catch (e: Exception) {
            Logger.e(TAG, "Error converting depth image to depth map", e)
            return null
        }
    }
}

/**
 * Data class for AR detected plane information
 */
data class PlaneData(
    val id: Int,
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val normalX: Float,
    val normalY: Float,
    val normalZ: Float,
    val extentX: Float,
    val extentZ: Float,
    val type: String
)

/**
 * Data class for camera pose information
 */
data class CameraPoseData(
    val tx: Float,
    val ty: Float,
    val tz: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val qw: Float
)

/**
 * Data class for point cloud data
 */
data class PointCloudData(
    val points: FloatArray,
    val confidences: FloatArray,
    val pointCount: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PointCloudData

        if (!points.contentEquals(other.points)) return false
        if (!confidences.contentEquals(other.confidences)) return false
        if (pointCount != other.pointCount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = points.contentHashCode()
        result = 31 * result + confidences.contentHashCode()
        result = 31 * result + pointCount
        return result
    }
}

/**
 * Data class for camera intrinsics
 */
data class CameraIntrinsicsData(
    val focalLengthX: Float,
    val focalLengthY: Float,
    val principalPointX: Float,
    val principalPointY: Float,
    val imageWidth: Int,
    val imageHeight: Int
)