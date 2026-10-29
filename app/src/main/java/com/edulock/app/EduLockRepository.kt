package com.edulock.app

import android.content.Context
import android.content.SharedPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.Locale
import java.security.SecureRandom
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ParentLearningSettings(
    val familyId: String,
    val displayName: String,
    val countryCode: String = "PK",
    val learningLanguageCode: String = "ur",
    val studyTrack: String = "none",
    val displayLanguageCode: String = "en"
)

data class PracticalTaskRow(
    val id: String,
    val childId: String,
    val title: String,
    val instructions: String,
    val materialsJson: String = "[]",
    val safetyNotes: String = "",
    val ageMin: Int = 3,
    val ageMax: Int = 19,
    val activityKind: String = "practical",
    val expectedMinutes: Int = 10
)

data class PracticalTaskSubmission(
    val id: String,
    val status: String,
    val submittedAt: String
)

data class WrittenAnswerFeedback(
    val id: String,
    val attemptId: String,
    val questionId: String,
    val childId: String,
    val conceptKey: String,
    val suppliedAnswer: String,
    val expectedAnswer: String,
    val accuracyScore: Int,
    val spellingScore: Int,
    val finalScore: Int,
    val detectedErrorsJson: String,
    val feedback: String,
    val createdAt: String
)

data class ChildProfile(
    val id: String,
    val familyId: String,
    val displayName: String,
    val preferredLanguage: String,
    val dailyCapMinutes: Int,
    val countryCode: String = "PK",
    val schoolGrade: Int = 1,
    val ageYears: Int = 8,
    val avatarIcon: String = "🦊",
    val dailyGameLimitMinutes: Int = 20,
    val studyTrack: String = "none",
    val kidsModeEnabled: Boolean = false,
    val deviceLinked: Boolean = false
)

data class SubjectRow(
    val id: String,
    val familyId: String,
    val title: String,
    val languageCode: String,
    val countryCode: String = "PK",
    val gradeLevel: Int = 1,
    val curriculumName: String = "Parent-created",
    val curriculumSubjectCode: String = ""
)

data class CurriculumQuestion(
    val id: String,
    val countryCode: String,
    val curriculumName: String,
    val gradeLevel: Int,
    val ageMin: Int,
    val ageMax: Int,
    val subjectCode: String,
    val subjectName: String,
    val languageCode: String,
    val topic: String,
    val prompt: String,
    val questionType: String,
    val options: List<String>,
    val conceptKey: String,
    val sourceNote: String
)

data class CurriculumSearchResult(
    val questions: List<CurriculumQuestion>,
    val fallbackUsed: Boolean,
    val message: String
)

data class CurriculumImportResult(
    val subjectId: String,
    val subjectTitle: String,
    val languageCode: String,
    val importedCount: Int,
    val selectedCount: Int,
    val message: String
)

data class QuestionRow(
    val id: String,
    val subjectId: String,
    val prompt: String,
    val questionType: String,
    val options: List<String>,
    val languageCode: String,
    val conceptKey: String,
    val picturePath: String? = null,
    val gradeLevel: Int = 1,
    val ageMin: Int = 3,
    val ageMax: Int = 19,
    val rewardMinutes: Int? = null
)

data class QuestionTranslation(
    val id: String,
    val questionId: String,
    val languageCode: String,
    val prompt: String,
    val options: List<String>
)

data class PhysicalChallenge(
    val id: String, val familyId: String, val childId: String?, val title: String,
    val description: String, val rewardMinutes: Int, val evidenceType: String,
    val active: Boolean
)
data class ChallengeSubmission(
    val id: String, val challengeId: String, val childId: String, val title: String,
    val description: String, val evidenceType: String, val mimeType: String,
    val mediaBase64: String, val note: String, val status: String,
    val rewardMinutes: Int, val reviewNote: String, val submittedAt: String
)
data class QuestionRewardGroup(val id: String, val subjectId: String, val conceptKey: String, val rewardMinutes: Int)


data class ConceptProgress(
    val conceptKey: String,
    val languageCode: String,
    val correct: Int,
    val total: Int
) {
    val percent: Int get() = if (total == 0) 0 else (correct * 100) / total
}

data class DailyTaskSettings(
    val childId: String,
    val enabled: Boolean = false,
    val dailyTaskCount: Int = 1,
    val questionsPerTask: Int = 4,
    val passPercent: Int = 70,
    val rewardMinutes: Int = 1,
    val dailyCapMinutes: Int = 30
)

data class TaskRow(
    val id: String,
    val childId: String,
    val subjectId: String?,
    val title: String,
    val questionCount: Int,
    val passPercent: Int,
    val rewardMinutes: Int,
    val dailyCapMinutes: Int,
    val active: Boolean,
    val rewardPackageName: String? = null,
    val rewardGroupId: String? = null,
    val timeLimitSeconds: Int? = null,
    val rewardToBank: Boolean = true,
    val questionTimeLimitSeconds: Int? = null,
    val targetGradeLevel: Int = 1,
    val targetAgeYears: Int = 8
)

data class AllowedApp(
    val id: String,
    val childId: String,
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val dailyLimitMinutes: Int = 0,
    val weeklyScheduleJson: String = "{}"
)

data class AppGroup(
    val id: String,
    val childId: String,
    val name: String,
    val packageNames: Set<String> = emptySet()
)

data class AppDayWindow(
    val enabled: Boolean = true,
    val start: String = "00:00",
    val end: String = "23:59"
)

data class AppWeeklySchedule(val days: Map<String, AppDayWindow>) {
    fun toJson(): JSONObject = JSONObject().apply {
        DAY_KEYS.forEach { key ->
            val window = days[key] ?: AppDayWindow(enabled = false)
            put(key, JSONObject().put("enabled", window.enabled).put("start", window.start).put("end", window.end))
        }
    }

    companion object {
        val DAY_KEYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        fun defaultSchedule() = AppWeeklySchedule(DAY_KEYS.associateWith { AppDayWindow() })
        fun parse(raw: String?): AppWeeklySchedule {
            if (raw.isNullOrBlank()) return defaultSchedule()
            return try {
                val root = JSONObject(raw)
                if (root.length() == 0) return defaultSchedule()
                AppWeeklySchedule(DAY_KEYS.associateWith { key ->
                    val item = root.optJSONObject(key)
                    if (item == null) AppDayWindow(enabled = false)
                    else AppDayWindow(item.optBoolean("enabled", false), item.optString("start", "00:00"), item.optString("end", "23:59"))
                })
            } catch (_: Exception) {
                defaultSchedule()
            }
        }
    }
}

data class ChildInstalledApp(
    val childId: String,
    val packageName: String,
    val displayName: String,
    val lastSeenAt: String? = null
)

data class AppUsageReport(
    val childId: String,
    val usageDate: String,
    val packageName: String,
    val displayName: String,
    val usedSeconds: Long
)

data class ChildLocationSnapshot(
    val childId: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Double?,
    val recordedAt: String?,
    val lastSeenAt: String?,
    val sharingEnabled: Boolean,
    val batteryPercent: Int?,
    val deviceLocked: Boolean? = null,
    val guardMode: String? = null,
    val currentAppPackage: String? = null,
    val screenOn: Boolean? = null,
    val blockedAppEvents: Int = 0
)
data class AttemptRow(
    val id: String,
    val childId: String,
    val taskId: String,
    val scorePercent: Int,
    val passed: Boolean,
    val awardedMinutes: Int,
    val completedAt: String
)
data class TaskResult(
    val scorePercent: Int,
    val passed: Boolean,
    val awardedMinutes: Int,
    val dailyUsedMinutes: Int,
    val dailyCapMinutes: Int,
    val rewardToBank: Boolean = false,
    val pendingSync: Boolean = false
)

data class RewardBankRedemption(
    val redeemedMinutes: Int,
    val balanceMinutes: Int,
    val targetPackageName: String,
    val targetAppName: String
)
data class ChildEmergencyAccessConfig(
    val childId: String,
    val familyId: String,
    val enabled: Boolean = false,
    val blockEmergencyAfterDailyLimit: Boolean = false,
    val emergencyCodeSalt: String = "",
    val emergencyCodeHash: String = "",
    val logoutCodeSalt: String = "",
    val logoutCodeHash: String = "",
    val allowedPackages: Set<String> = emptySet(),
    val accessMinutes: Int = 10,
    val expiresAt: String? = null,
    val logoutCodeExpiresAt: String? = null,
    val emergencyLastUsedDate: String? = null,
    val emergencyFailedAttempts: Int = 0,
    val emergencyLockedUntil: String? = null,
    val logoutFailedAttempts: Int = 0,
    val logoutLockedUntil: String? = null
)
data class ChildEmergencyOptions(
    val enabled: Boolean = false,
    val blockEmergencyAfterDailyLimit: Boolean = false,
    val allowedPackages: Set<String> = emptySet(),
    val accessMinutes: Int = 10,
    val expiresAt: String? = null,
    val lastUsedDate: String? = null
)
data class EmergencyGrantResult(
    val packageName: String,
    val expiresInSeconds: Int,
    val accessMinutes: Int,
    val usedDate: String
)
data class AuthResult(val session: AuthSession?, val message: String)

class EduLockRepository(context: Context) {
    private val appContext = context.applicationContext
    private val sessionStore = SecureSessionStore(appContext)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
    val isConfigured: Boolean
        get() = baseUrl.startsWith("https://") && anonKey.isNotBlank()

    fun currentSession(): AuthSession? = sessionStore.read()

