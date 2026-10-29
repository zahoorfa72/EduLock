package com.edulock.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class LearningReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_ENABLED, false)) return Result.success()

        if (Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Learning reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Gentle reminders to practise saved learning questions." }
            )
        }

        val childName = preferences.getString(KEY_CHILD_NAME, "Your learner")?.takeIf { it.isNotBlank() } ?: "Your learner"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.edulock_logo)
            .setContentTitle("A small learning moment")
            .setContentText("$childName, try one question you found tricky. You can learn step by step!")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Practise one saved question or review something you corrected before. Small steps count—ask a parent for help when needed."
            ))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        }
    }

    companion object {
        const val PREFS = "edulock_learning_reminder"
        const val KEY_ENABLED = "enabled"
        const val KEY_CHILD_NAME = "child_name"
        private const val CHANNEL_ID = "edulock_learning_reminders"
        private const val NOTIFICATION_ID = 4107
    }
}

object LearningReminderScheduler {
    private const val UNIQUE_WORK = "edulock-daily-gentle-learning-reminder"

    fun setEnabled(context: Context, enabled: Boolean, childName: String) {
        context.getSharedPreferences(LearningReminderWorker.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(LearningReminderWorker.KEY_ENABLED, enabled)
            .putString(LearningReminderWorker.KEY_CHILD_NAME, childName.take(80))
            .apply()

        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK)
            return
        }

        val request = PeriodicWorkRequestBuilder<LearningReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(24, TimeUnit.HOURS)
            .setInputData(workDataOf(LearningReminderWorker.KEY_CHILD_NAME to childName.take(80)))
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
