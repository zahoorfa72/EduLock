package com.edulock.app

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager

/** Device-owner-only kiosk policy. Ordinary installs use Accessibility fallback blocking. */
object ChildDeviceKioskPolicy {
    private const val LOCK_PREFS = "edulock_lock_state"
    private const val KEY_KIDS_MODE = "kids_mode_enabled"
    private const val KEY_POLICY_APPLIED = "lock_task_policy_applied"

    fun apply(context: Context, kidsModeEnabled: Boolean, allowedPackages: Set<String>): Boolean {
        val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!manager.isDeviceOwnerApp(context.packageName)) {
            context.getSharedPreferences(LOCK_PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_POLICY_APPLIED, false).apply()
            return false
        }
        val admin = ComponentName(context, EduLockDeviceAdminReceiver::class.java)
        return try {
            manager.setUninstallBlocked(admin, context.packageName, true)
            manager.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
            manager.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
            val homePackage = resolveHomePackage(context)
            val candidates = linkedSetOf(context.packageName)
            if (homePackage.isNotBlank()) candidates += homePackage
            if (kidsModeEnabled) {
                candidates += allowedPackages
                candidates += setOf(
                    "com.android.systemui",
                    "com.android.permissioncontroller",
                    "com.google.android.permissioncontroller",
                    "com.android.phone",
                    "com.android.server.telecom",
                    "com.android.incallui",
                    "com.android.dialer",
                    "com.google.android.dialer",
                    "com.samsung.android.dialer"
                )
            }
            val installed = candidates.filter { isInstalled(context, it) }.distinct()
            if (context.packageName !in installed) throw IllegalStateException("EduLock is missing from the kiosk allowlist.")
            manager.setLockTaskPackages(admin, installed.toTypedArray())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val features = if (kidsModeEnabled) {
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME or DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                } else DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                manager.setLockTaskFeatures(admin, features)
            }
            val confirmed = manager.isDeviceOwnerApp(context.packageName) &&
                context.packageName in manager.getLockTaskPackages(admin) &&
                manager.isUninstallBlocked(admin, context.packageName)
            context.getSharedPreferences(LOCK_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_POLICY_APPLIED, confirmed).apply()
            confirmed
        } catch (_: Exception) {
            context.getSharedPreferences(LOCK_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_POLICY_APPLIED, false).apply()
            false
        }
    }

    fun enterOrExitLockTask(context: Context, requested: Boolean) {
        val activity = context as? Activity ?: return
        val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!manager.isDeviceOwnerApp(context.packageName)) return
        val prefs = context.getSharedPreferences(LOCK_PREFS, Context.MODE_PRIVATE)
        val enabled = requested && prefs.getBoolean(KEY_KIDS_MODE, false) &&
            prefs.getBoolean(KEY_POLICY_APPLIED, false) && manager.isLockTaskPermitted(context.packageName)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            if (enabled && activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_LOCKED) activity.startLockTask()
            else if (!enabled && activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED) activity.stopLockTask()
        } catch (_: Exception) { /* UI does not report a failed request as success. */ }
    }

    private fun resolveHomePackage(context: Context): String = try {
        context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName.orEmpty()
    } catch (_: Exception) { "" }

    private fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: Exception) { false }
}
