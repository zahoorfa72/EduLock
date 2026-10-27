package com.edulock.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/**
 * Checks parent-visible device heartbeats while the parent phone has connectivity.
 * This can report "not checking in"; it cannot prove that the child phone is powered off.
 */
class LocationWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val session = SecureSessionStore(applicationContext).read() ?: return Result.success()
        if (session.accountRole != "parent") return Result.success()
        return try {
            val statuses = ChildLocationSyncClient.loadParentDeviceStatuses(applicationContext)
            val children = ChildLocationSyncClient.loadParentChildren(applicationContext)
            val names = mutableMapOf<String, String>()
            for (i in 0 until children.length()) {
                val row = children.optJSONObject(i) ?: continue
                names[row.optString("id")] = row.optString("display_name", "Your child").ifBlank { "Your child" }
            }
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            for (i in 0 until statuses.length()) {
                val row = statuses.optJSONObject(i) ?: continue
                val childId = row.optString("child_id")
                if (childId.isBlank()) continue
                val alertKey = ALERT_PREFIX + childId
                val sharing = row.optBoolean("sharing_enabled", false)
                val lastSeen = parseInstant(row.optString("last_seen_at"))?.toEpochMilli()
                val stale = lastSeen != null && now - lastSeen >= STALE_AFTER_MS
                if (stale) {
                    if (!prefs.getBoolean(alertKey, false) && canPostNotifications()) {
                        postOfflineNotification(childId, names[childId] ?: "Your child", lastSeen!!)
                        prefs.edit().putBoolean(alertKey, true).apply()
                    }
                } else {
                    prefs.edit().remove(alertKey).apply()
                    notificationManager().cancel(notificationId(childId))
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun parseInstant(value: String): Instant? = try {
        Instant.parse(value)
    } catch (_: Exception) {
        try { OffsetDateTime.parse(value).toInstant() } catch (_: Exception) { null }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun postOfflineNotification(childId: String, childName: String, lastSeenMillis: Long) {
        val manager = notificationManager()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Child device status", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Alerts when a paired child device stops checking in."
                }
            )
        }
        val openApp = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val minutes = ((System.currentTimeMillis() - lastSeenMillis).coerceAtLeast(0L) / 60_000L)
        val message = "$childName has not checked in for about $minutes minutes. The last location remains saved; the phone may be offline, switched off, or unable to report."
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("EduLock: child device not reporting")
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId(childId), notification)
    }

    private fun notificationManager() =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun notificationId(childId: String): Int =
        (childId.hashCode() and 0x7fffffff).coerceAtLeast(1)

    companion object {
        private const val PREFS = "edulock_location_watchdog"
        private const val ALERT_PREFIX = "offline_alerted_"
        private const val CHANNEL_ID = "edulock_child_offline"
        private const val STALE_AFTER_MS = 10 * 60 * 1000L

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<LocationWatchdogWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                "edulock-child-location-watchdog",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
