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
    val dailyCapMinutes: Int,
    val countryCode: String = "PK",
    val schoolGrade: Int = 1,
    val ageYears: Int = 8
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
    val conceptKey: String
)

data class QuestionTranslation(
    val id: String,
    val questionId: String,
    val languageCode: String,
    val prompt: String,
    val options: List<String>
)

data class ConceptProgress(
    val conceptKey: String,
    val languageCode: String,
    val correct: Int,
    val total: Int
) {
    val percent: Int get() = if (total == 0) 0 else (correct * 100) / total
}

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

data class AllowedApp(
    val id: String,
    val childId: String,
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val dailyLimitMinutes: Int = 1440
)

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
    val batteryPercent: Int?
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
        val json = authRequest("auth/v1/signup", body)
        val user = json.optJSONObject("user") ?: json.optJSONObject("data")?.optJSONObject("user")
        val token = sessionFrom(json, accountRole)
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
        require(code.length == 8) { "Enter the eight-digit code from your email" }
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
        authRequest("auth/v1/resend", JSONObject().put("type", "signup").put("email", email.trim()))
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
        } catch (_: Exception) {
            // Invalid/stale sessions must not open the wrong account dashboard.
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
        val rows = restArray("children?select=id,family_id,display_name,preferred_language,daily_cap_minutes,country_code,school_grade,age_years&family_id=eq.$familyId&order=created_at.desc")
        return (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.toChildProfile() }
    }

    suspend fun addChild(familyId: String, name: String, languageCode: String, dailyCap: Int, countryCode: String = "PK", schoolGrade: Int = 1, ageYears: Int = 8): ChildProfile {
        requireSession("parent")
        require(name.trim().isNotEmpty()) { "Enter the child’s name" }
        require(dailyCap in 0..1440) { "Daily screen-time limit must be from 0 to 1440 minutes" }
        require(countryCode.trim().uppercase().matches(Regex("[A-Z]{2}"))) { "Choose a valid country." }
        require(schoolGrade in 0..13) { "Choose a grade from kindergarten to grade 13." }
        require(ageYears in 3..19) { "Child age must be from 3 to 19 years." }
        val rows = restArray("children?select=id,family_id,display_name,preferred_language,daily_cap_minutes,country_code,school_grade,age_years", "POST",
            JSONObject()
                .put("family_id", familyId)
                .put("display_name", name.trim().take(120))
                .put("preferred_language", languageCode.trim().ifBlank { "en" }.take(16))
                .put("daily_cap_minutes", dailyCap)
                .put("country_code", countryCode.trim().uppercase())
                .put("school_grade", schoolGrade)
                .put("age_years", ageYears))
        return rows.optJSONObject(0)?.toChildProfile() ?: throw IOException("Could not create this child profile")
    }

    suspend fun deactivateChildLink(childId: String) {
        requireSession("parent")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        restArray("children?id=eq.$childId", "PATCH", JSONObject().put("child_user_id", JSONObject.NULL))
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

    suspend fun updateChildCurriculum(childId: String, countryCode: String, schoolGrade: Int, ageYears: Int) {
        requireSession("parent")
        require(childId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid child profile." }
        require(countryCode.matches(Regex("[A-Z]{2}"))) { "Choose a valid country." }
        require(schoolGrade in 0..13) { "Grade must be from kindergarten to grade 13." }
        require(ageYears in 3..19) { "Age must be from 3 to 19 years." }
        restArray("children?id=eq.$childId", "PATCH",
            JSONObject().put("country_code", countryCode).put("school_grade", schoolGrade).put("age_years", ageYears))
    }

    suspend fun loadCurrentChild(): ChildProfile? {
        val session = requireSession("child")
        val rows = restArray("children?select=id,family_id,display_name,preferred_language,daily_cap_minutes,country_code,school_grade,age_years&child_user_id=eq.${session.userId}&limit=1")
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
        val rows = restArray("subjects?select=id,family_id,title,language_code,country_code,grade_level,curriculum_name,curriculum_subject_code&family_id=eq.$familyId&order=created_at.desc")
        return (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                SubjectRow(row.optString("id"), row.optString("family_id"), row.optString("title"), row.optString("language_code", "en"), row.optString("country_code", "PK"), row.optInt("grade_level", 1), row.optString("curriculum_name", "Parent-created"), row.optString("curriculum_subject_code", ""))
            }
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

    suspend fun findOrCreateSubject(familyId: String, title: String, languageCode: String): SubjectRow {
        requireSession("parent")
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Enter a subject name." }
        val normalizedLanguage = languageCode.trim().ifBlank { "en" }
        val existing = loadSubjects(familyId).firstOrNull {
            it.title.equals(normalizedTitle, ignoreCase = true) &&
                it.languageCode.equals(normalizedLanguage, ignoreCase = true)
        }
        return existing ?: addSubject(familyId, normalizedTitle, normalizedLanguage)
    }

    suspend fun addQuestion(
        subjectId: String, prompt: String, type: String, options: List<String>, answer: String,
        languageCode: String, conceptKey: String, solution: String = ""
    ) {
        requireSession("parent")
        require(prompt.trim().isNotEmpty()) { "Enter the question" }
        require(answer.trim().isNotEmpty()) { "Enter the correct answer" }
        require(languageCode.matches(Regex("[A-Za-z][A-Za-z0-9-]{1,15}"))) { "Use a language code such as en, ur or sd." }
        require(conceptKey.trim().isNotEmpty()) { "Enter a concept tag. Reuse the same tag for questions that test the same idea." }
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
        val created = restArray("questions?select=id", "POST",
            JSONObject()
                .put("subject_id", subjectId)
                .put("prompt", prompt.trim().take(10_000))
                .put("question_type", type)
                .put("options", JSONArray(options.map { it.trim().take(500) }.filter { it.isNotEmpty() }))
                .put("answer_key", JSONObject().put("answer", answer.trim().take(1000)))
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
                try { restArray("questions?id=eq.$questionId", "DELETE") } catch (_: Exception) { }
                throw IOException("Could not save the parent-only worked solution. Apply the latest EduLock Supabase migration and try again.", e)
            }
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
        val rows = restArray("questions?select=id,subject_id,prompt,question_type,options,language_code,concept_key,created_at&subject_id=eq.$subjectId&order=created_at.asc,id.asc&limit=${limit.coerceIn(1, 500)}")
        return (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row -> row.toQuestionRow() }
        }
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
        val accepted = acceptedAnswers.map { it.trim().take(1000) }.filter { it.isNotEmpty() }.distinct()
        require(accepted.isNotEmpty() && accepted.size <= 30) { "Enter between 1 and 30 accepted answers, one per line." }
        val cleanOptions = options.map { it.trim().take(500) }.filter { it.isNotEmpty() }
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
        if (existing.length() > 0) {
            restArray(
                "question_translations?select=id,question_id,language_code,prompt,options,created_at,updated_at&question_id=eq.${question.id}&language_code=eq.$lang",
                "PATCH", body
            )
        } else {
            restArray("question_translations?select=id", "POST", body)
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

    suspend fun submitTask(
        taskId: String, answers: Map<String, String>, answerLanguages: Map<String, String>
    ): TaskResult {
        requireSession("child")
        val payload = JSONArray()
        answers.forEach { (questionId, answer) ->
            val languageCode = answerLanguages[questionId].orEmpty()
            require(languageCode.matches(Regex("[A-Za-z][A-Za-z0-9-]{1,15}"))) {
                "Select a valid language for every answer."
            }
            payload.put(JSONObject()
                .put("question_id", questionId)
                .put("answer", answer)
                .put("language_code", languageCode))
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

    suspend fun loadChildLocationSnapshot(childId: String): ChildLocationSnapshot {
        requireSession("parent")
        val locations = restArray(
            "child_locations?select=child_id,latitude,longitude,accuracy_meters,recorded_at&child_id=eq.$childId&limit=1"
        )
        val statuses = restArray(
            "child_device_status?select=child_id,last_seen_at,sharing_enabled,battery_percent&child_id=eq.$childId&limit=1"
        )
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
            batteryPercent = if (status != null && !status.isNull("battery_percent")) status.optInt("battery_percent").coerceIn(0, 100) else null
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
        // One PostgREST upsert avoids hundreds of sequential network requests on child startup.
        restArray("child_installed_apps?on_conflict=child_id,package_name", "POST", payload,
            prefer = "resolution=merge-duplicates,return=representation")
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
        require(limitMinutes in 1..1440) { "Daily app limit must be between 1 and 1440 minutes." }
        val existing = restArray("allowed_apps?select=id&child_id=eq.$childId&package_name=eq.${app.packageName}")
        if (existing.length() > 0) {
            val id = existing.optJSONObject(0)?.optString("id")
            if (!id.isNullOrBlank()) {
                restArray("allowed_apps?id=eq.$id", "PATCH", JSONObject().put("enabled", true).put("daily_limit_minutes", limitMinutes))
                return
            }
        }
        addAllowedApp(childId, app.packageName, app.displayName, limitMinutes)
    }

    suspend fun loadAllowedApps(childId: String): List<AllowedApp> {
        requireSession()
        val rows = restArray("allowed_apps?select=id,child_id,package_name,display_name,enabled,daily_limit_minutes&child_id=eq.$childId&order=display_name.asc")
        return (0 until rows.length()).mapNotNull { i ->
            rows.optJSONObject(i)?.let { row ->
                AllowedApp(
                    row.optString("id"), row.optString("child_id"), row.optString("package_name"),
                    row.optString("display_name"), row.optBoolean("enabled", true),
                    row.optInt("daily_limit_minutes", 1440).coerceIn(1, 1440)
                )
            }
        }
    }

    suspend fun addAllowedApp(childId: String, packageName: String, displayName: String, dailyLimitMinutes: Int) {
        requireSession("parent")
        require(dailyLimitMinutes in 1..1440) { "Daily app limit must be between 1 and 1440 minutes." }
        val normalizedPackage = packageName.trim()
        require(normalizedPackage.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
            "Enter a valid Android package name, for example com.example.app"
        }
        require(displayName.trim().isNotEmpty()) { "Enter the app's display name" }
        restArray("allowed_apps?select=id", "POST",
            JSONObject().put("child_id", childId).put("package_name", normalizedPackage)
                .put("display_name", displayName.trim().take(120)).put("enabled", true)
                .put("daily_limit_minutes", dailyLimitMinutes))
    }

    suspend fun updateAllowedAppLimit(appId: String, dailyLimitMinutes: Int) {
        requireSession("parent")
        require(dailyLimitMinutes in 1..1440) { "Daily app limit must be between 1 and 1440 minutes." }
        restArray("allowed_apps?id=eq.$appId", "PATCH", JSONObject().put("daily_limit_minutes", dailyLimitMinutes))
    }

    suspend fun updateAllowedAppEnabled(appId: String, enabled: Boolean) {
        requireSession("parent")
        restArray("allowed_apps?id=eq.$appId", "PATCH", JSONObject().put("enabled", enabled))
    }

    suspend fun deleteAllowedApp(appId: String) {
        requireSession("parent")
        send("rest/v1/allowed_apps?id=eq.$appId", "DELETE", null, authenticated = true, profile = true)
    }

    suspend fun refreshChildDeviceRestrictions(child: ChildProfile): Int {
        requireSession("child")
        val appRules = loadAllowedApps(child.id)
        // Allow every app by default; only explicit parent restrictions block access.
        val allowed = appRules.filter { it.enabled }.map { it.packageName }.toMutableSet()
        allowed += "com.edulock.app"
        val blocked = appRules.filter { !it.enabled }.map { it.packageName }.toSet()
        val appLimits = appRules.filter { it.enabled }
            .map { it.packageName + "=" + it.dailyLimitMinutes }.toSet()
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
            .putStringSet("blocked_packages", blocked)
            .putStringSet("app_daily_limits", appLimits)
            .putInt("earned_minutes_today", earned)
            .putInt("daily_cap_minutes", child.dailyCapMinutes)
            .putString("budget_day", LocalDate.now(ZoneOffset.UTC).toString())
            .apply()
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

    private suspend fun restArray(path: String, method: String = "GET", body: Any? = null, prefer: String = "return=representation"): JSONArray {
        val result = send("rest/v1/$path", method, body, authenticated = true, profile = true, prefer = prefer)
        return when (result) {
            is JSONArray -> result
            is JSONObject -> JSONArray().put(result)
            else -> JSONArray()
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
            } catch (_: Exception) {
                signOut()
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
            if (message != null) {
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
        ageYears = optInt("age_years", 8).coerceIn(3, 19)
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

    private fun JSONObject.toQuestionRow(): QuestionRow {
        val opts = optJSONArray("options") ?: JSONArray()
        return QuestionRow(
            id = optString("id"),
            subjectId = optString("subject_id"),
            prompt = optString("prompt"),
            questionType = optString("question_type", "short_answer"),
            options = (0 until opts.length()).map { opts.optString(it) },
            languageCode = optString("language_code", "en"),
            conceptKey = optString("concept_key", "general")
        )
    }

    private fun ensureConfiguredIfNeeded() = ensureConfigured()

    private companion object {
        const val LOCK_PREFS = "edulock_lock_state"
        const val APP_USED_PREFIX = "app_used_millis_"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
