package com.edulock.app

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.graphics.Color
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.max
import kotlin.math.min

/**
 * Opt-in parental restriction service. It observes package changes only; it does not read screen text.
 * A parent must explicitly enable this service in Android Accessibility settings on the child's device.
 */
class EduLockAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var blocker: LinearLayout? = null
    private var blockerDetail: TextView? = null
    private var lastPackage = ""
    private var lastEventAt = 0L
    private var blockedPackageName = ""

    private val ticker = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            lockPrefs().edit().putLong(KEY_LAST_SERVICE_TICK_AT, now).apply()
            accountForegroundUsage(now)
            val current = lastPackage
            if (current.isNotBlank()) applyRestriction(current)
            handler.postDelayed(this, TICK_MILLIS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val prefs = lockPrefs()
        val today = utcDay()
        if (prefs.getString(KEY_DAY, null) != today) resetDailyUsage(prefs, today)
        lastPackage = ""
        lastEventAt = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_SERVICE_TICK_AT, lastEventAt).apply()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName.isBlank()) return
        val now = System.currentTimeMillis()
        accountForegroundUsage(now)
        lastPackage = packageName
        lastEventAt = now
        applyRestriction(packageName)
    }

    override fun onInterrupt() {
        hideBlocker()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        lockPrefs().edit().putLong(KEY_LAST_SERVICE_TICK_AT, 0L).apply()
        hideBlocker()
        super.onDestroy()
    }

    private fun accountForegroundUsage(now: Long) {
        val prefs = lockPrefs()
        val today = utcDay()
        if (prefs.getString(KEY_DAY, null) != today) resetDailyUsage(prefs, today)
        // Do not charge the child for time spent on EduLock's full-screen restriction notice.
        if (blocker != null || !prefs.getBoolean(KEY_CHILD_ACTIVE, false) || !isAllowedPackageForUsage(lastPackage)) {
            lastEventAt = now
            return
        }
        val delta = (now - lastEventAt).coerceIn(0L, MAX_SINGLE_DELTA_MS)
        if (delta > 0) {
            val appUsageKey = APP_USED_PREFIX + lastPackage
            val usedBefore = prefs.getLong(appUsageKey, 0L)
            val editor = prefs.edit()
                .putLong(KEY_USED_MS, prefs.getLong(KEY_USED_MS, 0L) + delta)
                .putLong(appUsageKey, usedBefore + delta)
            val appLimit = prefs.getStringSet(KEY_APP_DAILY_LIMITS, emptySet()).orEmpty()
                .firstOrNull { it.startsWith("$lastPackage=") }
                ?.substringAfter("=")?.toIntOrNull()?.coerceIn(1, 1440)
            val appEarned = prefs.getStringSet(KEY_APP_EARNED_MINUTES, emptySet()).orEmpty()
                .firstOrNull { it.startsWith("$lastPackage=") }
                ?.substringAfter("=")?.toIntOrNull()?.coerceIn(0, 1440) ?: 0
            // Group reward time is shared among all apps in the group. Count only use
            // beyond this app's ordinary and app-targeted allowance.
            if (appLimit != null && usedBefore >= (appLimit + appEarned) * MILLIS_PER_MINUTE) {
                prefs.getStringSet(KEY_APP_GROUP_PACKAGES, emptySet()).orEmpty().forEach { row ->
                    val groupId = row.substringBefore("=")
                    val members = row.substringAfter("=", "")
                    if (groupId.isNotBlank() && members.isNotBlank() && lastPackage in members.split(",")) {
                        val groupUsageKey = GROUP_USED_PREFIX + groupId
                        editor.putLong(groupUsageKey, prefs.getLong(groupUsageKey, 0L) + delta)
                    }
                }
            }
            editor.apply()
        }
        lastEventAt = now
    }

    private fun isAllowedPackageForUsage(packageName: String): Boolean {
        if (packageName == applicationContext.packageName ||
            packageName in essentialPackages(lockPrefs().getBoolean(KEY_KIDS_MODE_ENABLED, false)) ||
            isInternalSystemPackage(packageName)) return false
        return !lockPrefs().getStringSet(KEY_BLOCKED_PACKAGES, emptySet()).orEmpty().contains(packageName)
    }

    private fun applyRestriction(packageName: String) {
        val prefs = lockPrefs()
        val kidsModeEnabled = prefs.getBoolean(KEY_KIDS_MODE_ENABLED, false)
        if (!prefs.getBoolean(KEY_CHILD_ACTIVE, false) || packageName == applicationContext.packageName ||
            // In managed kiosk mode the Lock Task controller is authoritative. The Accessibility
            // overlay must not blanket-exempt the normal launcher when Kids Mode is on.
            packageName in essentialPackages(kidsModeEnabled) ||
            isInternalSystemPackage(packageName)) {
            hideBlocker()
            return
        }
        val allowedPackages = prefs.getStringSet(KEY_ALLOWED_PACKAGES, emptySet()).orEmpty()
        val blockedByKidsMode = kidsModeEnabled && packageName !in allowedPackages
        val explicitlyBlocked = prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()).orEmpty().contains(packageName)
        val appLimitMinutes = prefs.getStringSet(KEY_APP_DAILY_LIMITS, emptySet()).orEmpty()
            .firstOrNull { it.startsWith("$packageName=") }
            ?.substringAfter("=")
            ?.toIntOrNull()
            ?.coerceIn(1, 1440)
        val appUsed = prefs.getLong(APP_USED_PREFIX + packageName, 0L)
        val appEarnedMinutes = prefs.getStringSet(KEY_APP_EARNED_MINUTES, emptySet()).orEmpty()
            .firstOrNull { it.startsWith("$packageName=") }
            ?.substringAfter("=")?.toIntOrNull()?.coerceIn(0, 1440) ?: 0
        val effectiveAppLimit = appLimitMinutes?.plus(appEarnedMinutes)
        val matchingGroups = prefs.getStringSet(KEY_APP_GROUP_PACKAGES, emptySet()).orEmpty()
            .mapNotNull { row ->
                val groupId = row.substringBefore("=")
                val members = row.substringAfter("=", "")
                if (groupId.isNotBlank() && packageName in members.split(",")) groupId else null
            }
        val groupTimeAvailable = matchingGroups.any { groupId ->
            val earnedGroup = prefs.getStringSet(KEY_APP_EARNED_GROUPS, emptySet()).orEmpty()
                .firstOrNull { it.startsWith("$groupId=") }
                ?.substringAfter("=")?.toIntOrNull()?.coerceIn(0, 1440) ?: 0
            val groupUsed = prefs.getLong(GROUP_USED_PREFIX + groupId, 0L)
            earnedGroup > 0 && groupUsed < earnedGroup * MILLIS_PER_MINUTE
        }
        val blockedForAppTime = effectiveAppLimit != null &&
            appUsed >= effectiveAppLimit * MILLIS_PER_MINUTE && !groupTimeAvailable
        // A specific app or group reward can still be used when the general reward pool is spent.
        val earnedMinutes = prefs.getInt(KEY_EARNED_MINUTES, 0).coerceIn(0, 1440)
        val totalUsed = prefs.getLong(KEY_USED_MS, 0L).coerceAtLeast(0L)
        val blockedForEarnedTime = earnedMinutes > 0 &&
            totalUsed >= earnedMinutes * MILLIS_PER_MINUTE && appEarnedMinutes == 0 && !groupTimeAvailable
        val blockedForSchedule = !isWithinWeeklySchedule(packageName)
        if (blockedByKidsMode || explicitlyBlocked || blockedForAppTime || blockedForEarnedTime || blockedForSchedule) {
            val message = when {
                blockedByKidsMode -> "Kids Mode is on. Ask your parent to allow this app in EduLock."
                explicitlyBlocked -> "This app has been disabled by your parent."
                blockedForAppTime -> "Your daily limit for this app has been reached."
                blockedForEarnedTime -> "Your earned screen time has been used. Complete learning tasks to earn more time."
                else -> "This app is outside the allowed hours set by your parent."
            }
            showBlocker(message, packageName)
        } else {
            hideBlocker()
        }
    }

    private fun isWithinWeeklySchedule(packageName: String): Boolean {
        val row = lockPrefs().getStringSet(KEY_APP_WEEKLY_SCHEDULES, emptySet()).orEmpty()
            .firstOrNull { it.startsWith("$packageName=") } ?: return true
        return try {
            val json = org.json.JSONObject(row.substringAfter("="))
            if (json.length() == 0) return true
            val dayKey = when (LocalDate.now().dayOfWeek.value) {
                1 -> "mon"; 2 -> "tue"; 3 -> "wed"; 4 -> "thu"
                5 -> "fri"; 6 -> "sat"; else -> "sun"
            }
            val today = json.optJSONObject(dayKey) ?: return false
            if (!today.optBoolean("enabled", false)) return false
            val start = LocalTime.parse(today.optString("start"))
            val end = LocalTime.parse(today.optString("end"))
            val now = LocalTime.now()
            when {
                start == end -> false
                start.isBefore(end) -> !now.isBefore(start) && now.isBefore(end)
                else -> !now.isBefore(start) || now.isBefore(end)
            }
        } catch (_: Exception) {
            // Corrupt schedule data must not trap a child outside an app.
            true
        }
    }
    private fun showBlocker(message: String, packageName: String) {
        blockedPackageName = packageName
        if (blocker != null) {
            blockerDetail?.text = message
            return
        }
        val manager = windowManager ?: return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(36), dp(28), dp(36))
            setBackgroundColor(Color.rgb(244, 246, 255))
            alpha = 1f
            isClickable = true
            isFocusable = true
        }
        val title = TextView(this).apply {
            text = "EduLock"
            setTextColor(Color.rgb(35, 43, 82))
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val detail = TextView(this).apply {
            text = message
            setTextColor(Color.rgb(66, 76, 110))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(28))
        }
        val button = Button(this).apply {
            text = "Return to learning"
            setOnClickListener {
                val launch = packageManager.getLaunchIntentForPackage(applicationContext.packageName)
                if (launch != null) {
                    taskIdForPackage(blockedPackageName)?.let { targetTaskId ->
                        lockPrefs().edit().putString(KEY_PENDING_TASK_ID, targetTaskId).apply()
                    }
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(launch)
                }
            }
        }
        panel.addView(title, LinearLayout.LayoutParams(-1, -2))
        panel.addView(detail, LinearLayout.LayoutParams(-1, -2))
        panel.addView(button, LinearLayout.LayoutParams(-1, -2))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try {
            manager.addView(panel, params)
            blocker = panel
            blockerDetail = detail
            lockPrefs().edit()
                .putLong(KEY_LAST_BLOCKER_SUCCESS_AT, System.currentTimeMillis())
                .remove(KEY_LAST_BLOCKER_ERROR)
                .apply()
        } catch (error: Exception) {
            lockPrefs().edit()
                .putString(KEY_LAST_BLOCKER_ERROR, error.javaClass.simpleName + ": " + (error.message ?: "Android rejected the blocker"))
                .putLong(KEY_LAST_BLOCKER_ERROR_AT, System.currentTimeMillis())
                .apply()
        }
    }

    private fun taskIdForPackage(packageName: String): String? {
        val prefs = lockPrefs()
        val targeted = prefs.getStringSet(KEY_APP_TASK_RULES, emptySet()).orEmpty()
            .firstOrNull { it.startsWith("$packageName=") }
            ?.substringAfter("=")
        val candidate = targeted ?: prefs.getString("general_task_id", null)
        return candidate?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) }
    }

    private fun hideBlocker() {
        val view = blocker ?: return
        blocker = null
        blockerDetail = null
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
            // View may already have been detached by Android.
        }
    }

    private fun resetDailyUsage(prefs: android.content.SharedPreferences, today: String) {
        val editor = prefs.edit().putString(KEY_DAY, today).putLong(KEY_USED_MS, 0L)
            .putInt(KEY_EARNED_MINUTES, 0)
            .putStringSet(KEY_APP_EARNED_MINUTES, emptySet())
            .putStringSet(KEY_APP_EARNED_GROUPS, emptySet())
        prefs.all.keys.filter { it.startsWith(APP_USED_PREFIX) || it.startsWith(GROUP_USED_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    private fun lockPrefs() = getSharedPreferences(LOCK_PREFS, MODE_PRIVATE)
    private fun utcDay() = LocalDate.now(ZoneOffset.UTC).toString()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun essentialPackages(kidsModeEnabled: Boolean): Set<String> {
        // Preserve launcher, navigation, calls and permission components. Do not exempt Settings
        // during Kids Mode because that would let a child switch off the restriction service.
        val core = setOf(
            "com.android.systemui",
            "com.android.internal.systemui.navbar.threebutton",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            // Third-party/OEM HOME launchers are not exempt in Kids Mode. In normal
            // mode, retaining them prevents accidental blocking during app management.
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer"
        )
        val safeSystem = core + setOf(
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.miui.home",
            "com.samsung.android.oneui.home",
            "com.oplus.launcher",
            "com.huawei.android.launcher",
            "com.sec.android.app.launcher",
            "com.sonymobile.home",
            "com.oneplus.launcher"
        )
        return if (kidsModeEnabled) core else safeSystem + setOf(
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller"
        )
    }

    private fun isInternalSystemPackage(packageName: String): Boolean =
        packageName.startsWith("com.android.internal.") ||
            packageName == "com.google.android.adservices.api" ||
            packageName.startsWith("com.android.providers.")
    private companion object {
        const val LOCK_PREFS = "edulock_lock_state"
        const val KEY_PENDING_TASK_ID = "pending_task_id"
        const val KEY_LAST_SERVICE_TICK_AT = "last_service_tick_at"
        const val KEY_LAST_BLOCKER_SUCCESS_AT = "last_blocker_success_at"
        const val KEY_LAST_BLOCKER_ERROR = "last_blocker_error"
        const val KEY_LAST_BLOCKER_ERROR_AT = "last_blocker_error_at"
        const val KEY_KIDS_MODE_ENABLED = "kids_mode_enabled"
        const val KEY_CHILD_ACTIVE = "child_active"
        const val KEY_ALLOWED_PACKAGES = "allowed_packages"
        const val KEY_BLOCKED_PACKAGES = "blocked_packages"
        const val KEY_EARNED_MINUTES = "earned_minutes_today"
        const val KEY_USED_MS = "used_millis_today"
        const val KEY_DAY = "budget_day"
        const val KEY_APP_DAILY_LIMITS = "app_daily_limits"
        const val KEY_APP_EARNED_MINUTES = "app_earned_minutes"
        const val KEY_APP_EARNED_GROUPS = "app_earned_groups"
        const val KEY_APP_GROUP_PACKAGES = "app_group_packages"
        const val KEY_APP_TASK_RULES = "app_task_rules"
        const val KEY_APP_WEEKLY_SCHEDULES = "app_weekly_schedules"
        const val APP_USED_PREFIX = "app_used_millis_"
        const val GROUP_USED_PREFIX = "group_used_millis_"
        const val MILLIS_PER_MINUTE = 60_000L
        const val MAX_SINGLE_DELTA_MS = 20_000L
        const val TICK_MILLIS = 10_000L
    }
}
