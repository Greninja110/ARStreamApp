// File: ARStreamApp/app/src/main/java/com/example/arstream/ui/CameraViewModel.kt
package com.example.arstream.ui

import androidx.camera.core.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.arstream.service.StreamingService
import com.example.arstream.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * ViewModel for camera and streaming state
 */
class CameraViewModel : ViewModel() {
    companion object {
        private const val TAG = "CameraViewModel"
    }

    private var streamingService: StreamingService? = null

    // Streaming state
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    // Network info
    private val _networkInfo = MutableStateFlow<StreamingService.NetworkInfo?>(null)
    val networkInfo: StateFlow<StreamingService.NetworkInfo?> = _networkInfo

    /**
     * Set streaming service and collect its state
     */
    fun setStreamingService(service: StreamingService?) {
        streamingService = service

        if (service != null) {
            viewModelScope.launch {
                service.isStreaming.collect {
                    _isStreaming.value = it
                }
            }

            viewModelScope.launch {
                service.networkInfo.collect {
                    _networkInfo.value = it
                }
            }
        }

        Logger.d(TAG, "StreamingService reference set")
    }

    /**
     * Clear streaming service reference
     */
    fun clearStreamingService() {
        streamingService = null
        _isStreaming.value = false
        _networkInfo.value = null
        Logger.d(TAG, "StreamingService reference cleared")
    }

    /**
     * Set surface provider for camera preview
     */
    fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider) {
        Logger.d(TAG, "SurfaceProvider received in ViewModel")
        // Surface provider is passed to service where the camera is managed
    }

    /**
     * Start streaming
     */
    fun startStreaming() {
        streamingService?.startProcessing()
        Logger.d(TAG, "Start streaming requested")
    }

    /**
     * Stop streaming
     */
    fun stopStreaming() {
        streamingService?.stopProcessing()
        Logger.d(TAG, "Stop streaming requested")
    }

    override fun onCleared() {
        super.onCleared()
        Logger.d(TAG, "ViewModel cleared")
    }
}