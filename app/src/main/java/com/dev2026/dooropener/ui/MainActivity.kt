package com.dev2026.dooropener.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dev2026.dooropener.App
import com.dev2026.dooropener.bridge.DoorBridgeService
import com.dev2026.dooropener.databinding.ActivityMainBinding
import com.dev2026.dooropener.wear.WearEngineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main activity showing connection status and recent door open history.
 * Entry point for the app launcher.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val wearEngineManager = WearEngineManager(this)
    private val prefs = App.instance.preferencesManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* Permission result handled by re-checking state */ }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Check result after returning */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
        checkPermissions()
        startBridgeService()
        updateStatusDisplay()
    }

    private fun setupToolbar() {
        // Toolbar setup via binding if using custom toolbar
    }

    private fun setupClickListeners() {
        binding.btnRefreshWear.setOnClickListener {
            refreshWearStatus()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnBatteryOptimization.setOnClickListener {
            requestBatteryOptimizationExemption()
        }
    }

    private fun checkPermissions() {
        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startBridgeService() {
        val intent = Intent(this, DoorBridgeService::class.java).apply {
            action = DoorBridgeService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun refreshWearStatus() {
        lifecycleScope.launch {
            binding.wearStatusProgress.visibility = View.VISIBLE
            binding.tvWearStatus.text = "Searching..."

            val device = withContext(Dispatchers.IO) {
                wearEngineManager.findFit4Device()
            }

            binding.wearStatusProgress.visibility = View.GONE

            if (device != null) {
                binding.tvWearStatus.text = "Connected: ${device.name}"
                binding.indicatorWear.setBackgroundResource(android.R.color.holo_green_dark)
                prefs.targetDeviceId = device.id
                prefs.targetDeviceName = device.name
            } else {
                binding.tvWearStatus.text = "No Fit 4 found"
                binding.indicatorWear.setBackgroundResource(android.R.color.holo_red_dark)
            }

            updateStatusDisplay()
        }
    }

    private fun updateStatusDisplay() {
        val savedDeviceName = prefs.targetDeviceName
        if (savedDeviceName != null) {
            binding.tvWearStatus.text = "Device: $savedDeviceName"
            binding.indicatorWear.setBackgroundResource(android.R.color.holo_green_dark)
        }

        val serverConfigured = prefs.lianzhangServerUrl != null
        binding.tvLzStatus.text = if (serverConfigured) "Server configured" else "Not configured"
        binding.indicatorLz.setBackgroundResource(
            if (serverConfigured) android.R.color.holo_green_dark
            else android.R.color.holo_orange_dark
        )

        val lastOpenTime = prefs.lastOpenDoorTime
        if (lastOpenTime > 0) {
            val timeAgo = (System.currentTimeMillis() - lastOpenTime) / 1000
            binding.tvLastOpen.text = "Last open: ${timeAgo}s ago"
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            batteryOptimizationLauncher.launch(intent)
        }
    }
}
