// File: ARStreamApp/app/src/main/java/com/example/arstream/ui/MainActivity.kt
package com.example.arstream.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.arstream.R
import com.example.arstream.databinding.ActivityMainBinding
import com.example.arstream.service.StreamingService
import com.example.arstream.utils.Logger
import com.example.arstream.utils.PermissionHelper
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: CameraViewModel
    private lateinit var permissionHelper: PermissionHelper

    // Service connection
    private var streamingService: StreamingService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Logger.d(TAG, "Service connected")
            val binder = service as StreamingService.LocalBinder
            streamingService = binder.getService()
            serviceBound = true

            // Pass service to view model
            viewModel.setStreamingService(streamingService)

            // Initialize AR session
            streamingService?.initializeARSession(this@MainActivity)

            // Set preview surface
            streamingService?.setPreviewSurface(binding.surfaceView)

            // Connect surface to preview
            binding.surfaceView.post {
                viewModel.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Logger.d(TAG, "Service disconnected")
            streamingService = null
            serviceBound = false
            viewModel.clearStreamingService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Logger.d(TAG, "MainActivity onCreate")

        // Inflate layout
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)

        // Initialize permission helper
        permissionHelper = PermissionHelper(this)

        // Initialize view model
        viewModel = ViewModelProvider(this)[CameraViewModel::class.java]

        // Start and bind to service
        val serviceIntent = Intent(this, StreamingService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        // Set up UI elements
        setupUI()

        // Check permissions
        checkPermissions()

        // Observe view model state
        observeViewModel()
    }

    private fun setupUI() {
        // Set up stream button
        binding.btnToggleStream.setOnClickListener {
            if (viewModel.isStreaming.value) {
                viewModel.stopStreaming()
            } else {
                viewModel.startStreaming()
            }
        }

        // Set up settings button
        binding.btnSettings.setOnClickListener {
            openSettings()
        }
    }

    private fun checkPermissions() {
        if (!permissionHelper.hasRequiredPermissions()) {
            permissionHelper.requestPermissions(this)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.isStreaming.collect { isStreaming ->
                updateStreamingUI(isStreaming)
            }
        }

        lifecycleScope.launch {
            viewModel.networkInfo.collect { info ->
                updateNetworkInfoUI(info)
            }
        }
    }

    private fun updateStreamingUI(isStreaming: Boolean) {
        binding.btnToggleStream.text = if (isStreaming) "Stop Streaming" else "Start Streaming"

        if (isStreaming) {
            binding.btnToggleStream.setBackgroundColor(ContextCompat.getColor(this, R.color.colorStop))
            binding.streamStatus.setText(R.string.status_streaming)
            binding.streamStatus.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess))
        } else {
            binding.btnToggleStream.setBackgroundColor(ContextCompat.getColor(this, R.color.colorStart))
            binding.streamStatus.setText(R.string.status_idle)
            binding.streamStatus.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary))
        }
    }

    private fun updateNetworkInfoUI(info: StreamingService.NetworkInfo?) {
        if (info == null) {
            binding.networkStatus.visibility = View.GONE
            return
        }

        binding.networkStatus.visibility = View.VISIBLE

        when (info.status) {
            StreamingService.NetworkStatus.CONNECTED -> {
                binding.networkStatus.setText(R.string.status_connected)
                binding.networkStatus.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess))
            }
            StreamingService.NetworkStatus.CONNECTING -> {
                binding.networkStatus.setText(R.string.status_connecting)
                binding.networkStatus.setTextColor(ContextCompat.getColor(this, R.color.colorWarning))
            }
            StreamingService.NetworkStatus.PARTIAL -> {
                binding.networkStatus.setText(R.string.status_partial)
                binding.networkStatus.setTextColor(ContextCompat.getColor(this, R.color.colorWarning))
            }
            StreamingService.NetworkStatus.ERROR -> {
                binding.networkStatus.setText(R.string.status_error)
                binding.networkStatus.setTextColor(ContextCompat.getColor(this, R.color.colorError))
            }
            else -> {
                binding.networkStatus.setText(R.string.status_disconnected)
                binding.networkStatus.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary))
            }
        }

        binding.serverAddress.text = getString(R.string.server_address, info.serverAddress)

        if (info.streamUrl.isNotEmpty()) {
            binding.streamUrl.visibility = View.VISIBLE
            binding.streamUrl.text = getString(R.string.stream_url, info.streamUrl)
        } else {
            binding.streamUrl.visibility = View.GONE
        }
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PermissionHelper.PERMISSION_REQUEST_CODE) {
            if (!permissionHelper.hasRequiredPermissions()) {
                if (permissionHelper.shouldShowRationale(this)) {
                    showPermissionExplanationDialog()
                } else {
                    showPermissionRequiredDialog()
                }
            }
        }
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required_title)
            .setMessage(R.string.permission_explanation)
            .setPositiveButton(R.string.grant) { _, _ ->
                permissionHelper.requestPermissions(this)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                Toast.makeText(this, R.string.permission_denied_message, Toast.LENGTH_LONG).show()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required_title)
            .setMessage(R.string.permission_required_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                permissionHelper.openAppSettings(this)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                Toast.makeText(this, R.string.permission_denied_message, Toast.LENGTH_LONG).show()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        Logger.d(TAG, "MainActivity onResume")

        // Resume AR session
        streamingService?.resumeARSession(this)
    }

    override fun onPause() {
        Logger.d(TAG, "MainActivity onPause")

        // Pause AR session
        streamingService?.pauseARSession()

        super.onPause()
    }

    override fun onDestroy() {
        Logger.d(TAG, "MainActivity onDestroy")

        // Unbind service
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }

        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                openSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}