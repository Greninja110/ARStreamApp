// File: ARStreamApp/app/src/main/java/com/example/arstream/network/NetworkManager.kt
package com.example.arstream.network

import android.content.Context
import com.example.arstream.utils.Logger
import com.example.arstream.utils.PreferenceManager
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer

/**
 * Manages network connections (RTSP and WebSocket)
 */
class NetworkManager(private val context: Context) : ConnectCheckerRtsp {
    companion object {
        private const val TAG = "NetworkManager"
        private const val WS_PATH = "/ws"
    }

    private val preferenceManager = PreferenceManager(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // RTSP server
    private lateinit var rtspServer: RTSPServer

    // WebSocket client
    private var webSocketClient: ARWebSocketClient? = null

    // Network status
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    // Last error message
    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage

    /**
     * Initialize the network manager
     */
    fun initialize(surfaceView: android.view.SurfaceView) {
        rtspServer = RTSPServer(context, this)
        rtspServer.initialize(surfaceView)

        Logger.d(TAG, "NetworkManager initialized")
    }

    /**
     * Connect to the server (both RTSP and WebSocket)
     */
    fun connect() {
        coroutineScope.launch {
            try {
                _connectionStatus.value = ConnectionStatus.CONNECTING

                // Connect WebSocket first
                connectWebSocket()

                // Then start RTSP stream
                rtspServer.startStream()

                // Update status
                if (webSocketClient?.isConnected() == true && rtspServer.isStreaming()) {
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    Logger.i(TAG, "Successfully connected to server")
                } else {
                    _connectionStatus.value = ConnectionStatus.PARTIAL
                    Logger.w(TAG, "Partially connected to server")
                }
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.ERROR
                _lastErrorMessage.value = e.message
                Logger.e(TAG, "Failed to connect to server", e)
            }
        }
    }

    /**
     * Disconnect from the server
     */
    fun disconnect() {
        coroutineScope.launch {
            try {
                // Stop RTSP streaming
                rtspServer.stopStream()

                // Disconnect WebSocket
                webSocketClient?.disconnect()
                webSocketClient = null

                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                Logger.i(TAG, "Disconnected from server")
            } catch (e: Exception) {
                _lastErrorMessage.value = e.message
                Logger.e(TAG, "Error disconnecting from server", e)
            }
        }
    }

    /**
     * Connect to the WebSocket server
     */
    private fun connectWebSocket() {
        try {
            // Create WebSocket URI
            val wsUri = URI("ws://${preferenceManager.serverAddress}:${preferenceManager.serverPort}$WS_PATH")

            // Create WebSocket client
            webSocketClient = ARWebSocketClient(
                wsUri,
                { handleTextMessage(it) },
                { handleBinaryMessage(it) }
            )

            // Connect to server
            webSocketClient?.connect()

            Logger.d(TAG, "Connecting to WebSocket server: $wsUri")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to connect to WebSocket server", e)
            throw e
        }
    }

    /**
     * Handle text messages from the server
     */
    private fun handleTextMessage(message: String) {
        try {
            Logger.d(TAG, "Received WebSocket message: $message")

            // Parse JSON message
            val json = JSONObject(message)
            val type = json.getString("type")

            when (type) {
                "connect_success" -> {
                    Logger.i(TAG, "Server acknowledged connection")
                }
                "config" -> {
                    // Handle configuration updates from server
                    // For example, changing resolution or quality
                }
                "command" -> {
                    // Handle commands from server
                    // For example, start/stop streaming
                    val command = json.getString("command")
                    handleServerCommand(command, json)
                }
                else -> {
                    Logger.w(TAG, "Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error handling WebSocket message", e)
        }
    }

    /**
     * Handle binary messages from the server
     */
    private fun handleBinaryMessage(buffer: ByteBuffer) {
        // For now, we don't expect binary messages from the server
        Logger.d(TAG, "Received binary WebSocket message (${buffer.remaining()} bytes)")
    }

    /**
     * Handle commands from the server
     */
    private fun handleServerCommand(command: String, json: JSONObject) {
        when (command) {
            "start_stream" -> {
                if (!rtspServer.isStreaming()) {
                    rtspServer.startStream()
                }
            }
            "stop_stream" -> {
                if (rtspServer.isStreaming()) {
                    rtspServer.stopStream()
                }
            }
            // Add more commands as needed
        }
    }

    /**
     * Send AR data to the server
     */
    fun sendARData(type: String, data: String) {
        webSocketClient?.let { client ->
            if (client.isConnected()) {
                val message = JSONObject().apply {
                    put("type", "ar_data")
                    put("data_type", type)
                    put("data", data)
                    put("timestamp", System.currentTimeMillis())
                }.toString()

                client.sendMessage(message)
                Logger.d(TAG, "Sent AR data: type=$type, size=${data.length}")
            } else {
                Logger.w(TAG, "Cannot send AR data - WebSocket not connected")
            }
        }
    }

    /**
     * Send binary AR data to the server
     */
    fun sendBinaryARData(type: String, data: ByteArray) {
        webSocketClient?.let { client ->
            if (client.isConnected()) {
                // Add header with type information
                val header = "$type:".toByteArray()

                // Combine header and data
                val combined = ByteArray(header.size + data.size)
                System.arraycopy(header, 0, combined, 0, header.size)
                System.arraycopy(data, 0, combined, header.size, data.size)

                client.sendBinary(combined)
                Logger.d(TAG, "Sent binary AR data: type=$type, size=${data.size}")
            } else {
                Logger.w(TAG, "Cannot send binary AR data - WebSocket not connected")
            }
        }
    }

    /**
     * Release resources
     */
    fun release() {
        try {
            rtspServer.release()
            webSocketClient?.disconnect()
            webSocketClient = null
            Logger.d(TAG, "NetworkManager released")
        } catch (e: Exception) {
            Logger.e(TAG, "Error releasing NetworkManager", e)
        }
    }

    // Connection status enum
    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        PARTIAL,
        ERROR
    }

    // ConnectCheckerRtsp implementation
    override fun onConnectionSuccessRtsp() {
        Logger.i(TAG, "RTSP connection successful")

        coroutineScope.launch {
            if (webSocketClient?.isConnected() == true) {
                _connectionStatus.value = ConnectionStatus.CONNECTED
            } else {
                _connectionStatus.value = ConnectionStatus.PARTIAL
            }
        }
    }

    override fun onConnectionFailedRtsp(reason: String) {
        Logger.e(TAG, "RTSP connection failed: $reason")

        coroutineScope.launch {
            _lastErrorMessage.value = "RTSP connection failed: $reason"

            _connectionStatus.value = if (webSocketClient?.isConnected() == true) {
                ConnectionStatus.PARTIAL
            } else {
                ConnectionStatus.ERROR
            }
        }
    }

    override fun onDisconnectRtsp() {
        Logger.i(TAG, "RTSP disconnected")

        coroutineScope.launch {
            _connectionStatus.value = if (webSocketClient?.isConnected() == true) {
                ConnectionStatus.PARTIAL
            } else {
                ConnectionStatus.DISCONNECTED
            }
        }
    }

    override fun onAuthErrorRtsp() {
        Logger.e(TAG, "RTSP authentication error")

        coroutineScope.launch {
            _lastErrorMessage.value = "RTSP authentication error"

            _connectionStatus.value = if (webSocketClient?.isConnected() == true) {
                ConnectionStatus.PARTIAL
            } else {
                ConnectionStatus.ERROR
            }
        }
    }

    override fun onAuthSuccessRtsp() {
        Logger.i(TAG, "RTSP authentication successful")
    }
}