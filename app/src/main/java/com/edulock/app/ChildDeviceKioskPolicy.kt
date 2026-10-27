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

/**
 * Device-owner-only kiosk policy.
 *
 * In Kids Mode, Lock Task is the enforcement boundary: the child's normal launcher is
 * deliberately not allowlisted, because allowlisting HOME lets the child leave EduLock.
 * Android system UI/permission and telephony packages remain available for safety-critical
 * prompts and calls. Ordinary installs still require the explicit Accessibility fallback.
 */
object ChildDeviceKioskPolicy {
    private const val LOCK_PREFS = "edulock_lock_state"
    private const val KEY_KIDS_MODE = "kids_mode_enabled"
    private const val KEY_POLICY_APPLIED = "lock_task_policy_applied"

    fun apply(context: Context, kidsModeEnabled: Boolean, allowedPackages: Set<String>): Boolean {
        val prefs = context.getSharedPreferences(LOCK_PREFS, Context.MODE_PRIVATE)
        val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!manager.isDeviceOwnerApp(context.packageName)) {
            prefs.edit()
                .putBoolean(KEY_POLICY_APPLIED, false)
                .putBoolean(KEY_KIOSK_ACTIVE, false)
                .apply()
            return false
        }
        val admin = ComponentName(context, EduLockDeviceAdminReceiver::class.java)
        return try {
            if (kidsModeEnabled) {
                manager.setUninstallBlocked(admin, context.packageName, true)
                manager.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
                manager.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
            } else {
                manager.setUninstallBlocked(admin, context.packageName, false)
                manager.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
                manager.clearUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
            }
            // Never add the device's ordinary HOME launcher while Kids Mode is on.
            // A launcher in the Lock Task allowlist can let the child exit the kiosk.
            val homePackage = resolveHomePackage(context)
            val candidates = linkedSetOf(context.packageName)
            if (!kidsModeEnabled && homePackage.isNotBlank()) candidates += homePackage
            if (kidsModeEnabled) {
                candidates += allowedPackages.filter { it != homePackage }
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
                // Home must remain disabled in Kids Mode; enabling LOCK_TASK_FEATURE_HOME lets
                // users navigate to a launcher (which is intentionally not allowlisted).
                // Permit status/system info, while leaving notifications, recents and global
                // actions disabled by omission.
                val features = if (kidsModeEnabled) {
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                } else {
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                }
                manager.setLockTaskFeatures(admin, features)
            }
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val restrictionsMatch = if (kidsModeEnabled) {
                userManager.hasUserRestriction(UserManager.DISALLOW_APPS_CONTROL) &&
                    userManager.hasUserRestriction(UserManager.DISALLOW_UNINSTALL_APPS)
            } else {
                !userManager.hasUserRestriction(UserManager.DISALLOW_APPS_CONTROL) &&
                    !userManager.hasUserRestriction(UserManager.DISALLOW_UNINSTALL_APPS)
            }
            val actualAllowlist = manager.getLockTaskPackages(admin).toSet()
            val homeExcluded = !kidsModeEnabled || homePackage.isBlank() || homePackage !in actualAllowlist
            val requiredSystemPackages = if (kidsModeEnabled) setOf(
                "com.android.systemui",
                "com.android.permissioncontroller",
                "com.google.android.permissioncontroller",
                "com.android.phone",
                "com.android.server.telecom",
                "com.android.incallui",
                "com.android.dialer",
                "com.google.android.dialer",
                "com.samsung.android.dialer"
            ).filter { isInstalled(context, it) }.toSet() else emptySet()
            val allowlistMatches = if (kidsModeEnabled) {
                context.packageName in actualAllowlist &&
                    allowedPackages.filter { it != homePackage && isInstalled(context, it) }.all { it in actualAllowlist } &&
                    requiredSystemPackages.all { it in actualAllowlist } &&
                    homeExcluded
            } else {
                context.packageName in actualAllowlist
            }
            val confirmed = manager.isDeviceOwnerApp(context.packageName) &&
                allowlistMatches &&
                manager.isUninstallBlocked(admin, context.packageName) == kidsModeEnabled &&
                restrictionsMatch
            prefs.edit()
                .putBoolean(KEY_POLICY_APPLIED, confirmed)
                .putBoolean(KEY_KIOSK_ACTIVE, false)
                .apply()
            confirmed
        } catch (_: Exception) {
            prefs.edit()
                .putBoolean(KEY_POLICY_APPLIED, false)
                .putBoolean(KEY_KIOSK_ACTIVE, false)
                .apply()
            false
        }
    }

    fun enterOrExitLockTask(context: Context, requested: Boolean) {
        val activity = context as? Activity ?: return
        val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!manager.isDeviceOwnerApp(context.packageName)) return
        val prefs = context.getSharedPreferences(LOCK_PREFS, Context.MODE_PRIVATE)
        // Keep the activity's enforcement mode aligned with the latest synced rule.
        // The calling activity passes the server-confirmed Kids Mode state; stale local
        // values must not prevent entry into kiosk after a child signs in or syncs rules.
        val enabled = requested && prefs.getBoolean(KEY_KIDS_MODE, false) &&
            prefs.getBoolean(KEY_POLICY_APPLIED, false) && manager.isLockTaskPermitted(context.packageName)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            if (enabled && activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_LOCKED) {
                activity.startLockTask()
                // Verify after the request; a non-device-owner or OEM failure must not be
                // silently presented as a successful kiosk lock.
                val locked = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                    .lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED
                prefs.edit().putBoolean(KEY_KIOSK_ACTIVE, locked).apply()
            } else if (!enabled && activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED) {
                activity.stopLockTask()
                prefs.edit().putBoolean(KEY_KIOSK_ACTIVE, false).apply()
            } else {
                prefs.edit().putBoolean(KEY_KIOSK_ACTIVE, enabled &&
                    activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED).apply()
            }
        } catch (_: Exception) {
            prefs.edit().putBoolean(KEY_KIOSK_ACTIVE, false).apply()
        }
    }

    private const val KEY_KIOSK_ACTIVE = "lock_task_kiosk_active"

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
