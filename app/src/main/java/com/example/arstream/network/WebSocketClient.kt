// File: ARStreamApp/app/src/main/java/com/example/arstream/network/WebSocketClient.kt
package com.example.arstream.network

import com.example.arstream.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer

/**
 * WebSocket client for bi-directional communication with the server
 */
class ARWebSocketClient(
    private val serverUri: URI,
    private val messageHandler: (String) -> Unit,
    private val binaryHandler: (ByteBuffer) -> Unit
) {
    companion object {
        private const val TAG = "WebSocketClient"
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private var client: InternalWebSocketClient? = null

    // Connection state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    /**
     * Connect to the WebSocket server
     */
    fun connect() {
        if (client?.isOpen == true) {
            Logger.d(TAG, "WebSocket already connected")
            return
        }

        try {
            _connectionState.value = ConnectionState.CONNECTING

            client = InternalWebSocketClient(serverUri, messageHandler, binaryHandler)
            client?.connect()

            Logger.d(TAG, "Connecting to WebSocket server: $serverUri")
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
            Logger.e(TAG, "Failed to connect to WebSocket server", e)
        }
    }

    /**
     * Disconnect from the WebSocket server
     */
    fun disconnect() {
        try {
            client?.close()
            client = null
            _connectionState.value = ConnectionState.DISCONNECTED
            Logger.d(TAG, "Disconnected from WebSocket server")
        } catch (e: Exception) {
            Logger.e(TAG, "Error disconnecting from WebSocket server", e)
        }
    }

    /**
     * Send a text message to the server
     */
    fun sendMessage(message: String): Boolean {
        if (client?.isOpen != true) {
            Logger.w(TAG, "Cannot send message - WebSocket not connected")
            return false
        }

        try {
            client?.send(message)
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Error sending WebSocket message", e)
            return false
        }
    }

    /**
     * Send binary data to the server
     */
    fun sendBinary(data: ByteArray): Boolean {
        if (client?.isOpen != true) {
            Logger.w(TAG, "Cannot send binary data - WebSocket not connected")
            return false
        }

        try {
            client?.send(data)
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Error sending WebSocket binary data", e)
            return false
        }
    }

    /**
     * Check if connection is active
     */
    fun isConnected(): Boolean {
        return client?.isOpen == true
    }

    /**
     * Internal WebSocket client implementation
     */
    private inner class InternalWebSocketClient(
        serverUri: URI,
        private val messageHandler: (String) -> Unit,
        private val binaryHandler: (ByteBuffer) -> Unit
    ) : WebSocketClient(serverUri) {

        override fun onOpen(handshakedata: ServerHandshake?) {
            Logger.i(TAG, "WebSocket connection opened")
            _connectionState.value = ConnectionState.CONNECTED
        }

        override fun onMessage(message: String?) {
            message?.let {
                messageHandler(it)
            }
        }

        override fun onMessage(bytes: ByteBuffer?) {
            bytes?.let {
                binaryHandler(it)
            }
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            Logger.i(TAG, "WebSocket connection closed: code=$code, reason=$reason, remote=$remote")
            _connectionState.value = ConnectionState.DISCONNECTED

            // Auto-reconnect if closed unexpectedly
            if (remote && code != 1000) {
                Logger.d(TAG, "Attempting to reconnect in ${RECONNECT_DELAY_MS}ms")
                Thread.sleep(RECONNECT_DELAY_MS)
                reconnect()
            }
        }

        override fun onError(ex: Exception?) {
            Logger.e(TAG, "WebSocket error", ex)
            _connectionState.value = ConnectionState.ERROR
        }
    }

    /**
     * Reconnect to the WebSocket server
     */
    fun reconnect() {
        disconnect()
        connect()
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
}