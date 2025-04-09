// File: ARStreamApp/app/src/main/java/com/example/arstream/ar/ARDataSerializer.kt
package com.example.arstream.ar

import android.media.Image
import com.example.arstream.utils.Logger
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayOutputStream

/**
 * Serializes ARCore data for transmission
 */
class ARDataSerializer {
    companion object {
        private const val TAG = "ARDataSerializer"
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    /**
     * Serialize camera pose to JSON
     */
    fun serializeCameraPose(pose: CameraPoseData): String {
        val adapter = moshi.adapter(CameraPoseData::class.java)
        return adapter.toJson(pose)
    }

    /**
     * Serialize planes data to JSON
     */
    fun serializePlanes(planes: List<PlaneData>): String {
        val json = JSONArray()

        for (plane in planes) {
            val planeJson = JSONObject().apply {
                put("id", plane.id)
                put("centerX", plane.centerX)
                put("centerY", plane.centerY)
                put("centerZ", plane.centerZ)
                put("normalX", plane.normalX)
                put("normalY", plane.normalY)
                put("normalZ", plane.normalZ)
                put("extentX", plane.extentX)
                put("extentZ", plane.extentZ)
                put("type", plane.type)
            }
            json.put(planeJson)
        }

        return json.toString()
    }

    /**
     * Serialize point cloud data to compressed binary format
     */
    fun serializePointCloud(pointCloud: PointCloudData): ByteArray {
        val points = pointCloud.points
        val confidences = pointCloud.confidences
        val pointCount = pointCloud.pointCount

        // Size of header (4 bytes for point count) + data
        val bufferSize = 4 + pointCount * 4 * 4  // 4 bytes for int, 4 floats per point, 4 bytes per float

        val buffer = ByteBuffer.allocate(bufferSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // Write point count
        buffer.putInt(pointCount)

        // Write points and confidences
        for (i in 0 until pointCount) {
            buffer.putFloat(points[i * 3])      // X
            buffer.putFloat(points[i * 3 + 1])  // Y
            buffer.putFloat(points[i * 3 + 2])  // Z
            buffer.putFloat(confidences[i])     // Confidence
        }

        // Compress the data
        return compressData(buffer.array())
    }

    /**
     * Serialize depth map to compressed binary format
     */
    fun serializeDepthMap(depthMap: FloatArray, width: Int, height: Int): ByteArray {
        // Size of header (8 bytes for width and height) + data
        val bufferSize = 8 + depthMap.size * 4  // 4 bytes per int, 4 bytes per float

        val buffer = ByteBuffer.allocate(bufferSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // Write width and height
        buffer.putInt(width)
        buffer.putInt(height)

        // Write depth values
        for (value in depthMap) {
            buffer.putFloat(value)
        }

        // Compress the data
        return compressData(buffer.array())
    }

    /**
     * Serialize camera intrinsics to JSON
     */
    fun serializeCameraIntrinsics(intrinsics: CameraIntrinsicsData): String {
        val adapter = moshi.adapter(CameraIntrinsicsData::class.java)
        return adapter.toJson(intrinsics)
    }

    /**
     * Compress binary data using Deflate algorithm
     */
    private fun compressData(data: ByteArray): ByteArray {
        try {
            val outputStream = ByteArrayOutputStream()
            val deflater = Deflater(Deflater.BEST_COMPRESSION)
            val deflaterStream = DeflaterOutputStream(outputStream, deflater)

            deflaterStream.write(data)
            deflaterStream.finish()
            deflaterStream.close()

            val compressedData = outputStream.toByteArray()
            Logger.d(TAG, "Compressed data size: ${data.size} -> ${compressedData.size} bytes")

            return compressedData
        } catch (e: Exception) {
            Logger.e(TAG, "Error compressing data", e)
            // Return original data if compression fails
            return data
        }
    }

    /**
     * Convert YUV Image to JPEG bytes for transmission
     */
    fun imageToJpegBytes(image: Image, quality: Int = 85): ByteArray {
        // Implementation will depend on Android's image processing APIs
        // For the sake of this example, we'll just return a placeholder
        // In a real implementation, you would convert YUV to RGB then to JPEG
        Logger.d(TAG, "Converting YUV image to JPEG bytes")

        // Placeholder for YUV to JPEG conversion
        // In a real implementation, you would use libraries like libyuv or implement the conversion
        return ByteArray(10) // Placeholder
    }
}