    fun signOut() {
        sessionStore.clear()
        appContext.getSharedPreferences(LOCK_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("child_active", false).apply()
    }

    suspend fun signUp(email: String, password: String, displayName: String, accountRole: String): AuthResult {
        ensureConfigured()
        require(accountRole == "parent") { "Only parent accounts can be created. Child devices are paired with a one-time code." }
        require(email.trim().contains("@") && email.trim().length <= 254) { "Enter a valid email address" }
        require(password.length >= 8) { "Use a password with at least 8 characters" }
        require(password.length <= 128) { "Password is too long" }
        if (displayName.trim().isEmpty()) throw IllegalArgumentException("Enter your name")

        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("data", JSONObject()
                .put("account_role", accountRole)
                .put("display_name", displayName.trim().take(120)))
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        val json = authRequest("auth/v1/signup", body.put("email", normalizedEmail))
        val user = json.optJSONObject("user") ?: json.optJSONObject("data")?.optJSONObject("user")
        val token = sessionFrom(json, accountRole)
        val identities = user?.optJSONArray("identities")
        if (token == null && identities != null && identities.length() == 0) {
            throw IOException("This email is already registered. Switch to Sign in and enter your existing password.")
        }
        if (token != null) {
            sessionStore.save(token)
            return AuthResult(token, "Account created successfully.")
        }
        val message = if (user != null) {
            "Parent account created. Check your email to confirm it, then sign in."
        } else {
            "Account request submitted. Check your email if confirmation is enabled."
        }
        return AuthResult(null, message)
    }

    suspend fun verifySignupOtp(email: String, token: String, accountRole: String): AuthSession {
        ensureConfigured()
        require(email.trim().contains("@")) { "Enter a valid email address" }
        val code = token.filter(Char::isDigit)
        require(code.length in 6..8) { "Enter the 6–8 digit code from your email. Use every digit shown in the latest message." }
        require(accountRole == "parent") { "Email verification is for parent accounts. Pair child devices with a one-time code." }
        val json = authRequest(
            "auth/v1/verify",
            JSONObject().put("type", "signup").put("email", email.trim()).put("token", code)
        )
        val verified = sessionFrom(json, accountRole)
            ?: throw IOException("Email verified, but no session was returned. Sign in with your email and password.")
        sessionStore.save(verified)
        return verified
    }

    suspend fun resendSignupOtp(email: String) {
        ensureConfigured()
        require(email.trim().contains("@")) { "Enter a valid email address" }
        try {
            authRequest("auth/v1/resend", JSONObject().put("type", "signup").put("email", email.trim().lowercase(Locale.ROOT)))
        } catch (e: Exception) {
            val raw = e.message.orEmpty()
            val friendly = when {
                raw.contains("rate", true) || raw.contains("security purposes", true) || raw.contains("too many", true) ->
                    "Email provider is rate-limiting new codes. Wait a little longer, then request once more. Check Inbox, Spam and Promotions before retrying."
                raw.contains("already confirmed", true) || raw.contains("already verified", true) ->
                    "This email is already verified. Return to Sign in and use your password."
                raw.contains("not found", true) || raw.contains("user not found", true) ->
                    "No pending signup was found for this email. Return to Create account and try again, or sign in if you already registered."
                else -> raw
            }
            throw IOException(friendly.ifBlank { "Could not request a new verification code. Check your connection and try again." }, e)
        }
    }

    /**
     * Creates/reuses the child device's anonymous Supabase session so a child can pair
     * with a parent's one-time code without creating an email/password account.
     */
    suspend fun signInAnonymouslyAsChild(): AuthSession {
        ensureConfigured()
        val existing = restoreSession()
        if (existing != null) {
            if (existing.accountRole == "child") return existing
            throw IOException("Sign out of the parent account before pairing this child device.")
        }

        val body = JSONObject().put(
            "data",
            JSONObject()
                .put("account_role", "child")
                .put("display_name", "EduLock child device")
        )
        val json = authRequest("auth/v1/signup", body)
        val childSession = sessionFrom(json, "child")
            ?: throw IOException("Supabase did not return a child device session. In the EduLock Supabase project, enable Authentication → Sign In / Providers → Anonymous, then retry.")
        sessionStore.save(childSession)
        return childSession
    }

    suspend fun signIn(email: String, password: String, accountRole: String): AuthSession {
        ensureConfigured()
        require(email.trim().contains("@")) { "Enter a valid email address" }
        require(password.isNotEmpty()) { "Enter your password" }
        require(accountRole == "parent") { "Email/password sign-in is for parents. Pair child devices with a one-time code." }
        val json = authRequest(
            "auth/v1/token?grant_type=password",
            JSONObject().put("email", email.trim()).put("password", password)
        )
        val session = sessionFrom(json, accountRole)
            ?: throw IOException("Sign-in succeeded without a session. Confirm your email address and try again.")
        sessionStore.save(session)
        return try {
            val profileRows = restArray("user_profiles?select=account_role&user_id=eq." + session.userId + "&limit=1")
            val storedRole = profileRows.optJSONObject(0)?.optString("account_role")
                ?: throw IOException("Your account profile is not ready. Apply the EduLock Supabase migrations and try again.")
            if (storedRole != "parent") {
                throw IOException("Child access is pairing-code-only. Use 'Set up a child's device with pairing code' instead of child email/password sign-in.")
            }
            session.copy(accountRole = "parent").also { sessionStore.save(it) }
        } catch (e: Exception) {
            signOut()
            throw e
        }
    }

    suspend fun restoreSession(): AuthSession? {
        if (!isConfigured) return null
        val existing = sessionStore.read() ?: return null
        return try {
            val candidate = if (existing.expiresAtMillis > System.currentTimeMillis() + 60_000) {
                existing
            } else {
                if (existing.refreshToken.isBlank()) throw IOException("Session expired. Please sign in again.")
                val json = send(
                    "auth/v1/token?grant_type=refresh_token",
                    "POST",
                    JSONObject().put("refresh_token", existing.refreshToken),
                    authenticated = false,
                    profile = false
                ) as? JSONObject ?: throw IOException("Unexpected session refresh response")
                sessionFrom(json, existing.accountRole, existing.userId)
                    ?: throw IOException("Could not refresh session")
            }

            // Verify the role on the server; never trust a cached role to choose a dashboard.
            sessionStore.save(candidate)
            val profileRows = restArray(
                "user_profiles?select=account_role&user_id=eq." + candidate.userId + "&limit=1"
            )
            val serverRole = profileRows.optJSONObject(0)?.optString("account_role")
                ?.takeIf { it == "parent" || it == "child" }
                ?: throw IOException("This session has no valid EduLock profile. Please sign in again.")
            if (serverRole != candidate.accountRole) {
                throw IOException("Account role mismatch. Please sign in again and pair this device using the parent code.")
            }
            candidate
        } catch (e: Exception) {
            // A transient network/server failure is not proof that the saved child session is invalid.
            // Keep a still-live encrypted session so children are not randomly logged out offline.
            if (existing.expiresAtMillis > System.currentTimeMillis() + 60_000 &&
                (e is IOException || (e.cause is IOException))) {
                existing
            } else {
                // Invalid/stale sessions must not open the wrong account dashboard.
                signOut()
                null
            }
        }
    }
    suspend fun ensureParentFamily(
        displayName: String,
        countryCode: String = "PK",
        learningLanguageCode: String = "ur",
        studyTrack: String = "none",
        displayLanguageCode: String = "en"
    ): String {
        val session = requireSession("parent")
        val profiles = restArray("family_profiles?select=id,display_name,owner_user_id&owner_user_id=eq.${session.userId}&limit=1")
        if (profiles.length() > 0) return profiles.getJSONObject(0).getString("id")
        val country = countryCode.trim().uppercase(Locale.ROOT).takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "PK"
        val learning = learningLanguageCode.trim().lowercase(Locale.ROOT).takeIf { it.matches(Regex("[a-z][a-z0-9-]{1,15}")) } ?: "en"
        val display = displayLanguageCode.trim().lowercase(Locale.ROOT).takeIf { it.matches(Regex("[a-z][a-z0-9-]{1,15}")) } ?: "en"
        val track = studyTrack.trim().lowercase(Locale.ROOT).takeIf { it in setOf("none", "islamic", "hindu", "christian", "sikh", "buddhist", "jewish", "jain", "other") } ?: "none"
        val profileName = displayName.trim().ifBlank { "My family" }.take(120)
        val rows = restArray("family_profiles?select=id", "POST",
            JSONObject().put("owner_user_id", session.userId).put("display_name", profileName)
                .put("country_code", country).put("learning_language_code", learning)
                .put("study_track", track).put("display_language_code", display))
        return rows.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
            ?: throw IOException("Could not create the parent profile")
    }

    suspend fun loadParentLearningSettings(familyId: String): ParentLearningSettings {
        requireSession("parent")
        val rows = restArray("family_profiles?select=id,display_name,country_code,learning_language_code,study_track,display_language_code&id=eq.$familyId&limit=1")
        val row = rows.optJSONObject(0) ?: throw IOException("Parent settings could not be loaded.")
        return ParentLearningSettings(
            familyId = row.optString("id"),
            displayName = row.optString("display_name", "My family"),
            countryCode = row.optString("country_code", "PK").uppercase().takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "PK",
            learningLanguageCode = row.optString("learning_language_code", "en"),
            studyTrack = row.optString("study_track", "none"),
            displayLanguageCode = row.optString("display_language_code", "en")
        )
    }

    suspend fun updateParentLearningSettings(
        familyId: String, countryCode: String, learningLanguageCode: String,
        studyTrack: String, displayLanguageCode: String
    ) {
        requireSession("parent")
        val country = countryCode.trim().uppercase(Locale.ROOT)
        require(country.matches(Regex("[A-Z]{2}"))) { "Choose a valid country." }
        fun language(value: String): String {
            val code = value.trim().lowercase(Locale.ROOT)
            require(code.matches(Regex("[a-z][a-z0-9-]{1,15}"))) { "Choose a valid language." }
            return code
        }
        val track = studyTrack.trim().lowercase(Locale.ROOT)
        require(track in setOf("none", "islamic", "hindu", "christian", "sikh", "buddhist", "jewish", "jain", "other")) { "Choose a study track." }
        restArray("family_profiles?id=eq.$familyId", "PATCH",
            JSONObject().put("country_code", country).put("learning_language_code", language(learningLanguageCode))
                .put("study_track", track).put("display_language_code", language(displayLanguageCode)))
    }

    suspend fun loadChildren(familyId: String): List<ChildProfile> {
        requireSession("parent")
        val rows = restArray("children?select=id,family_id,display_name,preferred_language,daily_cap_minutes,country_code,school_grade,age_years,avatar_icon,daily_game_limit_minutes,study_track,kids_mode_enabled,child_user_id&family_id=eq.$familyId&order=created_at.desc")
        return (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.toChildProfile() }
    }

    suspend fun addChild(
        familyId: String, name: String, languageCode: String, dailyCap: Int,
        countryCode: String = "PK", schoolGrade: Int = 1, ageYears: Int = 8,
        avatarIcon: String = "🦊", dailyGameLimitMinutes: Int = 20, studyTrack: String = "none"
    ): ChildProfile {
        requireSession("parent")
        require(name.trim().isNotEmpty()) { "Enter the child’s name" }
        require(dailyCap in 0..1440) { "Daily screen-time limit must be from 0 to 1440 minutes" }
        require(countryCode.trim().uppercase().matches(Regex("[A-Z]{2}"))) { "Choose a valid country." }
        val language = languageCode.trim().ifBlank { "en" }.lowercase(Locale.ROOT)
        require(language.matches(Regex("[a-z][a-z0-9-]{1,15}"))) { "Use a valid language code such as en, ur, sd or hi." }
        require(schoolGrade in 0..13) { "Choose a grade from kindergarten to grade 13." }
        require(ageYears in 3..19) { "Child age must be from 3 to 19 years." }
        require(dailyGameLimitMinutes in 0..1440) { "Daily educational game time must be from 0 to 1440 minutes." }
        val safeAvatar = avatarIcon.takeIf { it in CHILD_AVATAR_ICONS } ?: "🦊"
        val normalizedStudyTrack = studyTrack.trim().lowercase(Locale.ROOT)
        require(normalizedStudyTrack in setOf("none", "islamic", "hindu", "christian", "sikh", "buddhist", "jewish", "jain", "other")) {
            "Choose a valid child religious-education option."
        }
        val rows = restArray("children?select=id,family_id,display_name,preferred_language,daily_cap_minutes,country_code,school_grade,age_years,avatar_icon,daily_game_limit_minutes,study_track,kids_mode_enabled", "POST",
            JSONObject()
                .put("family_id", familyId)
                .put("display_name", name.trim().take(120))
                .put("preferred_language", language.take(16))
                .put("daily_cap_minutes", dailyCap)
                .put("country_code", countryCode.trim().uppercase())
                .put("school_grade", schoolGrade)
                .put("age_years", ageYears)
                .put("avatar_icon", safeAvatar)
                .put("daily_game_limit_minutes", dailyGameLimitMinutes)
                .put("study_track", normalizedStudyTrack)
                .put("kids_mode_enabled", true))
        return rows.optJSONObject(0)?.toChildProfile() ?: throw IOException("Could not create this child profile")
    }

    suspend fun updateChildStudyTrack(childId: String, studyTrack: String) {
        requireSession("parent")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        val normalized = studyTrack.trim().lowercase(Locale.ROOT)
        require(normalized in setOf("none", "islamic", "hindu", "christian", "sikh", "buddhist", "jewish", "jain", "other")) {
            "Choose a valid religious-education option."
        }
        restArray("children?id=eq.$childId", "PATCH", JSONObject().put("study_track", normalized))
    }

    suspend fun deactivateChildLink(childId: String) {
        requireSession("parent")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        restArray(
            "children?id=eq.$childId",
            "PATCH",
            JSONObject().put("child_user_id", JSONObject.NULL),
            prefer = "return=representation"
        )
        val verified = restArray("children?select=id,child_user_id&id=eq.$childId&limit=1").optJSONObject(0)
            ?: throw IOException("The child profile could not be found after delinking the device.")
        val remainingLink = verified.opt("child_user_id")
        if (remainingLink != null && remainingLink != JSONObject.NULL &&
            remainingLink.toString().isNotBlank() && remainingLink.toString() != "null") {
            throw IOException("The server did not confirm that the child device was delinked.")
        }
    }

    suspend fun deleteChildProfile(childId: String) {
        requireSession("parent")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        val result = send(
            "rest/v1/rpc/delete_child_profile",
            "POST",
            JSONObject().put("p_child_id", childId),
            authenticated = true,
            profile = true
        ) as? JSONObject ?: throw IOException("The server did not confirm child-profile deletion.")
        if (!result.optBoolean("deleted", false)) {
            throw IOException("The server did not delete this child profile. Refresh and try again.")
        }
    }

    suspend fun updateChildRewardCap(childId: String, dailyRewardCapMinutes: Int) {
        requireSession("parent")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        require(dailyRewardCapMinutes in 0..1440) { "Daily reward cap must be from 0 to 1440 minutes." }
        restArray("children?id=eq.$childId", "PATCH", JSONObject().put("daily_cap_minutes", dailyRewardCapMinutes))
        val verified = restArray("children?select=id,daily_cap_minutes&id=eq.$childId&limit=1").optJSONObject(0)
            ?: throw IOException("Supabase did not confirm the daily reward cap was saved.")
        if (verified.optInt("daily_cap_minutes", -1) != dailyRewardCapMinutes) {
            throw IOException("Supabase did not confirm the requested daily reward cap. No success was reported.")
        }
    }

    suspend fun loadChildRewardBankMinutes(childId: String): Int {
        requireSession("child")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        return try {
            val rows = restArray(
                "child_reward_bank?select=minutes_remaining&child_id=eq.$childId&minutes_remaining=gt.0"
            )
            var balance = 0
            for (index in 0 until rows.length()) {
                balance = (balance + rows.optJSONObject(index)?.optInt("minutes_remaining", 0)?.coerceAtLeast(0).orZero())
                    .coerceAtMost(Int.MAX_VALUE)
            }
            offlineReadCache.edit().putInt(offlineRewardBankKey(childId), balance).commit()
            balance + offlineAttemptQueue.getInt(offlinePendingBankCreditKey(childId), 0).coerceAtLeast(0)
        } catch (error: IOException) {
            val cached = offlineReadCache.getInt(offlineRewardBankKey(childId), -1)
            if (cached < 0) throw error
            (cached + offlineAttemptQueue.getInt(offlinePendingBankCreditKey(childId), 0).coerceAtLeast(0))
                .coerceAtMost(Int.MAX_VALUE)
        }
    }

    suspend fun redeemChildRewardBank(packageName: String, minutes: Int): RewardBankRedemption {
        requireSession("child")
        val normalized = packageName.trim()
        require(normalized.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
            "Choose a valid parent-approved app."
        }
        require(minutes in 1..1440) { "Choose a reward amount from 1 to 1440 minutes." }
        val response = requestFunction(
            "redeem-child-reward",
            JSONObject().put("package_name", normalized).put("minutes", minutes)
        )
        return RewardBankRedemption(
            redeemedMinutes = response.optInt("redeemed_minutes", 0).coerceIn(0, 1440),
            balanceMinutes = response.optInt("bank_balance_minutes", 0).coerceAtLeast(0),
            targetPackageName = response.optString("target_package_name", normalized),
            targetAppName = response.optString("target_app_name", normalized)
        )
    }

    private fun Int?.orZero(): Int = this ?: 0

    suspend fun updateChildGameLimit(childId: String, dailyGameLimitMinutes: Int) {
        requireSession("parent")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        require(dailyGameLimitMinutes in 0..1440) { "Daily educational game time must be from 0 to 1440 minutes." }
        restArray("children?id=eq.$childId", "PATCH", JSONObject().put("daily_game_limit_minutes", dailyGameLimitMinutes))
        val verified = restArray("children?select=id,daily_game_limit_minutes&id=eq.$childId&limit=1").optJSONObject(0)
            ?: throw IOException("Supabase did not confirm the child's game-time limit was saved.")
        if (verified.optInt("daily_game_limit_minutes", -1) != dailyGameLimitMinutes) {
            throw IOException("Supabase did not confirm the requested game-time limit. No success was reported.")
        }
    }

    suspend fun updateChildKidsMode(childId: String, enabled: Boolean): Boolean {
        requireSession("parent")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        restArray("children?id=eq.$childId", "PATCH", JSONObject().put("kids_mode_enabled", enabled))
        val verified = restArray("children?select=id,kids_mode_enabled&id=eq.$childId&limit=1").optJSONObject(0)
            ?: throw IOException("Supabase did not confirm the child's Kids Mode setting was saved.")
        val confirmed = verified.optBoolean("kids_mode_enabled", false)
        if (confirmed != enabled) {
            throw IOException("Supabase returned a different Kids Mode setting. The change was not confirmed.")
        }
        return confirmed
    }

    suspend fun updateChildCurriculum(
        childId: String, countryCode: String, schoolGrade: Int, ageYears: Int, languageCode: String
    ) {
        requireSession("parent")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        require(countryCode.matches(Regex("[A-Z]{2}"))) { "Choose a valid country." }
        require(schoolGrade in 0..13) { "Grade must be from kindergarten to grade 13." }
        require(ageYears in 3..19) { "Age must be from 3 to 19 years." }
        val language = languageCode.trim().lowercase(Locale.ROOT)
        require(language.matches(Regex("[a-z][a-z0-9-]{1,15}"))) { "Choose a valid language code." }
        restArray("children?id=eq.$childId", "PATCH",
            JSONObject().put("country_code", countryCode).put("school_grade", schoolGrade)
                .put("age_years", ageYears).put("preferred_language", language))
    }

    suspend fun loadCurrentChild(): ChildProfile? {
        val session = requireSession("child")
        val rows = restArray("children?select=id,family_id,display_name,preferred_language,daily_cap_minutes,country_code,school_grade,age_years,avatar_icon,daily_game_limit_minutes,study_track,kids_mode_enabled,child_user_id&child_user_id=eq.${session.userId}&limit=1")
        return rows.optJSONObject(0)?.toChildProfile()
    }

    suspend fun claimChildPairing(code: String): String {
        requireSession("child")
        val normalized = code.filter(Char::isDigit)
        require(normalized.length == 6) { "Enter the six-digit parent pairing code" }
        val result = requestFunction("claim-child-pairing", JSONObject().put("code", normalized))
        return result.optString("child_id").takeIf { it.isNotBlank() }
            ?: throw IOException("The pairing service did not return a child profile")
    }

    suspend fun createPairingCode(childId: String): Pair<String, Int> {
        requireSession("parent")
        val result = requestFunction("create-child-pairing-code", JSONObject().put("child_id", childId))
        val code = result.optString("code")
        if (!code.matches(Regex("\\d{6}"))) throw IOException("Could not create a pairing code")
        return code to result.optInt("expires_in_seconds", 600)
    }

    private fun emergencyCodeHash(salt: String, code: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((salt + ":" + code).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun randomEmergencyCode(): String =
        SecureRandom().nextInt(100_000_000).toString().padStart(8, '0')

    private fun randomEmergencySalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun JSONObject.toChildEmergencyConfig(childId: String, familyId: String): ChildEmergencyAccessConfig {
        val packages = optJSONArray("allowed_packages") ?: JSONArray()
        val allowed = (0 until packages.length()).mapNotNull { packages.optString(it).takeIf(String::isNotBlank) }.toSet()
        return ChildEmergencyAccessConfig(
            childId = optString("child_id", childId),
            familyId = optString("family_id", familyId),
            enabled = optBoolean("enabled", false),
            blockEmergencyAfterDailyLimit = optBoolean("block_emergency_after_daily_limit", false),
            emergencyCodeSalt = optString("emergency_code_salt"),
            emergencyCodeHash = optString("emergency_code_hash"),
            logoutCodeSalt = optString("logout_code_salt"),
            logoutCodeHash = optString("logout_code_hash"),
            allowedPackages = allowed,
            accessMinutes = optInt("access_minutes", 10).coerceIn(1, 30),
            expiresAt = optString("expires_at").takeIf { it.isNotBlank() && it != "null" },
            logoutCodeExpiresAt = optString("logout_code_expires_at").takeIf { it.isNotBlank() && it != "null" },
            emergencyLastUsedDate = optString("emergency_last_used_date").takeIf { it.isNotBlank() && it != "null" },
            emergencyFailedAttempts = optInt("emergency_failed_attempts", 0),
            emergencyLockedUntil = optString("emergency_locked_until").takeIf { it.isNotBlank() && it != "null" },
            logoutFailedAttempts = optInt("logout_failed_attempts", 0),
            logoutLockedUntil = optString("logout_locked_until").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun ChildEmergencyAccessConfig.toJson(): JSONObject = JSONObject()
        .put("child_id", childId)
        .put("family_id", familyId)
        .put("enabled", enabled)
        .put("block_emergency_after_daily_limit", blockEmergencyAfterDailyLimit)
        .put("emergency_code_salt", emergencyCodeSalt)
        .put("emergency_code_hash", emergencyCodeHash)
        .put("logout_code_salt", logoutCodeSalt)
        .put("logout_code_hash", logoutCodeHash)
        .put("allowed_packages", JSONArray(allowedPackages.sorted()))
        .put("access_minutes", accessMinutes.coerceIn(1, 30))
        .put("expires_at", expiresAt ?: JSONObject.NULL)
        .put("logout_code_expires_at", logoutCodeExpiresAt ?: JSONObject.NULL)
        .put("emergency_last_used_date", emergencyLastUsedDate ?: JSONObject.NULL)
        .put("emergency_failed_attempts", emergencyFailedAttempts.coerceIn(0, 100))
        .put("emergency_locked_until", emergencyLockedUntil ?: JSONObject.NULL)
        .put("logout_failed_attempts", logoutFailedAttempts.coerceIn(0, 100))
        .put("logout_locked_until", logoutLockedUntil ?: JSONObject.NULL)
        .put("updated_at", java.time.Instant.now().toString())

    suspend fun loadChildEmergencyAccess(childId: String): ChildEmergencyAccessConfig {
        requireSession("parent")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        val rows = restArray("child_emergency_access?select=*&child_id=eq.$childId&limit=1")
        val row = rows.optJSONObject(0)
        if (row != null) return row.toChildEmergencyConfig(childId, row.optString("family_id"))
        val childRows = restArray("children?select=id,family_id&id=eq.$childId&limit=1")
        val child = childRows.optJSONObject(0) ?: throw IOException("Child profile was not found in this family.")
        return ChildEmergencyAccessConfig(childId = childId, familyId = child.optString("family_id"))
    }

    private suspend fun upsertChildEmergencyAccess(config: ChildEmergencyAccessConfig): ChildEmergencyAccessConfig {
        val saved = restArray(
            "child_emergency_access?on_conflict=child_id",
            "POST",
            config.toJson(),
            prefer = "resolution=merge-duplicates,return=representation"
        ).optJSONObject(0) ?: throw IOException("The security settings could not be saved.")
        require(saved.optString("child_id") == config.childId) {
            "The security settings could not be confirmed by the server."
        }
        return saved.toChildEmergencyConfig(config.childId, config.familyId)
    }

    suspend fun saveChildEmergencySettings(
        familyId: String,
        childId: String,
        enabled: Boolean,
        allowedPackages: Set<String>,
        accessMinutes: Int,
        blockEmergencyAfterDailyLimit: Boolean
    ): ChildEmergencyAccessConfig {
        requireSession("parent")
        require(familyId.matches(Regex("[0-9a-fA-F-]{36}")) && childId.matches(Regex("[0-9a-fA-F-]{36}"))) {
            "Invalid family or child profile."
        }
        require(accessMinutes in 1..30) { "Emergency access must be between 1 and 30 minutes." }
        val normalized = allowedPackages.map { it.trim() }.filter(String::isNotBlank).toSet()
        require(normalized.size <= 100 && normalized.all {
            it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))
        }) { "Choose valid Android apps." }
        require(!enabled || normalized.isNotEmpty()) {
            "Select at least one parent-approved app before enabling emergency access."
        }
        val current = loadChildEmergencyAccess(childId)
        require(current.familyId == familyId) { "This child does not belong to the selected family." }
        return upsertChildEmergencyAccess(current.copy(
            enabled = enabled,
            allowedPackages = normalized,
            accessMinutes = accessMinutes,
            blockEmergencyAfterDailyLimit = blockEmergencyAfterDailyLimit
        ))
    }

    suspend fun generateChildEmergencyCode(
        familyId: String,
        childId: String,
        allowedPackages: Set<String>,
        accessMinutes: Int,
        blockEmergencyAfterDailyLimit: Boolean
    ): String {
        requireSession("parent")
        require(allowedPackages.isNotEmpty()) { "Select at least one parent-approved app." }
        val current = saveChildEmergencySettings(
            familyId, childId, true, allowedPackages, accessMinutes, blockEmergencyAfterDailyLimit
        )
        val code = randomEmergencyCode()
        val salt = randomEmergencySalt()
        val expires = java.time.Instant.now().plusSeconds(30L * 24L * 60L * 60L).toString()
        upsertChildEmergencyAccess(current.copy(
            emergencyCodeSalt = salt,
            emergencyCodeHash = emergencyCodeHash(salt, code),
            expiresAt = expires,
            emergencyFailedAttempts = 0,
            emergencyLockedUntil = null
        ))
        return code
    }

    suspend fun generateChildLogoutCode(childId: String): String {
        requireSession("parent")
        val current = loadChildEmergencyAccess(childId)
        val code = randomEmergencyCode()
        val salt = randomEmergencySalt()
        upsertChildEmergencyAccess(current.copy(
            logoutCodeSalt = salt,
            logoutCodeHash = emergencyCodeHash(salt, code),
            logoutCodeExpiresAt = null,
            logoutFailedAttempts = 0,
            logoutLockedUntil = null
        ))
        return code
    }

    suspend fun loadChildEmergencyOptions(): ChildEmergencyOptions {
        requireSession("child")
        val response = requestFunction("child-emergency-access", JSONObject().put("action", "options"))
        val packages = response.optJSONArray("allowed_packages") ?: JSONArray()
        return ChildEmergencyOptions(
            enabled = response.optBoolean("enabled", false),
            blockEmergencyAfterDailyLimit = response.optBoolean("block_emergency_after_daily_limit", false),
            allowedPackages = (0 until packages.length()).mapNotNull { packages.optString(it).takeIf(String::isNotBlank) }.toSet(),
            accessMinutes = response.optInt("access_minutes", 10).coerceIn(1, 30),
            expiresAt = response.optString("expires_at").takeIf { it.isNotBlank() && it != "null" },
            lastUsedDate = response.optString("last_used_date").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    suspend fun redeemChildEmergencyAccess(code: String, packageName: String): EmergencyGrantResult {
        requireSession("child")
        val normalizedCode = code.filter(Char::isDigit)
        require(normalizedCode.matches(Regex("\\d{8}"))) { "Enter the eight-digit emergency code created by the parent." }
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) { "Choose an app approved by the parent." }
        val response = requestFunction(
            "child-emergency-access",
            JSONObject().put("action", "emergency").put("code", normalizedCode).put("package_name", packageName)
        )
        val returnedPackage = response.optString("package_name")
        require(returnedPackage == packageName) { "The server returned a different app approval." }
        return EmergencyGrantResult(
            packageName = returnedPackage,
            expiresInSeconds = response.optInt("expires_in_seconds", 0).coerceIn(1, 30 * 60),
            accessMinutes = response.optInt("access_minutes", 0).coerceIn(1, 30),
            usedDate = response.optString("used_date")
        )
    }

    suspend fun redeemChildLogoutCode(code: String) {
        requireSession("child")
        val normalizedCode = code.filter(Char::isDigit)
        require(normalizedCode.matches(Regex("\\d{8}"))) { "Enter the eight-digit code created by the parent." }
        requestFunction("child-emergency-access", JSONObject().put("action", "logout").put("code", normalizedCode))
    }

    suspend fun loadChildSubjectSelections(familyId: String): Map<String, Set<String>> {
        requireSession("parent")
        val rows = restArray("child_subjects?select=child_id,subject_id,family_id&family_id=eq.$familyId")
        val grouped = mutableMapOf<String, MutableSet<String>>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val childId = row.optString("child_id")
            val subjectId = row.optString("subject_id")
            if (childId.isNotBlank() && subjectId.isNotBlank()) {
                grouped.getOrPut(childId) { mutableSetOf() }.add(subjectId)
            }
        }
        return grouped.mapValues { it.value.toSet() }
    }

    suspend fun saveChildSubjectSelection(familyId: String, childId: String, subjectIds: Set<String>) {
        requireSession("parent")
        require(familyId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid family profile." }
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        require(subjectIds.size <= 200) { "Choose no more than 200 subjects." }
        require(subjectIds.all { it.matches(Regex("[0-9a-fA-F-]{36}")) }) { "Invalid subject selection." }
        restArray("child_subjects?child_id=eq.$childId&family_id=eq.$familyId", "DELETE", prefer = "return=minimal")
        subjectIds.forEach { subjectId ->
            restArray(
                "child_subjects?select=child_id",
                "POST",
                JSONObject().put("family_id", familyId).put("child_id", childId).put("subject_id", subjectId)
            )
        }
    }

    suspend fun loadSubjects(familyId: String, countryCode: String? = null): List<SubjectRow> {
        requireSession("parent")
        val rows = restArray("subjects?select=id,family_id,title,language_code,country_code,grade_level,curriculum_name,curriculum_subject_code&family_id=eq.$familyId&order=created_at.desc")
        val items = (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                SubjectRow(row.optString("id"), row.optString("family_id"), row.optString("title"), row.optString("language_code", "en"), row.optString("country_code", "PK"), row.optInt("grade_level", 1), row.optString("curriculum_name", "Parent-created"), row.optString("curriculum_subject_code", ""))
            }
        }
        val normalizedCountry = countryCode?.trim()?.uppercase(Locale.ROOT)
            ?.takeIf { it.matches(Regex("[A-Z]{2}")) }
            ?: return items
        return items.filter { it.countryCode.equals(normalizedCountry, ignoreCase = true) || it.countryCode.equals("ZZ", ignoreCase = true) }
            .distinctBy { subject ->
                listOf(
                    subject.title.trim().lowercase(Locale.ROOT),
                    subject.languageCode.trim().lowercase(Locale.ROOT),
                    subject.countryCode.trim().uppercase(Locale.ROOT),
                    subject.gradeLevel.toString(),
                    subject.curriculumSubjectCode.trim().lowercase(Locale.ROOT)
                ).joinToString("|")
            }
    }

    suspend fun addSubject(familyId: String, title: String, languageCode: String, countryCode: String = "PK", gradeLevel: Int = 1, curriculumName: String = "Parent-created", curriculumSubjectCode: String = ""): SubjectRow {
        requireSession("parent")
        require(title.trim().isNotEmpty()) { "Enter a subject name" }
        require(countryCode.matches(Regex("[A-Z]{2}"))) { "Choose a valid country." }
        require(gradeLevel in 0..13) { "Grade must be from kindergarten to grade 13." }
        val rows = restArray("subjects?select=id,family_id,title,language_code,country_code,grade_level,curriculum_name,curriculum_subject_code", "POST",
            JSONObject().put("family_id", familyId)
                .put("title", title.trim().take(160))
                .put("language_code", languageCode.trim().ifBlank { "en" }.take(16))
                .put("country_code", countryCode)
                .put("grade_level", gradeLevel)
                .put("curriculum_name", curriculumName.take(160))
                .put("curriculum_subject_code", curriculumSubjectCode.take(40)))
        val row = rows.optJSONObject(0) ?: throw IOException("Could not create the subject")
        return SubjectRow(row.getString("id"), row.getString("family_id"), row.getString("title"), row.optString("language_code", "en"), row.optString("country_code", countryCode), row.optInt("grade_level", gradeLevel), row.optString("curriculum_name", curriculumName), row.optString("curriculum_subject_code", curriculumSubjectCode))
    }

    suspend fun updateSubject(
        subjectId: String, title: String, languageCode: String, countryCode: String, gradeLevel: Int
    ): SubjectRow {
        requireSession("parent")
        require(subjectId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid subject." }
        val cleanTitle = title.trim().take(160)
        require(cleanTitle.isNotBlank()) { "Enter a subject name." }
        val language = languageCode.trim().lowercase(Locale.ROOT)
        require(language.matches(Regex("[a-z][a-z0-9-]{1,15}"))) { "Use a valid language code such as en, ur or sd." }
        val country = countryCode.trim().uppercase(Locale.ROOT)
        require(country.matches(Regex("[A-Z]{2}"))) { "Choose a valid country." }
        require(gradeLevel in 0..13) { "Grade must be from kindergarten to grade 13." }
        val rows = restArray(
            "subjects?id=eq.$subjectId&select=id,family_id,title,language_code,country_code,grade_level,curriculum_name,curriculum_subject_code",
            "PATCH",
            JSONObject().put("title", cleanTitle).put("language_code", language).put("country_code", country).put("grade_level", gradeLevel)
        )
        val row = rows.optJSONObject(0) ?: JSONObject()
            .put("id", subjectId).put("title", cleanTitle).put("language_code", language)
            .put("country_code", country).put("grade_level", gradeLevel)
        return SubjectRow(
            row.optString("id", subjectId), row.optString("family_id"), row.optString("title", cleanTitle),
            row.optString("language_code", language), row.optString("country_code", country),
            row.optInt("grade_level", gradeLevel), row.optString("curriculum_name", "Parent-created"),
            row.optString("curriculum_subject_code", "")
        )
    }

    suspend fun deleteSubject(subjectId: String) {
        requireSession("parent")
        require(subjectId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid subject." }
        // Ask PostgREST to return the deleted row so an RLS-filtered no-op
        // cannot be mistaken for a successful delete.
        val deleted = restArray(
            "subjects?id=eq.$subjectId&select=id",
            "DELETE",
            prefer = "return=representation"
        )
        require((0 until deleted.length()).any { deleted.optJSONObject(it)?.optString("id") == subjectId }) {
            "Supabase did not confirm that this subject was deleted. Check parent permissions and whether dependent records prevent deletion."
        }
    }

    suspend fun findOrCreateSubject(
        familyId: String, title: String, languageCode: String, countryCode: String = "PK"
    ): SubjectRow {
        requireSession("parent")
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Enter a subject name." }
        val normalizedLanguage = languageCode.trim().ifBlank { "en" }
        val normalizedCountry = countryCode.trim().uppercase(Locale.ROOT).takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "PK"
        val existing = loadSubjects(familyId).firstOrNull {
            it.title.equals(normalizedTitle, ignoreCase = true) &&
                it.languageCode.equals(normalizedLanguage, ignoreCase = true) &&
                it.countryCode.equals(normalizedCountry, ignoreCase = true)
        }
        return existing ?: addSubject(familyId, normalizedTitle, normalizedLanguage, normalizedCountry)
    }

    suspend fun ensureTemplateQuestions(subjectId: String, templates: List<ParentLearningTemplate>): Int {
        requireSession("parent")
        require(subjectId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid subject." }
        if (templates.isEmpty()) return 0
        val existingRows = loadQuestions(subjectId).toMutableList()
        val existingConcepts = existingRows.map { it.conceptKey }.toMutableSet()
        val existingAnswers = loadQuestionAnswerKeys(existingRows.map { it.id }).toMutableMap()
        val existingSolutions = loadQuestionSolutions(existingRows.map { it.id }).toMutableMap()
        var created = 0

        fun generatedSeedSuffix(key: String): String? {
            val ordinarySeed = key.matches(Regex("(?:3d|4d):[^:]+:[a-z0-9-]+-[0-9]+"))
            val faithSeed = key.matches(Regex("(?:3d|4d):[^:]+:faith:[a-z-]+:[0-9]+"))
            if (!ordinarySeed && !faithSeed) return null
            return key.removePrefix("3d:").removePrefix("4d:").substringAfter(":", "")
        }

        templates.distinctBy { it.conceptKey }.forEach { template ->
            val key = template.conceptKey.trim()
            if (key.isEmpty()) return@forEach
            val wantedSuffix = generatedSeedSuffix(key)
            val sameKeyIndex = existingRows.indexOfFirst { it.conceptKey == key }
            if (sameKeyIndex >= 0) {
                val current = existingRows[sameKeyIndex]
                val changed = current.prompt != template.prompt ||
                    current.questionType != template.questionType ||
                    current.options != template.options ||
                    !current.languageCode.equals(template.languageCode, ignoreCase = true) ||
                    existingAnswers[current.id] != template.answer ||
                    (template.solution.isNotBlank() && existingSolutions[current.id] != template.solution)
                // An existing generated question may need updated wording when the parent's
                // child is in a different grade band. Keep its ID and history; update in place.
                if (wantedSuffix != null && changed) {
                    val payload = JSONObject()
                        .put("prompt", template.prompt)
                        .put("question_type", template.questionType)
                        .put("options", JSONArray(template.options))
                        .put("answer_key", JSONObject().put("answer", template.answer))
                        .put("language_code", template.languageCode.trim().lowercase(Locale.ROOT))
                    restArray("questions?id=eq.${current.id}", "PATCH", payload)
                    existingAnswers[current.id] = template.answer
                    if (template.solution.isNotBlank()) {
                        saveQuestionSolution(current.id, template.solution)
                        existingSolutions[current.id] = template.solution
                    }
                    existingRows[sameKeyIndex] = current.copy(
                        prompt = template.prompt, questionType = template.questionType,
                        options = template.options, languageCode = template.languageCode
                    )
                }
                return@forEach
            }
            if (key in existingConcepts) return@forEach

            // If a starter's visual-model tag changed, reuse the row with the same stable
            // subject/index suffix, instead of inserting a duplicate.
            val legacyIndex = if (wantedSuffix == null) -1 else existingRows.indexOfFirst { row ->
                row.conceptKey != key && generatedSeedSuffix(row.conceptKey) == wantedSuffix
            }
            if (legacyIndex >= 0) {
                val legacy = existingRows[legacyIndex]
                val payload = JSONObject()
                    .put("prompt", template.prompt)
                    .put("question_type", template.questionType)
                    .put("options", JSONArray(template.options))
                    .put("answer_key", JSONObject().put("answer", template.answer))
                    .put("language_code", template.languageCode.trim().lowercase(Locale.ROOT))
                    .put("concept_key", key)
                restArray("questions?id=eq.${legacy.id}", "PATCH", payload)
                existingAnswers[legacy.id] = template.answer
                if (template.solution.isNotBlank()) {
                    saveQuestionSolution(legacy.id, template.solution)
                    existingSolutions[legacy.id] = template.solution
                }
                existingConcepts.remove(legacy.conceptKey)
                existingConcepts.add(key)
                existingRows[legacyIndex] = legacy.copy(
                    prompt = template.prompt,
                    questionType = template.questionType,
                    options = template.options,
                    languageCode = template.languageCode,
                    conceptKey = key
                )
                return@forEach
            }

            try {
                addQuestion(
                    subjectId = subjectId,
                    prompt = template.prompt,
                    type = template.questionType,
                    options = template.options,
                    answer = template.answer,
                    languageCode = template.languageCode,
                    conceptKey = key,
                    solution = template.solution
                )
                existingConcepts.add(key)
                created++
            } catch (error: Exception) {
                // A second parent screen may seed at the same time. Only suppress the
                // error when the server confirms that the key now exists.
                val current = loadQuestions(subjectId)
                if (current.any { it.conceptKey == key }) {
                    existingConcepts.add(key)
                } else {
                    throw error
                }
            }
        }
        return created
    }

    suspend fun addQuestion(
        subjectId: String, prompt: String, type: String, options: List<String>, answer: String,
        languageCode: String, conceptKey: String, solution: String = ""
    ): String {
        requireSession("parent")
        require(prompt.trim().isNotEmpty()) { "Enter the question" }
        require(answer.trim().isNotEmpty() || type == "matching") { "Enter the correct answer" }
        require(languageCode.matches(Regex("[A-Za-z][A-Za-z0-9-]{1,15}"))) { "Use a language code such as en, ur or sd." }
        require(conceptKey.trim().isNotEmpty()) { "Enter a concept tag. Reuse the same tag for questions that test the same idea." }
        require(type in setOf("multiple_choice", "true_false", "short_answer", "numeric", "matching", "picture")) { "Select a valid question type" }
        if (type == "multiple_choice") {
            require(options.size >= 2) { "Add at least two answer options" }
            require(options.any { it.trim().equals(answer.trim(), ignoreCase = true) }) {
                "For multiple choice, the correct answer must match one of the options"
            }
        }
        if (type == "true_false") {
            require(answer.trim().equals("true", true) || answer.trim().equals("false", true)) {
                "For true/false questions, enter true or false as the correct answer"
            }
        }
        var storedOptions = options.map { it.trim().take(500) }.filter { it.isNotEmpty() }
        var storedAnswer = answer.trim().take(1000)
        if (type == "matching") {
            val pairs = options.mapNotNull { raw ->
                val split = raw.split("|", limit = 2)
                if (split.size == 2 && split[0].isNotBlank() && split[1].isNotBlank()) {
                    split[0].trim().take(250) to split[1].trim().take(250)
                } else null
            }.distinctBy { it.first }
            require(pairs.size >= 2 && pairs.size == options.size) {
                "Add at least two matching pairs, one pair per line in left item|right item format."
            }
            val rightValues = pairs.map { it.second }
            require(rightValues.distinct().size == rightValues.size) { "Each right-side match must be unique." }
            storedOptions = pairs.map { "L|" + it.first } + rightValues.shuffled().map { "R|" + it }
            storedAnswer = pairs.joinToString(";") { (left, right) -> "$left=$right" }.take(1000)
        }
        if (type == "picture") {
            require(answer.isNotBlank()) { "Enter the correct answer or a short description of the picture answer." }
        }
        val created = restArray("questions?select=id", "POST",
            JSONObject()
                .put("subject_id", subjectId)
                .put("prompt", prompt.trim().take(10_000))
                .put("question_type", type)
                .put("options", JSONArray(storedOptions))
                .put("answer_key", JSONObject().put("answer", storedAnswer))
                .put("language_code", languageCode.trim().lowercase())
                .put("concept_key", conceptKey.trim().replace(Regex("\\s+"), " ").take(160)))
        val questionId = created.optJSONObject(0)?.optString("id")
            ?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) }
            ?: throw IOException("The question was created but the server did not return its ID.")
        if (solution.isNotBlank()) {
            try {
                saveQuestionSolution(questionId, solution)
            } catch (e: Exception) {
                // Avoid leaving a template question half-saved without its private worked solution.
                try { restArray("questions?id=eq.$questionId", "DELETE", prefer = "return=minimal") } catch (_: Exception) { }
                throw IOException("Could not save the parent-only worked solution. Apply the latest EduLock Supabase migration and try again.", e)
            }
        }
        return questionId
    }

    suspend fun updateQuestion(
        question: QuestionRow, prompt: String, optionLines: List<String>, answer: String,
        languageCode: String, conceptKey: String, solution: String
    ) {
        requireSession("parent")
        require(question.id.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid question." }
        require(prompt.trim().isNotEmpty()) { "Enter the question." }
        require(languageCode.matches(Regex("[A-Za-z][A-Za-z0-9-]{1,15}"))) { "Use a language code such as en, ur or sd." }
        require(conceptKey.trim().isNotEmpty() && conceptKey.trim().length <= 160) { "Enter a concept tag of 1–160 characters." }
        require(answer.trim().isNotEmpty() || question.questionType == "matching") { "Enter the correct answer." }

        var storedOptions = optionLines.map { it.trim().take(500) }.filter { it.isNotEmpty() }
        var storedAnswer = answer.trim().take(1000)
        when (question.questionType) {
            "multiple_choice" -> {
                require(storedOptions.size >= 2) { "Add at least two answer options." }
                require(storedOptions.any { it.equals(storedAnswer, ignoreCase = true) }) {
                    "The correct answer must match one of the answer options."
                }
            }
            "true_false" -> require(storedAnswer.equals("true", true) || storedAnswer.equals("false", true)) {
                "For true/false questions, enter true or false as the correct answer."
            }
            "matching" -> {
                val pairs = optionLines.mapNotNull { raw ->
                    val parts = raw.split("|", limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank())
                        parts[0].trim().take(250) to parts[1].trim().take(250)
                    else null
                }.distinctBy { it.first }
                require(pairs.size >= 2 && pairs.size == optionLines.size) {
                    "Add at least two matching pairs, one per line in left item|right item format."
                }
                require(pairs.map { it.second }.distinct().size == pairs.size) { "Each right-side match must be unique." }
                storedOptions = pairs.map { "L|" + it.first } + pairs.map { it.second }.shuffled().map { "R|" + it }
                storedAnswer = pairs.joinToString(";") { it.first + "=" + it.second }.take(1000)
            }
            "picture" -> require(answer.isNotBlank()) { "Enter the correct answer for this picture question." }
            "short_answer", "numeric" -> Unit
            else -> throw IllegalArgumentException("Unsupported question type.")
        }

        restArray(
            "questions?id=eq." + question.id, "PATCH",
            JSONObject()
                .put("prompt", prompt.trim().take(10_000))
                .put("question_type", question.questionType)
                .put("options", JSONArray(storedOptions))
                .put("answer_key", JSONObject().put("answer", storedAnswer))
                .put("language_code", languageCode.trim().lowercase())
                .put("concept_key", conceptKey.trim().replace(Regex("\\s+"), " ").take(160)),
            prefer = "return=minimal"
        )
        if (solution.isBlank()) {
            restArray("question_solutions?question_id=eq." + question.id, "DELETE")
        } else {
            saveQuestionSolution(question.id, solution)
        }
    }

    suspend fun uploadQuestionImage(familyId: String, questionId: String, jpegBytes: ByteArray): String {
        val session = requireSession("parent")
        require(familyId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid family." }
        require(questionId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid question." }
        require(jpegBytes.isNotEmpty() && jpegBytes.size <= 2_000_000) { "The compressed picture must be under 2 MB." }
        val fileName = java.util.UUID.randomUUID().toString() + ".jpg"
        val path = "$familyId/$questionId/$fileName"
        val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val url = "$baseUrl/storage/v1/object/question-images/$encodedPath"
        val request = Request.Builder()
            .url(url)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Content-Type", "image/jpeg")
            .header("x-upsert", "false")
            .post(jpegBytes.toRequestBody("image/jpeg".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Picture upload failed (HTTP ${response.code}). Apply migration 0033 and check family storage permissions.")
        }
        restArray("questions?id=eq.$questionId", "PATCH", JSONObject().put("picture_path", path))
        val verify = restArray("questions?select=id,picture_path&id=eq.$questionId&limit=1").optJSONObject(0)
            ?: throw IOException("Picture uploaded but its question record could not be verified.")
        if (verify.optString("picture_path") != path) {
            throw IOException("Picture uploaded but Supabase did not confirm the saved path.")
        }
        return path
    }

    suspend fun createSignedQuestionImageUrl(path: String): String {
        val session = requireSession()
        require(path.matches(Regex("[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}/[A-Za-z0-9-]+\\.jpg"))) { "Invalid question image path." }
        val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val body = JSONObject().put("expiresIn", 60 * 60)
        val request = Request.Builder()
            .url("$baseUrl/storage/v1/object/sign/question-images/$encodedPath")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Could not open the private question picture (HTTP ${response.code}).")
            val signedPath = JSONObject(responseText).optString("signedURL")
                .takeIf { it.isNotBlank() } ?: throw IOException("Storage returned no signed picture URL.")
            return if (signedPath.startsWith("http")) signedPath else "$baseUrl/storage/v1$signedPath"
        }
    }

    suspend fun saveQuestionSolution(questionId: String, solution: String) {
        requireSession("parent")
        require(questionId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid question." }
        val cleaned = solution.trim().take(12000)
        if (cleaned.isEmpty()) return
        val existing = restArray("question_solutions?select=id&question_id=eq.$questionId&limit=1")
        val payload = JSONObject()
            .put("question_id", questionId)
            .put("solution", cleaned)
            .put("updated_at", java.time.Instant.now().toString())
        if (existing.length() > 0) {
            restArray("question_solutions?question_id=eq.$questionId", "PATCH", payload)
        } else {
            restArray("question_solutions?select=id", "POST", payload)
        }
    }

    suspend fun loadQuestionSolutions(questionIds: List<String>): Map<String, String> {
        requireSession("parent")
        val ids = questionIds.distinct().filter { it.matches(Regex("[0-9a-fA-F-]{36}")) }
        if (ids.isEmpty()) return emptyMap()
        val rows = restArray(
            "question_solutions?select=question_id,solution&question_id=in.(" + ids.joinToString(",") + ")"
        )
        return (0 until rows.length()).mapNotNull { index ->
            rows.optJSONObject(index)?.let { row ->
                val id = row.optString("question_id")
                val solution = row.optString("solution")
                if (id.isNotBlank() && solution.isNotBlank()) id to solution else null
            }
        }.toMap()
    }

    suspend fun loadQuestions(subjectId: String, limit: Int = 500): List<QuestionRow> {
        requireSession()
        val rows = restArray("questions?select=id,subject_id,prompt,question_type,options,language_code,concept_key,picture_path,grade_level,age_min,age_max,reward_minutes,created_at&subject_id=eq.$subjectId&order=created_at.asc,id.asc&limit=${limit.coerceIn(1, 500)}")
        return (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row -> row.toQuestionRow() }
        }
    }

    suspend fun loadQuestionAnswerKeys(questionIds: List<String>): Map<String, String> {
        requireSession("parent")
        val ids = questionIds.distinct().filter { it.matches(Regex("[0-9a-fA-F-]{36}")) }
        if (ids.isEmpty()) return emptyMap()
        val rows = restArray(
            "questions?select=id,answer_key&id=in.(" + ids.joinToString(",") + ")"
        )
        val result = linkedMapOf<String, String>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val key = row.optJSONObject("answer_key") ?: continue
            val id = row.optString("id")
            val answer = key.optString("answer").trim()
            if (id.isNotBlank() && answer.isNotBlank()) result[id] = answer
        }
        return result
    }

    suspend fun loadQuestionTranslations(questionIds: List<String>): Map<String, List<QuestionTranslation>> {
        requireSession()
        val ids = questionIds.distinct().filter { it.matches(Regex("[0-9a-fA-F-]{36}")) }
        if (ids.isEmpty()) return emptyMap()
        val rows = restArray(
            "question_translations?select=id,question_id,language_code,prompt,options,created_at" +
                "&question_id=in.(" + ids.joinToString(",") + ")&order=language_code.asc"
        )
        val result = linkedMapOf<String, MutableList<QuestionTranslation>>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val opts = row.optJSONArray("options") ?: JSONArray()
            val translation = QuestionTranslation(
                row.optString("id"), row.optString("question_id"), row.optString("language_code"),
                row.optString("prompt"), (0 until opts.length()).map { opts.optString(it) }
            )
            result.getOrPut(translation.questionId) { mutableListOf() }.add(translation)
        }
        return result.mapValues { it.value.toList() }
    }

    suspend fun saveQuestionTranslation(
        question: QuestionRow, languageCode: String, prompt: String,
        options: List<String>, acceptedAnswers: List<String>
    ) {
        requireSession("parent")
        val lang = languageCode.trim().lowercase()
        require(lang.matches(Regex("[a-z][a-z0-9-]{1,15}"))) { "Use a language code such as en, ur or sd." }
        require(!lang.equals(question.languageCode, ignoreCase = true)) {
            "Choose a different language from the question's original language."
        }
        require(prompt.trim().isNotEmpty()) { "Enter the translated question." }
        var accepted = acceptedAnswers.map { it.trim().take(1000) }.filter { it.isNotEmpty() }.distinct()
        require(accepted.isNotEmpty() && accepted.size <= 30) { "Enter between 1 and 30 accepted answers, one per line." }
        var cleanOptions = options.map { it.trim().take(500) }.filter { it.isNotEmpty() }
        if (question.questionType == "matching") {
            val pairs = options.mapNotNull { raw ->
                val parts = raw.split("|", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank())
                    parts[0].trim().take(250) to parts[1].trim().take(250)
                else null
            }.distinctBy { it.first }
            require(pairs.size >= 2 && pairs.size == options.size) { "Add at least two translated matching pairs in left|right format." }
            require(pairs.map { it.second }.distinct().size == pairs.size) { "Translated right-side matches must be unique." }
            cleanOptions = pairs.map { "L|" + it.first } + pairs.map { it.second }.shuffled().map { "R|" + it }
            accepted = listOf(pairs.joinToString(";") { (left, right) -> "$left=$right" })
        }
        if (question.questionType == "multiple_choice" || question.questionType == "true_false") {
            require(cleanOptions.size >= 2) { "Add at least two translated answer options." }
            require(accepted.any { answer -> cleanOptions.any { it.equals(answer, ignoreCase = true) } }) {
                "At least one accepted answer must match a translated option."
            }
        }
        val body = JSONObject()
            .put("question_id", question.id)
            .put("language_code", lang)
            .put("prompt", prompt.trim().take(10000))
            .put("options", JSONArray(cleanOptions))
            .put("accepted_answers", JSONArray(accepted))
            .put("updated_at", java.time.Instant.now().toString())
        val existing = restArray(
            "question_translations?select=id&question_id=eq.${question.id}&language_code=eq.$lang&limit=1"
        )
        val written = if (existing.length() > 0) {
            restArray(
                "question_translations?select=id,question_id,language_code,prompt,options,created_at,updated_at&question_id=eq.${question.id}&language_code=eq.$lang",
                "PATCH", body
            )
        } else {
            restArray("question_translations?select=id,question_id,language_code,prompt", "POST", body)
        }
        val saved = written.optJSONObject(0)
        require(
            written.length() == 1 && saved != null &&
                saved.optString("question_id") == question.id &&
                saved.optString("language_code").equals(lang, ignoreCase = true) &&
                saved.optString("prompt") == prompt.trim().take(10000)
        ) {
            "Supabase did not confirm that the translation was saved. Check your connection and parent permissions, then try again."
        }
    }

    suspend fun loadConceptProgress(): List<ConceptProgress> {
        requireSession("parent")
        val rows = restArray(
            "attempt_answers?select=concept_key,language_code,is_correct,created_at&order=created_at.desc&limit=1000"
        )
        data class Accumulator(var correct: Int = 0, var total: Int = 0)
        val grouped = linkedMapOf<Pair<String, String>, Accumulator>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val key = row.optString("concept_key").ifBlank { "general" }
            val language = row.optString("language_code").ifBlank { "und" }
            val acc = grouped.getOrPut(key to language) { Accumulator() }
            acc.total++
            if (row.optBoolean("is_correct")) acc.correct++
        }
        return grouped.map { (key, acc) -> ConceptProgress(key.first, key.second, acc.correct, acc.total) }
            .sortedWith(compareBy<ConceptProgress> { it.conceptKey.lowercase() }.thenBy { it.languageCode })
    }

    suspend fun addTask(
        familyId: String, child: ChildProfile, subjects: List<SubjectRow>, title: String,
        questionCount: Int, passPercent: Int, rewardMinutes: Int, taskDailyCapMinutes: Int,
        rewardPackageName: String? = null, rewardGroupId: String? = null,
        timeLimitSeconds: Int? = null, rewardToBank: Boolean = true,
        questionTimeLimitSeconds: Int? = null,
        selectedQuestionIds: List<String> = emptyList()
    ) {
        requireSession("parent")
        val selectedSubjects = subjects.distinctBy { it.id }.take(20)
        require(selectedSubjects.isNotEmpty()) { "Choose at least one subject for this task." }
        require(selectedSubjects.all { it.gradeLevel == child.schoolGrade }) {
            "All chosen subjects must match ${child.displayName}'s current grade (${child.schoolGrade})."
        }
        require(title.trim().isNotEmpty()) { "Enter a task title" }
        require(questionCount in 1..500) { "Question count must be from 1 to 500" }
        require(passPercent in 0..100) { "Passing score must be from 0 to 100 percent" }
        require(rewardMinutes in 0..1440) { "Reward is out of range." }
        require(taskDailyCapMinutes in 0..1440) { "Task daily cap must be from 0 to 1440 minutes" }
        require(timeLimitSeconds == null || timeLimitSeconds in 10..3600) {
            "Task time limit must be between 10 and 3600 seconds."
        }
        require(questionTimeLimitSeconds == null || questionTimeLimitSeconds in 5..600) {
            "Per-question time limit must be between 5 and 600 seconds."
        }
        require(rewardPackageName.isNullOrBlank() || rewardGroupId.isNullOrBlank()) {
            "Choose either one app or one app group as the reward destination, not both."
        }
        if (!rewardGroupId.isNullOrBlank()) {
            require(loadAppGroups(child.id).any { it.id == rewardGroupId && it.packageNames.isNotEmpty() }) {
                "The selected app group is empty or no longer available."
            }
        }
        val pools = selectedSubjects.map { subject ->
            loadQuestions(subject.id, 500).filter { question ->
                question.gradeLevel == child.schoolGrade &&
                    child.ageYears in question.ageMin..question.ageMax
            }
        }
        val available = pools.sumOf { it.size }
        require(available > 0) {
            "No questions in this subject match ${child.displayName}'s Grade ${child.schoolGrade} and age ${child.ageYears}. Add or import questions for this grade and age first."
        }
        require(questionCount <= available) {
            "Only $available questions match Grade ${child.schoolGrade}, age ${child.ageYears}, and this subject. Lower the task question count or add matching questions."
        }
        val selectedQuestions = if (selectedQuestionIds.isNotEmpty()) {
            val requestedIds = selectedQuestionIds.distinct()
            require(requestedIds.size == selectedQuestionIds.size) { "A question can only be selected once." }
            require(requestedIds.size == questionCount) {
                "Task question count must match the number of selected questions."
            }
            require(requestedIds.size <= 500) { "Choose no more than 500 questions in one task." }
            val availableById = pools.flatten().associateBy { it.id }
            val matched = requestedIds.mapNotNull { availableById[it] }
            require(matched.size == requestedIds.size) {
                "One or more selected questions do not match the child's grade, age or selected subjects. Refresh the question list."
            }
            matched
        } else {
            // Round-robin selection balances questions across the chosen subject banks.
            val chosen = mutableListOf<QuestionRow>()
            var poolIndex = 0
            while (chosen.size < questionCount) {
                var selectedAny = false
                pools.forEach { pool ->
                    if (poolIndex < pool.size && chosen.size < questionCount) {
                        chosen += pool[poolIndex]
                        selectedAny = true
                    }
                }
                if (!selectedAny) break
                poolIndex++
            }
            chosen
        }
        require(selectedQuestions.size == questionCount) { "Could not assemble the requested task questions." }
        val createdRows = restArray("tasks?select=id", "POST",
            JSONObject()
                .put("family_id", familyId)
                .put("child_id", child.id)
                .put("subject_id", selectedQuestions.first().subjectId)
                .put("target_grade_level", child.schoolGrade)
                .put("target_age_years", child.ageYears)
                .put("title", title.trim().take(160))
                .put("question_count", questionCount)
                .put("pass_percent", passPercent)
                .put("reward_minutes", rewardMinutes)
                .put("reward_package_name", rewardPackageName?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("reward_group_id", rewardGroupId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("reward_to_bank", rewardToBank)
                .put("daily_cap_minutes", minOf(taskDailyCapMinutes, child.dailyCapMinutes))
                .put("time_limit_seconds", timeLimitSeconds ?: JSONObject.NULL)
                .put("question_time_limit_seconds", questionTimeLimitSeconds ?: JSONObject.NULL)
                .put("active", true))
        val taskId = createdRows.optJSONObject(0)?.optString("id")
            ?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) }
            ?: throw IOException("Supabase did not return the new task ID.")
        try {
            val questionLinks = JSONArray()
            selectedQuestions.forEachIndexed { index, question ->
                questionLinks.put(JSONObject().put("task_id", taskId).put("question_id", question.id).put("sort_order", index))
            }
            val savedLinks = restArray("task_questions?select=task_id,question_id", "POST", questionLinks, prefer = "return=representation")
            if (savedLinks.length() != selectedQuestions.size) {
                throw IOException("Supabase linked only ${savedLinks.length()} of ${selectedQuestions.size} task questions.")
            }
        } catch (error: Exception) {
            try { send("rest/v1/tasks?id=eq.$taskId", "DELETE", null, authenticated = true, profile = true) } catch (_: Exception) { }
            throw error
        }
    }


    /** Parent-only list of all tasks for the signed-in family's children, including inactive tasks. */
    suspend fun loadParentTasks(): List<TaskRow> {
        requireSession("parent")
        val rows = restArray(
            "tasks?select=id,child_id,subject_id,title,question_count,pass_percent,reward_minutes,daily_cap_minutes,active,reward_package_name,reward_group_id,time_limit_seconds,question_time_limit_seconds,reward_to_bank,target_grade_level,target_age_years&order=created_at.desc&limit=500"
        )
        return (0 until rows.length()).mapNotNull { index -> rows.optJSONObject(index)?.toTaskRow() }
    }

    /** Update parent-configurable task settings. Existing question links are retained. */
    suspend fun updateTaskSettings(
        taskId: String, title: String, questionCount: Int, passPercent: Int,
        rewardMinutes: Int, dailyCapMinutes: Int, timeLimitSeconds: Int?,
        questionTimeLimitSeconds: Int?, active: Boolean
    ) {
        requireSession("parent")
        require(taskId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid task." }
        require(title.trim().isNotEmpty()) { "Enter a task title." }
        require(questionCount in 1..500 && passPercent in 0..100) { "Check the question count and passing score." }
        require(rewardMinutes in 0..1440 && dailyCapMinutes in 0..1440) { "Reward limits must be between 0 and 1440 minutes." }
        require(timeLimitSeconds == null || timeLimitSeconds in 10..3600) { "Task time limit must be 10–3600 seconds." }
        require(questionTimeLimitSeconds == null || questionTimeLimitSeconds in 5..600) { "Question time limit must be 5–600 seconds." }
        send(
            "rest/v1/tasks?id=eq.$taskId", "PATCH",
            JSONObject().put("title", title.trim().take(160))
                .put("question_count", questionCount).put("pass_percent", passPercent)
                .put("reward_minutes", rewardMinutes).put("daily_cap_minutes", dailyCapMinutes)
                .put("time_limit_seconds", timeLimitSeconds ?: JSONObject.NULL)
                .put("question_time_limit_seconds", questionTimeLimitSeconds ?: JSONObject.NULL)
                .put("active", active),
            authenticated = true, profile = true
        )
    }

    /** Delete a task and its linked question rows, but retain historical attempts. */
    suspend fun deleteTask(taskId: String) {
        requireSession("parent")
        require(taskId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid task." }
        send("rest/v1/task_questions?task_id=eq.$taskId", "DELETE", null, authenticated = true, profile = true)
        send("rest/v1/tasks?id=eq.$taskId", "DELETE", null, authenticated = true, profile = true)
    }

    /**
     * Submit a completed child task through the trusted server grading function.
     * Answer keys remain server-side; the client sends only question IDs, answers and
     * optional language selections. The function validates the linked child/task and
     * creates attempt/reward ledger rows atomically.
     */
    suspend fun submitTask(
        taskId: String,
        answers: Map<String, String>,
        answerLanguages: Map<String, String> = emptyMap()
    ): TaskResult {
        requireSession("child")
        require(taskId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid task." }
        require(answers.isNotEmpty() && answers.size <= 500) { "Answer every question before submitting." }
        val payload = JSONArray()
        answers.forEach { (questionId, answerText) ->
            require(questionId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid question." }
            require(answerText.length <= 10000) { "An answer is too long." }
            val row = JSONObject().put("question_id", questionId).put("answer", answerText)
            answerLanguages[questionId]?.takeIf { it.isNotBlank() }?.let { row.put("language_code", it.take(16)) }
            payload.put(row)
        }
        val requestBody = JSONObject().put("task_id", taskId).put("answers", payload)
        val response = try {
            requestFunction("submit-task-attempt", requestBody)
        } catch (error: IOException) {
            // Offline completion is provisional until the server re-grades it on sync.
            val provisional = gradeCachedTaskOffline(taskId, answers, answerLanguages)
            enqueueOfflineAttempt(requestBody)
            return provisional.copy(pendingSync = true)
        }
        return TaskResult(
            scorePercent = response.optInt("score_percent", 0).coerceIn(0, 100),
            passed = response.optBoolean("passed", false),
            awardedMinutes = response.optInt("awarded_minutes", 0).coerceIn(0, 1440),
            dailyUsedMinutes = response.optInt("daily_used_minutes_after", response.optInt("daily_used_minutes", 0)).coerceAtLeast(0),
            dailyCapMinutes = response.optInt("daily_cap_minutes", 0).coerceIn(0, 1440),
            rewardToBank = response.optBoolean("reward_to_bank", false)
        )
    }


    /**
     * Grade from the task package previously cached on this device. The server re-grades
     * every queued attempt on reconnect and remains authoritative for final reward ledgers.
     */
    private suspend fun gradeCachedTaskOffline(
        taskId: String,
        answers: Map<String, String>,
        answerLanguages: Map<String, String>
    ): TaskResult {
        val packageJson = try {
            JSONObject(offlineReadCache.getString(offlineTaskPackageKey(taskId), null)
                ?: throw IOException("Offline task package is missing. Open this task online first."))
        } catch (e: Exception) {
            throw IOException("Offline grading data is not available. Open this task online first.", e)
        }
        val task = packageJson.optJSONObject("task") ?: throw IOException("Offline task settings are missing.")
        val questionsArray = packageJson.optJSONArray("questions") ?: JSONArray()
        val questions = linkedMapOf<String, JSONObject>()
        for (i in 0 until questionsArray.length()) {
            val row = questionsArray.optJSONObject(i) ?: continue
            questions[row.optString("id")] = row
        }
        val ids = questions.keys.toList()
        if (ids.isEmpty()) throw IOException("No saved questions are available for offline use.")
        val expectedIds = ids.toSet()
        if (answers.keys != expectedIds) throw IOException("Offline task data is incomplete. Reconnect before submitting.")
        var correct = 0
        answers.forEach { (questionId, actual) ->
            val row = questions[questionId] ?: return@forEach
            val language = answerLanguages[questionId]?.takeIf { it.isNotBlank() }
                ?: row.optString("language_code", "en")
            val acceptedByLanguage = row.optJSONObject("accepted_answers_by_language")
            val accepted = acceptedByLanguage?.optJSONArray(language)
            val expectedAnswers = mutableListOf<String>()
            if (accepted != null) {
                for (i in 0 until accepted.length()) accepted.optString(i).takeIf { it.isNotBlank() }?.let(expectedAnswers::add)
            } else if (language.equals(row.optString("language_code", "en"), ignoreCase = true)) {
                row.optJSONObject("answer_key")?.optString("answer")?.takeIf { it.isNotBlank() }?.let(expectedAnswers::add)
            }
            if (expectedAnswers.any { normalizeOfflineAnswer(it) == normalizeOfflineAnswer(actual) }) correct++
        }
        val score = (correct * 100) / ids.size
        val passed = score >= task.optInt("pass_percent", 70)
        val dayKey = LocalDate.now().toString()
        val usageKey = "offline_reward_used_${taskId}_$dayKey"
        val usedForTask = offlineAttemptQueue.getInt(usageKey, 0).coerceAtLeast(0)
        val cap = task.optInt("daily_cap_minutes", 0).coerceAtLeast(0)
        val perCorrect = task.optInt("reward_minutes", 0).coerceAtLeast(0)
        val award = minOf(correct * perCorrect, (cap - usedForTask).coerceAtLeast(0))
        offlineAttemptQueue.edit().putInt(usageKey, usedForTask + award).commit()
        val rewardToBank = task.optBoolean("reward_to_bank", false)
        if (rewardToBank && award > 0) {
            val childId = task.optString("child_id")
            if (childId.isNotBlank()) {
                val pendingKey = offlinePendingBankCreditKey(childId)
                val pending = offlineAttemptQueue.getInt(pendingKey, 0).coerceAtLeast(0)
                offlineAttemptQueue.edit().putInt(pendingKey, (pending + award).coerceAtMost(Int.MAX_VALUE)).commit()
            }
        }
        return TaskResult(score, passed, award, usedForTask + award, cap,
            rewardToBank, pendingSync = true)
    }

    private fun normalizeOfflineAnswer(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun enqueueOfflineAttempt(body: JSONObject) {
        synchronized(offlineAttemptQueue) {
            val current = try {
                JSONArray(offlineAttemptQueue.getString("pending", "[]"))
            } catch (_: Exception) { JSONArray() }
            val taskId = body.optString("task_id")
            // A task can only have one pending completion; replace the queued draft
            // rather than accidentally granting duplicate rewards on retries.
            val next = JSONArray()
            for (i in 0 until current.length()) {
                val item = current.optJSONObject(i) ?: continue
                if (item.optString("task_id") != taskId) next.put(item)
            }
            next.put(body)
            offlineAttemptQueue.edit().putString("pending", next.toString()).commit()
        }
    }

    suspend fun syncPendingOfflineAttempts(): Int {
        requireSession("child")
        synchronized(offlineAttemptQueue) {
            // Read under lock; network work happens below.
        }
        val current = try {
            JSONArray(offlineAttemptQueue.getString("pending", "[]"))
        } catch (_: Exception) { JSONArray() }
        if (current.length() == 0) return 0
        val remaining = JSONArray()
        var synced = 0
        for (i in 0 until current.length()) {
            val item = current.optJSONObject(i) ?: continue
            try {
                val result = requestFunction("submit-task-attempt", item)
                // The server has now applied the authoritative award; remove the
                // provisional local credit so the next online balance refresh is exact.
                val childId = result.optString("child_id")
                if (childId.isNotBlank()) {
                    val key = offlinePendingBankCreditKey(childId)
                    offlineAttemptQueue.edit().putInt(key, 0).commit()
                }
                synced++
            } catch (error: IOException) {
                remaining.put(item)
            } catch (error: Exception) {
                // Keep rejected items for review instead of silently losing a child's work.
                remaining.put(item)
            }
        }
        offlineAttemptQueue.edit().putString("pending", remaining.toString()).commit()
        return synced
    }

    fun pendingOfflineAttemptCount(): Int = try {
        JSONArray(offlineAttemptQueue.getString("pending", "[]")).length()
    } catch (_: Exception) { 0 }

    /** Load recent attempts for parent progress/report screens. */
    suspend fun loadAttempts(): List<AttemptRow> {
        requireSession("parent")
        val rows = restArray(
            "attempts?select=id,task_id,child_id,score_percent,passed,awarded_minutes,completed_at&order=completed_at.desc&limit=1000"
        )
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            AttemptRow(
                id = row.optString("id"),
                taskId = row.optString("task_id"),
                childId = row.optString("child_id"),
                scorePercent = row.optInt("score_percent", 0).coerceIn(0, 100),
                passed = row.optBoolean("passed", false),
                awardedMinutes = row.optInt("awarded_minutes", 0).coerceAtLeast(0),
                completedAt = row.optString("completed_at").takeIf { it.isNotBlank() && it != "null" } ?: ""
            )
        }
    }


    /** Separate practical-task portal backed by the adaptive learning schema. */
    suspend fun loadPracticalTasksForParent(familyId: String, childId: String? = null): List<PracticalTaskRow> {
        requireSession("parent")
        require(familyId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Select a family first." }
        val childFilter = childId?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) }
            ?.let { "&child_id=eq.$it" } ?: ""
        val rows = restArray(
            "practical_tasks?select=id,child_id,title,instructions,materials,safety_notes,age_min,age_max,activity_kind,expected_minutes&family_id=eq.$familyId$childFilter&order=created_at.desc&limit=100"
        )
        return (0 until rows.length()).mapNotNull { index ->
            rows.optJSONObject(index)?.let { row ->
                PracticalTaskRow(
                    id = row.optString("id"), childId = row.optString("child_id"),
                    title = row.optString("title"), instructions = row.optString("instructions"),
                    materialsJson = row.optJSONArray("materials")?.toString() ?: "[]",
                    safetyNotes = row.optString("safety_notes"),
                    ageMin = row.optInt("age_min",3).coerceIn(3,19),
                    ageMax = row.optInt("age_max",19).coerceIn(3,19),
                    activityKind = row.optString("activity_kind","practical"),
                    expectedMinutes = row.optInt("expected_minutes",10).coerceIn(1,180)
                )
            }
        }
    }

    suspend fun createPracticalTask(
        familyId: String, childId: String, title: String, instructions: String,
        materialsJson: String = "[]", safetyNotes: String = "", activityKind: String = "practical",
        ageMin: Int = 3, ageMax: Int = 19, expectedMinutes: Int = 10, subjectId: String? = null
    ): PracticalTaskRow {
        requireSession("parent")
        require(familyId.matches(Regex("[0-9a-fA-F-]{36}")) &&
            childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Select a valid child profile." }
        require(title.trim().length in 1..160) { "Enter a practical-task title." }
        require(instructions.trim().length in 1..5000) { "Enter activity instructions." }
        require(activityKind in setOf("practical","physical","life_skill","creative")) { "Choose a supported activity type." }
        require(ageMin in 3..19 && ageMax in ageMin..19) { "Choose a valid age range." }
        require(expectedMinutes in 1..180) { "Duration must be 1–180 minutes." }
        val materials = try { JSONArray(materialsJson.ifBlank { "[]" }) } catch (_: Exception) {
            throw IllegalArgumentException("Materials must be a JSON array, for example [paper, pencil].")
        }
        val body = JSONObject()
            .put("family_id",familyId)
            .put("child_id",childId)
            .put("title",title.trim())
            .put("instructions",instructions.trim())
            .put("materials",materials)
            .put("safety_notes",safetyNotes.trim().take(1000))
            .put("activity_kind",activityKind)
            .put("age_min",ageMin)
            .put("age_max",ageMax)
            .put("expected_minutes",expectedMinutes)
            .put("active",true)
        if (!subjectId.isNullOrBlank() && subjectId.matches(Regex("[0-9a-fA-F-]{36}"))) body.put("subject_id",subjectId)
        val rows=restArray("practical_tasks?select=id,child_id,title,instructions,materials,safety_notes,age_min,age_max,activity_kind,expected_minutes","POST",body)
        val row=rows.optJSONObject(0) ?: throw IOException("Practical task was not saved.")
        return PracticalTaskRow(
            id=row.optString("id"), childId=row.optString("child_id"),
            title=row.optString("title"), instructions=row.optString("instructions"),
            materialsJson=row.optJSONArray("materials")?.toString() ?: "[]",
            safetyNotes=row.optString("safety_notes"), ageMin=row.optInt("age_min",3),
            ageMax=row.optInt("age_max",19), activityKind=row.optString("activity_kind","practical"),
            expectedMinutes=row.optInt("expected_minutes",10)
        )
    }

    suspend fun loadPracticalTasks(childId: String): List<PracticalTaskRow> {
        requireSession("child")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        val rows = restArray(
            "practical_tasks?select=id,child_id,title,instructions,materials,safety_notes,age_min,age_max,activity_kind,expected_minutes&child_id=eq.$childId&active=eq.true&order=created_at.desc&limit=50"
        )
        return (0 until rows.length()).mapNotNull { index ->
            rows.optJSONObject(index)?.let { row ->
                PracticalTaskRow(
                    id = row.optString("id"),
                    childId = row.optString("child_id"),
                    title = row.optString("title"),
                    instructions = row.optString("instructions"),
                    materialsJson = row.optJSONArray("materials")?.toString() ?: "[]",
                    safetyNotes = row.optString("safety_notes"),
                    ageMin = row.optInt("age_min", 3).coerceIn(3, 19),
                    ageMax = row.optInt("age_max", 19).coerceIn(3, 19),
                    activityKind = row.optString("activity_kind", "practical"),
                    expectedMinutes = row.optInt("expected_minutes", 10).coerceIn(1, 180)
                )
            }
        }
    }

    /** Store a practical completion; task ownership is validated by the server. */
    suspend fun submitPracticalTask(
        practicalTaskId: String,
        completionNote: String = "",
        evidencePath: String? = null
    ): PracticalTaskSubmission {
        requireSession("child")
        require(practicalTaskId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid practical task." }
        require(completionNote.length <= 2000) { "Your note must be 2,000 characters or fewer." }
        require(evidencePath.isNullOrBlank() || evidencePath.length <= 500) { "Invalid evidence path." }
        val response = requestFunction(
            "submit-practical-task",
            JSONObject()
                .put("practical_task_id", practicalTaskId)
                .put("completion_note", completionNote.trim())
                .apply { if (!evidencePath.isNullOrBlank()) put("evidence_path", evidencePath) }
        )
        val submission = response.optJSONObject("submission")
            ?: throw IOException("EduLock did not confirm the practical task submission.")
        val id = submission.optString("id")
        require(id.matches(Regex("[0-9a-fA-F-]{36}"))) {
            "EduLock returned an invalid practical-task submission."
        }
        return PracticalTaskSubmission(
            id = id,
            status = submission.optString("status", "submitted"),
            submittedAt = submission.optString("submitted_at")
        )
    }

    /** Per-answer written feedback visible to the linked child and its parent. */
    suspend fun loadWrittenAnswerFeedback(childId: String? = null): List<WrittenAnswerFeedback> {
        requireSession()
        val currentSession = sessionStore.read() ?: throw IOException("Sign in to view learning feedback.")
        val resolvedChildId = childId ?: if (currentSession.accountRole == "child") {
            restArray("children?select=id&child_user_id=eq.${currentSession.userId}&limit=1")
                .optJSONObject(0)?.optString("id")
        } else null
        if (resolvedChildId.isNullOrBlank() ||
            !resolvedChildId.matches(Regex("[0-9a-fA-F-]{36}"))) {
            throw IOException("Choose a child profile to view written-answer feedback.")
        }
        if (currentSession.accountRole == "child") {
            val linkedChildId = restArray("children?select=id&child_user_id=eq.${currentSession.userId}&limit=1")
                .optJSONObject(0)?.optString("id")
            require(linkedChildId == resolvedChildId) { "A child can only view their own learning feedback." }
        }
        val rows = restArray(
            "attempt_answer_feedback?select=id,attempt_id,question_id,child_id,concept_key,supplied_answer,expected_answer,accuracy_score,spelling_score,final_score,detected_errors,feedback,created_at&child_id=eq.$resolvedChildId&order=created_at.desc&limit=200"
        )
        return (0 until rows.length()).mapNotNull { index ->
            rows.optJSONObject(index)?.let { row ->
                WrittenAnswerFeedback(
                    id = row.optString("id"),
                    attemptId = row.optString("attempt_id"),
                    questionId = row.optString("question_id"),
                    childId = row.optString("child_id"),
                    conceptKey = row.optString("concept_key", "general"),
                    suppliedAnswer = row.optString("supplied_answer"),
                    expectedAnswer = row.optString("expected_answer"),
                    accuracyScore = row.optInt("accuracy_score").coerceIn(0, 100),
                    spellingScore = row.optInt("spelling_score").coerceIn(0, 100),
                    finalScore = row.optInt("final_score").coerceIn(0, 100),
                    detectedErrorsJson = row.optJSONArray("detected_errors")?.toString() ?: "[]",
                    feedback = row.optString("feedback"),
                    createdAt = row.optString("created_at")
                )
            }
        }
    }

    suspend fun loadPhysicalChallenges(familyId: String, childId: String? = null): List<PhysicalChallenge> {
        val session = sessionStore.read() ?: throw IOException("Sign in to view challenges.")
        val path = if (session.accountRole == "parent") {
            "physical_challenges?select=id,family_id,child_id,title,description,reward_minutes,evidence_type,active&family_id=eq.$familyId&order=created_at.desc"
        } else {
            "physical_challenges?select=id,family_id,child_id,title,description,reward_minutes,evidence_type,active&active=eq.true&order=created_at.desc"
        }
        val rows = restArray(path)
        return (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                PhysicalChallenge(row.optString("id"), row.optString("family_id"),
                    row.optString("child_id").takeIf { it.isNotBlank() && it != "null" },
                    row.optString("title"), row.optString("description"),
                    row.optInt("reward_minutes").coerceIn(0,1440), row.optString("evidence_type","either"),
                    row.optBoolean("active",true))
            }
        }.filter { session.accountRole != "child" || (it.active && (it.childId == null || it.childId == childId)) }
    }

    suspend fun createPhysicalChallenge(
        familyId: String, childId: String?, title: String, description: String,
        rewardMinutes: Int, evidenceType: String
    ): PhysicalChallenge {
        requireSession("parent")
        require(title.trim().length in 2..120) { "Enter a challenge title (2–120 characters)." }
        require(description.length <= 1000) { "Challenge instructions are too long." }
        require(rewardMinutes in 0..1440) { "Reward must be 0–1440 minutes." }
        require(evidenceType in setOf("photo","video","either")) { "Choose photo, video or either." }
        val userId = sessionStore.read()?.userId ?: throw IOException("Please sign in again.")
        val body = JSONObject().put("family_id",familyId).put("title",title.trim())
            .put("description",description.trim()).put("reward_minutes",rewardMinutes)
            .put("evidence_type",evidenceType).put("active",true).put("created_by",userId)
        if (!childId.isNullOrBlank()) body.put("child_id",childId)
        val rows = restArray("physical_challenges?select=id,family_id,child_id,title,description,reward_minutes,evidence_type,active","POST",body)
        val row = rows.optJSONObject(0) ?: throw IOException("Challenge could not be saved.")
        return PhysicalChallenge(row.optString("id"),row.optString("family_id"),
            row.optString("child_id").takeIf { it.isNotBlank() && it!="null" },row.optString("title"),
            row.optString("description"),row.optInt("reward_minutes"),row.optString("evidence_type"),row.optBoolean("active"))
    }

    suspend fun loadChallengeSubmissions(familyId: String, childId: String? = null): List<ChallengeSubmission> {
        requireSession()
        val session = sessionStore.read() ?: throw IOException("Sign in to view submissions.")
        val path = if (session.accountRole == "parent") {
            "physical_challenge_submissions?select=id,challenge_id,child_id,evidence_type,mime_type,media_base64,note,status,reward_minutes_awarded,review_note,submitted_at,physical_challenges!inner(title,description,family_id)&physical_challenges.family_id=eq.$familyId&order=submitted_at.desc&limit=5"
        } else {
            "physical_challenge_submissions?select=id,challenge_id,child_id,evidence_type,mime_type,media_base64,note,status,reward_minutes_awarded,review_note,submitted_at,physical_challenges!inner(title,description)&child_id=eq.${childId ?: ""}&order=submitted_at.desc&limit=5"
        }
        val rows=restArray(path)
        return (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                val challenge=row.optJSONObject("physical_challenges") ?: JSONObject()
                ChallengeSubmission(row.optString("id"),row.optString("challenge_id"),row.optString("child_id"),
                    challenge.optString("title","Physical challenge"),challenge.optString("description"),
                    row.optString("evidence_type"),row.optString("mime_type"),row.optString("media_base64"),
                    row.optString("note"),row.optString("status"),row.optInt("reward_minutes_awarded"),
                    row.optString("review_note"),row.optString("submitted_at"))
            }
        }
    }

    suspend fun submitChallengeEvidence(
        challengeId: String, childId: String, evidenceType: String, mimeType: String,
        mediaBase64: String, compressedBytes: Int, note: String
    ) {
        requireSession("child")
        require(challengeId.matches(Regex("[0-9a-fA-F-]{36}")) && childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid challenge." }
        require(evidenceType in setOf("photo","video") && mimeType in setOf("image/jpeg","video/mp4")) { "Choose a supported photo or MP4 video." }
        require(mediaBase64.length in 100..2_500_000 && compressedBytes in 1..1_800_000) { "The compressed evidence must be smaller than 1.8 MB. Please choose a shorter video or smaller photo." }
        require(note.length <= 500) { "Your note is too long." }
        val body=JSONObject().put("challenge_id",challengeId).put("child_id",childId)
            .put("evidence_type",evidenceType).put("mime_type",mimeType)
            .put("media_base64",mediaBase64).put("compressed_size_bytes",compressedBytes)
            .put("note",note.trim()).put("status","submitted").put("reward_minutes_awarded",0)
        restArray("physical_challenge_submissions?select=id","POST",body)
    }

    suspend fun reviewChallengeSubmission(submissionId: String, approve: Boolean, note: String = ""): JSONObject {
        requireSession("parent")
        require(submissionId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid submission." }
        return requestFunction("review-physical-challenge",JSONObject().put("submission_id",submissionId)
            .put("approve",approve).put("review_note",note.take(500)))
    }

    suspend fun setQuestionReward(questionId: String, minutes: Int?) {
        requireSession("parent")
        require((minutes == null || minutes in 0..1440) && questionId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Reward must be blank (use task default) or 0–1440 minutes." }
        val changed = restArray(
            "questions?id=eq.$questionId",
            "PATCH",
            JSONObject().put("reward_minutes", minutes ?: JSONObject.NULL)
        )
        val row = changed.optJSONObject(0)
        require(
            changed.length() == 1 && row != null && row.optString("id") == questionId &&
                (if (minutes == null) row.isNull("reward_minutes") else row.optInt("reward_minutes", -1) == minutes)
        ) {
            "Supabase did not confirm that this question reward was saved. Check your connection and parent permissions, then try again."
        }
    }

    suspend fun deleteQuestion(questionId: String) {
        requireSession("parent")
        require(questionId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid question." }
        // Request the deleted row so RLS-filtered no-op deletes cannot be reported as success.
        val deleted = restArray("questions?id=eq.$questionId", "DELETE")
        require((0 until deleted.length()).any { deleted.optJSONObject(it)?.optString("id") == questionId }) {
            "Supabase did not confirm that this question was removed. Check your connection and parent permissions, then try again."
        }
    }

    suspend fun loadQuestionRewardGroups(subjectId: String): List<QuestionRewardGroup> {
        requireSession("parent")
        val rows=restArray("question_reward_groups?select=id,subject_id,concept_key,reward_minutes&subject_id=eq.$subjectId&order=concept_key")
        return (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.let {
            QuestionRewardGroup(it.optString("id"),it.optString("subject_id"),it.optString("concept_key"),it.optInt("reward_minutes"))
        } }
    }

    suspend fun saveQuestionRewardGroup(subjectId: String, conceptKey: String, minutes: Int) {
        requireSession("parent")
        require(subjectId.matches(Regex("[0-9a-fA-F-]{36}")) && conceptKey.isNotBlank() && conceptKey.length<=120 && minutes in 0..1440) {
            "Enter a valid concept group and reward (0–1440 minutes)."
        }
        val expectedConcept = conceptKey.trim()
        val saved = restArray(
            "question_reward_groups?on_conflict=subject_id,concept_key",
            "POST",
            JSONObject().put("subject_id",subjectId).put("concept_key",expectedConcept).put("reward_minutes",minutes)
                .put("updated_at",java.time.Instant.now().toString()),
            prefer="resolution=merge-duplicates,return=representation"
        )
        val row = saved.optJSONObject(0)
        require(
            saved.length() == 1 && row != null &&
                row.optString("subject_id") == subjectId &&
                row.optString("concept_key") == expectedConcept && row.optInt("reward_minutes", -1) == minutes
        ) {
            "Supabase did not confirm that the group bonus was saved. Check your connection and parent permissions, then try again."
        }
    }


    suspend fun loadChildDailyTaskSettings(childId: String): DailyTaskSettings {
        requireSession("parent")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        val row = restArray(
            "child_daily_task_settings?select=child_id,enabled,daily_task_count,questions_per_task,pass_percent,reward_minutes,daily_cap_minutes&child_id=eq.$childId&limit=1"
        ).optJSONObject(0)
        return DailyTaskSettings(
            childId = childId,
            enabled = row?.optBoolean("enabled", false) ?: false,
            dailyTaskCount = (row?.optInt("daily_task_count", 1) ?: 1).coerceIn(1, 5),
            questionsPerTask = (row?.optInt("questions_per_task", 4) ?: 4).coerceIn(1, 20),
            passPercent = (row?.optInt("pass_percent", 70) ?: 70).coerceIn(0, 100),
            rewardMinutes = (row?.optInt("reward_minutes", 1) ?: 1).coerceIn(0, 60),
            dailyCapMinutes = (row?.optInt("daily_cap_minutes", 30) ?: 30).coerceIn(0, 1440)
        )
    }

    suspend fun saveChildDailyTaskSettings(settings: DailyTaskSettings) {
        requireSession("parent")
        require(settings.childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        require(settings.dailyTaskCount in 1..5) { "Daily task count must be from 1 to 5." }
        require(settings.questionsPerTask in 1..20) { "Questions per task must be from 1 to 20." }
        require(settings.passPercent in 0..100) { "Pass score must be from 0 to 100 percent." }
        require(settings.rewardMinutes in 0..60) { "Reward per correct answer must be from 0 to 60 minutes." }
        require(settings.dailyCapMinutes in 0..1440) { "Daily task reward cap must be from 0 to 1440 minutes." }
        val payload = JSONObject()
            .put("child_id", settings.childId)
            .put("enabled", settings.enabled)
            .put("daily_task_count", settings.dailyTaskCount)
            .put("questions_per_task", settings.questionsPerTask)
            .put("pass_percent", settings.passPercent)
            .put("reward_minutes", settings.rewardMinutes)
            .put("daily_cap_minutes", settings.dailyCapMinutes)
            .put("updated_at", java.time.Instant.now().toString())
        val saved = restArray(
            "child_daily_task_settings?on_conflict=child_id",
            "POST", payload, prefer = "resolution=merge-duplicates,return=representation"
        ).optJSONObject(0) ?: throw IOException("Supabase did not confirm the daily task settings were saved.")
        require(saved.optString("child_id") == settings.childId &&
            saved.optBoolean("enabled", !settings.enabled) == settings.enabled &&
            saved.optInt("daily_task_count", -1) == settings.dailyTaskCount &&
            saved.optInt("questions_per_task", -1) == settings.questionsPerTask
        ) { "Supabase returned different daily task settings. No success was reported." }
    }

    suspend fun ensureDailyTasks(): JSONObject {
        requireSession("child")
        return requestFunction("ensure-daily-tasks", JSONObject())
    }

    suspend fun loadChildTasks(
        childId: String, schoolGrade: Int? = null, ageYears: Int? = null
    ): List<TaskRow> {
        requireSession("child")
        val rows = restArray("tasks?select=id,child_id,subject_id,title,question_count,pass_percent,reward_minutes,daily_cap_minutes,active,reward_package_name,reward_group_id,time_limit_seconds,question_time_limit_seconds,reward_to_bank,target_grade_level,target_age_years&child_id=eq.$childId&active=eq.true&order=created_at.desc")
        try { syncPendingOfflineAttempts() } catch (_: Exception) { /* retain queued work until a later online read */ }
        return (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.toTaskRow() }
            .filter { task ->
                (schoolGrade == null || task.targetGradeLevel == schoolGrade) &&
                    (ageYears == null || task.targetAgeYears == ageYears)
            }
    }

    suspend fun loadQuestionsForTask(
        taskId: String, fallbackSubjectId: String?, limit: Int,
        targetGradeLevel: Int? = null, targetAgeYears: Int? = null
    ): List<QuestionRow> {
        requireSession("child")
        require(taskId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid task." }
        fun matchesChildScope(question: QuestionRow): Boolean =
            (targetGradeLevel == null || question.gradeLevel == targetGradeLevel) &&
                (targetAgeYears == null || targetAgeYears in question.ageMin..question.ageMax)
        val safeLimit = limit.coerceIn(1, 500)
        val links = restArray("task_questions?select=question_id,sort_order&task_id=eq.$taskId&order=sort_order.asc")
        val ids = (0 until links.length()).mapNotNull { index ->
            links.optJSONObject(index)?.optString("question_id")?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) }
        }.take(500)
        if (ids.isEmpty()) {
            val subjectQuestions = fallbackSubjectId?.let { loadQuestions(it, 500) } ?: emptyList()
            return subjectQuestions.filter(::matchesChildScope).take(safeLimit)
        }
        val byId = linkedMapOf<String, QuestionRow>()
        ids.chunked(50).forEach { chunk ->
            val rows = restArray("questions?select=id,subject_id,prompt,question_type,options,language_code,concept_key,picture_path,grade_level,age_min,age_max,reward_minutes,answer_key,created_at&id=in.(" +
                chunk.joinToString(",") + ")&limit=50")
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val question = row.toQuestionRow()
                if (matchesChildScope(question)) byId[question.id] = question
            }
        }
        val result = ids.mapNotNull { byId[it] }.take(safeLimit)
        try {
            val packageJson = requestFunction("offline-task-package", JSONObject().put("task_id", taskId))
            offlineReadCache.edit().putString(offlineTaskPackageKey(taskId), packageJson.toString()).commit()
        } catch (_: Exception) { /* task view still works if prefetch is temporarily unavailable */ }
        result
    }

    private fun offlineTaskPackageKey(taskId: String): String {
        val userId = sessionStore.read()?.userId ?: "signed-out"
        return "offline_task_package_${userId}_${taskId}"
    }

    private fun offlineRewardBankKey(childId: String) = "offline_reward_bank_${childId}"
    private fun offlinePendingBankCreditKey(childId: String) = "offline_reward_pending_credit_${childId}"


    suspend fun loadChildLocationSnapshot(childId: String): ChildLocationSnapshot {
        requireSession("parent")
        val locations = restArray(
            "child_locations?select=child_id,latitude,longitude,accuracy_meters,recorded_at&child_id=eq.$childId&limit=1"
        )
        val statuses = try {
            restArray(
                "child_device_status?select=child_id,last_seen_at,sharing_enabled,battery_percent,device_locked,guard_mode,current_app_package,screen_on,blocked_app_events&child_id=eq.$childId&limit=1"
            )
        } catch (_: IOException) {
            // Support backend installations before migration 0033.
            restArray(
                "child_device_status?select=child_id,last_seen_at,sharing_enabled,battery_percent&child_id=eq.$childId&limit=1"
            )
        }
        val point = locations.optJSONObject(0)
        val status = statuses.optJSONObject(0)
        val latitude = if (point != null && !point.isNull("latitude")) point.optDouble("latitude").takeIf { it.isFinite() } else null
        val longitude = if (point != null && !point.isNull("longitude")) point.optDouble("longitude").takeIf { it.isFinite() } else null
        val accuracy = if (point != null && !point.isNull("accuracy_meters")) point.optDouble("accuracy_meters").takeIf { it.isFinite() && it >= 0 } else null
        return ChildLocationSnapshot(
            childId = childId,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracy,
            recordedAt = point?.optString("recorded_at")?.takeIf { !it.isNullOrBlank() && it != "null" },
            lastSeenAt = status?.optString("last_seen_at")?.takeIf { !it.isNullOrBlank() && it != "null" },
            sharingEnabled = status?.optBoolean("sharing_enabled", false) ?: false,
            batteryPercent = if (status != null && !status.isNull("battery_percent")) status.optInt("battery_percent").coerceIn(0, 100) else null,
            deviceLocked = if (status != null && !status.isNull("device_locked")) status.optBoolean("device_locked") else null,
            guardMode = status?.optString("guard_mode")?.takeIf { it.isNotBlank() && it != "null" },
            currentAppPackage = status?.optString("current_app_package")?.takeIf { it.isNotBlank() && it != "null" },
            screenOn = if (status != null && !status.isNull("screen_on")) status.optBoolean("screen_on") else null,
            blockedAppEvents = status?.optInt("blocked_app_events", 0)?.coerceAtLeast(0) ?: 0
        )
    }

    suspend fun syncInstalledApps(childId: String, apps: List<Pair<String, String>>) {
        requireSession("child")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        val normalized = apps.mapNotNull { (packageName, displayName) ->
            val pkg = packageName.trim()
            val name = displayName.trim().take(120)
            if (pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) && name.isNotBlank())
                pkg to name else null
        }.distinctBy { it.first }.take(500)
        if (normalized.isEmpty()) return
        val fingerprintInput = normalized.sortedBy { it.first }
            .joinToString("\n") { (pkg, name) -> "$pkg\t$name" }
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256")
            .digest(fingerprintInput.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        val inventoryPrefs = appContext.getSharedPreferences("edulock_app_inventory", Context.MODE_PRIVATE)
        val hashKey = "uploaded_inventory_hash_$childId"
        if (inventoryPrefs.getString(hashKey, null) == fingerprint) return
        val timestamp = java.time.Instant.now().toString()
        val payload = JSONArray()
        normalized.forEach { (pkg, name) ->
            payload.put(JSONObject().put("child_id", childId).put("package_name", pkg)
                .put("display_name", name).put("last_seen_at", timestamp))
        }
        // Replace the child's inventory snapshot so older background/system-app rows disappear.
        // Allowed-app rules are kept separately; Settings is the only system app submitted by the client.
        restArray("child_installed_apps?child_id=eq.$childId", "DELETE", prefer = "return=minimal")
        val uploaded = restArray("child_installed_apps?on_conflict=child_id,package_name", "POST", payload,
            prefer = "resolution=merge-duplicates,return=representation")
        if (uploaded.length() < normalized.size) {
            throw IOException("Supabase accepted only ${uploaded.length()} of ${normalized.size} installed apps. The inventory will retry on the next sync.")
        }
        inventoryPrefs.edit().putString(hashKey, fingerprint).apply()
    }
    suspend fun loadInstalledApps(childId: String): List<ChildInstalledApp> {
        requireSession("parent")
        val rows = restArray("child_installed_apps?select=child_id,package_name,display_name,last_seen_at&child_id=eq.$childId&order=display_name.asc")
        return (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                ChildInstalledApp(
                    row.optString("child_id"), row.optString("package_name"),
                    row.optString("display_name"),
                    row.optString("last_seen_at").takeIf { it.isNotBlank() && it != "null" }
                )
            }
        }
    }

    suspend fun allowInstalledApp(childId: String, app: ChildInstalledApp, limitMinutes: Int) {
        requireSession("parent")
        require(limitMinutes in 0..1440) { "Daily app limit must be between 0 and 1440 minutes." }
        val existing = restArray("allowed_apps?select=id&child_id=eq.$childId&package_name=eq.${app.packageName}")
        if (existing.length() > 0) {
            val id = existing.optJSONObject(0)?.optString("id")
            if (!id.isNullOrBlank()) {
                restArray("allowed_apps?id=eq.$id", "PATCH", JSONObject().put("enabled", true).put("daily_limit_minutes", limitMinutes))
            } else {
                throw IOException("Supabase returned an invalid app-rule id. The rule was not reported as saved.")
            }
        } else {
            addAllowedApp(childId, app.packageName, app.displayName, limitMinutes)
        }
        val confirmed = loadAllowedApps(childId).firstOrNull { it.packageName == app.packageName }
        if (confirmed == null || !confirmed.enabled || confirmed.dailyLimitMinutes != limitMinutes) {
            throw IOException("Supabase did not confirm that ${app.displayName} is enabled with a $limitMinutes-minute limit.")
        }
    }

    suspend fun loadChildTaskStreak(childId: String): Pair<Int, Int> {
        requireSession("child")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        return try {
            val rows = restArray("child_task_streaks?select=consecutive_days,last_passed_day&child_id=eq.$childId&limit=1")
            val row = rows.optJSONObject(0) ?: return 0 to 0
            row.optInt("consecutive_days", 0).coerceIn(0, 30) to 0
        } catch (_: IOException) {
            // The pre-0033 app remains usable; the streak card simply shows no recorded streak.
            0 to 0
        }
    }

    suspend fun loadAllowedApps(childId: String): List<AllowedApp> {
        requireSession()
        val rows = restArray("allowed_apps?select=id,child_id,package_name,display_name,enabled,daily_limit_minutes,weekly_schedule&child_id=eq.$childId&order=display_name.asc")
        val loaded = (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                val packageName = row.optString("package_name").trim()
                if (packageName.isBlank()) return@let null
                AllowedApp(
                    id = row.optString("id"),
                    childId = row.optString("child_id"),
                    packageName = packageName,
                    displayName = row.optString("display_name").ifBlank { packageName.substringAfterLast('.') },
                    enabled = row.optBoolean("enabled", true),
                    dailyLimitMinutes = row.optInt("daily_limit_minutes", 0).coerceIn(0, 1440),
                    weeklyScheduleJson = row.optJSONObject("weekly_schedule")?.toString() ?: "{}"
                )
            }
        }
        // Legacy installs can contain duplicate rows for the same Android package.
        // Expose one stable rule per package so lists and reward selection never duplicate apps.
        return loaded.groupBy { it.packageName.lowercase(java.util.Locale.ROOT) }
            .values.map { duplicates ->
                val enabledRules = duplicates.filter { it.enabled }
                val candidates = if (enabledRules.isNotEmpty()) enabledRules else duplicates
                val chosen = candidates.minByOrNull { if (it.dailyLimitMinutes <= 0) Int.MAX_VALUE else it.dailyLimitMinutes }
                    ?: duplicates.first()
                chosen.copy(
                    enabled = enabledRules.isNotEmpty(),
                    dailyLimitMinutes = if (enabledRules.isEmpty()) chosen.dailyLimitMinutes
                        else enabledRules.map { it.dailyLimitMinutes }.filter { it > 0 }.minOrNull()
                            ?: enabledRules.first().dailyLimitMinutes
                )
            }
            .sortedWith(compareBy<AllowedApp> { it.displayName.lowercase(java.util.Locale.ROOT) }.thenBy { it.packageName })
    }

    suspend fun addAllowedApp(childId: String, packageName: String, displayName: String, dailyLimitMinutes: Int) {
        requireSession("parent")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        require(dailyLimitMinutes in 0..1440) { "Daily app limit must be between 0 and 1440 minutes." }
        val normalizedPackage = packageName.trim()
        require(normalizedPackage.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
            "Enter a valid Android package name, for example com.example.app"
        }
        require(displayName.trim().isNotEmpty()) { "Enter the app's display name" }
        restArray("allowed_apps?select=id", "POST",
            JSONObject().put("child_id", childId).put("package_name", normalizedPackage)
                .put("display_name", displayName.trim().take(120)).put("enabled", true)
                .put("daily_limit_minutes", dailyLimitMinutes))
        val confirmed = loadAllowedApps(childId).firstOrNull { it.packageName == normalizedPackage }
        if (confirmed == null || !confirmed.enabled || confirmed.dailyLimitMinutes != dailyLimitMinutes) {
            throw IOException("Supabase did not confirm that $displayName was added as an enabled app.")
        }
    }

    suspend fun updateAllowedAppLimit(appId: String, dailyLimitMinutes: Int) {
        requireSession("parent")
        require(appId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid app rule." }
        require(dailyLimitMinutes in 0..1440) { "Daily app limit must be between 0 and 1440 minutes." }
        restArray("allowed_apps?id=eq.$appId", "PATCH", JSONObject().put("daily_limit_minutes", dailyLimitMinutes))
        val row = restArray("allowed_apps?select=id,daily_limit_minutes&id=eq.$appId&limit=1").optJSONObject(0)
            ?: throw IOException("Supabase did not confirm the app daily limit was saved.")
        if (row.optInt("daily_limit_minutes", -1) != dailyLimitMinutes) {
            throw IOException("Supabase returned a different app daily limit. No success was reported.")
        }
    }

    suspend fun updateAllowedAppEnabled(appId: String, enabled: Boolean) {
        requireSession("parent")
        require(appId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid app rule." }
        restArray("allowed_apps?id=eq.$appId", "PATCH", JSONObject().put("enabled", enabled))
        val row = restArray("allowed_apps?select=id,enabled&id=eq.$appId&limit=1").optJSONObject(0)
            ?: throw IOException("Supabase did not confirm the app access change.")
        if (row.optBoolean("enabled", !enabled) != enabled) {
            throw IOException("Supabase returned a different app access setting. No success was reported.")
        }
    }

    suspend fun loadAppGroups(childId: String): List<AppGroup> {
        requireSession()
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        val rows = restArray("app_groups?select=id,child_id,name&child_id=eq.$childId&order=name.asc")
        val memberships = restArray("app_group_apps?select=group_id,package_name&child_id=eq.$childId")
        val packagesByGroup = linkedMapOf<String, MutableSet<String>>()
        for (i in 0 until memberships.length()) {
            val row = memberships.optJSONObject(i) ?: continue
            val groupId = row.optString("group_id")
            val packageName = row.optString("package_name")
            if (groupId.isNotBlank() && packageName.isNotBlank()) packagesByGroup.getOrPut(groupId) { linkedSetOf() }.add(packageName)
        }
        return (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                val id = row.optString("id")
                AppGroup(id, row.optString("child_id"), row.optString("name"), packagesByGroup[id].orEmpty())
            }
        }
    }

    suspend fun saveAppGroup(childId: String, name: String, packageNames: Set<String>, groupId: String? = null): AppGroup {
        requireSession("parent")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        val normalizedName = name.trim().take(80)
        require(normalizedName.isNotBlank()) { "Enter a name for the app group." }
        val packages = packageNames.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        require(packages.isNotEmpty()) { "Select at least one enabled app for this group." }
        require(packages.size <= 50) { "An app group can contain up to 50 apps." }
        val enabledPackages = loadAllowedApps(childId).filter { it.enabled }.map { it.packageName }.toSet()
        require(packages.all { it in enabledPackages }) { "Every group app must have an enabled parent-approved app rule." }
        val savedId = if (groupId.isNullOrBlank()) {
            val rows = restArray("app_groups?select=id", "POST", JSONObject().put("child_id", childId).put("name", normalizedName))
            rows.optJSONObject(0)?.optString("id")?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) }
                ?: throw IOException("Supabase did not return the app group ID.")
        } else {
            require(groupId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid app group." }
            restArray("app_groups?id=eq.$groupId", "PATCH", JSONObject().put("name", normalizedName))
            groupId
        }
        val packageFilter = packages.joinToString(",")
        restArray("app_group_apps?child_id=eq.$childId&package_name=in.($packageFilter)&group_id=neq.$savedId", "DELETE")
        restArray("app_group_apps?group_id=eq.$savedId", "DELETE")
        val payload = JSONArray()
        packages.sorted().forEach { packageName ->
            payload.put(JSONObject().put("group_id", savedId).put("child_id", childId).put("package_name", packageName))
        }
        val savedMemberships = restArray("app_group_apps?select=group_id,package_name", "POST", payload)
        if (savedMemberships.length() != packages.size) {
            throw IOException("Supabase saved only ${savedMemberships.length()} of ${packages.size} apps in the group.")
        }
        val confirmed = loadAppGroups(childId).firstOrNull { it.id == savedId }
            ?: throw IOException("Supabase did not confirm the app group.")
        if (confirmed.name != normalizedName || confirmed.packageNames != packages) {
            throw IOException("Supabase returned different app-group settings. No success was reported.")
        }
        return confirmed
    }

    suspend fun deleteAppGroup(groupId: String) {
        requireSession("parent")
        require(groupId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid app group." }
        restArray("app_groups?id=eq.$groupId", "DELETE")
        val remaining = restArray("app_groups?select=id&id=eq.$groupId&limit=1")
        if (remaining.length() > 0) throw IOException("This group is still referenced by a task or could not be removed. Reassign its tasks first.")
    }

    suspend fun updateAllowedAppWeeklySchedule(appId: String, schedule: AppWeeklySchedule) {
        requireSession("parent")
        val json = schedule.toJson()
        AppWeeklySchedule.DAY_KEYS.forEach { day ->
            val window = json.optJSONObject(day) ?: throw IllegalArgumentException("Set a valid schedule for every day.")
            val start = window.optString("start")
            val end = window.optString("end")
            require(start.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))) { "Use 24-hour start time HH:mm (for example 08:30)." }
            require(end.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))) { "Use 24-hour end time HH:mm (for example 20:00)." }
            if (window.optBoolean("enabled")) require(start != end) { "Start and end time must differ for an enabled day." }
        }
        restArray("allowed_apps?id=eq.$appId", "PATCH", JSONObject().put("weekly_schedule", json))
        val saved = restArray("allowed_apps?select=id,weekly_schedule&id=eq.$appId&limit=1").optJSONObject(0)
            ?: throw IOException("Supabase did not confirm the weekly timetable was saved.")
        val storedSchedule = saved.optJSONObject("weekly_schedule")
            ?: throw IOException("Supabase returned no weekly timetable after saving.")
        for (day in AppWeeklySchedule.DAY_KEYS) {
            val expected = json.optJSONObject(day) ?: throw IllegalArgumentException("Set a valid schedule for every day.")
            val actual = storedSchedule.optJSONObject(day)
            if (actual == null ||
                actual.optBoolean("enabled") != expected.optBoolean("enabled") ||
                actual.optString("start") != expected.optString("start") ||
                actual.optString("end") != expected.optString("end")) {
                throw IOException("Supabase returned a different weekly timetable. No success was reported.")
            }
        }
    }

    suspend fun deleteAllowedApp(appId: String) {
        requireSession("parent")
        require(appId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid app rule." }
        send("rest/v1/allowed_apps?id=eq.$appId", "DELETE", null, authenticated = true, profile = true)
        val stillExists = restArray("allowed_apps?select=id&id=eq.$appId&limit=1").length() > 0
        if (stillExists) throw IOException("Supabase did not confirm removal of this app rule.")
    }

    suspend fun refreshChildDeviceRestrictions(child: ChildProfile): Int {
        requireSession("child")
        val appRules = loadAllowedApps(child.id)
        val lockdownMode = child.kidsModeEnabled
        val allowed = appRules.filter { it.enabled }.map { it.packageName }.toMutableSet()
        allowed += "com.edulock.app"
        val blocked = appRules.filter { !it.enabled }.map { it.packageName }.toSet()
        val appLimits = appRules.filter { it.enabled && it.dailyLimitMinutes > 0 }.map { it.packageName + "=" + it.dailyLimitMinutes }.toSet()
        val weeklySchedules = appRules.filter { rule ->
            try { JSONObject(rule.weeklyScheduleJson).length() > 0 } catch (_: Exception) { false }
        }.map { it.packageName + "=" + it.weeklyScheduleJson }.toSet()
        val startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC)
        val stamp = DateTimeFormatter.ISO_INSTANT.format(startOfDay)
        val ledger = restArray(
            "screen_time_ledger?select=minutes_delta,target_package_name,target_group_id&child_id=eq.${child.id}&created_at=gte.$stamp"
        )
        var generalEarned = 0
        val appEarned = linkedMapOf<String, Int>()
        val groupEarned = linkedMapOf<String, Int>()
        for (i in 0 until ledger.length()) {
            val row = ledger.optJSONObject(i) ?: continue
            val minutes = row.optInt("minutes_delta").coerceAtLeast(0)
            val targetPackage = row.optString("target_package_name").takeIf { it.isNotBlank() && it != "null" }
            val targetGroup = row.optString("target_group_id").takeIf { it.isNotBlank() && it != "null" }
            when {
                targetGroup != null -> groupEarned[targetGroup] = (groupEarned[targetGroup] ?: 0) + minutes
                targetPackage != null -> appEarned[targetPackage] = (appEarned[targetPackage] ?: 0) + minutes
                else -> generalEarned += minutes
            }
        }
        val earned = generalEarned.coerceIn(0, child.dailyCapMinutes)
        val appEarnedRules = appEarned.map { (pkg, minutes) -> "$pkg=$minutes" }.toSet()
        val activeTasks = loadChildTasks(child.id)
        val activeTaskIds = activeTasks.map { it.id }.toSet()
        val enabledPackageSet = appRules.filter { it.enabled }.map { it.packageName }.toSet()
        val groups = loadAppGroups(child.id).map { group ->
            group.copy(packageNames = group.packageNames.filter { it in enabledPackageSet }.toSet())
        }
        val groupById = groups.associateBy { it.id }
        val groupPackageRules = groups.filter { it.packageNames.isNotEmpty() }.map { group ->
            group.id + "=" + group.packageNames.sorted().joinToString(",")
        }.toSet()
        val groupEarnedRules = groupEarned.map { (id, minutes) -> "$id=$minutes" }.toSet()
        val appTaskRules = linkedMapOf<String, String>()
        activeTasks.forEach { task ->
            when {
                !task.rewardPackageName.isNullOrBlank() -> {
                    if (!appTaskRules.containsKey(task.rewardPackageName)) appTaskRules[task.rewardPackageName] = task.id
                }
                !task.rewardGroupId.isNullOrBlank() -> {
                    groupById[task.rewardGroupId]?.packageNames?.forEach { pkg ->
                        if (!appTaskRules.containsKey(pkg)) appTaskRules[pkg] = task.id
                    }
                }
            }
        }
        val generalTaskId = activeTasks.firstOrNull {
            it.rewardPackageName.isNullOrBlank() && it.rewardGroupId.isNullOrBlank()
        }?.id
        val prefs = appContext.getSharedPreferences(LOCK_PREFS, Context.MODE_PRIVATE)
        val pendingTask = prefs.getString("pending_task_id", null)
        val editor = prefs.edit()
        if (pendingTask != null && pendingTask !in activeTaskIds) editor.remove("pending_task_id")
        editor
            .putBoolean("child_active", true)
            .putString("child_id", child.id)
            .putBoolean("kids_mode_enabled", lockdownMode)
            .putStringSet("allowed_packages", allowed)
            .putStringSet("blocked_packages", blocked)
            .putStringSet("app_daily_limits", appLimits)
            .putStringSet("app_weekly_schedules", weeklySchedules)
            .putStringSet("app_earned_minutes", appEarnedRules)
            .putStringSet("app_earned_groups", groupEarnedRules)
            .putStringSet("app_group_packages", groupPackageRules)
            .putStringSet("app_task_rules", appTaskRules.map { (pkg, taskId) -> "$pkg=$taskId" }.toSet())
            .putString("general_task_id", generalTaskId)
            .putInt("earned_minutes_today", earned)
            .putInt("daily_cap_minutes", child.dailyCapMinutes)
            .putString("budget_day", LocalDate.now(ZoneOffset.UTC).toString())
            .putLong("rules_synced_at", System.currentTimeMillis())
            .apply()
        val managedPolicyApplied = ChildDeviceKioskPolicy.apply(appContext, lockdownMode, allowed)
        prefs.edit().putBoolean("lock_task_policy_applied", managedPolicyApplied).apply()
        return earned
    }
    suspend fun syncAppUsage(childId: String) {
        requireSession("child")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        val allowedApps = loadAllowedApps(childId).filter { it.enabled }.associateBy { it.packageName }
        if (allowedApps.isEmpty()) return
        val prefs = appContext.getSharedPreferences(LOCK_PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now(ZoneOffset.UTC).toString()
        val packageKeys = prefs.all.keys.filter { it.startsWith(APP_USED_PREFIX) }
        for (key in packageKeys) {
            val packageName = key.removePrefix(APP_USED_PREFIX)
            val allowed = allowedApps[packageName] ?: continue
            val usedMillis = (prefs.all[key] as? Long ?: 0L).coerceAtLeast(0L)
            val usedSeconds = (usedMillis / 1000L).coerceAtMost(86400L)
            if (usedSeconds <= 0L) continue
            val existing = restArray(
                "app_usage_daily?select=id&child_id=eq.$childId&usage_date=eq.$today&package_name=eq.$packageName&limit=1"
            )
            val payload = JSONObject()
                .put("child_id", childId)
                .put("usage_date", today)
                .put("package_name", packageName)
                .put("display_name", allowed.displayName)
                .put("used_seconds", usedSeconds)
                .put("updated_at", java.time.Instant.now().toString())
            if (existing.length() > 0) {
                restArray(
                    "app_usage_daily?child_id=eq.$childId&usage_date=eq.$today&package_name=eq.$packageName",
                    "PATCH",
                    payload
                )
            } else {
                restArray("app_usage_daily?select=id", "POST", payload)
            }
        }
    }

    suspend fun loadAppUsageReports(childId: String, days: Int = 7): List<AppUsageReport> {
        requireSession("parent")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        val startDate = LocalDate.now(ZoneOffset.UTC).minusDays((days.coerceIn(1, 30) - 1).toLong()).toString()
        val rows = restArray(
            "app_usage_daily?select=child_id,usage_date,package_name,display_name,used_seconds" +
                "&child_id=eq.$childId&usage_date=gte.$startDate&order=usage_date.desc,display_name.asc&limit=300"
        )
        return (0 until rows.length()).mapNotNull { index ->
            rows.optJSONObject(index)?.let { row ->
                AppUsageReport(
                    childId = row.optString("child_id"),
                    usageDate = row.optString("usage_date"),
                    packageName = row.optString("package_name"),
                    displayName = row.optString("display_name"),
                    usedSeconds = row.optLong("used_seconds", 0L).coerceAtLeast(0L)
                )
            }
        }
    }

    suspend fun loadCurriculumQuestions(
        childId: String, countryCode: String, gradeLevel: Int, ageYears: Int,
        subjectCode: String, languageCode: String
    ): CurriculumSearchResult {
        requireSession("parent")
        val response = requestFunction("curriculum-library", JSONObject()
            .put("child_id", childId)
            .put("country_code", countryCode.uppercase())
            .put("grade_level", gradeLevel)
            .put("age_years", ageYears)
            .put("subject_code", subjectCode.lowercase())
            .put("language_code", languageCode.lowercase()))
        val array = response.optJSONArray("questions") ?: JSONArray()
        val questions = (0 until array.length()).mapNotNull { index ->
            val row = array.optJSONObject(index) ?: return@mapNotNull null
            val options = row.optJSONArray("options") ?: JSONArray()
            CurriculumQuestion(
                id = row.optString("id"),
                countryCode = row.optString("country_code", "ZZ"),
                curriculumName = row.optString("curriculum_name", "Starter bank"),
                gradeLevel = row.optInt("grade_level", gradeLevel),
                ageMin = row.optInt("age_min", 3),
                ageMax = row.optInt("age_max", 19),
                subjectCode = row.optString("subject_code", subjectCode),
                subjectName = row.optString("subject_name", subjectCode),
                languageCode = row.optString("language_code", "en"),
                topic = row.optString("topic"),
                prompt = row.optString("prompt"),
                questionType = row.optString("question_type", "multiple_choice"),
                options = (0 until options.length()).map { options.optString(it) },
                conceptKey = row.optString("concept_key", "general"),
                sourceNote = row.optString("source_note", "Starter practice content; verify curriculum alignment.")
            )
        }
        return CurriculumSearchResult(
            questions,
            response.optBoolean("fallback_used", false),
            response.optString("message", "Select questions to add to a subject.")
        )
    }

    suspend fun importCurriculumQuestions(
        childId: String, countryCode: String, gradeLevel: Int, ageYears: Int,
        subjectCode: String, languageCode: String, questionIds: List<String>
    ): CurriculumImportResult {
        requireSession("parent")
        val ids = questionIds.distinct().filter { it.matches(Regex("[0-9a-fA-F-]{36}")) }
        require(ids.isNotEmpty() && ids.size <= 50) { "Select between 1 and 50 questions." }
        val response = requestFunction("import-curriculum-questions", JSONObject()
            .put("child_id", childId)
            .put("country_code", countryCode.uppercase())
            .put("grade_level", gradeLevel)
            .put("age_years", ageYears)
            .put("subject_code", subjectCode.lowercase())
            .put("language_code", languageCode.lowercase())
            .put("question_ids", JSONArray(ids)))
        return CurriculumImportResult(
            subjectId = response.optString("subject_id"),
            subjectTitle = response.optString("subject_title"),
            languageCode = response.optString("language_code", languageCode),
            importedCount = response.optInt("imported_count", 0),
            selectedCount = response.optInt("selected_count", ids.size),
            message = response.optString("message", "Curriculum questions imported.")
        )
    }

    private suspend fun requestFunction(name: String, body: JSONObject): JSONObject {
        val result = send("functions/v1/$name", "POST", body, authenticated = true, profile = false)
        return result as? JSONObject ?: throw IOException("The server returned an unexpected response")
    }

    private suspend fun authRequest(path: String, body: JSONObject): JSONObject {
        val result = send(path, "POST", body, authenticated = false, profile = false, allowRefresh = false)
        return result as? JSONObject ?: throw IOException("The authentication server returned an unexpected response")
    }

    private val offlineAttemptQueue by lazy {
        appContext.getSharedPreferences("edulock_offline_attempt_queue_v1", Context.MODE_PRIVATE)
    }

    private val offlineReadCache by lazy {
        appContext.getSharedPreferences("edulock_offline_read_cache_v1", Context.MODE_PRIVATE)
    }

    private fun offlineCacheKey(path: String): String {
        val userId = sessionStore.read()?.userId ?: "signed-out"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(path.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return "read_" + userId + "_" + digest
    }

    private suspend fun restArray(path: String, method: String = "GET", body: Any? = null, prefer: String = "return=representation"): JSONArray {
        val canCache = method.equals("GET", ignoreCase = true)
        val cacheKey = if (canCache) offlineCacheKey(path) else null
        try {
            val result = send("rest/v1/$path", method, body, authenticated = true, profile = true, prefer = prefer)
            val array = when (result) {
                is JSONArray -> result
                is JSONObject -> JSONArray().put(result)
                else -> JSONArray()
            }
            if (canCache && cacheKey != null) {
                offlineReadCache.edit().putString(cacheKey, array.toString()).apply()
            }
            return array
        } catch (error: IOException) {
            if (canCache && cacheKey != null) {
                val saved = offlineReadCache.getString(cacheKey, null)
                if (!saved.isNullOrBlank()) {
                    try {
                        return JSONArray(saved)
                    } catch (_: JSONException) {
                        offlineReadCache.edit().remove(cacheKey).apply()
                    }
                }
            }
            throw error
        }
    }

    private suspend fun send(
        path: String,
        method: String,
        body: Any?,
        authenticated: Boolean,
        profile: Boolean,
        allowRefresh: Boolean = true,
        prefer: String = "return=representation"
    ): Any {
        ensureConfigured()
        var session = if (authenticated) sessionStore.read() else null
        var result = perform(path, method, body, session?.accessToken, profile, prefer)
        if (result.status == 401 && allowRefresh && authenticated && session?.refreshToken?.isNotBlank() == true) {
            try {
                val refreshedJson = perform(
                    "auth/v1/token?grant_type=refresh_token", "POST",
                    JSONObject().put("refresh_token", session.refreshToken), null, false
                ).let { response ->
                    if (response.status !in 200..299) throw IOException("Session refresh failed")
                    JSONObject(response.text)
                }
                session = sessionFrom(refreshedJson, session.accountRole, session.userId)
                    ?: throw IOException("Session refresh failed")
                sessionStore.save(session)
                result = perform(path, method, body, session.accessToken, profile, prefer)
            } catch (error: Exception) {
                // A temporary connection failure must not erase a child's saved session.
                // Only clear it when the server actually rejects the refresh token.
                val message = error.message.orEmpty()
                val isNetworkFailure = error is IOException &&
                    !message.contains("Session refresh failed", ignoreCase = true) &&
                    !message.contains("invalid refresh token", ignoreCase = true) &&
                    !message.contains("refresh_token_not_found", ignoreCase = true) &&
                    !message.contains("Invalid Refresh Token", ignoreCase = true)
                if (isNetworkFailure) {
                    throw IOException("Connection unavailable. Your saved session is kept; reconnect and try again.", error)
                }
                signOut()
                throw IOException("Your session expired. Sign in again.", error)
            }
        }
        if (result.status !in 200..299) throw IOException(extractError(result.text, result.status))
        if (result.text.isBlank()) return JSONObject()
        return try {
            if (result.text.trimStart().startsWith("[")) JSONArray(result.text) else JSONObject(result.text)
        } catch (_: JSONException) {
            result.text
        }
    }

    private data class RawResult(val status: Int, val text: String)

    private suspend fun perform(
        path: String, method: String, body: Any?, bearerToken: String?, profile: Boolean,
        prefer: String = "return=representation"
    ): RawResult = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("$baseUrl/$path")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${bearerToken ?: anonKey}")
            .header("Accept", "application/json")
        if (profile) {
            if (method == "GET") builder.header("Accept-Profile", "edulock")
            else builder.header("Content-Profile", "edulock")
        }
        if (method == "POST" || method == "PATCH" || method == "PUT") {
            builder.header("Content-Type", "application/json")
                .header("Prefer", prefer)
                .method(method, (body ?: JSONObject()).toString().toRequestBody(JSON_MEDIA))
        } else if (method == "DELETE") {
            builder.header("Prefer", prefer)
            builder.method("DELETE", null)
        } else {
            builder.get()
        }
        val request = builder.build()
        var lastNetworkError: IOException? = null
        for (attempt in 0..2) {
            try {
                return@withContext http.newCall(request).execute().use { response ->
                    RawResult(response.code, response.body?.string().orEmpty())
                }
            } catch (error: IOException) {
                lastNetworkError = error
                if (attempt < 2) Thread.sleep(350L * (attempt + 1))
            }
        }
        val cause = lastNetworkError
        if (cause?.message.orEmpty().contains("resolve host", ignoreCase = true)) {
            throw IOException("EduLock cannot resolve the Supabase server address. Check Wi-Fi/mobile data and Android Private DNS (set it to Automatic), then try again. Your saved account is unchanged.", cause)
        }
        throw IOException("EduLock could not connect to its server. Check your connection and try again. Your saved account is unchanged.", cause)
    }

    private fun sessionFrom(json: JSONObject, role: String, fallbackUserId: String? = null): AuthSession? {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val user = json.optJSONObject("user")
        val userId = user?.optString("id")?.takeIf { it.isNotBlank() && it != "null" } ?: fallbackUserId
        if (access.isBlank() || refresh.isBlank() || userId.isNullOrBlank()) return null
        val expiresIn = json.optLong("expires_in", 3600L).coerceIn(60L, 86_400L)
        return AuthSession(access, refresh, userId, role, System.currentTimeMillis() + expiresIn * 1000L)
    }

    private fun extractError(text: String, status: Int): String {
        try {
            val json = JSONObject(text)
            val message = listOf("msg", "message", "error_description", "error", "hint")
                .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() && it != "null" } }
            if (message != null) {
                if (message.contains("user already registered", ignoreCase = true) ||
                    message.contains("email already registered", ignoreCase = true) ||
                    message.contains("already been registered", ignoreCase = true)) {
                    return "This email is already registered. Switch to Sign in and use your existing password."
                }
                if (message.contains("token has expired", ignoreCase = true) ||
                    message.contains("otp_expired", ignoreCase = true) ||
                    message.contains("invalid token", ignoreCase = true)) {
                    return "This verification code has expired or is incorrect. Request a fresh code and enter the latest email's code."
                }
                if (message.contains("email rate limit", ignoreCase = true) ||
                    message.contains("over_email_send_rate_limit", ignoreCase = true) ||
                    message.contains("for security purposes", ignoreCase = true) ||
                    message.contains("too many requests", ignoreCase = true)) {
                    return "Too many email-code requests. Wait a little longer, check Inbox/Spam/Promotions, then request just one fresh code."
                }
                if (message.contains("anonymous_provider_disabled", ignoreCase = true) ||
                    message.contains("anonymous sign-ins are disabled", ignoreCase = true)) {
                    return "Child pairing needs Anonymous Sign-Ins enabled in the EduLock Supabase project: Authentication → Sign In / Providers → Anonymous."
                }
                if (message.contains("invalid schema: edulock", ignoreCase = true)) {
                    return "EduLock database is installed, but the edulock schema is not exposed to the Supabase Data API. In Integrations > Data API > Settings > Exposed schemas, select edulock and Save."
                }
                return message.take(500)
            }
        } catch (_: Exception) {
            // Fall through to safe generic message.
        }
        return when (status) {
            429 -> "Too many email-code requests. Wait a little longer, check Inbox/Spam/Promotions, then request one fresh code."
            400 -> "The request could not be accepted. Check the entered values."
            401 -> "Sign-in required. Check your email and password."
            403 -> "You do not have permission to perform this action."
            404 -> "The requested EduLock service or record was not found. Check Supabase setup."
            409 -> "This item already exists or is already linked."
            in 500..599 -> "Supabase is temporarily unavailable. Please retry."
            else -> "Request failed (HTTP $status). Please retry."
        }
    }

    private fun requireSession(role: String? = null): AuthSession {
        val session = sessionStore.read() ?: throw IOException("Please sign in first.")
        if (role != null && session.accountRole != role) {
            throw IOException(if (role == "parent") "Parent sign-in is required for this screen." else "Child sign-in is required for this screen.")
        }
        return session
    }

    private fun ensureConfigured() {
        if (!isConfigured) {
            throw IOException("Supabase is not configured. Set SUPABASE_URL and SUPABASE_ANON_KEY in GitHub Actions secrets, then rebuild.")
        }
    }

    private fun JSONObject.toChildProfile() = ChildProfile(
        id = optString("id"),
        familyId = optString("family_id"),
        displayName = optString("display_name"),
        preferredLanguage = optString("preferred_language", "en"),
        dailyCapMinutes = optInt("daily_cap_minutes", 60).coerceIn(0, 1440),
        countryCode = optString("country_code", "PK").uppercase().takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "PK",
        schoolGrade = optInt("school_grade", 1).coerceIn(0, 13),
        ageYears = optInt("age_years", 8).coerceIn(3, 19),
        avatarIcon = optString("avatar_icon", "🦊").takeIf { it in CHILD_AVATAR_ICONS } ?: "🦊",
        dailyGameLimitMinutes = optInt("daily_game_limit_minutes", 20).coerceIn(0, 1440),
        studyTrack = optString("study_track", "none").lowercase(Locale.ROOT).takeIf {
            it in setOf("none", "islamic", "hindu", "christian", "sikh", "buddhist", "jewish", "jain", "other")
        } ?: "none",
        kidsModeEnabled = optBoolean("kids_mode_enabled", false),
        deviceLinked = optString("child_user_id").takeIf { it.isNotBlank() && it != "null" } != null
    )

    private fun JSONObject.toTaskRow() = TaskRow(
        id = optString("id"),
        childId = optString("child_id"),
        subjectId = optString("subject_id").takeIf { it.isNotBlank() && it != "null" },
        title = optString("title"),
        questionCount = optInt("question_count", 1).coerceIn(1, 500),
        passPercent = optInt("pass_percent", 70).coerceIn(0, 100),
        rewardMinutes = optInt("reward_minutes").coerceIn(0, 1440),
        dailyCapMinutes = optInt("daily_cap_minutes").coerceIn(0, 1440),
        active = optBoolean("active", true),
        rewardPackageName = optString("reward_package_name").takeIf { it.isNotBlank() && it != "null" },
        rewardGroupId = optString("reward_group_id").takeIf { it.isNotBlank() && it != "null" },
        timeLimitSeconds = optInt("time_limit_seconds", 0).takeIf { !isNull("time_limit_seconds") && it in 10..3600 },
        rewardToBank = optBoolean("reward_to_bank", false),
        questionTimeLimitSeconds = optInt("question_time_limit_seconds", 0)
            .takeIf { !isNull("question_time_limit_seconds") && it in 5..600 },
        targetGradeLevel = optInt("target_grade_level", 1),
        targetAgeYears = optInt("target_age_years", 8)
    )

    private fun JSONObject.toQuestionRow(): QuestionRow {
        val opts = optJSONArray("options") ?: JSONArray()
        return QuestionRow(
            id = optString("id"),
            subjectId = optString("subject_id"),
            prompt = optString("prompt"),
            questionType = optString("question_type", "short_answer"),
            options = (0 until opts.length()).map { opts.optString(it) },
            languageCode = optString("language_code", "en"),
            conceptKey = optString("concept_key", "general"),
            picturePath = optString("picture_path").takeIf { it.isNotBlank() && it != "null" },
            gradeLevel = optInt("grade_level", 1),
            ageMin = optInt("age_min", 3),
            ageMax = optInt("age_max", 19),
            rewardMinutes = if (isNull("reward_minutes")) null else optInt("reward_minutes", 0).coerceIn(0,1440)
        )
    }

    private fun ensureConfiguredIfNeeded() = ensureConfigured()

    private companion object {
        val CHILD_AVATAR_ICONS = setOf("🦊","🐼","🐯","🐸","🐰","🦄","🐻","🐨","🐵","🐧","🦋","🐢")
        const val LOCK_PREFS = "edulock_lock_state"
        const val APP_USED_PREFIX = "app_used_millis_"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
