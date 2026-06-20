package com.dev2026.dooropener.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dev2026.dooropener.App
import com.dev2026.dooropener.databinding.ActivitySettingsBinding
import com.dev2026.dooropener.door.DoorControlManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings activity for configuring 联掌门户 server connection,
 * Fit 4 device selection, and notification preferences.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs = App.instance.preferencesManager
    private val doorControlManager = DoorControlManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        loadCurrentSettings()
        setupClickListeners()
    }

    private fun loadCurrentSettings() {
        binding.etServerUrl.setText(prefs.lianzhangServerUrl ?: "")
        binding.etUsername.setText(prefs.lianzhangUsername ?: "")
        binding.etPassword.setText(prefs.lianzhangPassword ?: "")
        binding.etDeviceId.setText(prefs.lianzhangDeviceId ?: "")
        binding.switchConfirmOpen.isChecked = prefs.requireConfirmToOpen

        val timeout = prefs.callTimeoutSeconds
        binding.etCallTimeout.setText(timeout.toString())

        // Listener mode
        val mode = prefs.eventListenerMode
        when (mode) {
            "mqtt" -> binding.rgListenerMode.check(binding.rbMqtt.id)
            "http_poll" -> binding.rgListenerMode.check(binding.rbHttpPoll.id)
            "notification_listener" -> binding.rgListenerMode.check(binding.rbNotificationListener.id)
            else -> binding.rgListenerMode.check(binding.rbAuto.id)
        }
    }

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }
    }

    private fun saveSettings() {
        val serverUrl = binding.etServerUrl.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val deviceId = binding.etDeviceId.text.toString().trim()
        val callTimeoutStr = binding.etCallTimeout.text.toString().trim()

        // Basic validation
        if (serverUrl.isBlank()) {
            Toast.makeText(this, "Server URL is required", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.lianzhangServerUrl = serverUrl
        prefs.lianzhangUsername = username.ifBlank { null }
        prefs.lianzhangPassword = password.ifBlank { null }
        prefs.lianzhangDeviceId = deviceId.ifBlank { null }
        prefs.requireConfirmToOpen = binding.switchConfirmOpen.isChecked

        val timeout = callTimeoutStr.toIntOrNull()
        if (timeout != null && timeout in 10..120) {
            prefs.callTimeoutSeconds = timeout
        }

        // Save listener mode
        val mode = when (binding.rgListenerMode.checkedRadioButtonId) {
            binding.rbMqtt.id -> "mqtt"
            binding.rbHttpPoll.id -> "http_poll"
            binding.rbNotificationListener.id -> "notification_listener"
            else -> "auto"
        }
        prefs.eventListenerMode = mode

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testConnection() {
        lifecycleScope.launch {
            val serverUrl = binding.etServerUrl.text.toString().trim()
            if (serverUrl.isBlank()) {
                Toast.makeText(this@SettingsActivity, "Enter server URL first", Toast.LENGTH_SHORT).show()
                return@launch
            }

            prefs.lianzhangServerUrl = serverUrl
            binding.btnTestConnection.isEnabled = false
            binding.btnTestConnection.text = "Testing..."

            val result = withContext(Dispatchers.IO) {
                doorControlManager.validateConnection()
            }

            binding.btnTestConnection.isEnabled = true
            binding.btnTestConnection.text = "Test Connection"

            when (result) {
                is DoorControlManager.DoorResult.Success ->
                    Toast.makeText(this@SettingsActivity, "Connection OK", Toast.LENGTH_SHORT).show()
                is DoorControlManager.DoorResult.Failure ->
                    Toast.makeText(this@SettingsActivity, "Failed: ${result.error}", Toast.LENGTH_LONG).show()
                is DoorControlManager.DoorResult.Timeout ->
                    Toast.makeText(this@SettingsActivity, "Connection timed out", Toast.LENGTH_LONG).show()
                is DoorControlManager.DoorResult.NotConfigured ->
                    Toast.makeText(this@SettingsActivity, "Not configured", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
