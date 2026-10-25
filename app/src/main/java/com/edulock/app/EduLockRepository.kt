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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChildProfile(
    val id: String,
    val familyId: String,
    val displayName: String,
    val preferredLanguage: String,
    val dailyCapMinutes: Int
)

data class SubjectRow(val id: String, val familyId: String, val title: String, val languageCode: String)

data class QuestionRow(
    val id: String,
    val subjectId: String,
    val prompt: String,
    val questionType: String,
    val options: List<String>
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
    val active: Boolean
)

data class AllowedApp(val id: String, val childId: String, val packageName: String, val displayName: String)
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
    val dailyCapMinutes: Int
)
data class AuthResult(val session: AuthSession?, val message: String)

class EduLockRepository(context: Context) {
    private val appContext = context.applicationContext
    private val sessionStore = SecureSessionStore(appContext)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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
        require(accountRole == "parent" || accountRole == "child") { "Select a valid account type" }
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
        val json = authRequest("auth/v1/signup", body)
        val user = json.optJSONObject("user") ?: json.optJSONObject("data")?.optJSONObject("user")
        val token = sessionFrom(json, accountRole)
        if (token != null) {
            sessionStore.save(token)
            return AuthResult(token, "Account created successfully.")
        }
        val message = if (user != null) {
            "Account created. Check your email to confirm it, then sign in. Keep your parent pairing code safe if this is a child account."
        } else {
            "Account request submitted. Check your email if confirmation is enabled."
        }
        return AuthResult(null, message)
    }

    suspend fun signIn(email: String, password: String, accountRole: String): AuthSession {
        ensureConfigured()
        require(email.trim().contains("@")) { "Enter a valid email address" }
        require(password.isNotEmpty()) { "Enter your password" }
        require(accountRole == "parent" || accountRole == "child") { "Select a valid account type" }
        val json = authRequest(
            "auth/v1/token?grant_type=password",
            JSONObject().put("email", email.trim()).put("password", password)
        )
        val session = sessionFrom(json, accountRole)
            ?: throw IOException("Sign-in succeeded without a session. Confirm your email address and try again.")
        sessionStore.save(session)
        return session
    }

    suspend fun restoreSession(): AuthSession? {
        if (!isConfigured) return null
        val existing = sessionStore.read() ?: return null
        if (existing.expiresAtMillis > System.currentTimeMillis() + 60_000) return existing
        if (existing.refreshToken.isBlank()) {
            signOut()
            return null
        }
        return try {
            val json = send("auth/v1/token?grant_type=refresh_token", "POST",
                JSONObject().put("refresh_token", existing.refreshToken), authenticated = false, profile = false) as? JSONObject
                ?: throw IOException("Unexpected session refresh response")
            val refreshed = sessionFrom(json, existing.accountRole, existing.userId)
                ?: throw IOException("Could not refresh session")
            sessionStore.save(refreshed)
            refreshed
        } catch (_: Exception) {
            signOut()
            null
        }
    }

    suspend fun ensureParentFamily(displayName: String): String {
        val session = requireSession("parent")
        val profiles = restArray(
            "family_profiles?select=id,display_name,owner_user_id&owner_user_id=eq.${session.userId}&limit=1"
        )
        if (profiles.length() > 0) return profiles.getJSONObject(0).getString("id")

        val profileName = displayName.trim().ifBlank { "My family" }.take(120)
        val rows = restArray("family_profiles?select=id", "POST",
            JSONObject().put("owner_user_id", session.userId).put("display_name", profileName))
        return rows.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
            ?: throw IOException("Could not create the parent profile")
    }

    suspend fun loadChildren(familyId: String): List<ChildProfile> {
        requireSession("parent")
        val rows = restArray("children?select=id,family_id,display_name,preferred_language,daily_cap_minutes&family_id=eq.$familyId&order=created_at.desc")
        return (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.toChildProfile() }
    }

    suspend fun addChild(familyId: String, name: String, languageCode: String, dailyCap: Int): ChildProfile {
        requireSession("parent")
        require(name.trim().isNotEmpty()) { "Enter the child’s name" }
        require(dailyCap in 0..1440) { "Daily screen-time limit must be from 0 to 1440 minutes" }
        val rows = restArray("children?select=id,family_id,display_name,preferred_language,daily_cap_minutes", "POST",
            JSONObject()
                .put("family_id", familyId)
                .put("display_name", name.trim().take(120))
                .put("preferred_language", languageCode.trim().ifBlank { "en" }.take(16))
                .put("daily_cap_minutes", dailyCap))
        return rows.optJSONObject(0)?.toChildProfile() ?: throw IOException("Could not create this child profile")
    }

    suspend fun loadCurrentChild(): ChildProfile? {
        val session = requireSession("child")
        val rows = restArray("children?select=id,family_id,display_name,preferred_language,daily_cap_minutes&child_user_id=eq.${session.userId}&limit=1")
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

    suspend fun loadSubjects(familyId: String): List<SubjectRow> {
        requireSession("parent")
        val rows = restArray("subjects?select=id,family_id,title,language_code&family_id=eq.$familyId&order=created_at.desc")
        return (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                SubjectRow(row.optString("id"), row.optString("family_id"), row.optString("title"), row.optString("language_code", "en"))
            }
        }
    }

    suspend fun addSubject(familyId: String, title: String, languageCode: String): SubjectRow {
        requireSession("parent")
        require(title.trim().isNotEmpty()) { "Enter a subject name" }
        val rows = restArray("subjects?select=id,family_id,title,language_code", "POST",
            JSONObject().put("family_id", familyId)
                .put("title", title.trim().take(160))
                .put("language_code", languageCode.trim().ifBlank { "en" }.take(16)))
        val row = rows.optJSONObject(0) ?: throw IOException("Could not create the subject")
        return SubjectRow(row.getString("id"), row.getString("family_id"), row.getString("title"), row.optString("language_code", "en"))
    }

    suspend fun addQuestion(subjectId: String, prompt: String, type: String, options: List<String>, answer: String) {
        requireSession("parent")
        require(prompt.trim().isNotEmpty()) { "Enter the question" }
        require(answer.trim().isNotEmpty()) { "Enter the correct answer" }
        require(type in setOf("multiple_choice", "true_false", "short_answer", "numeric")) { "Select a valid question type" }
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
        restArray("questions?select=id", "POST",
            JSONObject()
                .put("subject_id", subjectId)
                .put("prompt", prompt.trim().take(10_000))
                .put("question_type", type)
                .put("options", JSONArray(options.map { it.trim().take(500) }.filter { it.isNotEmpty() }))
                .put("answer_key", JSONObject().put("answer", answer.trim().take(1000))))
    }

    suspend fun loadQuestions(subjectId: String, limit: Int = 500): List<QuestionRow> {
        val rows = restArray("questions?select=id,subject_id,prompt,question_type,options,created_at&subject_id=eq.$subjectId&order=created_at.asc,id.asc&limit=${limit.coerceIn(1, 500)}")
        return (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                val opts = row.optJSONArray("options") ?: JSONArray()
                QuestionRow(
                    row.optString("id"), row.optString("subject_id"), row.optString("prompt"),
                    row.optString("question_type", "short_answer"),
                    (0 until opts.length()).map { opts.optString(it) }
                )
            }
        }
    }

    suspend fun addTask(
        familyId: String, child: ChildProfile, subject: SubjectRow, title: String,
        questionCount: Int, passPercent: Int, rewardMinutes: Int, taskDailyCapMinutes: Int
    ) {
        requireSession("parent")
        require(title.trim().isNotEmpty()) { "Enter a task title" }
        require(questionCount in 1..500) { "Question count must be from 1 to 500" }
        require(passPercent in 0..100) { "Passing score must be from 0 to 100 percent" }
        require(rewardMinutes in 0..1440) { "Reward must be from 0 to 1440 minutes" }
        require(taskDailyCapMinutes in 0..1440) { "Task daily cap must be from 0 to 1440 minutes" }
        val count = loadQuestions(subject.id, 500).size
        require(count > 0) { "Add at least one question to this subject first" }
        require(questionCount <= count) { "This subject has only $count questions. Add questions or lower the task question count." }
        restArray("tasks?select=id", "POST",
            JSONObject()
                .put("family_id", familyId)
                .put("child_id", child.id)
                .put("subject_id", subject.id)
                .put("title", title.trim().take(160))
                .put("question_count", questionCount)
                .put("pass_percent", passPercent)
                .put("reward_minutes", rewardMinutes)
                .put("daily_cap_minutes", minOf(taskDailyCapMinutes, child.dailyCapMinutes))
                .put("active", true))
    }

    suspend fun loadChildTasks(childId: String): List<TaskRow> {
        requireSession("child")
        val rows = restArray("tasks?select=id,child_id,subject_id,title,question_count,pass_percent,reward_minutes,daily_cap_minutes,active&child_id=eq.$childId&active=eq.true&order=created_at.desc")
        return (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.toTaskRow() }
    }

    suspend fun submitTask(taskId: String, answers: Map<String, String>): TaskResult {
        requireSession("child")
        val payload = JSONArray()
        answers.forEach { (questionId, answer) ->
            payload.put(JSONObject().put("question_id", questionId).put("answer", answer))
        }
        val result = requestFunction("submit-task-attempt", JSONObject().put("task_id", taskId).put("answers", payload))
        return TaskResult(
            scorePercent = result.optInt("score_percent"),
            passed = result.optBoolean("passed"),
            awardedMinutes = result.optInt("awarded_minutes"),
            dailyUsedMinutes = result.optInt("daily_used_minutes_after"),
            dailyCapMinutes = result.optInt("daily_cap_minutes")
        )
    }

    suspend fun loadAttempts(): List<AttemptRow> {
        requireSession("parent")
        val rows = restArray("attempts?select=id,child_id,task_id,score_percent,passed,awarded_minutes,completed_at&order=completed_at.desc&limit=100")
        return (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                AttemptRow(
                    row.optString("id"), row.optString("child_id"), row.optString("task_id"),
                    row.optInt("score_percent"), row.optBoolean("passed"),
                    row.optInt("awarded_minutes"), row.optString("completed_at")
                )
            }
        }
    }

    suspend fun loadAllowedApps(childId: String): List<AllowedApp> {
        requireSession()
        val rows = restArray("allowed_apps?select=id,child_id,package_name,display_name,enabled&child_id=eq.$childId&order=display_name.asc")
        return (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                AllowedApp(row.optString("id"), row.optString("child_id"), row.optString("package_name"), row.optString("display_name"))
            }
        }
    }

    suspend fun addAllowedApp(childId: String, packageName: String, displayName: String) {
        requireSession("parent")
        val normalizedPackage = packageName.trim()
        require(normalizedPackage.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
            "Enter a valid Android package name, for example com.example.app"
        }
        require(displayName.trim().isNotEmpty()) { "Enter the app's display name" }
        restArray("allowed_apps?select=id", "POST",
            JSONObject().put("child_id", childId).put("package_name", normalizedPackage)
                .put("display_name", displayName.trim().take(120)).put("enabled", true))
    }

    suspend fun deleteAllowedApp(appId: String) {
        requireSession("parent")
        send("rest/v1/allowed_apps?id=eq.$appId", "DELETE", null, authenticated = true, profile = true)
    }

    suspend fun refreshChildDeviceRestrictions(child: ChildProfile): Int {
        requireSession("child")
        val allowed = loadAllowedApps(child.id).map { it.packageName }.toMutableSet()
        allowed += "com.edulock.app"
        val startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC)
        val stamp = DateTimeFormatter.ISO_INSTANT.format(startOfDay)
        val ledger = restArray("screen_time_ledger?select=minutes_delta&child_id=eq.${child.id}&created_at=gte.$stamp")
        val earned = (0 until ledger.length()).sumOf { i -> ledger.optJSONObject(i)?.optInt("minutes_delta") ?: 0 }
            .coerceAtLeast(0).coerceAtMost(child.dailyCapMinutes)
        val prefs = appContext.getSharedPreferences(LOCK_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("child_active", true)
            .putString("child_id", child.id)
            .putStringSet("allowed_packages", allowed)
            .putInt("earned_minutes_today", earned)
            .putInt("daily_cap_minutes", child.dailyCapMinutes)
            .putString("budget_day", LocalDate.now(ZoneOffset.UTC).toString())
            .apply()
        return earned
    }

    private suspend fun requestFunction(name: String, body: JSONObject): JSONObject {
        val result = send("functions/v1/$name", "POST", body, authenticated = true, profile = false)
        return result as? JSONObject ?: throw IOException("The server returned an unexpected response")
    }

    private suspend fun authRequest(path: String, body: JSONObject): JSONObject {
        val result = send(path, "POST", body, authenticated = false, profile = false, allowRefresh = false)
        return result as? JSONObject ?: throw IOException("The authentication server returned an unexpected response")
    }

    private suspend fun restArray(path: String, method: String = "GET", body: JSONObject? = null): JSONArray {
        val result = send("rest/v1/$path", method, body, authenticated = true, profile = true)
        return when (result) {
            is JSONArray -> result
            is JSONObject -> JSONArray().put(result)
            else -> JSONArray()
        }
    }

    private suspend fun send(
        path: String,
        method: String,
        body: JSONObject?,
        authenticated: Boolean,
        profile: Boolean,
        allowRefresh: Boolean = true
    ): Any {
        ensureConfigured()
        var session = if (authenticated) sessionStore.read() else null
        var result = perform(path, method, body, session?.accessToken, profile)
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
                result = perform(path, method, body, session.accessToken, profile)
            } catch (_: Exception) {
                sessionStore.clear()
                throw IOException("Your session expired. Sign in again.")
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
        path: String, method: String, body: JSONObject?, bearerToken: String?, profile: Boolean
    ): RawResult = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("$baseUrl/$path")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${bearerToken ?: anonKey}")
            .header("Accept", "application/json")
        if (profile) {
            if (method == "GET" || method == "DELETE") builder.header("Accept-Profile", "edulock")
            if (method != "GET" && method != "DELETE") builder.header("Content-Profile", "edulock")
        }
        if (method == "POST" || method == "PATCH" || method == "PUT") {
            builder.header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .method(method, (body ?: JSONObject()).toString().toRequestBody(JSON_MEDIA))
        } else if (method == "DELETE") {
            builder.method("DELETE", null)
        } else {
            builder.get()
        }
        http.newCall(builder.build()).execute().use { response ->
            RawResult(response.code, response.body?.string().orEmpty())
        }
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
            if (message != null) return message.take(500)
        } catch (_: Exception) {
            // Fall through to safe generic message.
        }
        return when (status) {
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
        dailyCapMinutes = optInt("daily_cap_minutes", 60).coerceIn(0, 1440)
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
        active = optBoolean("active", true)
    )

    private fun ensureConfiguredIfNeeded() = ensureConfigured()

    private companion object {
        const val LOCK_PREFS = "edulock_lock_state"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
