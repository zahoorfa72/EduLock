package com.edulock.app

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.provider.Settings.Secure
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

private enum class AppScreen {
    DEVICE_SETUP, AUTH, VERIFY_OTP, PAIR_CHILD, PARENT_HOME, CHILD_HOME, CHILDREN, ADD_CHILD,
    SUBJECTS, ADD_SUBJECT, QUESTIONS, ADD_QUESTION, ADD_TRANSLATION, ASSIGN_TASK,
    REPORTS, ALLOWED_APPS, PROTECTION_STATUS, CHILD_LOCATIONS, APP_USAGE_REPORT, PLAY_TASK, TASK_RESULT
}

@Composable
private fun EduLockApp(repository: EduLockRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val devicePreferences = remember { context.getSharedPreferences("edulock_device_mode", Context.MODE_PRIVATE) }
    var deviceMode by remember { mutableStateOf(devicePreferences.getString("mode", "unknown") ?: "unknown") }
    var screen by remember { mutableStateOf(if (deviceMode == "unknown") AppScreen.DEVICE_SETUP else AppScreen.AUTH) }
    // Never populate the UI from an unverified cached session.
    var session by remember { mutableStateOf<AuthSession?>(null) }
    var accountRole by remember { mutableStateOf("parent") }
    var isRegistering by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailOtp by remember { mutableStateOf("") }
    var resendSeconds by remember { mutableStateOf(0) }
    var displayName by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var familyId by remember { mutableStateOf("") }
    var childProfile by remember { mutableStateOf<ChildProfile?>(null) }
    var children by remember { mutableStateOf(emptyList<ChildProfile>()) }
    var subjects by remember { mutableStateOf(emptyList<SubjectRow>()) }
    var selectedChild by remember { mutableStateOf<ChildProfile?>(null) }
    var selectedSubject by remember { mutableStateOf<SubjectRow?>(null) }
    var questions by remember { mutableStateOf(emptyList<QuestionRow>()) }
    var questionTranslations by remember { mutableStateOf<Map<String, List<QuestionTranslation>>>(emptyMap()) }
    var answerLanguages by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var conceptProgress by remember { mutableStateOf(emptyList<ConceptProgress>()) }
    var selectedQuestionForTranslation by remember { mutableStateOf<QuestionRow?>(null) }
    var tasks by remember { mutableStateOf(emptyList<TaskRow>()) }
    var attempts by remember { mutableStateOf(emptyList<AttemptRow>()) }
    var allowedApps by remember { mutableStateOf(emptyList<AllowedApp>()) }
    var childInstalledApps by remember { mutableStateOf(emptyList<ChildInstalledApp>()) }
    var installedAppLimitEdits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var lastPairingCode by remember { mutableStateOf("") }
    var pairingChildName by remember { mutableStateOf("") }
    var pairingCodeExpiry by remember { mutableStateOf(0) }
    var earnedToday by remember { mutableStateOf(0) }
    var activeTask by remember { mutableStateOf<TaskRow?>(null) }
    var questionIndex by remember { mutableStateOf(0) }
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var taskResult by remember { mutableStateOf<TaskResult?>(null) }

    var childName by remember { mutableStateOf("") }
    var childLanguage by remember { mutableStateOf("en") }
    var childDailyCap by remember { mutableStateOf("60") }
    var subjectTitle by remember { mutableStateOf("") }
    var subjectLanguage by remember { mutableStateOf("en") }
    var questionPrompt by remember { mutableStateOf("") }
    var questionType by remember { mutableStateOf("multiple_choice") }
    var questionOptions by remember { mutableStateOf("") }
    var questionAnswer by remember { mutableStateOf("") }
    var questionLanguage by remember { mutableStateOf("en") }
    var questionConceptKey by remember { mutableStateOf("") }
    var translationLanguage by remember { mutableStateOf("ur") }
    var translationPrompt by remember { mutableStateOf("") }
    var translationOptions by remember { mutableStateOf("") }
    var translationAcceptedAnswers by remember { mutableStateOf("") }
    var taskTitle by remember { mutableStateOf("") }
    var taskQuestionCount by remember { mutableStateOf("5") }
    var taskPassPercent by remember { mutableStateOf("70") }
    var taskRewardMinutes by remember { mutableStateOf("15") }
    var taskDailyCapMinutes by remember { mutableStateOf("60") }
    var appPackage by remember { mutableStateOf("") }
    var appDisplayName by remember { mutableStateOf("") }
    var appDailyLimit by remember { mutableStateOf("60") }
    var appLimitEdits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var appUsageChild by remember { mutableStateOf<ChildProfile?>(null) }
    var appUsageReports by remember { mutableStateOf(emptyList<AppUsageReport>()) }
    var childLocationSharing by remember {
        mutableStateOf(context.getSharedPreferences(ChildLocationService.PREFS, Context.MODE_PRIVATE)
            .getBoolean(ChildLocationService.KEY_SHARING_ENABLED, false))
    }
    var childLocationRows by remember { mutableStateOf(emptyList<ChildLocationSnapshot>()) }
    var pendingChildAction by remember { mutableStateOf<Pair<ChildProfile, String>?>(null) }
    var protectionAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var protectionDeviceAdminEnabled by remember { mutableStateOf(isDeviceAdminActive(context)) }
    var protectionManagedEnabled by remember { mutableStateOf(isManagedDeviceProtectionActive(context)) }

    fun refreshProtectionStatus() {
        protectionAccessibilityEnabled = isAccessibilityServiceEnabled(context)
        protectionDeviceAdminEnabled = isDeviceAdminActive(context)
        protectionManagedEnabled = isManagedDeviceProtectionActive(context)
    }

    fun enableChildLocationSharing() {
        val child = childProfile
        if (child == null || repository.currentSession()?.accountRole != "child") {
            notice = "Pair this device with a parent before enabling location sharing."
            return
        }
        val locationPrefs = context.getSharedPreferences(ChildLocationService.PREFS, Context.MODE_PRIVATE)
        locationPrefs.edit()
            .putString(ChildLocationService.KEY_CHILD_ID, child.id)
            .putBoolean(ChildLocationService.KEY_SHARING_ENABLED, true)
            .apply()
        childLocationSharing = true
        try {
            context.startForegroundService(
                Intent(context, ChildLocationService::class.java).setAction(ChildLocationService.ACTION_START)
            )
            notice = "Location sharing enabled. Android shows an ongoing notification while EduLock reports location."
        } catch (e: Exception) {
            locationPrefs.edit().putBoolean(ChildLocationService.KEY_SHARING_ENABLED, false).apply()
            childLocationSharing = false
            notice = "Could not start location sharing. Check permissions and battery settings: " + (e.message ?: "try again")
        }
    }

    fun disableChildLocationSharing() {
        val locationPrefs = context.getSharedPreferences(ChildLocationService.PREFS, Context.MODE_PRIVATE)
        locationPrefs.edit().putBoolean(ChildLocationService.KEY_SHARING_ENABLED, false).apply()
        childLocationSharing = false
        try {
            context.startForegroundService(
                Intent(context, ChildLocationService::class.java).setAction(ChildLocationService.ACTION_DISABLE)
            )
            notice = "Location sharing paused. The last successful location remains saved for the parent."
        } catch (e: Exception) {
            context.stopService(Intent(context, ChildLocationService::class.java))
            notice = "Location sharing paused on this device. The server status may take time to update."
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val foregroundGranted =
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!foregroundGranted) {
            notice = "Location permission was not granted. EduLock cannot share a location without it."
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            notice = "To keep location updates running when EduLock is not on screen, open App info → Permissions → Location and choose Allow all the time. Then return and tap Enable location sharing again."
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            } catch (_: Exception) {
                // Keep instructions available if Android cannot open settings.
            }
        } else {
            enableChildLocationSharing()
        }
    }

    fun requestChildLocationSharing() {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            notice = "Enable Location → Allow all the time in Android App info so sharing can continue in the background."
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            } catch (_: Exception) { }
            return
        }
        enableChildLocationSharing()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notice = if (granted) "EduLock notifications are enabled." else
            "Notifications are disabled. EduLock can show status in the app, but Android may suppress notifications."
    }

    fun execute(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            notice = null
            try {
                action()
            } catch (e: Exception) {
                notice = e.message?.take(500) ?: "Something went wrong. Please try again."
            } finally {
                busy = false
            }
        }
    }


    pendingChildAction?.let { (targetChild, action) ->
        val isDelete = action == "delete"
        AlertDialog(
            onDismissRequest = { pendingChildAction = null },
            title = { Text(if (isDelete) "Delete child profile?" else "Deactivate device link?") },
            text = {
                Text(
                    if (isDelete)
                        "Permanently delete ${targetChild.displayName}'s profile and its linked EduLock records. This cannot be undone."
                    else
                        "Unlink ${targetChild.displayName}'s device. The current device will lose access to this child profile; a new pairing code can link a device again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingChildAction = null
                    execute {
                        if (isDelete) {
                            repository.deleteChildProfile(targetChild.id)
                            if (selectedChild?.id == targetChild.id) selectedChild = null
                            if (childProfile?.id == targetChild.id) childProfile = null
                            notice = "Child profile deleted."
                        } else {
                            repository.deactivateChildLink(targetChild.id)
                            notice = "Device link deactivated. Generate a new pairing code to link a device again."
                        }
                        children = repository.loadChildren(familyId)
                        childInstalledApps = emptyList()
                        allowedApps = emptyList()
                    }
                }) { Text(if (isDelete) "Delete permanently" else "Deactivate link") }
            },
            dismissButton = {
                TextButton(onClick = { pendingChildAction = null }) { Text("Cancel") }
            }
        )
    }

    suspend fun refreshParentData() {
        familyId = repository.ensureParentFamily(displayName.ifBlank { "My family" })
        children = repository.loadChildren(familyId)
        subjects = repository.loadSubjects(familyId)
    }

    suspend fun finishChildLogin(newSession: AuthSession, suppliedCode: String) {
        session = newSession
        screen = AppScreen.PAIR_CHILD
        if (suppliedCode.isNotBlank()) repository.claimChildPairing(suppliedCode)
        val linked = repository.loadCurrentChild()
        if (linked == null) {
            notice = "Link this child account with the six-digit code generated by the parent."
            return
        }
        childProfile = linked
        val locationPrefs = context.getSharedPreferences(ChildLocationService.PREFS, Context.MODE_PRIVATE)
        if (locationPrefs.getString(ChildLocationService.KEY_CHILD_ID, null) != linked.id) {
            locationPrefs.edit()
                .putString(ChildLocationService.KEY_CHILD_ID, linked.id)
                .putBoolean(ChildLocationService.KEY_SHARING_ENABLED, false)
                // Never carry a previous child's locally queued point into a new pairing.
                .remove(ChildLocationService.KEY_PENDING)
                .remove(ChildLocationService.KEY_LATITUDE)
                .remove(ChildLocationService.KEY_LONGITUDE)
                .remove(ChildLocationService.KEY_ACCURACY)
                .remove(ChildLocationService.KEY_POINT_TIME)
                .apply()
            childLocationSharing = false
        } else {
            childLocationSharing = locationPrefs.getBoolean(ChildLocationService.KEY_SHARING_ENABLED, false)
        }
        tasks = repository.loadChildTasks(linked.id)
        try {
            earnedToday = repository.refreshChildDeviceRestrictions(linked)
            repository.syncAppUsage(linked.id)
        } catch (e: Exception) {
            notice = "Signed in, but device settings or usage did not sync: " + (e.message ?: "try again")
        }
        screen = AppScreen.CHILD_HOME
    }

    suspend fun resolveSignedIn(newSession: AuthSession, suppliedCode: String = "") {
        if (deviceMode == "unknown") {
            repository.signOut()
            session = null
            screen = AppScreen.DEVICE_SETUP
            notice = "Choose whether this is a parent phone or a child phone before continuing."
            return
        }
        if (newSession.accountRole != deviceMode) {
            repository.signOut()
            session = null
            childProfile = null
            screen = if (deviceMode == "child") AppScreen.PAIR_CHILD else AppScreen.AUTH
            notice = if (deviceMode == "child")
                "This is configured as a child phone. A parent session was cleared; enter the parent's six-digit pairing code."
            else
                "This is configured as a parent phone. Sign in with the parent's email and password."
            return
        }
        session = newSession
        accountRole = newSession.accountRole
        if (newSession.accountRole == "parent") {
            childProfile = null
            familyId = repository.ensureParentFamily(displayName.ifBlank { "My family" })
            children = repository.loadChildren(familyId)
            subjects = repository.loadSubjects(familyId)
            LocationWatchdogWorker.schedule(context)
            screen = AppScreen.PARENT_HOME
        } else {
            finishChildLogin(newSession, suppliedCode)
        }
    }


    fun chooseParentDevice() {
        devicePreferences.edit().putString("mode", "parent").apply()
        deviceMode = "parent"
        screen = AppScreen.AUTH
        execute {
            val restored = repository.restoreSession()
            if (restored != null && restored.accountRole == "parent") {
                session = restored
                accountRole = "parent"
                resolveSignedIn(restored)
            } else {
                repository.signOut()
                session = null
                childProfile = null
                screen = AppScreen.AUTH
            }
        }
    }

    fun chooseChildDevice() {
        devicePreferences.edit().putString("mode", "child").apply()
        deviceMode = "child"
        screen = AppScreen.PAIR_CHILD
        pairingCode = ""
        execute {
            val restored = repository.restoreSession()
            if (restored != null && restored.accountRole == "child") {
                session = restored
                accountRole = "child"
                resolveSignedIn(restored)
                if (screen != AppScreen.CHILD_HOME) {
                    screen = AppScreen.PAIR_CHILD
                    notice = "Enter the six-digit code generated in the parent's profile to finish pairing."
                }
            } else {
                repository.signOut()
                session = null
                childProfile = null
                pairingCode = ""
                screen = AppScreen.PAIR_CHILD
                notice = "This phone is now in child mode. Enter the six-digit code generated in the parent's profile. Parent dashboard access is disabled on this phone."
            }
        }
    }


    LaunchedEffect(resendSeconds) {
        if (resendSeconds > 0) { delay(1000); resendSeconds -= 1 }
    }

    LaunchedEffect(screen, childProfile?.id) {
        val activeChildId = childProfile?.id
        if (screen == AppScreen.CHILD_HOME && !activeChildId.isNullOrBlank()) {
            while (true) {
                delay(30_000)
                try {
                    val linked = childProfile
                    if (linked != null && linked.id == activeChildId) {
                        // Pull parent changes to app allow/deny state and per-app limits
                        // while the child's learning screen is active.
                        earnedToday = repository.refreshChildDeviceRestrictions(linked)
                        repository.syncAppUsage(activeChildId)
                    }
                } catch (_: Exception) {
                    // Keep the last applied rules while offline; retry on the next cycle.
                }
            }
        }
    }

    LaunchedEffect(screen, childProfile?.id) {
        val child = childProfile
        if (screen == AppScreen.CHILD_HOME && child != null && repository.currentSession()?.accountRole == "child") {
            while (true) {
                try {
                    // Include installed packages without a launcher icon; parents need a full inventory.
                    val installedApps = if (Build.VERSION.SDK_INT >= 33) {
                        context.packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getInstalledApplications(0)
                    }
                    val visibleApps = installedApps.mapNotNull { info ->
                        val pkg = info.packageName
                        if (pkg == context.packageName) return@mapNotNull null
                        val label = try {
                            info.loadLabel(context.packageManager)?.toString()?.trim().orEmpty()
                        } catch (_: Exception) { "" }
                        if (label.isBlank() || pkg.startsWith("com.android.providers.")) null else pkg to label
                    }.distinctBy { it.first }.take(500)
                    repository.syncInstalledApps(child.id, visibleApps)
                } catch (_: Exception) {
                    // Keep the child experience available if inventory sync is temporarily unavailable.
                }
                delay(5 * 60 * 1000L)
            }
        }
    }

    LaunchedEffect(screen, familyId) {
        if (screen == AppScreen.CHILD_LOCATIONS && familyId.isNotBlank()) {
            while (true) {
                delay(60_000)
                try {
                    val latestChildren = repository.loadChildren(familyId)
                    children = latestChildren
                    childLocationRows = latestChildren.map { repository.loadChildLocationSnapshot(it.id) }
                } catch (_: Exception) {
                    // Keep the last successfully loaded points visible when temporarily offline.
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (deviceMode == "unknown") {
            screen = AppScreen.DEVICE_SETUP
            if (!repository.isConfigured) {
                notice = "Configuration needed: set the public Supabase URL and anon/publishable key in GitHub Actions secrets before building."
            }
            return@LaunchedEffect
        }

        val restored = repository.restoreSession()
        if (restored != null && restored.accountRole == deviceMode) {
            session = restored
            accountRole = restored.accountRole
            try {
                resolveSignedIn(restored)
            } catch (e: Exception) {
                notice = e.message ?: "Could not restore the session. Please sign in again."
                screen = if (deviceMode == "child") AppScreen.PAIR_CHILD else AppScreen.AUTH
            }
        } else {
            if (restored != null) repository.signOut()
            session = null
            childProfile = null
            screen = if (deviceMode == "child") AppScreen.PAIR_CHILD else AppScreen.AUTH
            if (deviceMode == "child") {
                notice = "Enter the six-digit code generated by the parent to link this child phone."
            }
        }
        if (!repository.isConfigured) {
            notice = "Supabase is not configured yet. Add SUPABASE_URL and SUPABASE_ANON_KEY to GitHub Actions secrets before building."
        }
    }
    BackHandler(enabled = screen != AppScreen.AUTH) {
        screen = when (screen) {
            AppScreen.DEVICE_SETUP -> AppScreen.DEVICE_SETUP
            AppScreen.VERIFY_OTP -> AppScreen.AUTH
            AppScreen.PAIR_CHILD -> {
                repository.signOut()
                session = null
                childProfile = null
                devicePreferences.edit().remove("mode").apply()
                deviceMode = "unknown"
                AppScreen.DEVICE_SETUP
            }
            AppScreen.PARENT_HOME, AppScreen.AUTH -> AppScreen.AUTH
            // Keep a child device out of the parent login/dashboard route.
            AppScreen.CHILD_HOME -> AppScreen.CHILD_HOME
            AppScreen.CHILDREN, AppScreen.SUBJECTS, AppScreen.ASSIGN_TASK, AppScreen.REPORTS, AppScreen.PROTECTION_STATUS, AppScreen.CHILD_LOCATIONS -> AppScreen.PARENT_HOME
            AppScreen.ADD_CHILD, AppScreen.APP_USAGE_REPORT, AppScreen.ALLOWED_APPS -> AppScreen.CHILDREN
            AppScreen.ADD_SUBJECT, AppScreen.QUESTIONS -> AppScreen.SUBJECTS
            AppScreen.ADD_QUESTION, AppScreen.ADD_TRANSLATION -> AppScreen.QUESTIONS
            AppScreen.PLAY_TASK, AppScreen.TASK_RESULT -> AppScreen.CHILD_HOME
        }
        notice = null
    }

    val eduLockColors = lightColorScheme(
        primary = Color(0xFF5147E8),
        onPrimary = Color.White,
        secondary = Color(0xFF078C87),
        onSecondary = Color.White,
        tertiary = Color(0xFFFFB547),
        background = Color(0xFFF7F8FF),
        surface = Color.White,
        surfaceVariant = Color(0xFFEEF0FF),
        onSurface = Color(0xFF1B2440),
        onSurfaceVariant = Color(0xFF5B6680),
        error = Color(0xFFCE3D52)
    )
    val eduLockShapes = Shapes(
        small = RoundedCornerShape(15.dp),
        medium = RoundedCornerShape(22.dp),
        large = RoundedCornerShape(29.dp)
    )
    MaterialTheme(colorScheme = eduLockColors, shapes = eduLockShapes) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (screen) {
                AppScreen.DEVICE_SETUP -> ScreenFrame("Set up this phone", notice = notice, busy = busy) {
                    Text("Choose how this phone will use EduLock.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("A parent phone signs in with the parent's email and password. A child phone must be linked using the six-digit, one-time code generated from the parent's child profile.")
                    Button(
                        onClick = { chooseParentDevice() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("This is the parent's phone") }
                    OutlinedButton(
                        onClick = { chooseChildDevice() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("This is the child's phone — pair with code") }
                }
                AppScreen.AUTH -> ScreenFrame("EduLock", notice = notice, busy = busy) {
                    Text("Learn first. Play with purpose.", style = MaterialTheme.typography.titleMedium)
                    Text("A parent manages the family. A child device joins securely with a one-time pairing code.")
                    if (isRegistering) {
                        Field(displayName, { displayName = it }, "Parent name")
                    }
                    Field(email, { email = it }, "Parent email", keyboardType = KeyboardType.Email)
                    Field(password, { password = it }, "Password (minimum 8 characters)", password = true)
                    Button(
                        onClick = {
                            execute {
                                val result = repository.signUp(email, password, displayName, "parent")
                                if (result.session == null) {
                                    emailOtp = ""
                                    resendSeconds = 60
                                    accountRole = "parent"
                                    screen = AppScreen.VERIFY_OTP
                                    notice = "We sent an eight-digit verification code to $email. Enter it below to verify your email."
                                } else {
                                    resolveSignedIn(result.session)
                                    if (screen == AppScreen.PARENT_HOME) notice = result.message
                                }
                            }
                        },
                        enabled = !busy && isRegistering,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Create parent account") }
                    Button(
                        onClick = {
                            execute {
                                val signedIn = repository.signIn(email, password, "parent")
                                resolveSignedIn(signedIn)
                            }
                        },
                        enabled = !busy && !isRegistering,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sign in as parent") }
                    TextButton(onClick = { isRegistering = !isRegistering; notice = null }) {
                        Text(if (isRegistering) "Already registered? Sign in" else "New to EduLock? Create parent account")
                    }
                    OutlinedButton(
                        onClick = { chooseChildDevice() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Set up a child's device with pairing code") }
                    if (!repository.isConfigured) {
                        Text("Configuration needed: set the public Supabase URL and anon/publishable key in the GitHub Actions secrets before building. Never add a service-role key to the Android app.")
                    }
                }

                AppScreen.VERIFY_OTP -> ScreenFrame("Let's verify your email ✨", onBack = {
                    screen = AppScreen.AUTH
                    isRegistering = true
                    notice = "Enter the eight-digit code sent to $email to finish creating your account."
                }, notice = notice, busy = busy) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FF)), shape = MaterialTheme.shapes.extraLarge) {
                        Column(modifier = Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🎨", fontSize = 42.sp)
                            Text("One tiny step to start!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF173B67))
                            Text("We sent an 8-digit code to", color = Color(0xFF42566D))
                            Text(email, fontWeight = FontWeight.SemiBold, color = Color(0xFF087E8B))
                            Text("Check your inbox and spam folder, then type the code below.", color = Color(0xFF42566D))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Field(emailOtp, { emailOtp = it.filter(Char::isDigit).take(8) }, "8-digit verification code", keyboardType = KeyboardType.Number)
                    Button(onClick = {
                        execute {
                            val verified = repository.verifySignupOtp(email, emailOtp, accountRole)
                            resolveSignedIn(verified, pairingCode)
                            if (screen == AppScreen.PARENT_HOME || screen == AppScreen.CHILD_HOME) notice = "🎉 Email verified! Welcome to EduLock."
                        }
                    }, enabled = !busy && emailOtp.length == 8, modifier = Modifier.fillMaxWidth()) { Text("✓  Verify & continue") }
                    OutlinedButton(onClick = {
                        execute {
                            repository.resendSignupOtp(email)
                            resendSeconds = 60
                            emailOtp = ""
                            notice = "A new code was requested. Check your inbox and spam folder."
                        }
                    }, enabled = !busy && resendSeconds == 0, modifier = Modifier.fillMaxWidth()) {
                        Text(if (resendSeconds > 0) "Resend code in " + resendSeconds + "s" else "↻  Send a new code")
                    }
                    Text(if (resendSeconds > 0) "For your security, you can request another code in " + resendSeconds + " seconds." else "Didn't receive it? Check Spam, then request a new code.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppScreen.PAIR_CHILD -> ScreenFrame("Pair this child's device", onBack = {
                    repository.signOut()
                    session = null
                    childProfile = null
                    devicePreferences.edit().remove("mode").apply()
                    deviceMode = "unknown"
                    screen = AppScreen.DEVICE_SETUP
                }, notice = notice, busy = busy) {
                    Text("Enter the six-digit, one-time code generated by the parent. No child email address or password is needed.")
                    Text("The code expires after 10 minutes and can be used once. Keep this device's app data intact after pairing; anonymous child sessions cannot be recovered if app data is cleared.")
                    Field(pairingCode, { pairingCode = it.filter(Char::isDigit).take(6) }, "6-digit parent pairing code", keyboardType = KeyboardType.Number)
                    Button(onClick = {
                        execute {
                            val childSession = repository.signInAnonymouslyAsChild()
                            session = childSession
                            finishChildLogin(childSession, pairingCode)
                        }
                    }, enabled = !busy && pairingCode.filter(Char::isDigit).length == 6, modifier = Modifier.fillMaxWidth()) {
                        Text("Pair securely with parent")
                    }
                    OutlinedButton(onClick = {
                        execute {
                            val current = repository.currentSession()
                                ?: throw IllegalStateException("Enter the pairing code to set up this device.")
                            resolveSignedIn(current)
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Continue with an already linked device") }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D8)),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("One-time setup for the parent", fontWeight = FontWeight.Bold, color = Color(0xFF704B00))
                            Text(
                                "If pairing says anonymous sign-ins are disabled, enable this setting in the parent-managed Supabase project: Dashboard → Authentication → Sign In / Providers → Anonymous. Save the setting, then retry pairing. Reinstalling the app will not enable this project setting.",
                                color = Color(0xFF704B00)
                            )
                            Text(
                                "Never put a Supabase service-role key in the Android app. The child session is linked only after the parent-generated one-time code is claimed.",
                                color = Color(0xFF704B00),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                AppScreen.PARENT_HOME -> ScreenFrame("Parent dashboard", notice = notice, busy = busy) {
                    Text("Your family is protected by your signed-in parent account.")
                    SummaryCard("Children", children.size.toString() + " profile(s)", "Add children and link their own devices.")
                    SummaryCard("Learning", subjects.size.toString() + " subject(s)", "Create questions in Urdu, Sindhi, English or another language.")
                    Button(onClick = { screen = AppScreen.CHILDREN; execute { children = repository.loadChildren(familyId) } }, modifier = Modifier.fillMaxWidth()) { Text("Manage child profiles") }
                    Button(onClick = { screen = AppScreen.SUBJECTS; execute { subjects = repository.loadSubjects(familyId) } }, modifier = Modifier.fillMaxWidth()) { Text("Subjects & questions") }
                    Button(onClick = { selectedChild = null; selectedSubject = null; screen = AppScreen.ASSIGN_TASK }, modifier = Modifier.fillMaxWidth()) { Text("Assign learning task & reward") }
                    OutlinedButton(onClick = { screen = AppScreen.REPORTS; execute { attempts = repository.loadAttempts(); conceptProgress = repository.loadConceptProgress() } }, modifier = Modifier.fillMaxWidth()) { Text("🏆  Learning progress reports") }
                    OutlinedButton(onClick = {
                        screen = AppScreen.CHILDREN
                        execute { children = repository.loadChildren(familyId) }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Allowed apps & screen-time settings") }
                    OutlinedButton(onClick = {
                        screen = AppScreen.CHILD_LOCATIONS
                        execute {
                            val latestChildren = repository.loadChildren(familyId)
                            children = latestChildren
                            childLocationRows = latestChildren.map { repository.loadChildLocationSnapshot(it.id) }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("📍 Family location & offline alerts") }
                    if (Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        OutlinedButton(
                            onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Enable offline-device notifications") }
                    }
                    OutlinedButton(onClick = {
                        refreshProtectionStatus()
                        screen = AppScreen.PROTECTION_STATUS
                    }, modifier = Modifier.fillMaxWidth()) { Text("Device protection & setup status") }
                    OutlinedButton(onClick = {
                        repository.signOut()
                        session = null
                        childProfile = null
                        screen = AppScreen.AUTH
                        isRegistering = false
                        notice = "Signed out."
                    }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
                }

                AppScreen.CHILDREN -> ScreenFrame("Child profiles", onBack = { screen = AppScreen.PARENT_HOME }, notice = notice, busy = busy) {
                    Button(onClick = {
                        childName = ""; childLanguage = "en"; childDailyCap = "60"
                        screen = AppScreen.ADD_CHILD
                    }, modifier = Modifier.fillMaxWidth()) { Text("Add child profile") }
                    if (children.isEmpty()) Text("No child profiles yet. Add a child to create the protected parent-child link.")
                    children.forEach { child ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(child.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Language: " + child.preferredLanguage + " • Daily cap: " + child.dailyCapMinutes + " minutes")
                                Text("Device link: generate a one-time code on the child's first setup.")
                                OutlinedButton(onClick = {
                                    selectedChild = child
                                    lastPairingCode = ""
                                    execute {
                                        val generated = repository.createPairingCode(child.id)
                                        lastPairingCode = generated.first
                                        pairingCodeExpiry = generated.second
                                        pairingChildName = child.displayName
                                    }
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Generate pairing code") }
                                OutlinedButton(onClick = {
                                    selectedChild = child
                                    screen = AppScreen.ALLOWED_APPS
                                    execute { allowedApps = repository.loadAllowedApps(child.id) }
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("🎮  App access & daily limits") }
                                OutlinedButton(
                                    onClick = { pendingChildAction = child to "unlink" },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Deactivate device link") }
                                OutlinedButton(
                                    onClick = { pendingChildAction = child to "delete" },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Delete child profile") }
                                OutlinedButton(onClick = {
                                    appUsageChild = child
                                    screen = AppScreen.APP_USAGE_REPORT
                                    execute { appUsageReports = repository.loadAppUsageReports(child.id) }
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("📊  App usage report") }
                            }
                        }
                    }
                    if (lastPairingCode.isNotBlank()) {
                        Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("One-time pairing code for " + pairingChildName, fontWeight = FontWeight.SemiBold)
                                Text(lastPairingCode, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                                Text("Valid for " + (pairingCodeExpiry / 60) + " minutes. Share privately with the child; only its hash is stored.")
                                OutlinedButton(onClick = { lastPairingCode = "" }, modifier = Modifier.fillMaxWidth()) { Text("Hide code") }
                            }
                        }
                    }
                }

                AppScreen.ADD_CHILD -> ScreenFrame("Add child", onBack = { screen = AppScreen.CHILDREN }, notice = notice, busy = busy) {
                    Field(childName, { childName = it }, "Child's display name")
                    Field(childLanguage, { childLanguage = it }, "Preferred language code: en, ur, sd or another code")
                    Text("Language is a preference; questions can also be written in any Unicode language.")
                    Field(childDailyCap, { childDailyCap = it.filter(Char::isDigit).take(4) }, "Maximum reward minutes per day (0–1440)", keyboardType = KeyboardType.Number)
                    Button(onClick = {
                        execute {
                            val created = repository.addChild(familyId, childName, childLanguage, childDailyCap.toIntOrNull() ?: 60)
                            children = repository.loadChildren(familyId)
                            selectedChild = created
                            screen = AppScreen.CHILDREN
                            notice = "Child profile saved. Generate a pairing code so the child can sign in on their own device."
                        }
                    }, enabled = !busy && childName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save child profile") }
                }

                AppScreen.SUBJECTS -> ScreenFrame("Subjects & questions", onBack = { screen = AppScreen.PARENT_HOME }, notice = notice, busy = busy) {
                    Button(onClick = { subjectTitle = ""; subjectLanguage = "en"; screen = AppScreen.ADD_SUBJECT }, modifier = Modifier.fillMaxWidth()) { Text("Create subject") }
                    if (subjects.isEmpty()) Text("Create a subject first, for example Mathematics (en), رياضي (ur) or a Sindhi subject.")
                    subjects.forEach { subject ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedSubject = subject
                                screen = AppScreen.QUESTIONS
                                execute { questions = repository.loadQuestions(subject.id) }
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(subject.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Language: " + subject.languageCode)
                                Text("Tap to manage questions")
                            }
                        }
                    }
                }

                AppScreen.ADD_SUBJECT -> ScreenFrame("Create subject", onBack = { screen = AppScreen.SUBJECTS }, notice = notice, busy = busy) {
                    Field(subjectTitle, { subjectTitle = it }, "Subject name")
                    Field(subjectLanguage, { subjectLanguage = it }, "Language code (en, ur, sd, or any language)")
                    Button(onClick = {
                        execute {
                            repository.addSubject(familyId, subjectTitle, subjectLanguage)
                            subjects = repository.loadSubjects(familyId)
                            screen = AppScreen.SUBJECTS
                            notice = "Subject saved."
                        }
                    }, enabled = !busy && subjectTitle.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save subject") }
                }

                AppScreen.QUESTIONS -> ScreenFrame(selectedSubject?.title ?: "Questions", onBack = { screen = AppScreen.SUBJECTS }, notice = notice, busy = busy) {
                    val subject = selectedSubject
                    if (subject != null) {
                        Text("Create a question in " + subject.languageCode + " and give related questions the same concept tag. The tag lets Concept Bridge report understanding separately from language.")
                        Button(onClick = {
                            questionPrompt = ""; questionOptions = ""; questionAnswer = ""; questionType = "multiple_choice"
                            questionLanguage = subject.languageCode
                            questionConceptKey = ""
                            screen = AppScreen.ADD_QUESTION
                        }, modifier = Modifier.fillMaxWidth()) { Text("Add question") }
                        if (questions.isEmpty()) Text("No questions yet.")
                        questions.forEachIndexed { index, question ->
                            CompositionLocalProvider(LocalLayoutDirection provides directionFor(question.languageCode)) {
                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Text((index + 1).toString() + ". " + question.prompt, fontWeight = FontWeight.SemiBold)
                                        Text("Type: " + readableQuestionType(question.questionType))
                                        Text("Language: " + question.languageCode + " • Concept tag: " + question.conceptKey)
                                        if (question.options.isNotEmpty()) Text("Options: " + question.options.joinToString(" • "))
                                        Text("Translations: " + (questionTranslations[question.id]?.size ?: 0))
                                        OutlinedButton(onClick = {
                                            selectedQuestionForTranslation = question
                                            translationLanguage = when (question.languageCode.lowercase()) {
                                                "en" -> "ur"
                                                "ur" -> "sd"
                                                else -> "en"
                                            }
                                            translationPrompt = ""
                                            translationOptions = ""
                                            translationAcceptedAnswers = ""
                                            screen = AppScreen.ADD_TRANSLATION
                                        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Add / edit translation") }
                                    }
                                }
                            }
                        }
                    }
                }

                AppScreen.ADD_QUESTION -> ScreenFrame("Add question", onBack = { screen = AppScreen.QUESTIONS }, notice = notice, busy = busy) {
                    val lang = questionLanguage
                    Text("Question language: " + lang + ". Type in Urdu, Sindhi, English or another Unicode language.")
                    CompositionLocalProvider(LocalLayoutDirection provides directionFor(lang)) {
                        Field(questionPrompt, { questionPrompt = it }, "Question prompt")
                        Field(questionConceptKey, { questionConceptKey = it }, "Concept tag (reuse for same idea across questions)")
                        Field(questionLanguage, { questionLanguage = it }, "Question language code (en, ur, sd, etc.)")
                        Text("Question type")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { questionType = "multiple_choice" }) { Text(if (questionType == "multiple_choice") "✓ MCQ" else "MCQ") }
                            OutlinedButton(onClick = { questionType = "true_false" }) { Text(if (questionType == "true_false") "✓ True/False" else "True/False") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { questionType = "short_answer" }) { Text(if (questionType == "short_answer") "✓ Short" else "Short") }
                            OutlinedButton(onClick = { questionType = "numeric" }) { Text(if (questionType == "numeric") "✓ Numeric" else "Numeric") }
                        }
                        if (questionType == "multiple_choice") {
                            Field(questionOptions, { questionOptions = it }, "Options (one per line)")
                        } else if (questionType == "true_false") {
                            Text("Save the canonical answer as true or false. A translation can use localized labels and accepted answers.")
                        }
                        Field(questionAnswer, { questionAnswer = it }, "Canonical correct answer (private)")
                    }
                    Button(onClick = {
                        execute {
                            val subject = selectedSubject ?: throw IllegalStateException("Choose a subject first.")
                            val options = questionOptions.lines().map { it.trim() }.filter { it.isNotEmpty() }
                            repository.addQuestion(subject.id, questionPrompt, questionType, options, questionAnswer, questionLanguage, questionConceptKey)
                            questions = repository.loadQuestions(subject.id)
                            questionTranslations = repository.loadQuestionTranslations(questions.map { it.id })
                            screen = AppScreen.QUESTIONS
                            notice = "Question saved. Add translations from the question card to activate Concept Bridge."
                        }
                    }, enabled = !busy && questionPrompt.isNotBlank() && questionAnswer.isNotBlank() && questionConceptKey.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save question") }
                }

                AppScreen.ADD_TRANSLATION -> ScreenFrame("Concept Bridge — translation", onBack = { screen = AppScreen.QUESTIONS }, notice = notice, busy = busy) {
                    val original = selectedQuestionForTranslation
                    if (original == null) {
                        Text("Choose a question first.")
                    } else {
                        Text("Original: " + original.prompt)
                        Text("Concept tag: " + original.conceptKey + " • Original language: " + original.languageCode)
                        Text("Save each translated question with its translated options and accepted correct answers. The child can switch between these language versions without changing the underlying question or concept.")
                        Field(translationLanguage, { translationLanguage = it }, "Target language code (en, ur, sd, etc.)")
                        CompositionLocalProvider(LocalLayoutDirection provides directionFor(translationLanguage)) {
                            Field(translationPrompt, { translationPrompt = it }, "Translated question prompt")
                            if (original.questionType == "multiple_choice" || original.questionType == "true_false") {
                                Field(translationOptions, { translationOptions = it }, "Translated options (one per line)")
                            }
                            Field(translationAcceptedAnswers, { translationAcceptedAnswers = it }, "Accepted correct answers (one per line)")
                        }
                        Button(onClick = {
                            execute {
                                val question = selectedQuestionForTranslation ?: throw IllegalStateException("Choose a question first.")
                                val opts = translationOptions.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                val accepted = translationAcceptedAnswers.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                repository.saveQuestionTranslation(question, translationLanguage, translationPrompt, opts, accepted)
                                selectedSubject?.let { subject ->
                                    questionTranslations = repository.loadQuestionTranslations(questions.map { it.id })
                                }
                                screen = AppScreen.QUESTIONS
                                notice = "Translation saved. Concept Bridge now tracks answers in this language separately."
                            }
                        }, enabled = !busy && translationPrompt.isNotBlank() && translationAcceptedAnswers.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text("Save translation")
                        }
                    }
                }

                AppScreen.ASSIGN_TASK -> ScreenFrame("Assign task & reward", onBack = { screen = AppScreen.PARENT_HOME }, notice = notice, busy = busy) {
                    Text("Choose the child")
                    children.forEach { child ->
                        OutlinedButton(onClick = { selectedChild = child }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (selectedChild?.id == child.id) "✓ " else "") + child.displayName)
                        }
                    }
                    Text("Choose a subject")
                    subjects.forEach { subject ->
                        OutlinedButton(onClick = { selectedSubject = subject }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (selectedSubject?.id == subject.id) "✓ " else "") + subject.title + " (" + subject.languageCode + ")")
                        }
                    }
                    Field(taskTitle, { taskTitle = it }, "Task title")
                    Field(taskQuestionCount, { taskQuestionCount = it.filter(Char::isDigit).take(3) }, "Questions in task (1–500)", keyboardType = KeyboardType.Number)
                    Field(taskPassPercent, { taskPassPercent = it.filter(Char::isDigit).take(3) }, "Passing score percent (0–100)", keyboardType = KeyboardType.Number)
                    Field(taskRewardMinutes, { taskRewardMinutes = it.filter(Char::isDigit).take(4) }, "Reward minutes after passing", keyboardType = KeyboardType.Number)
                    Field(taskDailyCapMinutes, { taskDailyCapMinutes = it.filter(Char::isDigit).take(4) }, "Task daily reward cap (0–1440)", keyboardType = KeyboardType.Number)
                    Text("The actual award is calculated by Supabase and capped by both the child and task daily limits.")
                    Button(onClick = {
                        execute {
                            val child = selectedChild ?: throw IllegalArgumentException("Select a child.")
                            val subject = selectedSubject ?: throw IllegalArgumentException("Select a subject.")
                            repository.addTask(
                                familyId, child, subject, taskTitle,
                                taskQuestionCount.toIntOrNull() ?: 0,
                                taskPassPercent.toIntOrNull() ?: -1,
                                taskRewardMinutes.toIntOrNull() ?: -1,
                                taskDailyCapMinutes.toIntOrNull() ?: -1
                            )
                            tasks = emptyList()
                            screen = AppScreen.PARENT_HOME
                            taskTitle = ""
                            notice = "Task assigned to " + child.displayName + ". Rewards are issued only after server-side grading."
                        }
                    }, enabled = !busy && children.isNotEmpty() && subjects.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Assign task") }
                }

                AppScreen.REPORTS -> ScreenFrame("Progress reports", onBack = { screen = AppScreen.PARENT_HOME }, notice = notice, busy = busy) {
                    val total = attempts.size
                    val average = if (total == 0) 0 else attempts.sumOf { it.scorePercent } / total
                    SummaryCard("Completed tasks", total.toString(), "Only submitted attempts appear here.")
                    SummaryCard("Average score", average.toString() + "%", "Calculated from the recorded task attempts.")
                    SummaryCard("Reward minutes", attempts.sumOf { it.awardedMinutes }.toString(), "Total awarded minutes for the listed attempts.")
                    Text("Concept Bridge — concept understanding by language", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("The same concept tag is grouped across translated question versions. Accuracy is shown separately for each language; a translated question remains the same underlying question.")
                    OutlinedButton(onClick = {
                        execute {
                            attempts = repository.loadAttempts()
                            conceptProgress = repository.loadConceptProgress()
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Refresh report") }
                    if (conceptProgress.isEmpty()) Text("No concept-level answers yet. Create a question with a concept tag, add a translation, and complete a task.")
                    conceptProgress.forEach { item ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(item.conceptKey, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Language: " + languageLabel(item.languageCode))
                                Text("Correct: " + item.correct + " / " + item.total + " • " + item.percent + "%")
                                LinearProgressIndicator(progress = { item.percent / 100f }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    Text("Task history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (attempts.isEmpty()) Text("No submitted task attempts yet.")
                    attempts.forEach { attempt ->
                        val child = children.firstOrNull { it.id == attempt.childId }
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(child?.displayName ?: "Child profile", fontWeight = FontWeight.SemiBold)
                                Text("Score: " + attempt.scorePercent + "% • " + if (attempt.passed) "Passed" else "Not passed")
                                Text("Earned: " + attempt.awardedMinutes + " minutes")
                                Text("Completed: " + attempt.completedAt.replace("T", " ").take(16))
                            }
                        }
                    }
                }

                AppScreen.ALLOWED_APPS -> ScreenFrame("Allowed apps", onBack = { screen = AppScreen.CHILDREN }, notice = notice, busy = busy) {
                    val child = selectedChild
                    Text("Child: " + (child?.displayName ?: "Not selected"))
                    Text("Choose from apps detected on the child's paired phone. Allow or disable an app and set its daily limit here; the child's EduLock checks the rules when it next syncs.")
                    OutlinedButton(
                        onClick = {
                            execute {
                                val selected = child ?: throw IllegalStateException("Select a child profile first.")
                                childInstalledApps = repository.loadInstalledApps(selected.id)
                                allowedApps = repository.loadAllowedApps(selected.id)
                            }
                        },
                        enabled = !busy && child != null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("↻ Refresh apps from child's phone") }
                    if (childInstalledApps.isEmpty()) {
                        Text("No app list has arrived yet. Open EduLock on the child's phone, sign in to the paired child profile, and keep it connected to the internet. EduLock will send the launchable app list.")
                    }
                    childInstalledApps.forEach { installed ->
                        val existingRule = allowedApps.firstOrNull { it.packageName == installed.packageName }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(installed.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                Text(installed.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (existingRule == null) {
                                    var limitText = installedAppLimitEdits[installed.packageName] ?: "60"
                                    Field(limitText, { value ->
                                        installedAppLimitEdits = installedAppLimitEdits + (installed.packageName to value.filter(Char::isDigit).take(4))
                                    }, "Daily limit in minutes (1–1440)", keyboardType = KeyboardType.Number)
                                    Button(
                                        onClick = {
                                            execute {
                                                val limit = (installedAppLimitEdits[installed.packageName] ?: "60").toIntOrNull()
                                                    ?: throw IllegalArgumentException("Enter a daily limit from 1 to 1440 minutes.")
                                                require(limit in 1..1440) { "Daily limit must be between 1 and 1440 minutes." }
                                                val selected = child ?: throw IllegalStateException("Select a child profile first.")
                                                repository.allowInstalledApp(selected.id, installed, limit)
                                                allowedApps = repository.loadAllowedApps(selected.id)
                                                notice = "App allowed with a $limit-minute daily limit. The child's phone applies the rule after syncing."
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Allow on child's phone") }
                                } else {
                                    Text(if (existingRule.enabled) "Allowed • ${existingRule.dailyLimitMinutes} minutes/day" else "Disabled")
                                    Field(
                                        installedAppLimitEdits[installed.packageName] ?: existingRule.dailyLimitMinutes.toString(),
                                        { value -> installedAppLimitEdits = installedAppLimitEdits + (installed.packageName to value.filter(Char::isDigit).take(4)) },
                                        "Daily limit in minutes (1–1440)",
                                        keyboardType = KeyboardType.Number
                                    )
                                    Button(onClick = {
                                        execute {
                                            val limit = (installedAppLimitEdits[installed.packageName] ?: existingRule.dailyLimitMinutes.toString()).toIntOrNull()
                                                ?: throw IllegalArgumentException("Enter a daily limit from 1 to 1440 minutes.")
                                            require(limit in 1..1440) { "Daily limit must be between 1 and 1440 minutes." }
                                            repository.updateAllowedAppLimit(existingRule.id, limit)
                                            allowedApps = child?.let { repository.loadAllowedApps(it.id) } ?: emptyList()
                                            installedAppLimitEdits = installedAppLimitEdits - installed.packageName
                                            notice = "Daily limit saved. The child's phone applies it after syncing."
                                        }
                                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save limit") }
                                    OutlinedButton(onClick = {
                                        execute {
                                            repository.updateAllowedAppEnabled(existingRule.id, !existingRule.enabled)
                                            allowedApps = child?.let { repository.loadAllowedApps(it.id) } ?: emptyList()
                                            notice = if (existingRule.enabled) "App disabled for child after the next sync." else "App enabled for child after the next sync."
                                        }
                                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (existingRule.enabled) "Disable on child's phone" else "Enable on child's phone")
                                    }
                                }
                            }
                        }
                    }
                    if (allowedApps.isEmpty()) Text("No apps are allowed yet. EduLock will keep restricted apps blocked once Accessibility is enabled on the child's phone.")
                    allowedApps.forEach { app ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(app.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Field(
                                    appLimitEdits[app.id] ?: app.dailyLimitMinutes.toString(),
                                    { newValue -> appLimitEdits = appLimitEdits + (app.id to newValue.filter(Char::isDigit).take(4)) },
                                    "Daily limit in minutes (1–1440)",
                                    keyboardType = KeyboardType.Number
                                )
                                Button(onClick = {
                                    execute {
                                        val limit = (appLimitEdits[app.id] ?: app.dailyLimitMinutes.toString()).toIntOrNull()
                                            ?: throw IllegalArgumentException("Enter a daily limit from 1 to 1440 minutes.")
                                        require(limit in 1..1440) { "Daily limit must be between 1 and 1440 minutes." }
                                        repository.updateAllowedAppLimit(app.id, limit)
                                        allowedApps = child?.let { repository.loadAllowedApps(it.id) } ?: emptyList()
                                        appLimitEdits = appLimitEdits - app.id
                                        notice = "Daily limit updated. Sync the child's device to apply the change."
                                    }
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save daily limit") }
                                OutlinedButton(
                                    onClick = {
                                        execute {
                                            repository.updateAllowedAppEnabled(app.id, !app.enabled)
                                            allowedApps = child?.let { repository.loadAllowedApps(it.id) } ?: emptyList()
                                            notice = if (app.enabled) "App disabled for this child. Sync the child's device to apply the change."
                                                else "App enabled for this child. Sync the child's device to apply the change."
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (app.enabled) "Disable this app on child's device" else "Allow this app on child's device") }
                                OutlinedButton(onClick = {
                                    execute {
                                        repository.deleteAllowedApp(app.id)
                                        allowedApps = child?.let { repository.loadAllowedApps(it.id) } ?: emptyList()
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text("Remove app") }
                            }
                        }
                    }
                    Text("To enforce these rules on the child's phone, the child must enable EduLock in Android Settings → Accessibility on that device. Android may allow a device owner/admin to enforce stronger controls; a normal app cannot guarantee that it cannot be uninstalled or have its data cleared.")
                }

                AppScreen.CHILD_HOME -> ScreenFrame("Your learning space", notice = notice, busy = busy) {
                    val child = childProfile
                    Text("Hello, " + (child?.displayName ?: "learner") + "!")
                    SummaryCard("Screen time earned today", earnedToday.toString() + " minutes", "Only successful tasks earn minutes. The server enforces daily caps.")
                    val prefs = context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE)
                    val used = prefs.getLong("used_millis_today", 0L) / 60_000L
                    val remaining = (earnedToday - used.toInt()).coerceAtLeast(0)
                    SummaryCard("Remaining allowed time", remaining.toString() + " minutes", "Local app-use accounting is reset daily in UTC and refreshed at sign-in.")
                    SummaryCard(
                        "App blocking",
                        if (isAccessibilityServiceEnabled(context)) "Enabled" else "Needs setup",
                        if (isAccessibilityServiceEnabled(context)) "EduLock can display its restriction screen over unapproved apps while its rules are active." else "Turn on EduLock in Android Accessibility settings on this child's device to show blocking screens over restricted apps."
                    )
                    val managedProtectionActive = isManagedDeviceProtectionActive(context)
                    SummaryCard(
                        "Uninstall & clear-data protection",
                        if (managedProtectionActive) "Managed policy active" else "Limited on this device",
                        if (managedProtectionActive) "Android device-owner policies are blocking app-management actions, including uninstall and Clear data through Settings." else "Full protection is not active. Device-admin mode alone cannot reliably block Clear data. Stronger protection requires setting up this phone as a managed device before normal use."
                    )
                    if (isAccessibilityServiceEnabled(context)) {
                        Text("Device restriction service: enabled on this device.")
                    } else {
                        Text("Automatic blocking needs your parent's permission and one-time activation in Android Accessibility settings on this device.")
                        Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Enable EduLock app restrictions")
                        }
                        OutlinedButton(
                            onClick = {
                                val appInfoIntent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + context.packageName)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(appInfoIntent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Fix Android “Restricted setting”") }
                        Text(
                            "If Android shows “Restricted setting”, open this app's App info, tap the ⋮ menu, choose “Allow restricted settings”, then return here and enable EduLock under Accessibility. This is an Android security step for apps installed outside an approved store; EduLock cannot bypass it."
                        )
                    }
                    if (isDeviceAdminActive(context)) {
                        Text("🔒 Device administrator is enabled. It may add a deactivation step before ordinary uninstall, but it does not reliably prevent Clear data or factory reset.")
                    } else {
                        Text("Optional: a parent can enable Android's visible device-administrator setting. This adds uninstall friction on supported devices; it cannot guarantee protection against Clear data or factory reset.")
                        OutlinedButton(onClick = {
                            val component = ComponentName(context, EduLockDeviceAdminReceiver::class.java)
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                                putExtra(
                                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "With a parent's permission, enable Android's visible device-administrator setting. It may add a deactivation step before ordinary uninstall, but does not reliably prevent Clear data or factory reset."
                                )
                            }
                            context.startActivity(intent)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("🔒 Enable device administrator")
                        }
                    }
                    OutlinedButton(onClick = {
                        refreshProtectionStatus()
                        screen = AppScreen.PROTECTION_STATUS
                    }, modifier = Modifier.fillMaxWidth()) { Text("Protection status & setup") }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FF))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📍 Family location sharing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (childLocationSharing) "Location sharing is enabled on this child's device. Android shows an ongoing notification while the service is active."
                                else "Location sharing is off. With permission, EduLock can report this device's location to the paired parent."
                            )
                            if (childLocationSharing) {
                                OutlinedButton(onClick = { disableChildLocationSharing() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Pause location sharing")
                                }
                            } else {
                                Button(onClick = { requestChildLocationSharing() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Enable location sharing")
                                }
                            }
                            if (Build.VERSION.SDK_INT >= 33 &&
                                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                OutlinedButton(
                                    onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Enable location status notification") }
                            }
                            Text("Only the latest location is retained on the server. If offline, the previous server location stays visible and new fixes queue locally until connection returns.")
                        }
                    }
                    Button(onClick = {
                        execute {
                            val linked = childProfile ?: throw IllegalStateException("Sign in to your child account again.")
                            tasks = repository.loadChildTasks(linked.id)
                            earnedToday = repository.refreshChildDeviceRestrictions(linked)
                            repository.syncAppUsage(linked.id)
                            screen = AppScreen.CHILD_HOME
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Sync tasks and screen-time settings") }
                    Text("Assigned tasks")
                    if (tasks.isEmpty()) Text("No active tasks yet. Ask your parent to assign a task.")
                    tasks.forEach { task ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(task.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                Text(task.questionCount.toString() + " questions • pass " + task.passPercent + "% • reward " + task.rewardMinutes + " min")
                                Button(onClick = {
                                    execute {
                                        val subjectId = task.subjectId ?: throw IllegalStateException("This task has no subject.")
                                        val loadedQuestions = repository.loadQuestions(subjectId, task.questionCount)
                                        if (loadedQuestions.size != task.questionCount) {
                                            throw IllegalStateException("This task needs " + task.questionCount + " questions, but only " + loadedQuestions.size + " are available. Ask your parent to repair the task.")
                                        }
                                        val loadedTranslations = repository.loadQuestionTranslations(loadedQuestions.map { it.id })
                                        questions = loadedQuestions
                                        questionTranslations = loadedTranslations
                                        answerLanguages = loadedQuestions.associate { item ->
                                            val preferred = childProfile?.preferredLanguage ?: item.languageCode
                                            val translated = loadedTranslations[item.id].orEmpty().any { it.languageCode.equals(preferred, ignoreCase = true) }
                                            item.id to if (translated) preferred.lowercase() else item.languageCode
                                        }
                                        activeTask = task
                                        questionIndex = 0
                                        answers = emptyMap()
                                        screen = AppScreen.PLAY_TASK
                                    }
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Start task") }
                            }
                        }
                    }
                    Text("This device is paired with your parent. Keep EduLock installed and its app data intact so this device can reconnect securely.")
                }

                AppScreen.PROTECTION_STATUS -> ScreenFrame(
                    "Protection & device setup",
                    onBack = { screen = if (childProfile != null) AppScreen.CHILD_HOME else AppScreen.PARENT_HOME },
                    notice = notice,
                    busy = busy
                ) {
                    Text(
                        "EduLock only reports protections that Android says are enabled on this phone. " +
                            "For restrictions to protect a child, complete setup on the child's device."
                    )
                    SummaryCard(
                        "App blocking overlay",
                        if (protectionAccessibilityEnabled) "Enabled" else "Needs setup",
                        if (protectionAccessibilityEnabled)
                            "The accessibility service is enabled. It can show EduLock's transparent restriction screen over unapproved apps while its rules are active."
                        else
                            "Enable EduLock in Android Accessibility settings on the child's device to allow app-level restriction screens."
                    )
                    if (!protectionAccessibilityEnabled) {
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Enable app blocking in Accessibility") }
                    }
                    SummaryCard(
                        "Device administrator",
                        if (protectionDeviceAdminEnabled) "Enabled" else "Not enabled",
                        if (protectionDeviceAdminEnabled)
                            "Android's visible device-admin permission is active. This may add a deactivation step before ordinary uninstall, but does not guarantee protection against clearing data."
                        else
                            "Optional and parent-approved. This permission alone cannot reliably prevent Clear data, factory reset or uninstall on every device."
                    )
                    if (!protectionDeviceAdminEnabled) {
                        OutlinedButton(
                            onClick = {
                                val component = ComponentName(context, EduLockDeviceAdminReceiver::class.java)
                                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                                    putExtra(
                                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                        "With a parent's permission, enable Android's visible device-administrator setting. It may add a deactivation step before ordinary uninstall, but does not guarantee protection against clearing data or factory reset."
                                    )
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Enable visible device administrator") }
                    }
                    SummaryCard(
                        "Managed-device enforcement",
                        if (protectionManagedEnabled) "Active" else "Not active",
                        if (protectionManagedEnabled)
                            "Android reports EduLock is enforcing supported uninstall/app-control restrictions as device owner."
                        else
                            "Strong uninstall/app-control restrictions require EduLock to be provisioned as the Android device owner. A normal app cannot grant itself this status."
                    )
                    if (!protectionManagedEnabled) {
                        Text(
                            "To use device-owner protection, a parent or device administrator must provision the phone as a fully managed device. This often has to be done during initial setup and may require a factory reset. Do not rely on Accessibility or ordinary device-admin mode to prevent clearing app data.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            applyManagedDeviceProtection(context)
                            refreshProtectionStatus()
                            notice = if (protectionManagedEnabled)
                                "Managed-device restrictions are active on this phone."
                            else
                                "Status refreshed. EduLock can apply stronger controls only if Android has already provisioned it as device owner."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Refresh status and apply supported device-owner rules") }
                    Text(
                        "Safety note: EduLock displays its blocker only over restricted apps. It does not cover Android Settings or secretly intercept system controls. If a child clears app data, an anonymous pairing session may be lost and the device may need to be paired again.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AppScreen.CHILD_LOCATIONS -> ScreenFrame(
                    "Family location",
                    onBack = { screen = AppScreen.PARENT_HOME },
                    notice = notice,
                    busy = busy
                ) {
                    Text(
                        "Latest reported location for each paired child. A missing check-in means the device is not reporting; it cannot prove the phone is switched off.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            execute {
                                val latestChildren = repository.loadChildren(familyId)
                                children = latestChildren
                                childLocationRows = latestChildren.map { repository.loadChildLocationSnapshot(it.id) }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("↻ Refresh locations") }
                    if (children.isEmpty()) Text("No child profiles are linked yet.")
                    children.forEach { child ->
                        val snapshot = childLocationRows.firstOrNull { it.childId == child.id }
                        val lastSeenMillis = snapshot?.lastSeenAt?.let { raw ->
                            runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
                        }
                        val minutesSinceSeen = lastSeenMillis?.let { ((System.currentTimeMillis() - it).coerceAtLeast(0L) / 60_000L) }
                        val statusText = when {
                            snapshot == null || snapshot.lastSeenAt == null -> "Waiting for the child's first device check-in"
                            !snapshot.sharingEnabled -> "Location sharing is paused on the child's device"
                            minutesSinceSeen != null && minutesSinceSeen >= 15 -> "Not reporting for about $minutesSinceSeen minutes. The phone may be offline, switched off, or EduLock may be stopped."
                            else -> "Device checking in • last contact ${snapshot.lastSeenAt.replace("T", " ").take(19)}"
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(child.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(statusText, color = if (minutesSinceSeen != null && minutesSinceSeen >= 15 && snapshot?.sharingEnabled == true) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (snapshot?.batteryPercent != null) Text("Child-device battery: ${snapshot.batteryPercent}%")
                                if (snapshot?.latitude != null && snapshot.longitude != null) {
                                    Text("Last saved location: ${snapshot.latitude}, ${snapshot.longitude}", fontWeight = FontWeight.SemiBold)
                                    ChildLocationMap(snapshot.latitude, snapshot.longitude)
                                    if (snapshot.accuracyMeters != null) Text("Reported accuracy: about ${snapshot.accuracyMeters.toInt()} m")
                                    Text(
                                        "Location fix time: " + (snapshot.recordedAt?.replace("T", " ")?.take(19) ?: "not available"),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(onClick = {
                                        val lat = snapshot.latitude
                                        val lon = snapshot.longitude
                                        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(child.displayName)})")
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                        } catch (_: Exception) {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=16/$lat/$lon")))
                                            } catch (_: Exception) {
                                                notice = "No map app or browser is available on this phone."
                                            }
                                        }
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Open last location in map") }
                                } else {
                                    Text("No location has reached the server yet. Keep location sharing enabled on the child's phone and make sure it has an internet connection.")
                                }
                                Text("The server retains the last successful point when the child device goes offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(
                        "Offline alerts need notification permission on this parent phone and an active internet connection. Android schedules checks periodically, so an alert may be delayed; it cannot tell whether a device is truly powered off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AppScreen.APP_USAGE_REPORT -> ScreenFrame("App usage report", onBack = { screen = AppScreen.CHILDREN }, notice = notice, busy = busy) {
                    val child = appUsageChild
                    Text("Daily app usage for " + (child?.displayName ?: "this child"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Shows package-level foreground time recorded by EduLock. Reports sync when the child opens EduLock or taps Sync; this is not a live remote screen view.")
                    OutlinedButton(onClick = {
                        execute {
                            val selected = child ?: throw IllegalStateException("Select a child profile first.")
                            appUsageReports = repository.loadAppUsageReports(selected.id)
                        }
                    }, enabled = !busy && child != null, modifier = Modifier.fillMaxWidth()) { Text("↻  Refresh report") }
                    val totalSeconds = appUsageReports.sumOf { it.usedSeconds }
                    SummaryCard("Recorded use (reported days)", (totalSeconds / 3600).toString() + " h " + ((totalSeconds % 3600) / 60).toString() + " min", "Totals reflect the last seven days of reported, approved-app use.")
                    if (appUsageReports.isEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FF))) {
                            Text("No usage reports yet. On the child device, open EduLock and tap “Sync tasks and screen-time settings”. Make sure the parent-approved Accessibility service is enabled.", modifier = Modifier.padding(18.dp), color = Color(0xFF244568))
                        }
                    } else {
                        appUsageReports.groupBy { it.usageDate }.forEach { (day, dailyRows) ->
                            Text(day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            dailyRows.forEach { row ->
                                Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Color(0xFFE1E6F4)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(48.dp).background(Color(0xFFFFE9B5), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Text("📱", fontSize = 23.sp) }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(row.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(row.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text((row.usedSeconds / 3600).toString() + " h " + ((row.usedSeconds % 3600) / 60).toString() + " min", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text("Privacy: EduLock records app package names and total foreground time for approved apps only. It does not collect screen contents, keystrokes, messages, or browser history.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                AppScreen.PLAY_TASK -> ScreenFrame(activeTask?.title ?: "Task", onBack = { screen = AppScreen.CHILD_HOME }, notice = notice, busy = busy) {
                    val task = activeTask
                    val question = questions.getOrNull(questionIndex)
                    if (task != null && question != null) {
                        val selectedLanguage = answerLanguages[question.id] ?: question.languageCode
                        val translated = questionTranslations[question.id].orEmpty().firstOrNull {
                            it.languageCode.equals(selectedLanguage, ignoreCase = true)
                        }
                        val promptForLanguage = translated?.prompt ?: question.prompt
                        val questionOptions = translated?.options?.takeIf { it.isNotEmpty() } ?: question.options
                        val languageOptions = (listOf(question.languageCode) + questionTranslations[question.id].orEmpty().map { it.languageCode })
                            .distinctBy { it.lowercase() }.sortedWith(compareBy<String> { if (it.equals(selectedLanguage, true)) 0 else 1 }.thenBy { it })
                        Text("Question " + (questionIndex + 1) + " of " + questions.size, style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(progress = { (questionIndex + 1).toFloat() / questions.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                        Text("Concept: " + question.conceptKey, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Try this same concept in another language:", style = MaterialTheme.typography.bodyMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            languageOptions.forEach { lang ->
                                if (lang.equals(selectedLanguage, ignoreCase = true)) {
                                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("✓ " + languageLabel(lang)) }
                                } else {
                                    OutlinedButton(onClick = {
                                        answerLanguages = answerLanguages + (question.id to lang)
                                        answers = answers - question.id
                                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(languageLabel(lang)) }
                                }
                            }
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides directionFor(selectedLanguage)) {
                            Text(promptForLanguage, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            if (question.questionType == "multiple_choice") {
                                questionOptions.forEach { option ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { answers = answers + (question.id to option) }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = answers[question.id] == option, onClick = { answers = answers + (question.id to option) })
                                        Text(option)
                                    }
                                }
                            } else if (question.questionType == "true_false") {
                                if (translated != null && translated.options.size >= 2) {
                                    translated.options.take(2).forEach { option ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable { answers = answers + (question.id to option) }.padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = answers[question.id] == option, onClick = { answers = answers + (question.id to option) })
                                            Text(option)
                                        }
                                    }
                                } else {
                                    listOf("true", "false").forEach { value ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable { answers = answers + (question.id to value) }.padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = answers[question.id] == value, onClick = { answers = answers + (question.id to value) })
                                            Text(if (value == "true") "True / صحيح" else "False / غلط")
                                        }
                                    }
                                }
                            } else {
                                Field(answers[question.id].orEmpty(), { answers = answers + (question.id to it) }, if (question.questionType == "numeric") "Your numeric answer" else "Your answer")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { questionIndex = (questionIndex - 1).coerceAtLeast(0) }, enabled = !busy && questionIndex > 0, modifier = Modifier.weight(1f)) {
                                Text("Previous")
                            }
                            if (questionIndex < questions.lastIndex) {
                                Button(onClick = { questionIndex = (questionIndex + 1).coerceAtMost(questions.lastIndex) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                    Text("Next")
                                }
                            } else {
                                Button(onClick = {
                                    execute {
                                        val unanswered = questions.firstOrNull { answers[it.id].isNullOrBlank() }
                                        if (unanswered != null) throw IllegalArgumentException("Please answer every question before submitting.")
                                        val submitted = repository.submitTask(task.id, answers, answerLanguages)
                                        taskResult = submitted
                                        val linked = childProfile
                                        if (linked != null) {
                                            try { earnedToday = repository.refreshChildDeviceRestrictions(linked) }
                                            catch (_: Exception) { earnedToday = submitted.dailyUsedMinutes }
                                        }
                                        screen = AppScreen.TASK_RESULT
                                    }
                                }, enabled = !busy && answers[question.id].isNullOrBlank().not(), modifier = Modifier.weight(1f)) { Text("Submit") }
                            }
                        }
                    } else {
                        Text("No question is available. Return to your task list and sync again.")
                    }
                }

                AppScreen.TASK_RESULT -> ScreenFrame("Task result", onBack = { screen = AppScreen.CHILD_HOME }, notice = notice, busy = busy) {
                    val result = taskResult
                    if (result != null) {
                        SummaryCard("Your score", result.scorePercent.toString() + "%", if (result.passed) "Great work — you passed!" else "Keep practising and try again after your parent assigns another attempt.")
                        SummaryCard("Screen time awarded", result.awardedMinutes.toString() + " minutes", "Rewards are calculated and recorded by Supabase.")
                        SummaryCard("Daily reward cap", result.dailyUsedMinutes.toString() + " / " + result.dailyCapMinutes + " minutes", "The child-wide daily cap is enforced server-side.")
                    }
                    Button(onClick = { screen = AppScreen.CHILD_HOME }, modifier = Modifier.fillMaxWidth()) { Text("Back to my tasks") }
                }
            }
        }
    }
}

@Composable
private fun ChildLocationMap(latitude: Double, longitude: Double) {
    val html = remember(latitude, longitude) {
        """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=3">
          <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src https://unpkg.com 'unsafe-inline'; style-src https://unpkg.com 'unsafe-inline'; img-src https://tile.openstreetmap.org https://unpkg.com data:; connect-src https://tile.openstreetmap.org;">
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
          <style>html,body,#map{height:100%;width:100%;margin:0;padding:0} .leaflet-container{font-family:sans-serif}</style>
        </head>
        <body>
          <div id="map"></div>
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <script>
            const point = [${latitude}, ${longitude}];
            const map = L.map('map').setView(point, 15);
            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
              maxZoom: 19,
              attribution: '&copy; OpenStreetMap contributors'
            }).addTo(map);
            L.marker(point).addTo(map).bindPopup('Latest reported location').openPopup();
          </script>
        </body>
        </html>
        """.trimIndent()
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(240.dp),
        factory = { viewContext ->
            WebView(viewContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = WebViewClient()
                tag = html
                loadDataWithBaseURL("https://edulock.local/", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL("https://edulock.local/", html, "text/html", "UTF-8", null)
            }
        }
    )
}

@Composable
private fun ScreenFrame(
    title: String,
    onBack: (() -> Unit)? = null,
    notice: String?,
    busy: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF4F5FF), Color(0xFFF4FFFC), Color(0xFFFFFAEF))))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) {
                Text("← Back to safety", fontWeight = FontWeight.SemiBold)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = Color(0xFF5147E8)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF5147E8), Color(0xFF078C87), Color(0xFF6C5CE7))))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(58.dp)
                        .background(Color(0xFFFFD978), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(screenEmoji(title), fontSize = 29.sp) }
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(screenTagline(title), fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFFE2E7FF))
                }
            }
        }
        if (busy) LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = Color(0xFFDCE2F6)
        )
        if (!notice.isNullOrBlank()) {
            val isError = listOf("error", "failed", "invalid", "could not", "cannot", "required", "expired", "not found")
                .any { notice.contains(it, ignoreCase = true) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = if (isError) Color(0xFFFFE8EC) else Color(0xFFE0F7EF))
            ) {
                Text(
                    notice, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) Color(0xFF9F263B) else Color(0xFF176A58)
                )
            }
        }
        content()
        Spacer(Modifier.height(14.dp))
    }
}

private fun screenEmoji(title: String): String = when {
    title.contains("verify", true) -> "🔐"
    title.contains("parent", true) -> "👨‍👩‍👧"
    title.contains("child", true) || title.contains("learner", true) -> "🧸"
    title.contains("learning", true) || title.contains("task", true) -> "🚀"
    title.contains("subject", true) -> "📚"
    title.contains("question", true) -> "🧩"
    title.contains("report", true) -> "🏆"
    title.contains("app", true) -> "🎮"
    title.contains("translation", true) || title.contains("concept", true) -> "🌈"
    title.equals("EduLock", true) -> "🦉"
    else -> "✨"
}

private fun screenTagline(title: String): String = when {
    title.contains("parent", true) -> "Your family's learning hub"
    title.contains("child", true) || title.contains("learning", true) -> "Learn • play • grow"
    title.contains("verify", true) -> "Your learning adventure is almost ready"
    title.contains("question", true) -> "Little puzzles, big discoveries"
    title.contains("report", true) -> "Celebrate every step forward"
    title.contains("app", true) -> "Safe screen time, one app at a time"
    title.equals("EduLock", true) -> "Safe screen time. Brighter learning."
    else -> "Small steps, big discoveries"
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = !label.contains("prompt", true) && !label.contains("options", true),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
private fun SummaryCard(title: String, value: String, explanation: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Color(0xFFE1E6F4)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(Color(0xFFDCDFFF), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) { Text(summaryEmoji(title), fontSize = 23.sp) }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun summaryEmoji(title: String): String = when {
    title.contains("children", true) -> "🧒"
    title.contains("learning", true) || title.contains("subject", true) -> "📚"
    title.contains("score", true) -> "⭐"
    title.contains("screen time", true) || title.contains("minutes", true) -> "⏱️"
    title.contains("average", true) -> "📈"
    title.contains("task", true) -> "🎯"
    else -> "✨"
}

private fun languageLabel(code: String): String = when (code.lowercase()) {
    "en" -> "English (en)"
    "ur" -> "اردو (ur)"
    "sd" -> "سنڌي (sd)"
    "ar" -> "العربية (ar)"
    "fa" -> "فارسی (fa)"
    "ps" -> "پښتو (ps)"
    "fr" -> "Français (fr)"
    "es" -> "Español (es)"
    else -> code
}

private fun readableQuestionType(type: String): String = when (type) {
    "multiple_choice" -> "Multiple choice"
    "true_false" -> "True / false"
    "numeric" -> "Numeric"
    else -> "Short answer"
}

private fun directionFor(language: String): LayoutDirection =
    if (language.lowercase() in setOf("ur", "sd", "ar", "fa", "ps", "he")) LayoutDirection.Rtl else LayoutDirection.Ltr

/**
 * Enforces the stronger Android controls only when EduLock is already provisioned
 * as the device owner during managed-device setup. A normal app cannot grant itself
 * device-owner status at runtime.
 */
private fun applyManagedDeviceProtection(context: Context): Boolean {
    val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    if (!manager.isDeviceOwnerApp(context.packageName)) return false
    val component = ComponentName(context, EduLockDeviceAdminReceiver::class.java)
    return try {
        manager.setUninstallBlocked(component, context.packageName, true)
        manager.addUserRestriction(component, UserManager.DISALLOW_APPS_CONTROL)
        manager.addUserRestriction(component, UserManager.DISALLOW_UNINSTALL_APPS)
        true
    } catch (_: Exception) {
        false
    }
}

private fun isManagedDeviceProtectionActive(context: Context): Boolean {
    val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    if (!manager.isDeviceOwnerApp(context.packageName)) return false
    val component = ComponentName(context, EduLockDeviceAdminReceiver::class.java)
    val userManager = context.getSystemService(Context.USER_SERVICE) as android.os.UserManager
    return try {
        manager.isUninstallBlocked(component, context.packageName) &&
            userManager.hasUserRestriction(UserManager.DISALLOW_APPS_CONTROL) &&
            userManager.hasUserRestriction(UserManager.DISALLOW_UNINSTALL_APPS)
    } catch (_: SecurityException) {
        false
    }
}

private fun isDeviceAdminActive(context: Context): Boolean {
    val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val component = ComponentName(context, EduLockDeviceAdminReceiver::class.java)
    return manager.isAdminActive(component)
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabled = Secure.getString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val serviceName = context.packageName + "/" + EduLockAccessibilityService::class.java.name
    return enabled.split(':').any { it.equals(serviceName, ignoreCase = true) }
}

class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        applyManagedDeviceProtection(applicationContext)
        val repository = EduLockRepository(applicationContext)
        setContent {
            EduLockApp(repository)
        }
    }
}
