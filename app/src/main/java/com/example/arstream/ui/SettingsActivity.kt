// File: ARStreamApp/app/src/main/java/com/example/arstream/ui/SettingsActivity.kt
package com.example.arstream.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.example.arstream.R
import com.example.arstream.databinding.ActivitySettingsBinding
import com.example.arstream.utils.Logger
import com.example.arstream.utils.PreferenceManager

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "SettingsActivity"
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Logger.d(TAG, "SettingsActivity onCreate")

        // Inflate layout
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize preference manager
        preferenceManager = PreferenceManager(this)

        // Set up UI
        setupUI()

        // Load current settings
        loadSettings()
    }

    private fun setupUI() {
        // Set up resolution spinner
        val resolutionAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.resolution_options,
            android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerResolution.adapter = resolutionAdapter

        // Set up quality spinner
        val qualityAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.quality_options,
            android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerQuality.adapter = qualityAdapter

        // Set up save button
        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        // Set up reset button
        binding.btnReset.setOnClickListener {
            resetSettings()
        }
    }

    private fun loadSettings() {
        // Load server settings
        binding.editServerAddress.setText(preferenceManager.serverAddress)
        binding.editServerPort.setText(preferenceManager.serverPort.toString())

        // Load resolution setting
        val resolutions = resources.getStringArray(R.array.resolution_options)
        val resolutionIndex = resolutions.indexOf(preferenceManager.videoResolution)
        if (resolutionIndex >= 0) {
            binding.spinnerResolution.setSelection(resolutionIndex)
        }

        // Load quality setting
        val qualities = resources.getStringArray(R.array.quality_options)
        val qualityIndex = qualities.indexOf(preferenceManager.streamQuality)
        if (qualityIndex >= 0) {
            binding.spinnerQuality.setSelection(qualityIndex)
        }

        // Load depth setting
        binding.switchDepth.isChecked = preferenceManager.enableDepth
    }

    private fun saveSettings() {
        // Save server settings
        preferenceManager.serverAddress = binding.editServerAddress.text.toString()
        preferenceManager.serverPort = binding.editServerPort.text.toString().toIntOrNull() ?: 8080

        // Save resolution setting
        preferenceManager.videoResolution = binding.spinnerResolution.selectedItem.toString()

        // Save quality setting
        preferenceManager.streamQuality = binding.spinnerQuality.selectedItem.toString()

        // Save depth setting
        preferenceManager.enableDepth = binding.switchDepth.isChecked

        Logger.d(TAG, "Settings saved")

        // Show confirmation
        binding.saveFeedback.text = getString(R.string.settings_saved)
        binding.saveFeedback.visibility = View.VISIBLE

        // Hide confirmation after 3 seconds
        binding.root.postDelayed({
            binding.saveFeedback.visibility = View.GONE
        }, 3000)
    }

    private fun resetSettings() {
        // Reset to default settings
        preferenceManager.resetToDefaults()

        // Reload UI
        loadSettings()

        Logger.d(TAG, "Settings reset to defaults")

        // Show confirmation
        binding.saveFeedback.text = getString(R.string.settings_reset)
        binding.saveFeedback.visibility = View.VISIBLE

        // Hide confirmation after 3 seconds
        binding.root.postDelayed({
            binding.saveFeedback.visibility = View.GONE
        }, 3000)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}