package com.remoteunlock.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.remoteunlock.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Main Activity providing UI controls for configuration, manual testing,
 * background service management, and handling native BiometricPrompt authorization.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferenceHelper: PreferenceHelper

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    // Runtime permission launcher for Android 13+ (TIRAMISU) notification permission
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startDiscoveryService()
        } else {
            binding.switchDiscoveryService.isChecked = false
            preferenceHelper.isServiceEnabled = false
            Toast.makeText(
                this,
                "Notification permission is required for discovery alerts",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceHelper = PreferenceHelper(this)

        initViews()
        initBiometrics()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Synchronize switch state with service running status
        binding.switchDiscoveryService.isChecked = LaptopDiscoveryService.isRunning
        updateServiceStatusText(LaptopDiscoveryService.isRunning)
    }

    /**
     * Initializes UI components with stored preferences and click listeners.
     */
    private fun initViews() {
        // Populate inputs from saved preferences
        binding.etLaptopIp.setText(preferenceHelper.laptopIp)
        binding.etHttpPort.setText(preferenceHelper.httpPort.toString())
        binding.etSecretToken.setText(preferenceHelper.secretToken)
        binding.etWindowsPin.setText(preferenceHelper.windowsPin)
        binding.switchDiscoveryService.isChecked = LaptopDiscoveryService.isRunning

        // Save Settings button
        binding.btnSaveSettings.setOnClickListener {
            saveSettingsFromInput()
            Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
            setStatus("Settings saved. Ready to connect.", isError = false)
        }

        // Manual Unlock button -> initiates Biometric authentication
        binding.btnUnlock.setOnClickListener {
            saveSettingsFromInput()
            showBiometricPromptForUnlock()
        }

        // Manual Lock button
        binding.btnLock.setOnClickListener {
            saveSettingsFromInput()
            performLock()
        }

        // Ping Agent button
        binding.btnPing.setOnClickListener {
            saveSettingsFromInput()
            performPing()
        }

        // Discovery Service toggle switch
        binding.switchDiscoveryService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestNotificationPermissionAndStartService()
            } else {
                stopDiscoveryService()
            }
        }
    }

    /**
     * Inspects incoming intent for auto-trigger flag from high-priority notification.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val autoTrigger = intent.getBooleanExtra(EXTRA_AUTO_TRIGGER, false)
        val incomingIp = intent.getStringExtra(EXTRA_LAPTOP_IP)

        if (!incomingIp.isNullOrBlank()) {
            binding.etLaptopIp.setText(incomingIp)
            preferenceHelper.laptopIp = incomingIp
        }

        if (autoTrigger) {
            Log.i(TAG, "Launched with EXTRA_AUTO_TRIGGER. Presenting BiometricPrompt immediately.")
            setStatus("Laptop detected! Scan fingerprint to unlock...", isError = false)
            // Present biometric prompt immediately on heads-up tap
            showBiometricPromptForUnlock()
        }
    }

    /**
     * Sets up the AndroidX BiometricPrompt with strong biometrics and device credentials.
     */
    private fun initBiometrics() {
        val executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.i(TAG, "Biometric authentication succeeded")
                setStatus(getString(R.string.status_unlocking), isError = false)
                // Proceed with authenticated HTTP unlock request
                performUnlock()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.w(TAG, "Biometric authentication error ($errorCode): $errString")
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    setStatus("Biometric Error: $errString", isError = true)
                } else {
                    setStatus("Unlock cancelled by user", isError = false)
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "Biometric authentication failed (fingerprint not recognized)")
                setStatus("Fingerprint not recognized. Please try again.", isError = true)
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
    }

    /**
     * Displays the biometric prompt dialog after verifying hardware capability.
     */
    private fun showBiometricPromptForUnlock() {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                biometricPrompt.authenticate(promptInfo)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                setStatus("No biometric hardware available on this device.", isError = true)
                Toast.makeText(this, "Biometric hardware not available", Toast.LENGTH_SHORT).show()
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                setStatus("Biometric sensor is currently unavailable.", isError = true)
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                setStatus("No biometrics enrolled. Please set up a fingerprint or PIN in Android Settings.", isError = true)
            }
            else -> {
                setStatus("Biometric authentication unavailable.", isError = true)
            }
        }
    }

    /**
     * Executes the HTTP POST /unlock call to the Windows Agent.
     */
    private fun performUnlock() {
        val ip = binding.etLaptopIp.text.toString().trim()
        val port = binding.etHttpPort.text.toString().toIntOrNull() ?: 5000
        val token = binding.etSecretToken.text.toString().trim()
        val pin = binding.etWindowsPin.text.toString().trim()

        if (ip.isBlank()) {
            setStatus("Please enter a valid laptop IP address.", isError = true)
            return
        }

        lifecycleScope.launch {
            setStatus(getString(R.string.status_unlocking), isError = false)
            val result = ApiClient.unlock(ip, port, token, pin)

            result.onSuccess { response ->
                Log.i(TAG, "Unlock succeeded: $response")
                setStatus(getString(R.string.status_unlock_success), isError = false)
                Toast.makeText(this@MainActivity, "Unlock command sent to laptop", Toast.LENGTH_SHORT).show()
            }.onFailure { ex ->
                Log.e(TAG, "Unlock failed: ${ex.message}", ex)
                val errorMsg = when (ex) {
                    is SecurityException -> getString(R.string.status_error_auth)
                    is IllegalStateException -> ex.message ?: "Configuration error"
                    else -> "${getString(R.string.status_error_connection)} (${ex.localizedMessage})"
                }
                setStatus(errorMsg, isError = true)
            }
        }
    }

    /**
     * Executes the HTTP POST /lock call to the Windows Agent.
     */
    private fun performLock() {
        val ip = binding.etLaptopIp.text.toString().trim()
        val port = binding.etHttpPort.text.toString().toIntOrNull() ?: 5000
        val token = binding.etSecretToken.text.toString().trim()

        if (ip.isBlank()) {
            setStatus("Please enter a valid laptop IP address.", isError = true)
            return
        }

        lifecycleScope.launch {
            setStatus("Locking laptop workstation...", isError = false)
            val result = ApiClient.lock(ip, port, token)

            result.onSuccess {
                setStatus(getString(R.string.status_lock_success), isError = false)
                Toast.makeText(this@MainActivity, "Laptop Locked!", Toast.LENGTH_SHORT).show()
            }.onFailure { ex ->
                val errorMsg = if (ex is SecurityException) {
                    getString(R.string.status_error_auth)
                } else {
                    "Failed to lock laptop: ${ex.localizedMessage}"
                }
                setStatus(errorMsg, isError = true)
            }
        }
    }

    /**
     * Executes the HTTP GET /ping call to check agent health and connectivity.
     */
    private fun performPing() {
        val ip = binding.etLaptopIp.text.toString().trim()
        val port = binding.etHttpPort.text.toString().toIntOrNull() ?: 5000

        if (ip.isBlank()) {
            setStatus("Please enter a valid laptop IP address.", isError = true)
            return
        }

        lifecycleScope.launch {
            setStatus("Connecting to agent at $ip:$port...", isError = false)
            val result = ApiClient.ping(ip, port)

            result.onSuccess { body ->
                setStatus("Agent is ONLINE! Response: $body", isError = false)
                Toast.makeText(this@MainActivity, "Agent Online", Toast.LENGTH_SHORT).show()
            }.onFailure { ex ->
                setStatus("Agent unreachable: ${ex.localizedMessage}", isError = true)
            }
        }
    }

    /**
     * Requests POST_NOTIFICATIONS permission on Android 13+ before starting foreground service.
     */
    private fun requestNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                startDiscoveryService()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startDiscoveryService()
        }
    }

    private fun startDiscoveryService() {
        LaptopDiscoveryService.startService(this)
        preferenceHelper.isServiceEnabled = true
        binding.switchDiscoveryService.isChecked = true
        updateServiceStatusText(true)
        Toast.makeText(this, "Discovery service started", Toast.LENGTH_SHORT).show()
    }

    private fun stopDiscoveryService() {
        LaptopDiscoveryService.stopService(this)
        preferenceHelper.isServiceEnabled = false
        binding.switchDiscoveryService.isChecked = false
        updateServiceStatusText(false)
        Toast.makeText(this, "Discovery service stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateServiceStatusText(isRunning: Boolean) {
        if (isRunning) {
            setStatus(getString(R.string.status_service_running), isError = false)
        } else {
            setStatus(getString(R.string.status_service_stopped), isError = false)
        }
    }

    private fun setStatus(message: String, isError: Boolean) {
        binding.tvStatus.text = message
        val cardColor = if (isError) {
            ContextCompat.getColor(this, R.color.error_container)
        } else {
            ContextCompat.getColor(this, R.color.primary_container)
        }
        val textColor = if (isError) {
            ContextCompat.getColor(this, R.color.error)
        } else {
            ContextCompat.getColor(this, R.color.on_primary_container)
        }
        binding.cardStatus.setCardBackgroundColor(cardColor)
        binding.tvStatus.setTextColor(textColor)
    }

    private fun saveSettingsFromInput() {
        preferenceHelper.laptopIp = binding.etLaptopIp.text.toString().trim()
        preferenceHelper.httpPort = binding.etHttpPort.text.toString().toIntOrNull() ?: 5000
        preferenceHelper.secretToken = binding.etSecretToken.text.toString().trim()
        preferenceHelper.windowsPin = binding.etWindowsPin.text.toString().trim()
    }

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_AUTO_TRIGGER = "EXTRA_AUTO_TRIGGER"
        const val EXTRA_LAPTOP_IP = "EXTRA_LAPTOP_IP"
    }
}
