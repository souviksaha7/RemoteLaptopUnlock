package com.remoteunlock.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * Persistent Foreground Service that listens on UDP Port 5005 for broadcast beacons
 * emitted by the Windows Agent upon laptop boot (payload: 'LAPTOP_AWAKE').
 *
 * When detected, this service issues a high-priority heads-up notification prompting
 * the user to tap and authenticate via biometrics.
 */
class LaptopDiscoveryService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var datagramSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private lateinit var preferenceHelper: PreferenceHelper

    // Debounce to prevent flooding notifications if multiple beacon packets arrive in quick succession
    private var lastAlertTimestamp: Long = 0L
    private var lastAlertedIp: String = ""

    override fun onCreate() {
        super.onCreate()
        preferenceHelper = PreferenceHelper(this)
        createNotificationChannels()

        // Acquire Wi-Fi MulticastLock:
        // By default, many Android Wi-Fi drivers filter out incoming broadcast/multicast packets
        // to conserve battery unless a MulticastLock is explicitly held.
        acquireMulticastLock()

        // Start listening on UDP port 5005 in background coroutine
        startUdpListener()

        isRunning = true
        Log.i(TAG, "LaptopDiscoveryService created and listening on UDP port $UDP_PORT")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Build and display persistent foreground notification
        val foregroundNotification = buildForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID_FOREGROUND,
                foregroundNotification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } else {
            startForeground(NOTIFICATION_ID_FOREGROUND, foregroundNotification)
        }

        return START_STICKY
    }

    /**
     * Acquires Android WifiManager.MulticastLock to permit reception of UDP broadcast frames.
     */
    private fun acquireMulticastLock() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("RemoteLaptopUnlock:MulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.d(TAG, "Wi-Fi MulticastLock acquired successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire MulticastLock: ${e.message}")
        }
    }

    /**
     * Binds a DatagramSocket with SO_BROADCAST and SO_REUSEADDR to port 5005
     * and receives incoming UDP packets.
     */
    private fun startUdpListener() {
        serviceScope.launch {
            try {
                datagramSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(UDP_PORT))
                }

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive && datagramSocket != null && !datagramSocket!!.isClosed) {
                    try {
                        datagramSocket?.receive(packet)

                        val payload = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        val senderIp = packet.address?.hostAddress.orEmpty()

                        Log.d(TAG, "Received UDP packet from $senderIp: '$payload'")

                        if (payload == EXPECTED_PAYLOAD) {
                            handleBeaconReceived(senderIp)
                        }
                    } catch (sockEx: Exception) {
                        if (!isActive) break
                        Log.w(TAG, "UDP receive exception: ${sockEx.message}")
                    }
                }
            } catch (bindEx: Exception) {
                Log.e(TAG, "Failed to initialize UDP socket on port $UDP_PORT: ${bindEx.message}", bindEx)
            }
        }
    }

    /**
     * Processes verified LAPTOP_AWAKE beacon, debounces bursts, saves discovered IP,
     * and issues a high-priority heads-up notification.
     */
    private fun handleBeaconReceived(laptopIp: String) {
        val now = SystemClock.elapsedRealtime()
        val isSameIp = (laptopIp == lastAlertedIp)
        val isCooldownActive = (now - lastAlertTimestamp) < DEBOUNCE_INTERVAL_MS

        if (isSameIp && isCooldownActive) {
            Log.d(TAG, "Debounced duplicate beacon packet from $laptopIp")
            return
        }

        lastAlertTimestamp = now
        lastAlertedIp = laptopIp

        // Automatically store the dynamically discovered IP address
        preferenceHelper.laptopIp = laptopIp
        Log.i(TAG, "Discovered laptop at $laptopIp! Issuing heads-up notification.")

        showHeadsUpUnlockNotification(laptopIp)
    }

    /**
     * Creates high-priority heads-up notification with action to launch MainActivity
     * with EXTRA_AUTO_TRIGGER enabled.
     */
    private fun showHeadsUpUnlockNotification(laptopIp: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to launch MainActivity and immediately trigger BiometricPrompt
        val unlockIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_AUTO_TRIGGER, true)
            putExtra(MainActivity.EXTRA_LAPTOP_IP, laptopIp)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID_AWAKE,
            unlockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_AWAKE_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_awake_title))
            .setContentText(getString(R.string.notif_awake_text, laptopIp))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true) // Presents as heads-up notification
            .addAction(
                R.drawable.ic_fingerprint,
                getString(R.string.notif_action_unlock),
                pendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID_AWAKE, notification)
    }

    /**
     * Builds the persistent, low-priority foreground notification to keep the service alive.
     */
    private fun buildForegroundNotification(): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_service_title))
            .setContentText(getString(R.string.notif_service_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    /**
     * Creates notification channels for Android 8.0+ (Oreo).
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Low-priority channel for persistent foreground service
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                getString(R.string.notif_channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_service_desc)
                setShowBadge(false)
            }

            // High-priority channel for Laptop Powered On alerts (heads-up banner)
            val awakeChannel = NotificationChannel(
                CHANNEL_AWAKE_ID,
                getString(R.string.notif_channel_alert_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notif_channel_alert_desc)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(awakeChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        // Close UDP DatagramSocket to interrupt any blocking receive call
        try {
            datagramSocket?.close()
            datagramSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing datagram socket: ${e.message}")
        }

        // Cancel CoroutineScope
        serviceJob.cancel()
        serviceScope.cancel()

        // Release Wi-Fi MulticastLock
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                multicastLock = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MulticastLock: ${e.message}")
        }

        Log.i(TAG, "LaptopDiscoveryService stopped and resources released")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LaptopDiscoveryService"
        const val UDP_PORT = 5005
        const val EXPECTED_PAYLOAD = "LAPTOP_AWAKE"
        private const val DEBOUNCE_INTERVAL_MS = 15000L // 15 seconds debounce window

        const val CHANNEL_SERVICE_ID = "channel_laptop_discovery_service"
        const val CHANNEL_AWAKE_ID = "channel_laptop_awake_alert"
        const val NOTIFICATION_ID_FOREGROUND = 1001
        const val NOTIFICATION_ID_AWAKE = 2002

        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * Starts the discovery foreground service.
         */
        fun startService(context: Context) {
            val intent = Intent(context, LaptopDiscoveryService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stops the discovery service.
         */
        fun stopService(context: Context) {
            val intent = Intent(context, LaptopDiscoveryService::class.java)
            context.stopService(intent)
        }
    }
}
