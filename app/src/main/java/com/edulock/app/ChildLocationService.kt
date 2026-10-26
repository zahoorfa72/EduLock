package com.edulock.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import android.util.Log
import java.time.Instant
import java.util.concurrent.Executors

/**
 * User-enabled foreground location sharing for a paired child device.
 * It stores only the latest point locally and remotely. If offline, the latest point
 * remains pending locally and is retried after connectivity returns.
 */
class ChildLocationService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var locationManager: LocationManager? = null
    private var listenerRegistered = false

    private val locationListener = LocationListener { location ->
        rememberLocation(location)
        queueSync()
    }

    private val heartbeat = object : Runnable {
        override fun run() {
            if (!isSharingEnabled()) return
            queueSync()
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        // startForegroundService() requires promotion immediately, including stop requests.
        startAsForeground()
        if (action == ACTION_DISABLE || !isSharingEnabled()) {
            handler.removeCallbacks(heartbeat)
            stopLocationUpdates()
            io.execute {
                try {
                    val childId = locationPrefs().getString(KEY_CHILD_ID, null)
                    if (!childId.isNullOrBlank()) {
                        ChildLocationSyncClient.sendHeartbeat(this, childId, false, batteryPercent())
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not sync location-sharing disabled status", e)
                } finally {
                    handler.post {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                }
            }
            return START_NOT_STICKY
        }

        val session = SecureSessionStore(applicationContext).read()
        val childId = locationPrefs().getString(KEY_CHILD_ID, null)
        if (session?.accountRole != "child" || childId.isNullOrBlank() ||
            (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
             checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            updateNotification("Open EduLock to restore location permissions and pairing.")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startLocationUpdates()
        handler.removeCallbacks(heartbeat)
        handler.post(heartbeat)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(heartbeat)
        stopLocationUpdates()
        io.shutdown()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = buildNotification("Location sharing is active. EduLock keeps only the latest reported point.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startLocationUpdates() {
        if (listenerRegistered) return
        val manager = locationManager ?: return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            updateNotification("Location permission is off. Open EduLock to re-enable sharing.")
            return
        }
        var registered = false
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
            try {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, LOCATION_INTERVAL_MS, MIN_DISTANCE_METERS, locationListener, Looper.getMainLooper())
                    registered = true
                    try {
                        val last = manager.getLastKnownLocation(provider)
                        if (last != null && System.currentTimeMillis() - last.time <= MAX_CACHED_FIX_AGE_MS) {
                            rememberLocation(last)
                        }
                    } catch (_: SecurityException) {
                        // Permission can be revoked while the service is running.
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to register $provider location updates", e)
            }
        }
        listenerRegistered = registered
        if (!registered) updateNotification("Waiting for device location. Check that Location is turned on.")
        else updateNotification("Location sharing is active. Updates retry automatically when online.")
    }

    private fun stopLocationUpdates() {
        if (!listenerRegistered) return
        try { locationManager?.removeUpdates(locationListener) } catch (_: Exception) { }
        listenerRegistered = false
    }

    private fun rememberLocation(location: Location) {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return
        val prefs = locationPrefs()
        val storedAt = prefs.getLong(KEY_POINT_TIME, 0L)
        if (location.time < storedAt) return
        prefs.edit()
            .putLong(KEY_POINT_TIME, location.time)
            .putString(KEY_LATITUDE, location.latitude.toString())
            .putString(KEY_LONGITUDE, location.longitude.toString())
            .putFloat(KEY_ACCURACY, if (location.hasAccuracy()) location.accuracy else -1f)
            .putBoolean(KEY_PENDING, true)
            .apply()
    }

    private fun queueSync() {
        val childId = locationPrefs().getString(KEY_CHILD_ID, null) ?: return
        io.execute {
            try {
                val prefs = locationPrefs()
                if (!prefs.getBoolean(KEY_SHARING_ENABLED, false)) return@execute
                if (prefs.getBoolean(KEY_PENDING, false)) {
                    val lat = prefs.getString(KEY_LATITUDE, null)?.toDoubleOrNull()
                    val lon = prefs.getString(KEY_LONGITUDE, null)?.toDoubleOrNull()
                    val pointTime = prefs.getLong(KEY_POINT_TIME, 0L)
                    val accuracy = prefs.getFloat(KEY_ACCURACY, -1f).takeIf { it >= 0f }
                    if (lat != null && lon != null && pointTime > 0L) {
                        ChildLocationSyncClient.uploadLatestLocation(this, childId, lat, lon, accuracy, pointTime)
                        prefs.edit().putBoolean(KEY_PENDING, false).apply()
                    }
                }
                ChildLocationSyncClient.sendHeartbeat(this, childId, true, batteryPercent())
                updateNotification("Location sharing is active. Last check-in: " + Instant.now().toString().take(16).replace("T", " "))
            } catch (e: Exception) {
                // Keep the pending point on disk; the next heartbeat retries it.
                Log.w(TAG, "Location remains saved locally and will retry when possible", e)
                updateNotification("Waiting for connection. The latest location is saved on this device.")
            }
        }
    }

    private fun isSharingEnabled(): Boolean =
        locationPrefs().getBoolean(KEY_SHARING_ENABLED, false)

    private fun locationPrefs() =
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun batteryPercent(): Int? {
        return try {
            val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
            val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) null else ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } catch (_: Exception) { null }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Child location sharing", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Visible notification while a parent-authorized child location is being shared."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("EduLock location sharing")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setOngoing(isSharingEnabled())
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to refresh location notification", e)
        }
    }

    companion object {
        const val ACTION_START = "com.edulock.app.location.START"
        const val ACTION_DISABLE = "com.edulock.app.location.DISABLE"
        const val PREFS = "edulock_location_state"
        const val KEY_SHARING_ENABLED = "sharing_enabled"
        const val KEY_CHILD_ID = "child_id"
        const val KEY_PENDING = "pending_upload"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_ACCURACY = "accuracy_meters"
        const val KEY_POINT_TIME = "point_time_millis"
        const val CHANNEL_ID = "edulock_child_location"
        const val NOTIFICATION_ID = 27410
        const val TAG = "EduLockLocation"
        private const val HEARTBEAT_INTERVAL_MS = 2 * 60 * 1000L
        private const val LOCATION_INTERVAL_MS = 2 * 60 * 1000L
        private const val MIN_DISTANCE_METERS = 50f
        private const val MAX_CACHED_FIX_AGE_MS = 15 * 60 * 1000L
    }
}
