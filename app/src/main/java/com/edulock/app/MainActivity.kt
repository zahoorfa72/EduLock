package com.edulock.app

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.DisposableEffect
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
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.IOException
import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

private suspend fun translateManyOnDevice(texts: List<String>, sourceCode: String, targetCode: String): List<String> {
    if (sourceCode.equals(targetCode, ignoreCase = true)) return texts
    val source = TranslateLanguage.fromLanguageTag(sourceCode.trim())
        ?: throw IllegalArgumentException("The on-device translator does not support source language '$sourceCode'.")
    val target = TranslateLanguage.fromLanguageTag(targetCode.trim())
        ?: throw IllegalArgumentException("The on-device translator does not support target language '$targetCode'. Choose a supported language code.")
    val translator = Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
    )
    // Keep equations, units, variables and LaTeX outside the translator. Translating these
    // tokens changes the meaning of physics and mathematics questions and their answer keys.
    val mathExpression = Regex(
        """\$\$[\s\S]*?\$\$|\$[^$\n]+\$|\\\([\s\S]*?\\\)|\\\[[\s\S]*?\\\]|(?<![\p{L}\p{N}])(?:[A-Za-zΑ-ω][A-Za-z0-9₀-₉²³]*(?:_[A-Za-z0-9]+)?|\d+(?:\.\d+)?)(?:\s*(?:=|≈|≠|≤|≥|±|×|÷|\+|−|-|\*|/|\^|√)\s*(?:[A-Za-zΑ-ω][A-Za-z0-9₀-₉²³]*(?:_[A-Za-z0-9]+)?|\d+(?:\.\d+)?))+(?:\s*(?:kg|g|cm|mm|km|m/s(?:²|\^2)?|°C|°F|N|J|W|Pa|Hz|V|A|Ω|mol|s|min|h))?|(?<![\p{L}\p{N}])\d+(?:\.\d+)?\s*(?:m/s(?:²|\^2)?|kg|g|cm|mm|km|°C|°F|N|J|W|Pa|Hz|V|A|Ω|mol|s|min|h)\b""",
        RegexOption.IGNORE_CASE
    )
    return try {
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        suspend fun translateProse(value: String): String =
            if (value.isBlank()) value else translator.translate(value).await()
        suspend fun translatePreservingMath(value: String): String {
            val result = StringBuilder()
            var cursor = 0
            mathExpression.findAll(value).forEach { match ->
                if (match.range.first > cursor) {
                    result.append(translateProse(value.substring(cursor, match.range.first)))
                }
                result.append(match.value)
                cursor = match.range.last + 1
            }
            if (cursor < value.length) result.append(translateProse(value.substring(cursor)))
            return result.toString()
        }
        texts.map { value -> if (value.isBlank()) value else translatePreservingMath(value) }
    } finally {
        translator.close()
    }
}

private suspend fun translateOnDevice(text: String, sourceCode: String, targetCode: String): String =
    translateManyOnDevice(listOf(text), sourceCode, targetCode).first()

private fun templateTranslationKey(template: ParentLearningTemplate): String =
    template.subjectTitle + "|" + template.conceptKey

private suspend fun translateTemplate(
    template: ParentLearningTemplate,
    targetCode: String
): ParentLearningTemplate {
    val values = listOf(template.title, template.prompt) + template.options +
        listOf(template.answer, template.solution) + template.materials + template.steps + listOf(template.safety)
    val translated = translateManyOnDevice(values, template.languageCode, targetCode)
    var index = 0
    val title = translated[index++]
    val prompt = translated[index++]
    val options = translated.subList(index, index + template.options.size); index += template.options.size
    val answer = translated[index++]
    val solution = translated[index++]
    val materials = translated.subList(index, index + template.materials.size); index += template.materials.size
    val steps = translated.subList(index, index + template.steps.size); index += template.steps.size
    val safety = translated[index]
    return template.copy(
        title = title, prompt = prompt, options = options, answer = answer, solution = solution,
        materials = materials, steps = steps, safety = safety, languageCode = targetCode
    )
}

private fun studyTrackLabel(code: String): String = when (code) {
    "islamic" -> "Islam"
    "hindu" -> "Hinduism"
    "christian" -> "Christianity"
    "sikh" -> "Sikhism"
    "buddhist" -> "Buddhism"
    "jewish" -> "Judaism"
    "jain" -> "Jainism"
    "other" -> "Other"
    else -> "Not selected"
}

private fun religiousStudyOptionsForCountry(countryCode: String): List<Pair<String, String>> {
    val noTrack = "none" to "No religious-education track"
    val allTracks = listOf(
        "islamic" to "Islam",
        "hindu" to "Hinduism",
        "christian" to "Christianity",
        "sikh" to "Sikhism",
        "buddhist" to "Buddhism",
        "jain" to "Jainism",
        "jewish" to "Judaism",
        "other" to "Other / parent-created"
    )
    val country = countryCode.uppercase(Locale.ROOT)
    val locallyRelevantOrder = when (country) {
        "PK" -> listOf("islamic", "hindu", "christian", "sikh", "buddhist", "jain", "jewish", "other")
        "IN" -> listOf("hindu", "islamic", "sikh", "christian", "buddhist", "jain", "jewish", "other")
        "BD" -> listOf("islamic", "hindu", "buddhist", "christian", "other")
        "LK" -> listOf("buddhist", "hindu", "islamic", "christian", "other")
        "NP" -> listOf("hindu", "buddhist", "islamic", "christian", "other")
        "ID" -> listOf("islamic", "christian", "hindu", "buddhist", "other")
        "MY" -> listOf("islamic", "buddhist", "hindu", "christian", "sikh", "other")
        "SA", "AE", "QA", "OM", "KW", "BH" -> listOf("islamic", "christian", "hindu", "buddhist", "other")
        "US", "GB", "CA", "AU", "NZ" -> listOf("christian", "islamic", "hindu", "sikh", "buddhist", "jewish", "jain", "other")
        else -> allTracks.map { it.first }
    }
    val ordered = locallyRelevantOrder.mapNotNull { key -> allTracks.firstOrNull { it.first == key } }
    val remaining = allTracks.filterNot { item -> ordered.any { it.first == item.first } }
    // The local order is a helpful starting point, not a statement about a family's beliefs.
    return listOf(noTrack) + ordered + remaining
}

private fun religionTrackForSubject(title: String): String? = when {
    title.contains("Islamic Studies", ignoreCase = true) -> "islamic"
    title.contains("Hindu Studies", ignoreCase = true) -> "hindu"
    title.contains("Christian Studies", ignoreCase = true) -> "christian"
    title.contains("Sikh Studies", ignoreCase = true) -> "sikh"
    title.contains("Buddhist Studies", ignoreCase = true) -> "buddhist"
    title.contains("Jewish Studies", ignoreCase = true) -> "jewish"
    title.contains("Jain Studies", ignoreCase = true) -> "jain"
    title.contains("World Religions", ignoreCase = true) -> "other"
    else -> null
}

private fun religiousSubjectTitle(track: String): String = when (track) {
    "islamic" -> "Islamic Studies"
    "hindu" -> "Hindu Studies"
    "christian" -> "Christian Studies"
    "sikh" -> "Sikh Studies"
    "buddhist" -> "Buddhist Studies"
    "jewish" -> "Jewish Studies"
    "jain" -> "Jain Studies"
    "other" -> "World Religions & Ethics"
    else -> ""
}

private data class SubjectSuggestion(val title: String, val languageCode: String = "en", val studyTrack: String? = null)

private fun suggestedSubjectsForCountry(countryCode: String): List<SubjectSuggestion> {
    val common = listOf(
        "Mathematics", "Physics", "Chemistry", "Biology", "General Science", "English",
        "Social Studies", "Computer Skills", "Astronomy", "Geography", "Health & Safety",
        "Art & Design", "Drawing & Colour"
    ).map { SubjectSuggestion(it) }
    val local = when (countryCode.uppercase(Locale.ROOT)) {
        "PK" -> listOf(SubjectSuggestion("Urdu", "ur"), SubjectSuggestion("Sindhi", "sd"))
        "IN" -> listOf(SubjectSuggestion("Hindi", "hi"), SubjectSuggestion("Urdu", "ur"))
        else -> emptyList()
    }
    // Religious education is an optional child-profile choice, never a general subject suggestion.
    return (common + local).distinctBy { it.title.lowercase(Locale.ROOT) + ":" + it.languageCode }
}

private fun fourDTemplatesForSubject(
    subjectTitle: String,
    languageCode: String,
    gradeLevel: Int = 3,
    ageYears: Int = (gradeLevel + 5).coerceIn(3, 19)
): List<ParentLearningTemplate> =
    FourDQuestionBanks.forLearningContext(subjectTitle, languageCode, gradeLevel, ageYears)

private fun learningTemplateCategories(countryCode: String): List<String> {
    val common = listOf(
        "Mathematics", "Physics", "Chemistry", "Biology", "General Science",
        "English", "Social Studies", "Computer Skills", "Astronomy", "Geography",
        "Health & Safety", "Art & Design", "Drawing & Colour", "Religious Education"
    )
    val localLanguageSubjects = when (countryCode.uppercase(Locale.ROOT)) {
        "PK" -> listOf("Urdu", "Sindhi")
        "IN" -> listOf("Hindi", "Urdu")
        else -> emptyList()
    }
    return (common + localLanguageSubjects).distinct()
}

private data class LocalTaskDraft(
    val answers: Map<String, String> = emptyMap(),
    val questionIndex: Int = 0,
    val expiredQuestionIds: Set<String> = emptySet(),
    val taskDeadlineAtMillis: Long? = null,
    val questionDeadlineAtMillis: Long? = null,
    val questionDeadlineQuestionId: String? = null
)

private enum class AppScreen {
    DEVICE_SETUP, AUTH, VERIFY_OTP, PAIR_CHILD, PARENT_HOME, CHILD_HOME, CHILDREN, ADD_CHILD, CURRICULUM_LIBRARY, LEARNING_TEMPLATES, SOLAR_SYSTEM_QUIZ, KIDS_GAMES, LEARNING_3D_LAB, DRAWING_STUDIO,
    SUBJECTS, ADD_SUBJECT, QUESTIONS, ADD_QUESTION, ADD_TRANSLATION, ASSIGN_TASK,
    REPORTS, ALLOWED_APPS, PROTECTION_STATUS, CHILD_LOCATIONS, APP_USAGE_REPORT, APP_SETTINGS, PLAY_TASK, TASK_RESULT
}

@Composable
private fun EduLockApp(repository: EduLockRepository) {
    val context = LocalContext.current

    val scope = rememberCoroutineScope()



    val devicePreferences = remember { context.getSharedPreferences("edulock_device_mode", Context.MODE_PRIVATE) }
    var deviceMode by remember { mutableStateOf(devicePreferences.getString("mode", "unknown") ?: "unknown") }
    var screen by remember { mutableStateOf(if (deviceMode == "unknown") AppScreen.DEVICE_SETUP else AppScreen.AUTH) }
    var solarQuizReturnScreen by remember { mutableStateOf(AppScreen.PARENT_HOME) }
    var selected3DModel by remember { mutableStateOf("solar-system") }
    // Never populate the UI from an unverified cached session.
    var session by remember { mutableStateOf<AuthSession?>(null) }
    var accountRole by remember { mutableStateOf("parent") }
    var isRegistering by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
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
    var weeklyScheduleEditIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var weeklyScheduleEdits by remember { mutableStateOf<Map<String, AppWeeklySchedule>>(emptyMap()) }
    var lastPairingCode by remember { mutableStateOf("") }
    var pairingChildName by remember { mutableStateOf("") }
    var pairingCodeExpiry by remember { mutableStateOf(0) }
    var earnedToday by remember { mutableStateOf(0) }
    var rewardBankMinutes by remember { mutableStateOf(0) }
    var rewardAppSelection by remember {
        mutableStateOf(context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE)
            .getString("pending_reward_package", "") ?: "")
    }
    var rewardMinutesToRedeem by remember { mutableStateOf("10") }
    var childRewardCapEdits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var childStreakDays by remember { mutableStateOf(0) }
    var activeTask by remember { mutableStateOf<TaskRow?>(null) }
    var questionIndex by remember { mutableStateOf(0) }
    var expandedFourDQuestionId by remember { mutableStateOf<String?>(null) }
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var taskResult by remember { mutableStateOf<TaskResult?>(null) }
    var taskDeadlineAtMillis by remember { mutableStateOf<Long?>(null) }
    var taskSecondsRemaining by remember { mutableStateOf(0) }
    var questionDeadlineAtMillis by remember { mutableStateOf<Long?>(null) }
    var questionDeadlineQuestionId by remember { mutableStateOf<String?>(null) }
    var questionSecondsRemaining by remember { mutableStateOf(0) }
    var expiredQuestionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val taskDraftPrefs = remember { context.getSharedPreferences("edulock_task_drafts", Context.MODE_PRIVATE) }
    fun saveTaskDraft(
        taskId: String,
        draftAnswers: Map<String, String>,
        draftIndex: Int,
        draftExpiredQuestionIds: Set<String> = expiredQuestionIds,
        taskDeadline: Long? = taskDeadlineAtMillis,
        questionDeadline: Long? = questionDeadlineAtMillis,
        deadlineQuestionId: String? = questionDeadlineQuestionId
    ) {
        val encodedAnswers = JSONObject()
        draftAnswers.forEach { (id, value) -> encodedAnswers.put(id, value) }
        val encodedExpiredIds = JSONArray()
        draftExpiredQuestionIds.forEach { encodedExpiredIds.put(it) }
        taskDraftPrefs.edit().putString(taskId, JSONObject()
            .put("answers", encodedAnswers)
            .put("question_index", draftIndex)
            .put("expired_question_ids", encodedExpiredIds)
            .put("task_deadline_at_ms", taskDeadline ?: JSONObject.NULL)
            .put("question_deadline_at_ms", questionDeadline ?: JSONObject.NULL)
            .put("question_deadline_question_id", deadlineQuestionId ?: JSONObject.NULL)
            .put("updated_at", System.currentTimeMillis()).toString()).apply()
    }
    fun readTaskDraft(taskId: String): LocalTaskDraft {
        return try {
            val saved = JSONObject(taskDraftPrefs.getString(taskId, "{}") ?: "{}")
            val savedAnswers = saved.optJSONObject("answers") ?: JSONObject()
            val parsedAnswers = linkedMapOf<String, String>()
            val keys = savedAnswers.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                parsedAnswers[key] = savedAnswers.optString(key, "")
            }
            val expiredJson = saved.optJSONArray("expired_question_ids") ?: JSONArray()
            val expired = (0 until expiredJson.length()).mapNotNull { index ->
                expiredJson.optString(index).takeIf { it.isNotBlank() && it != "null" }
            }.toSet()
            LocalTaskDraft(
                answers = parsedAnswers,
                questionIndex = saved.optInt("question_index", 0),
                expiredQuestionIds = expired,
                taskDeadlineAtMillis = saved.optLong("task_deadline_at_ms", 0L).takeIf { it > 0L },
                questionDeadlineAtMillis = saved.optLong("question_deadline_at_ms", 0L).takeIf { it > 0L },
                questionDeadlineQuestionId = saved.optString("question_deadline_question_id")
                    .takeIf { it.isNotBlank() && it != "null" }
            )
        } catch (_: Exception) { LocalTaskDraft() }
    }
    LaunchedEffect(
        activeTask?.id, answers, questionIndex, expiredQuestionIds,
        taskDeadlineAtMillis, questionDeadlineAtMillis, questionDeadlineQuestionId
    ) {
        activeTask?.id?.let {
            saveTaskDraft(
                it, answers, questionIndex, expiredQuestionIds,
                taskDeadlineAtMillis, questionDeadlineAtMillis, questionDeadlineQuestionId
            )
        }
    }

    var childName by remember { mutableStateOf("") }
    var childLanguage by remember { mutableStateOf("en") }
    var childDailyCap by remember { mutableStateOf("60") }
    var childCountryCode by remember { mutableStateOf("PK") }
    var childSchoolGrade by remember { mutableStateOf("1") }
    var childAgeYears by remember { mutableStateOf("8") }
    var childAvatarIcon by remember { mutableStateOf("🦊") }
    var childStudyTrack by remember { mutableStateOf("none") }
    var childReligionEnabled by remember { mutableStateOf(false) }
    var studyTrackPickerTarget by remember { mutableStateOf("parent") }
    var studyTrackChildId by remember { mutableStateOf("") }
    var childDailyGameLimit by remember { mutableStateOf("20") }
    var childGameLimitEdits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var kidsGameSecondsRemaining by remember { mutableStateOf(0) }
    var countryPickerOpen by remember { mutableStateOf(false) }
    var languagePickerOpen by remember { mutableStateOf(false) }
    var languageSearch by remember { mutableStateOf("") }
    var countryPickerTarget by remember { mutableStateOf("child") }
    var countrySearch by remember { mutableStateOf("") }
    var parentCountryCode by remember { mutableStateOf("PK") }
    var parentLearningLanguageCode by remember { mutableStateOf("ur") }
    var parentStudyTrack by remember { mutableStateOf("none") }
    var parentDisplayLanguageCode by remember {
        mutableStateOf(
            context.getSharedPreferences("edulock_display_language", Context.MODE_PRIVATE)
                .getString("language", "en") ?: "en"
        )
    }
    var languagePickerTarget by remember { mutableStateOf("curriculum") }
    var studyTrackPickerOpen by remember { mutableStateOf(false) }
    var familySettingsOpen by remember { mutableStateOf(false) }
    var curriculumCountryCode by remember { mutableStateOf("PK") }
    var curriculumGradeLevel by remember { mutableStateOf("1") }
    var curriculumAgeYears by remember { mutableStateOf("8") }
    var curriculumSubjectCode by remember { mutableStateOf("math") }
    var curriculumLanguageCode by remember { mutableStateOf("en") }
    var curriculumSearchResult by remember { mutableStateOf<CurriculumSearchResult?>(null) }
    var curriculumSelectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var subjectTitle by remember { mutableStateOf("") }
    var subjectLanguage by remember { mutableStateOf("en") }
    var subjectCountryCode by remember { mutableStateOf("PK") }
    var subjectGradeLevel by remember { mutableStateOf("1") }
    var editingSubjectId by remember { mutableStateOf<String?>(null) }
    var questionPrompt by remember { mutableStateOf("") }
    var questionPictureUri by remember { mutableStateOf<Uri?>(null) }
    var questionPictureBytes by remember { mutableStateOf<ByteArray?>(null) }
    var questionPicturePreview by remember { mutableStateOf<Bitmap?>(null) }
    var questionPictureError by remember { mutableStateOf<String?>(null) }
    var displayedQuestionImageUrl by remember { mutableStateOf<String?>(null) }
    var questionImageLoading by remember { mutableStateOf(false) }
    var question3DModel by remember { mutableStateOf("none") }
    var questionType by remember { mutableStateOf("multiple_choice") }
    var questionOptions by remember { mutableStateOf("") }
    var questionAnswer by remember { mutableStateOf("") }
    var questionSolution by remember { mutableStateOf("") }
    var questionSolutions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var templateCategory by remember { mutableStateOf("Mathematics") }
    var questionLanguage by remember { mutableStateOf("en") }
    var questionConceptKey by remember { mutableStateOf("") }
    var translationLanguage by remember { mutableStateOf("ur") }
    var translationPrompt by remember { mutableStateOf("") }
    var translationOptions by remember { mutableStateOf("") }
    var translationAcceptedAnswers by remember { mutableStateOf("") }
    var bulkTranslationLanguage by remember { mutableStateOf("ur") }
    var templateTranslationLanguage by remember { mutableStateOf("ur") }
    var translatedTemplateRows by remember { mutableStateOf<Map<String, ParentLearningTemplate>>(emptyMap()) }
    var taskTitle by remember { mutableStateOf("") }
    var taskQuestionCount by remember { mutableStateOf("5") }
    var taskPassPercent by remember { mutableStateOf("70") }
    var taskRewardMinutes by remember { mutableStateOf("3") }
    var taskDailyCapMinutes by remember { mutableStateOf("60") }
    var taskTimeLimitSeconds by remember { mutableStateOf("0") }
    var taskQuestionTimeLimitSeconds by remember { mutableStateOf("0") }
    var taskRewardToBank by remember { mutableStateOf(true) }
    var taskRewardPackageName by remember { mutableStateOf("") }
    var taskRewardGroupId by remember { mutableStateOf("") }
    var selectedTaskSubjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var appGroups by remember { mutableStateOf(emptyList<AppGroup>()) }
    var appGroupName by remember { mutableStateOf("") }
    var appGroupEditingId by remember { mutableStateOf<String?>(null) }
    var appGroupSelectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var appPackage by remember { mutableStateOf("") }
    var appDisplayName by remember { mutableStateOf("") }
    var appDailyLimit by remember { mutableStateOf("60") }
    var appLimitEdits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var appSearchQuery by remember { mutableStateOf("") }
    var appStatusFilter by remember { mutableStateOf("All") }
    var appUsageChild by remember { mutableStateOf<ChildProfile?>(null) }
    var appUsageReports by remember { mutableStateOf(emptyList<AppUsageReport>()) }
    var childLocationSharing by remember {
        mutableStateOf(context.getSharedPreferences(ChildLocationService.PREFS, Context.MODE_PRIVATE)
            .getBoolean(ChildLocationService.KEY_SHARING_ENABLED, false))
    }
    var childLocationRows by remember { mutableStateOf(emptyList<ChildLocationSnapshot>()) }
    var pendingChildAction by remember { mutableStateOf<Pair<ChildProfile, String>?>(null) }
    var protectionAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var protectionAccessibilityOperational by remember { mutableStateOf(isAccessibilityServiceOperational(context)) }
    var protectionDeviceAdminEnabled by remember { mutableStateOf(isDeviceAdminActive(context)) }
    var protectionManagedEnabled by remember { mutableStateOf(isManagedDeviceProtectionActive(context)) }
    var childRulesSyncError by remember { mutableStateOf<String?>(null) }
    var childRulesLastSyncedAt by remember {
        mutableStateOf(context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE)
            .getLong("rules_synced_at", 0L).takeIf { it > 0L })
    }

    fun refreshProtectionStatus() {
        protectionAccessibilityEnabled = isAccessibilityServiceEnabled(context)
        protectionAccessibilityOperational = isAccessibilityServiceOperational(context)
        protectionDeviceAdminEnabled = isDeviceAdminActive(context)
        protectionManagedEnabled = isManagedDeviceProtectionActive(context)
    }

    val accessibilitySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshProtectionStatus()
        notice = when {
            isAccessibilityServiceOperational(context) ->
                "Android confirms EduLock's Accessibility service is enabled and responding on this phone."
            isAccessibilityServiceEnabled(context) ->
                "Android shows Accessibility enabled, but EduLock has not confirmed a live service heartbeat. Refresh status before relying on blocking."
            else ->
                "Accessibility was not enabled. Automatic app restrictions are not active on this phone."
        }
    }
    val deviceAdminSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshProtectionStatus()
        notice = if (isDeviceAdminActive(context)) {
            "Android confirms device-administrator permission is enabled. This alone does not provide kiosk or Clear-data protection."
        } else {
            "Device-administrator permission was not enabled. EduLock has not applied or reported that protection."
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshProtectionStatus()
                val modePrefs = context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE)
                ChildDeviceKioskPolicy.enterOrExitLockTask(context, modePrefs.getBoolean("kids_mode_enabled", false))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    fun changeWeeklyWindow(appId: String, dayKey: String, update: (AppDayWindow) -> AppDayWindow) {
        val current = weeklyScheduleEdits[appId]
            ?: allowedApps.firstOrNull { it.id == appId }?.let { AppWeeklySchedule.parse(it.weeklyScheduleJson) }
            ?: AppWeeklySchedule.defaultSchedule()
        val oldWindow = current.days[dayKey] ?: AppDayWindow(enabled = false)
        weeklyScheduleEdits = weeklyScheduleEdits + (appId to AppWeeklySchedule(current.days + (dayKey to update(oldWindow))))
    }

    fun remainingKidsGameSeconds(learner: ChildProfile): Int {
        val prefs = context.getSharedPreferences("edulock_kids_games", Context.MODE_PRIVATE)
        val day = LocalDate.now().toString()
        val usedKey = "used_ms_" + learner.id + "_" + day
        val usedSeconds = (prefs.getLong(usedKey, 0L) / 1000L).toInt()
        return (learner.dailyGameLimitMinutes * 60 - usedSeconds).coerceAtLeast(0)
    }

    fun openKidsGames() {
        val learner = childProfile
        if (learner == null) {
            notice = "Pair a child profile before starting games."
            return
        }
        kidsGameSecondsRemaining = remainingKidsGameSeconds(learner)
        screen = AppScreen.KIDS_GAMES
        notice = null
    }

    fun openKidsDrawingStudio() {
        val learner = childProfile
        if (learner == null) {
            screen = AppScreen.DRAWING_STUDIO
            notice = null
            return
        }
        kidsGameSecondsRemaining = remainingKidsGameSeconds(learner)
        if (kidsGameSecondsRemaining <= 0) {
            screen = AppScreen.KIDS_GAMES
            notice = "Today's educational game time is finished. Ask your parent to change the daily allowance."
        } else {
            screen = AppScreen.DRAWING_STUDIO
            notice = null
        }
    }

    fun openSolarQuiz() {
        val learner = childProfile
        if (learner == null) {
            notice = "Pair a child profile before starting the planet quiz."
            return
        }
        kidsGameSecondsRemaining = remainingKidsGameSeconds(learner)
        if (kidsGameSecondsRemaining <= 0) {
            screen = AppScreen.KIDS_GAMES
            notice = "Today's educational game time is finished. Ask your parent to change the daily allowance."
        } else {
            solarQuizReturnScreen = AppScreen.CHILD_HOME
            screen = AppScreen.SOLAR_SYSTEM_QUIZ
            notice = null
        }
    }

    fun openKids3DLab() {
        val learner = childProfile
        if (learner == null) {
            selected3DModel = "solar-system"
            solarQuizReturnScreen = AppScreen.PARENT_HOME
            screen = AppScreen.LEARNING_3D_LAB
            notice = null
            return
        }
        kidsGameSecondsRemaining = remainingKidsGameSeconds(learner)
        if (kidsGameSecondsRemaining <= 0) {
            screen = AppScreen.KIDS_GAMES
            notice = "Today's educational game time is finished. Ask your parent to change the daily allowance."
        } else {
            selected3DModel = "solar-system"
            solarQuizReturnScreen = AppScreen.CHILD_HOME
            screen = AppScreen.LEARNING_3D_LAB
            notice = null
        }
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


    val questionPicturePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedUri ->
        if (selectedUri != null) {
            questionPictureUri = selectedUri
            questionPictureError = null
            execute {
                val rawBytes = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(selectedUri)?.use { it.readBytes() }
                        ?: throw IOException("Could not read the selected picture.")
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("The selected file is not a supported picture.")
                val factor = maxOf(bounds.outWidth / 1024f, bounds.outHeight / 1024f, 1f)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = Integer.highestOneBit(factor.toInt().coerceAtLeast(1))
                }
                val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
                    ?: throw IOException("Could not decode the selected picture.")
                val scale = minOf(1f, 1024f / maxOf(decoded.width, decoded.height).toFloat())
                val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                ) else decoded
                val stream = ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)) throw IOException("Could not compress the selected picture.")
                val bytes = stream.toByteArray()
                if (bytes.size > 2_000_000) throw IOException("The compressed picture is larger than 2 MB. Please choose a smaller picture.")
                questionPictureBytes = bytes
                questionPicturePreview = bitmap
            }
        }
    }
    val launchPreferences = remember { context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE) }
    LaunchedEffect(childProfile?.id, screen) {
        val requestedTaskId = launchPreferences.getString("pending_task_id", null)
        if (requestedTaskId != null && childProfile != null && screen == AppScreen.CHILD_HOME &&
            repository.currentSession()?.accountRole == "child" && !busy) {
            // Keep the pending request until loading succeeds so offline/temporary errors can retry.
            val target = tasks.firstOrNull { it.id == requestedTaskId && it.active }
            if (target == null) {
                launchPreferences.edit().remove("pending_task_id").apply()
                notice = "The requested task is no longer available. Ask your parent to assign an active task."
            } else {
                execute {
                    val loadedQuestions = repository.loadQuestionsForTask(target.id, target.subjectId, target.questionCount)
                    if (loadedQuestions.isEmpty()) {
                        throw IllegalStateException("This task has no linked questions. Ask your parent to repair it.")
                    }
                    if (loadedQuestions.size != target.questionCount) {
                        throw IllegalStateException("This task needs " + target.questionCount + " questions, but only " + loadedQuestions.size + " are available. Ask your parent to repair the task.")
                    }
                    val loadedTranslations = repository.loadQuestionTranslations(loadedQuestions.map { it.id })
                    questions = loadedQuestions
                    questionTranslations = loadedTranslations
                    answerLanguages = loadedQuestions.associate { item ->
                        val preferred = childProfile?.preferredLanguage ?: item.languageCode
                        val translated = loadedTranslations[item.id].orEmpty().any { it.languageCode.equals(preferred, ignoreCase = true) }
                        item.id to if (translated) preferred.lowercase() else item.languageCode
                    }
                    activeTask = target
                    questionIndex = 0
                    answers = emptyMap()
                    launchPreferences.edit().remove("pending_task_id").apply()
                    notice = "Screen-time target reached. Answer the learning task to earn more time."
                    screen = AppScreen.PLAY_TASK
                }
            }
        }
    }


    pendingChildAction?.let { (targetChild, action) ->
        val isDelete = action == "delete"
        AlertDialog(
            onDismissRequest = { pendingChildAction = null },
            title = { AppUiText(if (isDelete) "Delete child profile?" else "Deactivate device link?") },
            text = {
                AppUiText(
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
                }) { AppUiText(if (isDelete) "Delete permanently" else "Deactivate link") }
            },
            dismissButton = {
                TextButton(onClick = { pendingChildAction = null }) { AppUiText("Cancel") }
            }
        )
    }

    suspend fun refreshParentData() {
        familyId = repository.ensureParentFamily(
            displayName.ifBlank { "My family" }, parentCountryCode, parentLearningLanguageCode,
            parentStudyTrack, parentDisplayLanguageCode
        )
        val prefs = repository.loadParentLearningSettings(familyId)
        parentCountryCode = prefs.countryCode
        parentLearningLanguageCode = prefs.learningLanguageCode
        parentStudyTrack = prefs.studyTrack
        parentDisplayLanguageCode = prefs.displayLanguageCode
        context.getSharedPreferences("edulock_display_language", Context.MODE_PRIVATE)
            .edit().putString("language", parentDisplayLanguageCode).apply()
        children = repository.loadChildren(familyId)
        subjects = repository.loadSubjects(familyId, parentCountryCode)
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
            childRulesLastSyncedAt = context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE)
                .getLong("rules_synced_at", System.currentTimeMillis())
            childRulesSyncError = null
            ChildDeviceKioskPolicy.enterOrExitLockTask(context, linked.kidsModeEnabled)
            repository.syncAppUsage(linked.id)
        } catch (e: Exception) {
            childRulesSyncError = e.message ?: "Supabase did not confirm the child-device rules sync."
            notice = "Signed in, but device settings or usage did not sync: " + childRulesSyncError
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
            familyId = repository.ensureParentFamily(
                displayName.ifBlank { "My family" }, parentCountryCode, parentLearningLanguageCode,
                parentStudyTrack, parentDisplayLanguageCode
            )
            val prefs = repository.loadParentLearningSettings(familyId)
            parentCountryCode = prefs.countryCode
            parentLearningLanguageCode = prefs.learningLanguageCode
            parentStudyTrack = prefs.studyTrack
            parentDisplayLanguageCode = prefs.displayLanguageCode
            context.getSharedPreferences("edulock_display_language", Context.MODE_PRIVATE)
                .edit().putString("language", parentDisplayLanguageCode).apply()
            children = repository.loadChildren(familyId)
            subjects = repository.loadSubjects(familyId, parentCountryCode)
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

    LaunchedEffect(screen, activeTask?.id, questions.getOrNull(questionIndex)?.id) {
        val picture = questions.getOrNull(questionIndex)?.picturePath
        if (screen == AppScreen.PLAY_TASK && picture != null) {
            displayedQuestionImageUrl = null
            questionImageLoading = true
            try {
                displayedQuestionImageUrl = repository.createSignedQuestionImageUrl(picture)
            } catch (error: Exception) {
                notice = error.message ?: "Could not load the private question picture."
            } finally {
                questionImageLoading = false
            }
        } else {
            displayedQuestionImageUrl = null
            questionImageLoading = false
        }
    }

    LaunchedEffect(screen, activeTask?.id) {
        val task = activeTask
        if (screen == AppScreen.PLAY_TASK && task?.timeLimitSeconds != null) {
            val limit = task.timeLimitSeconds.coerceIn(10, 3600)
            if (taskDeadlineAtMillis == null) taskDeadlineAtMillis = System.currentTimeMillis() + limit * 1000L
            while (screen == AppScreen.PLAY_TASK && activeTask?.id == task.id) {
                val remaining = (((taskDeadlineAtMillis ?: System.currentTimeMillis()) - System.currentTimeMillis() + 999L) / 1000L)
                    .toInt().coerceAtLeast(0)
                taskSecondsRemaining = remaining
                if (remaining <= 0) {
                    execute {
                        val stillActive = activeTask
                        if (screen == AppScreen.PLAY_TASK && stillActive?.id == task.id) {
                            val filledAnswers = questions.associate { question ->
                                question.id to answers[question.id].orEmpty()
                            }
                            try {
                                val submitted = repository.submitTask(task.id, filledAnswers, answerLanguages)
                                taskResult = submitted
                                taskDraftPrefs.edit().remove(task.id).apply()
                                val linked = childProfile
                                if (linked != null) {
                                    try {
                                        earnedToday = repository.refreshChildDeviceRestrictions(linked)
                                        rewardBankMinutes = repository.loadChildRewardBankMinutes(linked.id)
                                        tasks = repository.loadChildTasks(linked.id)
                                    } catch (_: Exception) { earnedToday = submitted.dailyUsedMinutes }
                                }
                                taskDeadlineAtMillis = null
                                questionDeadlineAtMillis = null
                                questionDeadlineQuestionId = null
                                questionSecondsRemaining = 0
                                expiredQuestionIds = emptySet()
                                screen = AppScreen.TASK_RESULT
                                notice = "Time is up. Your answers were submitted automatically."
                            } catch (error: Exception) {
                                notice = "Time is up, but submission failed: " + (error.message ?: "please reconnect and retry.")
                                taskDeadlineAtMillis = System.currentTimeMillis() + 30_000L
                            }
                        }
                    }
                    break
                }
                delay(250)
            }
        } else if (activeTask == null) {
            taskSecondsRemaining = 0
        }
    }

    // A separate countdown can be configured for each question. The deadline and
    // expired-question set are saved in the task draft so leaving the task does not reset them.
    LaunchedEffect(
        screen, activeTask?.id, questions.getOrNull(questionIndex)?.id,
        activeTask?.questionTimeLimitSeconds
    ) {
        val task = activeTask
        val question = questions.getOrNull(questionIndex)
        val perQuestionLimit = task?.questionTimeLimitSeconds
        if (screen == AppScreen.PLAY_TASK && task != null && question != null &&
            perQuestionLimit != null && question.id !in expiredQuestionIds) {
            if (questionDeadlineQuestionId != question.id || questionDeadlineAtMillis == null) {
                questionDeadlineQuestionId = question.id
                questionDeadlineAtMillis = System.currentTimeMillis() + perQuestionLimit * 1000L
            }
            while (screen == AppScreen.PLAY_TASK &&
                activeTask?.id == task.id &&
                questions.getOrNull(questionIndex)?.id == question.id) {
                val deadline = questionDeadlineAtMillis ?: (System.currentTimeMillis() + perQuestionLimit * 1000L)
                val remaining = (((deadline - System.currentTimeMillis() + 999L) / 1000L))
                    .toInt().coerceAtLeast(0)
                questionSecondsRemaining = remaining
                if (remaining <= 0) {
                    expiredQuestionIds = expiredQuestionIds + question.id
                    questionDeadlineAtMillis = null
                    questionDeadlineQuestionId = null
                    if (questionIndex < questions.lastIndex) {
                        questionIndex = (questionIndex + 1).coerceAtMost(questions.lastIndex)
                        notice = "Question time ended. That question is locked; continue with the next one."
                    } else {
                        execute {
                            if (screen == AppScreen.PLAY_TASK && activeTask?.id == task.id) {
                                try {
                                    val filledAnswers = questions.associate { item ->
                                        item.id to answers[item.id].orEmpty()
                                    }
                                    val submitted = repository.submitTask(task.id, filledAnswers, answerLanguages)
                                    taskResult = submitted
                                    taskDraftPrefs.edit().remove(task.id).apply()
                                    val linked = childProfile
                                    if (linked != null) {
                                        try {
                                            earnedToday = repository.refreshChildDeviceRestrictions(linked)
                                            rewardBankMinutes = repository.loadChildRewardBankMinutes(linked.id)
                                            tasks = repository.loadChildTasks(linked.id)
                                        } catch (_: Exception) { earnedToday = submitted.dailyUsedMinutes }
                                    }
                                    taskDeadlineAtMillis = null
                                    questionDeadlineAtMillis = null
                                    questionDeadlineQuestionId = null
                                    questionSecondsRemaining = 0
                                    expiredQuestionIds = emptySet()
                                    screen = AppScreen.TASK_RESULT
                                    notice = "The last question's time ended. Your answers were submitted."
                                } catch (error: Exception) {
                                    notice = "The last question timed out, but submission failed: " +
                                        (error.message ?: "reconnect and retry.")
                                }
                            }
                        }
                    }
                    break
                }
                delay(250)
            }
        } else {
            questionSecondsRemaining = 0
        }
    }

    // Educational games have a separate daily cap. Time is recorded locally while the
    // arcade screen is open, so entering an in-app game cannot bypass the parent's allowance.
    LaunchedEffect(screen, childProfile?.id) {
        val learner = childProfile
        val isChildSolarGame = screen == AppScreen.SOLAR_SYSTEM_QUIZ && solarQuizReturnScreen == AppScreen.CHILD_HOME
        val isChild3DLab = screen == AppScreen.LEARNING_3D_LAB && solarQuizReturnScreen == AppScreen.CHILD_HOME
        val isChildDrawingGame = screen == AppScreen.DRAWING_STUDIO && learner != null
        if ((screen == AppScreen.KIDS_GAMES || isChildSolarGame || isChild3DLab || isChildDrawingGame) && learner != null) {
            val prefs = context.getSharedPreferences("edulock_kids_games", Context.MODE_PRIVATE)
            while ((screen == AppScreen.KIDS_GAMES ||
                    (screen == AppScreen.SOLAR_SYSTEM_QUIZ && solarQuizReturnScreen == AppScreen.CHILD_HOME) ||
                    (screen == AppScreen.LEARNING_3D_LAB && solarQuizReturnScreen == AppScreen.CHILD_HOME) ||
                    (screen == AppScreen.DRAWING_STUDIO && childProfile?.id == learner.id)) &&
                childProfile?.id == learner.id) {
                delay(1000)
                val day = LocalDate.now().toString()
                val usedKey = "used_ms_" + learner.id + "_" + day
                val usedMillis = prefs.getLong(usedKey, 0L) + 1000L
                prefs.edit().putLong(usedKey, usedMillis).apply()
                val remaining = (learner.dailyGameLimitMinutes * 60L - usedMillis / 1000L).toInt().coerceAtLeast(0)
                kidsGameSecondsRemaining = remaining
                if (remaining <= 0) {
                    screen = AppScreen.CHILD_HOME
                    notice = "Today's educational game time is finished. Ask your parent to change the daily allowance."
                    break
                }
            }
        }
    }

    LaunchedEffect(screen, childProfile?.id) {
        val activeChildId = childProfile?.id
        if (screen == AppScreen.CHILD_HOME && !activeChildId.isNullOrBlank()) {
            try {
                childStreakDays = repository.loadChildTaskStreak(activeChildId).first
            } catch (_: Exception) {
                childStreakDays = 0
            }
        }
    }

    LaunchedEffect(screen, childProfile?.id) {
        val activeChildId = childProfile?.id
        if (screen == AppScreen.CHILD_HOME && !activeChildId.isNullOrBlank()) {
            while (true) {
                delay(30_000)
                try {
                    val linked = childProfile
                    if (linked != null && linked.id == activeChildId) {
                        // Refresh tasks, profile limits and parental rules; updated game allowances apply without sign-out.
                        val currentProfile = repository.loadCurrentChild() ?: linked
                        childProfile = currentProfile
                        tasks = repository.loadChildTasks(activeChildId)
                        allowedApps = repository.loadAllowedApps(activeChildId)
                        appGroups = repository.loadAppGroups(activeChildId)
                        earnedToday = repository.refreshChildDeviceRestrictions(currentProfile)
                        childRulesLastSyncedAt = context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE)
                            .getLong("rules_synced_at", System.currentTimeMillis())
                        childRulesSyncError = null
                        ChildDeviceKioskPolicy.enterOrExitLockTask(context, currentProfile.kidsModeEnabled)
                        repository.syncAppUsage(activeChildId)
                    }
                } catch (e: Exception) {
                    childRulesSyncError = e.message ?: "Supabase did not confirm the child-device rules sync."
                    // Keep the last confirmed rules while offline and surface the failed refresh.
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
                    val protectedSystemPackages = setOf(
                        "com.android.settings", "com.android.systemui",
                        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
                        "com.android.packageinstaller", "com.google.android.packageinstaller",
                        "com.android.launcher3", "com.google.android.apps.nexuslauncher",
                        "com.miui.home", "com.samsung.android.oneui.home",
                        "com.oplus.launcher", "com.huawei.android.launcher"
                    )
                    val visibleApps = installedApps.mapNotNull { info ->
                        val pkg = info.packageName
                        if (pkg == context.packageName || pkg in protectedSystemPackages ||
                            pkg.startsWith("com.android.internal.") ||
                            pkg.startsWith("com.android.providers.") ||
                            pkg == "com.google.android.adservices.api" ||
                            pkg.contains("agingfunction", ignoreCase = true)) return@mapNotNull null
                        // Only include apps a child can actually open. This removes navigation
                        // components, hidden providers and background-only services from the list.
                        if (context.packageManager.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null
                        val label = try {
                            info.loadLabel(context.packageManager)?.toString()?.trim().orEmpty()
                        } catch (_: Exception) { "" }
                        if (label.isBlank() ||
                            label.equals("3 Button Navigation Bar", ignoreCase = true) ||
                            label.equals("Ad Privacy", ignoreCase = true) ||
                            label.equals("AgingFunction", ignoreCase = true)) null else pkg to label
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
                delay(30_000)
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
            // Don't let Android Back from child pairing return to parent sign-in/role selection.
            AppScreen.PAIR_CHILD -> AppScreen.PAIR_CHILD
            // Android Back must not send a signed-in parent to the sign-in screen.
            AppScreen.PARENT_HOME -> AppScreen.PARENT_HOME
            AppScreen.AUTH -> AppScreen.AUTH
            // Keep a child device out of the parent login/dashboard route.
            AppScreen.CHILD_HOME -> AppScreen.CHILD_HOME
            AppScreen.CHILDREN, AppScreen.SUBJECTS, AppScreen.ASSIGN_TASK, AppScreen.REPORTS, AppScreen.PROTECTION_STATUS, AppScreen.CHILD_LOCATIONS -> AppScreen.PARENT_HOME
            AppScreen.ADD_CHILD, AppScreen.APP_USAGE_REPORT, AppScreen.ALLOWED_APPS, AppScreen.CURRICULUM_LIBRARY -> AppScreen.CHILDREN
            AppScreen.ADD_SUBJECT, AppScreen.QUESTIONS -> AppScreen.SUBJECTS
            AppScreen.LEARNING_TEMPLATES -> if (selectedSubject != null) AppScreen.QUESTIONS else AppScreen.PARENT_HOME
            AppScreen.ADD_QUESTION, AppScreen.ADD_TRANSLATION -> AppScreen.QUESTIONS
            AppScreen.PLAY_TASK, AppScreen.TASK_RESULT -> AppScreen.CHILD_HOME
            AppScreen.SOLAR_SYSTEM_QUIZ -> solarQuizReturnScreen
            AppScreen.KIDS_GAMES -> AppScreen.CHILD_HOME
            AppScreen.APP_SETTINGS -> AppScreen.PARENT_HOME
            AppScreen.LEARNING_3D_LAB -> if (solarQuizReturnScreen == AppScreen.CHILD_HOME) AppScreen.CHILD_HOME else AppScreen.PARENT_HOME
            AppScreen.DRAWING_STUDIO -> if (childProfile != null) AppScreen.CHILD_HOME else AppScreen.PARENT_HOME
        }
        notice = null
    }

    LaunchedEffect(screen, childProfile?.id) {
        val linked = childProfile
        if (linked != null && session?.accountRole == "child" &&
            (screen == AppScreen.CHILD_HOME || screen == AppScreen.TASK_RESULT)) {
            try {
                rewardBankMinutes = repository.loadChildRewardBankMinutes(linked.id)
                val approved = allowedApps.filter { it.enabled }
                if (approved.none { it.packageName == rewardAppSelection }) {
                    val pendingTarget = context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE)
                        .getString("pending_reward_package", null)
                    rewardAppSelection = approved.firstOrNull { it.packageName == pendingTarget }?.packageName
                        ?: approved.firstOrNull()?.packageName.orEmpty()
                }
                val requested = rewardMinutesToRedeem.toIntOrNull() ?: 0
                if (rewardBankMinutes > 0 && (requested < 1 || requested > rewardBankMinutes)) {
                    rewardMinutesToRedeem = minOf(rewardBankMinutes, 10).toString()
                }
            } catch (_: Exception) {
                // Keep the last confirmed balance visible when offline or before migration 0034 is applied.
            }
        }
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
        CompositionLocalProvider(
            LocalLayoutDirection provides directionFor(parentDisplayLanguageCode),
            LocalEduLockDisplayLanguage provides parentDisplayLanguageCode
        ) {
        if (countryPickerOpen) {
            val countryOptions = remember(countrySearch) {
                Locale.getISOCountries().map { code ->
                    code to Locale("", code).getDisplayCountry(Locale.ENGLISH).ifBlank { code }
                }.filter { (_, name) -> name.contains(countrySearch, ignoreCase = true) }
                    .sortedBy { it.second }
            }
            AlertDialog(
                onDismissRequest = { countryPickerOpen = false },
                title = { AppUiText("Choose a country") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Field(countrySearch, { countrySearch = it }, "Search countries")
                        countryOptions.forEach { (code, name) ->
                            TextButton(
                                onClick = {
                                    when (countryPickerTarget) {
                                        "child" -> childCountryCode = code
                                        "subject" -> subjectCountryCode = code
                                        "parent" -> {
                                            parentCountryCode = code
                                            selectedSubject = null
                                            curriculumCountryCode = code
                                            if (familyId.isNotBlank() && session?.accountRole == "parent") {
                                                execute {
                                                    repository.updateParentLearningSettings(familyId, parentCountryCode, parentLearningLanguageCode, parentStudyTrack, parentDisplayLanguageCode)
                                                    subjects = repository.loadSubjects(familyId, parentCountryCode)
                                                    notice = "Country saved. The question bank now prioritizes this country's curriculum."
                                                }
                                            }
                                        }
                                        else -> {
                                            curriculumCountryCode = code
                                            curriculumSubjectCode = "math"
                                            curriculumLanguageCode = "en"
                                            curriculumSearchResult = null
                                            curriculumSelectedIds = emptySet()
                                        }
                                    }
                                    countryPickerOpen = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AppUiText(name + if (countryPickerTarget == "curriculum") " ($code)" else "", modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { countryPickerOpen = false }) { AppUiText("Close") } }
            )
        }
        if (languagePickerOpen) {
            val languageOptions = remember(languageSearch, languagePickerTarget) {
                val supportedUiLanguages = listOf("en", "ur", "sd", "hi", "af", "sq", "be", "bg", "bn", "ca", "zh", "hr", "cs", "da", "nl", "et", "fi", "fr", "gl", "ka", "de", "el", "gu", "ht", "he", "hu", "is", "id", "ga", "it", "ja", "kn", "ko", "lv", "lt", "mk", "ms", "ml", "mt", "mr", "ne", "no", "fa", "pl", "pt", "ro", "ru", "sr", "sk", "sl", "es", "sw", "sv", "tl", "ta", "te", "th", "tr", "uk", "vi", "cy", "ar", "pa")
                val optionCodes = when (languagePickerTarget) {
                    "display" -> supportedUiLanguages.filter { code ->
                        code == "en" || code == "sd" || TranslateLanguage.fromLanguageTag(code) != null
                    }
                    "translation" -> supportedUiLanguages.filter { code -> TranslateLanguage.fromLanguageTag(code) != null }
                    else -> Locale.getISOLanguages().distinct()
                }
                optionCodes.map { code ->
                    val languageLocale = Locale.forLanguageTag(code)
                    code to when (code) {
                        "ur" -> "اردو"
                        "sd" -> "سنڌي"
                        "hi" -> "हिन्दी"
                        "en" -> "English"
                        else -> languageLocale.getDisplayLanguage(Locale.ENGLISH).ifBlank { code }
                    }
                }.filter { (code, name) ->
                    name.contains(languageSearch, ignoreCase = true) ||
                        code.contains(languageSearch, ignoreCase = true)
                }.sortedBy { it.second.lowercase(Locale.ENGLISH) }
            }
            AlertDialog(
                onDismissRequest = { languagePickerOpen = false },
                title = { AppUiText(when (languagePickerTarget) {
                    "display" -> "Choose app display language"
                    "translation" -> "Choose translation language"
                    else -> "Choose a learning language"
                }) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Field(languageSearch, { languageSearch = it }, "Search language or code")
                        languageOptions.forEach { (code, name) ->
                            TextButton(
                                onClick = {
                                    when (languagePickerTarget) {
                                        "parent-learning" -> {
                                            parentLearningLanguageCode = code.lowercase(Locale.ROOT)
                                            if (familyId.isNotBlank() && session?.accountRole == "parent") execute {
                                                repository.updateParentLearningSettings(familyId, parentCountryCode, parentLearningLanguageCode, parentStudyTrack, parentDisplayLanguageCode)
                                                notice = "Preferred lesson language saved."
                                            }
                                        }
                                        "translation" -> {
                                            bulkTranslationLanguage = code.lowercase(Locale.ROOT)
                                            templateTranslationLanguage = code.lowercase(Locale.ROOT)
                                        }
                                        "display" -> {
                                            parentDisplayLanguageCode = code.lowercase(Locale.ROOT)
                                            context.getSharedPreferences("edulock_display_language", Context.MODE_PRIVATE)
                                                .edit().putString("language", parentDisplayLanguageCode).apply()
                                            if (familyId.isNotBlank() && session?.accountRole == "parent") execute {
                                                repository.updateParentLearningSettings(familyId, parentCountryCode, parentLearningLanguageCode, parentStudyTrack, parentDisplayLanguageCode)
                                                notice = "App display-language preference saved."
                                            }
                                        }
                                        else -> {
                                            curriculumLanguageCode = code.lowercase(Locale.ROOT)
                                            curriculumSearchResult = null
                                            curriculumSelectedIds = emptySet()
                                        }
                                    }
                                    languagePickerOpen = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AppUiText("$name ($code)", modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { languagePickerOpen = false }) { AppUiText("Close") }
                }
            )
        }
        if (studyTrackPickerOpen) {
            val pickerCountry = when (studyTrackPickerTarget) {
                "child" -> childCountryCode
                "existing-child" -> children.firstOrNull { it.id == studyTrackChildId }?.countryCode ?: parentCountryCode
                else -> parentCountryCode
            }
            val pickedTrack = when (studyTrackPickerTarget) {
                "child" -> childStudyTrack
                "existing-child" -> children.firstOrNull { it.id == studyTrackChildId }?.studyTrack ?: "none"
                else -> parentStudyTrack
            }
            AlertDialog(
                onDismissRequest = { studyTrackPickerOpen = false },
                title = { AppUiText("Choose a religious-education subject") },
                text = {
                    Column(Modifier.fillMaxWidth().height(420.dp).verticalScroll(rememberScrollState())) {
                        AppUiText("Optional for this child. Country changes the order of suggestions, but you choose the actual study track.")
                        religiousStudyOptionsForCountry(pickerCountry).forEach { (code, label) ->
                            TextButton(onClick = {
                                studyTrackPickerOpen = false
                                when (studyTrackPickerTarget) {
                                    "child" -> {
                                        childStudyTrack = code
                                        notice = if (code == "none") "Religious education is off for this child." else "$label selected for this child's learning profile."
                                    }
                                    "existing-child" -> {
                                        val childId = studyTrackChildId
                                        execute {
                                            val existingChild = children.firstOrNull { it.id == childId }
                                                ?: throw IllegalStateException("The child profile could not be found.")
                                            repository.updateChildStudyTrack(childId, code)
                                            if (code != "none") {
                                                val religiousSubject = repository.findOrCreateSubject(familyId, religiousSubjectTitle(code), existingChild.preferredLanguage, existingChild.countryCode)
                                            val starterQuestions = fourDTemplatesForSubject(religiousSubject.title, existingChild.preferredLanguage, existingChild.schoolGrade, existingChild.ageYears)
                                            repository.ensureTemplateQuestions(religiousSubject.id, starterQuestions)
                                            }
                                            children = repository.loadChildren(familyId)
                                            subjects = repository.loadSubjects(familyId, parentCountryCode)
                                            notice = if (code == "none") "Religious education disabled for this child." else "$label is set for this child. Matching questions are available from Learning Templates."
                                        }
                                    }
                                    else -> {
                                        parentStudyTrack = code
                                        if (familyId.isNotBlank() && session?.accountRole == "parent") execute {
                                            repository.updateParentLearningSettings(familyId, parentCountryCode, parentLearningLanguageCode, parentStudyTrack, parentDisplayLanguageCode)
                                            notice = "Default religious-study preference saved. Each child can have a separate choice."
                                        }
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                AppUiText((if (pickedTrack == code) "✓ " else "") + label, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { studyTrackPickerOpen = false }) { AppUiText("Close") } }
            )
        }
        if (familySettingsOpen) {
            AlertDialog(
                onDismissRequest = { familySettingsOpen = false },
                title = { AppUiText("Family learning settings") },
                text = {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppUiText("These preferences determine the curriculum country, lesson language and interface language. Religious education is chosen separately for each child.")
                        OutlinedButton(onClick = { familySettingsOpen = false; countryPickerTarget = "parent"; countrySearch = ""; countryPickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            AppUiText("🌍 Country: " + displayCountryName(parentCountryCode))
                        }
                        OutlinedButton(onClick = { familySettingsOpen = false; languagePickerTarget = "parent-learning"; languageSearch = ""; languagePickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            AppUiText("📚 Lesson language: " + languageLabel(parentLearningLanguageCode))
                        }
                        OutlinedButton(onClick = { familySettingsOpen = false; languagePickerTarget = "display"; languageSearch = ""; languagePickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            AppUiText("🌐 App display language: " + languageLabel(parentDisplayLanguageCode))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { familySettingsOpen = false }) { AppUiText("Done") }
                }
            )
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (screen) {
                AppScreen.DEVICE_SETUP -> ScreenFrame("Welcome to EduLock ✨", notice = notice, busy = busy) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF0FF))) {
                        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(84.dp).background(Brush.linearGradient(listOf(Color(0xFF6E60F5), Color(0xFF12B8AC))), RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                                AppUiText("🎓", fontSize = 42.sp)
                            }
                            AppUiText("Learn. Explore. Grow.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color(0xFF29245F))
                            AppUiText("A safer learning world for curious kids — managed by parents.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF566080))
                        }
                    }
                    AppUiText("Choose how you want to use EduLock.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    AppUiText("Parents manage learning and screen time. Children join through a secure one-time pairing code.")
                    Button(
                        onClick = { chooseParentDevice() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("This is the parent's phone") }
                    OutlinedButton(
                        onClick = { chooseChildDevice() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("This is the child's phone — pair with code") }
                }
                AppScreen.AUTH -> ScreenFrame(if (isRegistering) "Create your parent account" else "Welcome back", notice = notice, busy = busy) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0FF))) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(68.dp).background(Brush.linearGradient(listOf(Color(0xFF5B4CE8), Color(0xFF00A99D))), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                                AppUiText("🎓", fontSize = 34.sp)
                            }
                            AppUiText(if (isRegistering) "Build their bright future" else "Good to see you again", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF29245F))
                            AppUiText("Learn first. Play with purpose.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF626987))
                        }
                    }
                    AppUiText(if (isRegistering) "Create a parent account to manage learning, games and screen time." else "Sign in to continue managing your family.", style = MaterialTheme.typography.bodyMedium)
                    if (isRegistering) {
                        Field(displayName, { displayName = it }, "Parent name")
                        OutlinedButton(onClick = { countryPickerTarget = "parent"; countrySearch = ""; countryPickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            AppUiText("🌍 Curriculum country: " + displayCountryName(parentCountryCode))
                        }
                        OutlinedButton(onClick = { languagePickerTarget = "parent-learning"; languageSearch = ""; languagePickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            AppUiText("📚 Preferred lesson language: " + languageLabel(parentLearningLanguageCode))
                        }
                        OutlinedButton(onClick = { languagePickerTarget = "display"; languageSearch = ""; languagePickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            AppUiText("🌐 App display language: " + languageLabel(parentDisplayLanguageCode))
                        }
                        AppUiText("You can change these choices later in Family learning settings. Country is chosen by you, never inferred from location.")
                    }
                    Field(email, { email = it }, "Parent email", keyboardType = KeyboardType.Email)
                    Field(password, { password = it }, "Password (minimum 8 characters)", password = true)
                    if (isRegistering) {
                        Field(confirmPassword, { confirmPassword = it }, "Confirm password", password = true)
                        if (confirmPassword.isNotEmpty() && confirmPassword != password) {
                            AppUiText("Passwords do not match yet.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Button(
                        onClick = {
                            execute {
                                require(password == confirmPassword || !isRegistering) { "Passwords do not match. Please confirm your password." }
                                val result = repository.signUp(email, password, displayName, "parent")
                                if (result.session == null) {
                                    emailOtp = ""
                                    resendSeconds = 60
                                    accountRole = "parent"
                                    screen = AppScreen.VERIFY_OTP
                                    notice = "We requested a verification code for $email. Enter the 6–8 digit code from the newest email. Check Spam and Promotions too."
                                } else {
                                    resolveSignedIn(result.session)
                                    if (screen == AppScreen.PARENT_HOME) notice = result.message
                                }
                            }
                        },
                        enabled = !busy && isRegistering,
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("Create parent account") }
                    Button(
                        onClick = {
                            execute {
                                val signedIn = repository.signIn(email, password, "parent")
                                resolveSignedIn(signedIn)
                            }
                        },
                        enabled = !busy && !isRegistering,
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("Sign in as parent") }
                    TextButton(onClick = {
                        isRegistering = !isRegistering
                        confirmPassword = ""
                        notice = null
                    }) {
                        AppUiText(if (isRegistering) "Already have an account? Sign in" else "New to EduLock? Create an account")
                    }
                    OutlinedButton(
                        onClick = { chooseChildDevice() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("Set up a child's device with pairing code") }
                    if (!repository.isConfigured) {
                        AppUiText("Configuration needed: set the public Supabase URL and anon/publishable key in the GitHub Actions secrets before building. Never add a service-role key to the Android app.")
                    }
                }

                AppScreen.VERIFY_OTP -> ScreenFrame("Let's verify your email ✨", onBack = {
                    screen = AppScreen.AUTH
                    isRegistering = true
                    notice = "Enter the 6–8 digit code sent to $email to finish creating your account."
                }, notice = notice, busy = busy) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FF)), shape = MaterialTheme.shapes.extraLarge) {
                        Column(modifier = Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppUiText("🎨", fontSize = 42.sp)
                            AppUiText("One tiny step to start!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF173B67))
                            AppUiText("We sent a verification code to", color = Color(0xFF42566D))
                            AppUiText(email, fontWeight = FontWeight.SemiBold, color = Color(0xFF087E8B))
                            AppUiText("Check your inbox and spam folder, then type the code below.", color = Color(0xFF42566D))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Field(emailOtp, { emailOtp = it.filter(Char::isDigit).take(8) }, "Verification code (6–8 digits)", keyboardType = KeyboardType.Number)
                    Button(onClick = {
                        execute {
                            val verified = repository.verifySignupOtp(email, emailOtp, accountRole)
                            resolveSignedIn(verified, pairingCode)
                            if (screen == AppScreen.PARENT_HOME || screen == AppScreen.CHILD_HOME) notice = "🎉 Email verified! Welcome to EduLock."
                        }
                    }, enabled = !busy && emailOtp.length in 6..8, modifier = Modifier.fillMaxWidth()) { AppUiText("✓  Verify & continue") }
                    OutlinedButton(onClick = {
                        execute {
                            repository.resendSignupOtp(email)
                            resendSeconds = 60
                            emailOtp = ""
                            notice = "A new code was requested. Check your inbox and spam folder."
                        }
                    }, enabled = !busy && resendSeconds == 0, modifier = Modifier.fillMaxWidth()) {
                        AppUiText(if (resendSeconds > 0) "Resend code in " + resendSeconds + "s" else "↻  Send a new code")
                    }
                    AppUiText(if (resendSeconds > 0) "You can request another code in " + resendSeconds + " seconds." else "Still no email? Check Inbox, Spam and Promotions, then request one fresh code. Email providers may enforce a longer cooldown.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppScreen.PAIR_CHILD -> ScreenFrame("Pair this child's device", onBack = {
                    repository.signOut()
                    session = null
                    childProfile = null
                    devicePreferences.edit().remove("mode").apply()
                    deviceMode = "unknown"
                    screen = AppScreen.DEVICE_SETUP
                }, notice = notice, busy = busy) {
                    AppUiText("Enter the six-digit, one-time code generated by the parent. No child email address or password is needed.")
                    AppUiText("The code expires after 10 minutes and can be used once. Keep this device's app data intact after pairing; anonymous child sessions cannot be recovered if app data is cleared.")
                    Field(pairingCode, { pairingCode = it.filter(Char::isDigit).take(6) }, "6-digit parent pairing code", keyboardType = KeyboardType.Number)
                    Button(onClick = {
                        execute {
                            val childSession = repository.signInAnonymouslyAsChild()
                            session = childSession
                            finishChildLogin(childSession, pairingCode)
                        }
                    }, enabled = !busy && pairingCode.filter(Char::isDigit).length == 6, modifier = Modifier.fillMaxWidth()) {
                        AppUiText("Pair securely with parent")
                    }
                    OutlinedButton(onClick = {
                        execute {
                            val current = repository.currentSession()
                                ?: throw IllegalStateException("Enter the pairing code to set up this device.")
                            resolveSignedIn(current)
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { AppUiText("Continue with an already linked device") }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D8)),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppUiText("One-time setup for the parent", fontWeight = FontWeight.Bold, color = Color(0xFF704B00))
                            AppUiText(
                                "If pairing says anonymous sign-ins are disabled, enable this setting in the parent-managed Supabase project: Dashboard → Authentication → Sign In / Providers → Anonymous. Save the setting, then retry pairing. Reinstalling the app will not enable this project setting.",
                                color = Color(0xFF704B00)
                            )
                            AppUiText(
                                "Never put a Supabase service-role key in the Android app. The child session is linked only after the parent-generated one-time code is claimed.",
                                color = Color(0xFF704B00),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                AppScreen.APP_SETTINGS -> ScreenFrame("App settings", onBack = { screen = AppScreen.PARENT_HOME }, notice = notice, busy = busy) {
                    AppUiText("Everything in one place", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    AppUiText("Choose the curriculum country, learning language and app language. Religious-education subjects are chosen separately for each child profile.")
                    AppUiText("Family learning preferences", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardIconTile(
                                icon = "🌍", title = "Country", subtitle = displayCountryName(parentCountryCode),
                                modifier = Modifier.weight(1f), accent = Color(0xFF138DB8),
                                onClick = { countryPickerTarget = "parent"; countrySearch = ""; countryPickerOpen = true }
                            )
                            DashboardIconTile(
                                icon = "📚", title = "Learning language", subtitle = languageLabel(parentLearningLanguageCode),
                                modifier = Modifier.weight(1f), accent = Color(0xFF078C87),
                                onClick = { languagePickerTarget = "parent-learning"; languageSearch = ""; languagePickerOpen = true }
                            )
                        }
                        DashboardIconTile(
                            icon = "🌐", title = "App language", subtitle = languageLabel(parentDisplayLanguageCode),
                            accent = Color(0xFFDA6195),
                            onClick = { languagePickerTarget = "display"; languageSearch = ""; languagePickerOpen = true }
                        )
                    }
                    AppUiText("App & child controls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardIconTile(
                                icon = "👨‍👩‍👧", title = "Child profiles", subtitle = "Profiles, pairing and daily limits",
                                modifier = Modifier.weight(1f), accent = Color(0xFF6756E8),
                                onClick = { screen = AppScreen.CHILDREN; execute { children = repository.loadChildren(familyId) } }
                            )
                            DashboardIconTile(
                                icon = "📵", title = "App limits", subtitle = "Allowed apps and screen-time schedules",
                                modifier = Modifier.weight(1f), accent = Color(0xFFE19B32),
                                onClick = { screen = AppScreen.CHILDREN; execute { children = repository.loadChildren(familyId) } }
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardIconTile(
                                icon = "📍", title = "Location & alerts", subtitle = "Family location and offline notifications",
                                modifier = Modifier.weight(1f), accent = Color(0xFF168DBA),
                                onClick = {
                                    screen = AppScreen.CHILD_LOCATIONS
                                    execute {
                                        val latestChildren = repository.loadChildren(familyId)
                                        children = latestChildren
                                        childLocationRows = latestChildren.map { repository.loadChildLocationSnapshot(it.id) }
                                    }
                                }
                            )
                            DashboardIconTile(
                                icon = "🛡️", title = "Device protection", subtitle = "Accessibility and managed-device status",
                                modifier = Modifier.weight(1f), accent = Color(0xFF0C948F),
                                onClick = { refreshProtectionStatus(); screen = AppScreen.PROTECTION_STATUS }
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardIconTile(
                                icon = "🔔", title = "Notifications", subtitle = if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) "Tap to enable alerts" else "Notification permission status",
                                modifier = Modifier.weight(1f), accent = Color(0xFF8A72D6),
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        notice = "Notification permission is already granted or is managed by Android on this device."
                                    }
                                }
                            )
                            DashboardIconTile(
                                icon = "🚪", title = "Sign out", subtitle = "Sign out of the parent account",
                                modifier = Modifier.weight(1f), accent = Color(0xFF69758C),
                                onClick = {
                                    repository.signOut()
                                    session = null
                                    childProfile = null
                                    screen = AppScreen.AUTH
                                    isRegistering = false
                                    notice = "Signed out."
                                }
                            )
                        }
                    }
                    AppUiText("Religious learning content is optional and follows the track selected by the parent. Country selection helps filter curriculum; it does not assume a family's personal beliefs.")
                }

                AppScreen.PARENT_HOME -> ScreenFrame("Parent dashboard", notice = notice, busy = busy) {
                    AppUiText("Your family is protected by your signed-in parent account.")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DashboardIconTile(
                            icon = "👨‍👩‍👧",
                            title = children.size.toString() + " children",
                            subtitle = "Add profiles and pair their devices",
                            modifier = Modifier.weight(1f),
                            accent = Color(0xFF6756E8),
                            onClick = { screen = AppScreen.CHILDREN; execute { children = repository.loadChildren(familyId) } }
                        )
                        DashboardIconTile(
                            icon = "📚",
                            title = subjects.size.toString() + " subjects",
                            subtitle = "Questions for " + displayCountryName(parentCountryCode),
                            modifier = Modifier.weight(1f),
                            accent = Color(0xFF079B91),
                            onClick = { screen = AppScreen.SUBJECTS; execute { subjects = repository.loadSubjects(familyId, parentCountryCode) } }
                        )
                    }
                    AppUiText("Your learning toolkit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardIconTile(
                                icon = "🧪", title = "Subjects & questions", subtitle = "Create and manage question banks",
                                modifier = Modifier.weight(1f), accent = Color(0xFF5147E8),
                                onClick = { screen = AppScreen.SUBJECTS; execute { subjects = repository.loadSubjects(familyId, parentCountryCode) } }
                            )
                            DashboardIconTile(
                                icon = "🗂️", title = "Learning templates", subtitle = "Ready-made subjects and activities",
                                modifier = Modifier.weight(1f), accent = Color(0xFF078C87),
                                onClick = { selectedSubject = null; templateCategory = "Mathematics"; screen = AppScreen.LEARNING_TEMPLATES }
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardIconTile(
                                icon = "🌌", title = "4D Learning Lab", subtitle = "Explore interactive models in every subject",
                                modifier = Modifier.weight(1f), accent = Color(0xFF7759D7),
                                onClick = { selected3DModel = "solar-system"; screen = AppScreen.LEARNING_3D_LAB }
                            )
                            DashboardIconTile(
                                icon = "🎨", title = "Drawing & colour", subtitle = "Draw, colour and practise shapes",
                                modifier = Modifier.weight(1f), accent = Color(0xFFDA6195),
                                onClick = { screen = AppScreen.DRAWING_STUDIO }
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardIconTile(
                                icon = "🎯", title = "Assign learning", subtitle = "Build tasks and learning rewards",
                                modifier = Modifier.weight(1f), accent = Color(0xFFE19B32),
                                onClick = { selectedChild = null; selectedSubject = null; screen = AppScreen.ASSIGN_TASK }
                            )
                            DashboardIconTile(
                                icon = "🏆", title = "Progress reports", subtitle = "View scores and learning progress",
                                modifier = Modifier.weight(1f), accent = Color(0xFF168DBA),
                                onClick = { screen = AppScreen.REPORTS; execute { attempts = repository.loadAttempts(); conceptProgress = repository.loadConceptProgress() } }
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardIconTile(
                                icon = "⚙️", title = "App settings", subtitle = "Country, languages, religion, limits and protection",
                                modifier = Modifier.weight(1f), accent = Color(0xFF4E5D75),
                                onClick = {
                                    screen = AppScreen.APP_SETTINGS
                                    execute {
                                        val prefs = repository.loadParentLearningSettings(familyId)
                                        parentCountryCode = prefs.countryCode
                                        parentLearningLanguageCode = prefs.learningLanguageCode
                                        parentStudyTrack = prefs.studyTrack
                                        parentDisplayLanguageCode = prefs.displayLanguageCode
                                        context.getSharedPreferences("edulock_display_language", Context.MODE_PRIVATE).edit()
                                            .putString("language", prefs.displayLanguageCode).apply()
                                    }
                                }
                            )
                            DashboardIconTile(
                                icon = "🚪", title = "Log out", subtitle = "Safely sign out of this parent account",
                                modifier = Modifier.weight(1f), accent = Color(0xFF69758C),
                                onClick = {
                                    repository.signOut()
                                    session = null
                                    childProfile = null
                                    screen = AppScreen.AUTH
                                    isRegistering = false
                                    notice = "You have been logged out."
                                }
                            )
                        }
                    }
                }

                AppScreen.CHILDREN -> ScreenFrame("Child profiles", onBack = { screen = AppScreen.PARENT_HOME }, notice = notice, busy = busy) {
                    Button(onClick = {
                        childName = ""; childLanguage = parentLearningLanguageCode; childDailyCap = "60"
                        childCountryCode = parentCountryCode; childSchoolGrade = "1"; childAgeYears = "8"
                        childAvatarIcon = "🦊"; childStudyTrack = "none"; childReligionEnabled = false; childDailyGameLimit = "20"
                        screen = AppScreen.ADD_CHILD
                    }, modifier = Modifier.fillMaxWidth()) { AppUiText("Add child profile") }
                    if (children.isEmpty()) AppUiText("No child profiles yet. Add a child to create the protected parent-child link.")
                    children.forEach { child ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(48.dp).background(Color(0xFFE8E7FF), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                        AppUiText(child.avatarIcon, fontSize = 28.sp)
                                    }
                                    Column {
                                        AppUiText(child.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        AppUiText("🎮 Educational games: ${child.dailyGameLimitMinutes} min/day", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                AppUiText("Language: " + child.preferredLanguage + " • Daily reward cap: " + child.dailyCapMinutes + " minutes")
                                Field(
                                    childRewardCapEdits[child.id] ?: child.dailyCapMinutes.toString(),
                                    { value -> childRewardCapEdits = childRewardCapEdits + (child.id to value.filter(Char::isDigit).take(4)) },
                                    "Daily reward-minutes cap (0–1440)",
                                    keyboardType = KeyboardType.Number
                                )
                                OutlinedButton(
                                    onClick = {
                                        execute {
                                            val cap = (childRewardCapEdits[child.id] ?: child.dailyCapMinutes.toString()).toIntOrNull()
                                                ?: throw IllegalArgumentException("Enter a daily reward cap from 0 to 1440.")
                                            require(cap in 0..1440) { "Daily reward cap must be between 0 and 1440 minutes." }
                                            repository.updateChildRewardCap(child.id, cap)
                                            children = repository.loadChildren(familyId)
                                            childRewardCapEdits = childRewardCapEdits - child.id
                                            notice = "Daily reward cap saved and verified for ${child.displayName}."
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { AppUiText("Save daily reward cap") }
                                AppUiText(
                                    displayCountryName(child.countryCode) + " • " +
                                        (if (child.schoolGrade == 0) "Kindergarten" else "Grade ${child.schoolGrade}") +
                                        " • Age " + child.ageYears,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (child.kidsModeEnabled) Color(0xFFFFE8B8) else Color(0xFFEAF5FF)
                                    )
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AppUiText(
                                            if (child.kidsModeEnabled) "🔒 Kids Mode: ON" else "🔓 Kids Mode: OFF",
                                            fontWeight = FontWeight.Bold
                                        )
                                        AppUiText(
                                            if (child.kidsModeEnabled)
                                                "Only apps explicitly enabled by the parent can open. The launcher/navigation and essential phone/permission components remain available. Accessibility must be active on the child phone; true kiosk enforcement additionally requires device-owner setup."
                                            else
                                                "Enable Kids Mode to block apps that are not explicitly allowed for this child."
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                execute {
                                                    val requested = !child.kidsModeEnabled
                                                    val existingRules = repository.loadAllowedApps(child.id)
                                                    val confirmed = repository.updateChildKidsMode(child.id, requested)
                                                    if (confirmed != requested) {
                                                        throw IllegalStateException("Supabase did not confirm the requested Kids Mode setting.")
                                                    }
                                                    val latest = repository.loadChildren(familyId)
                                                    val updated = latest.firstOrNull { it.id == child.id }
                                                        ?: throw IllegalStateException("Supabase did not return this child profile after saving Kids Mode.")
                                                    if (updated.kidsModeEnabled != requested) {
                                                        throw IllegalStateException("Supabase returned a different Kids Mode value. No success was reported.")
                                                    }
                                                    children = latest
                                                    selectedChild = updated
                                                    notice = when {
                                                        requested && existingRules.none { it.enabled } ->
                                                            "Kids Mode was saved and verified in Supabase, but no apps are enabled yet. Third-party apps will be blocked after the child phone syncs. Add permitted apps first."
                                                        requested ->
                                                            "Kids Mode was saved and verified in Supabase. The child phone applies it after its next successful sync; verify Accessibility and device-owner status on that phone."
                                                        else ->
                                                            "Kids Mode was disabled and verified in Supabase. Individual app rules apply again after the next child-device sync."
                                                    }
                                                }
                                            },
                                            enabled = !busy,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            AppUiText(if (child.kidsModeEnabled) "Turn Kids Mode OFF" else "Enable Kids Mode • block unapproved apps")
                                        }
                                    }
                                }
                                Field(
                                    childGameLimitEdits[child.id] ?: child.dailyGameLimitMinutes.toString(),
                                    { value -> childGameLimitEdits = childGameLimitEdits + (child.id to value.filter(Char::isDigit).take(4)) },
                                    "Daily educational game limit in minutes (0 disables games)",
                                    keyboardType = KeyboardType.Number
                                )
                                OutlinedButton(
                                    onClick = {
                                        execute {
                                            val limit = (childGameLimitEdits[child.id] ?: child.dailyGameLimitMinutes.toString()).toIntOrNull()
                                                ?: throw IllegalArgumentException("Enter a game limit from 0 to 1440 minutes.")
                                            require(limit in 0..1440) { "Game limit must be from 0 to 1440 minutes." }
                                            repository.updateChildGameLimit(child.id, limit)
                                            children = repository.loadChildren(familyId)
                                            childGameLimitEdits = childGameLimitEdits - child.id
                                            notice = "Daily educational game allowance saved for ${child.displayName}."
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { AppUiText("Save educational game limit") }
                                OutlinedButton(
                                    onClick = {
                                        studyTrackPickerTarget = "existing-child"
                                        studyTrackChildId = child.id
                                        studyTrackPickerOpen = true
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { AppUiText("🕊 Religious education: " + studyTrackLabel(child.studyTrack)) }
                                OutlinedButton(
                                    onClick = {
                                        selectedChild = child
                                        curriculumCountryCode = child.countryCode
                                        curriculumGradeLevel = child.schoolGrade.toString()
                                        curriculumAgeYears = child.ageYears.toString()
                                        curriculumLanguageCode = child.preferredLanguage
                                        curriculumSubjectCode = "math"
                                        curriculumSearchResult = null
                                        curriculumSelectedIds = emptySet()
                                        screen = AppScreen.CURRICULUM_LIBRARY
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { AppUiText("📚  Country syllabus & question bank") }
                                AppUiText("Device link: generate a one-time code on the child's first setup.")
                                OutlinedButton(onClick = {
                                    selectedChild = child
                                    lastPairingCode = ""
                                    execute {
                                        val generated = repository.createPairingCode(child.id)
                                        lastPairingCode = generated.first
                                        pairingCodeExpiry = generated.second
                                        pairingChildName = child.displayName
                                    }
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { AppUiText("Generate pairing code") }
                                OutlinedButton(onClick = {
                                    selectedChild = child
                                    screen = AppScreen.ALLOWED_APPS
                                    execute {
                                        allowedApps = repository.loadAllowedApps(child.id)
                                        childInstalledApps = repository.loadInstalledApps(child.id)
                                        appGroups = repository.loadAppGroups(child.id)
                                    }
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { AppUiText("🎮  App access & daily limits") }
                                OutlinedButton(
                                    onClick = { pendingChildAction = child to "unlink" },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { AppUiText("Deactivate device link") }
                                OutlinedButton(
                                    onClick = { pendingChildAction = child to "delete" },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { AppUiText("Delete child profile") }
                                OutlinedButton(onClick = {
                                    appUsageChild = child
                                    screen = AppScreen.APP_USAGE_REPORT
                                    execute { appUsageReports = repository.loadAppUsageReports(child.id) }
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { AppUiText("📊  App usage report") }
                            }
                        }
                    }
                    if (lastPairingCode.isNotBlank()) {
                        Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppUiText("One-time pairing code for " + pairingChildName, fontWeight = FontWeight.SemiBold)
                                AppUiText(lastPairingCode, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                                AppUiText("Valid for " + (pairingCodeExpiry / 60) + " minutes. Share privately with the child; only its hash is stored.")
                                OutlinedButton(onClick = { lastPairingCode = "" }, modifier = Modifier.fillMaxWidth()) { AppUiText("Hide code") }
                            }
                        }
                    }
                }

                AppScreen.ADD_CHILD -> ScreenFrame("Create your child's learning profile", onBack = { screen = AppScreen.CHILDREN }, notice = notice, busy = busy) {
                    AppUiText("Personalize lessons to the child's country, grade and age. You can update these later from the curriculum screen.")
                    Field(childName, { childName = it }, "Child's name")
                    AppUiText("Choose a profile icon", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    listOf("🦊","🐼","🐯","🐸","🐰","🦄","🐻","🐨","🐵","🐧","🦋","🐢").chunked(6).forEach { rowIcons ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            rowIcons.forEach { avatar ->
                                OutlinedButton(onClick = { childAvatarIcon = avatar }, modifier = Modifier.weight(1f)) {
                                    AppUiText((if (childAvatarIcon == avatar) "✓ " else "") + avatar, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = {
                        countryPickerTarget = "child"
                        countrySearch = ""
                        countryPickerOpen = true
                    }, modifier = Modifier.fillMaxWidth()) {
                        AppUiText("🌍  Country: ${displayCountryName(childCountryCode)}")
                    }
                    AppUiText("Choose the child's school standard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    (0..5).chunked(3).forEach { gradeRow ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            gradeRow.forEach { grade ->
                                OutlinedButton(
                                    onClick = { childSchoolGrade = grade.toString() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    AppUiText((if (childSchoolGrade == grade.toString()) "✓ " else "") +
                                        if (grade == 0) "Kindergarten" else "Class $grade")
                                }
                            }
                        }
                    }
                    AppUiText("Choose Kindergarten or Class 1–5. Lessons should match the child's selected standard and age.")
                    Field(childAgeYears, { childAgeYears = it.filter(Char::isDigit).take(2) }, "Child age (3–12 years)", keyboardType = KeyboardType.Number)
                    Field(childLanguage, { childLanguage = it.trim().take(16) }, "Preferred learning language (en, ur, sd...)")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = childReligionEnabled,
                            onCheckedChange = { enabled ->
                                childReligionEnabled = enabled
                                if (!enabled) childStudyTrack = "none"
                            }
                        )
                        Column(Modifier.weight(1f)) {
                            AppUiText("Include religious education", fontWeight = FontWeight.SemiBold)
                            AppUiText("Optional. No religion is selected automatically.")
                        }
                    }
                    if (childReligionEnabled) {
                        AppUiText("Choose the child's religion to show matching lessons only.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = {
                            studyTrackPickerTarget = "child"
                            studyTrackPickerOpen = true
                        }, modifier = Modifier.fillMaxWidth()) {
                            AppUiText("🕊 Choose religion: " + studyTrackLabel(childStudyTrack))
                        }
                    }
                    AppUiText("Lessons use the child's country, class, age and language. Religious lessons appear only when enabled and selected by the parent.")
                    Field(childDailyCap, { childDailyCap = it.filter(Char::isDigit).take(4) }, "Maximum reward minutes per day (0–1440)", keyboardType = KeyboardType.Number)
                    Field(childDailyGameLimit, { childDailyGameLimit = it.filter(Char::isDigit).take(4) }, "Educational games per day in minutes (0 disables; max 1440)", keyboardType = KeyboardType.Number)
                    Button(onClick = {
                        execute {
                            val grade = childSchoolGrade.toIntOrNull() ?: throw IllegalArgumentException("Enter a grade from 0 to 13.")
                            val age = childAgeYears.toIntOrNull() ?: throw IllegalArgumentException("Enter an age from 3 to 12.")
                            require(grade in 0..5) { "Choose Kindergarten or a class from 1 to 5." }
                            require(age in 3..12) { "Enter an age from 3 to 12." }
                            val created = repository.addChild(
                                familyId, childName, childLanguage, childDailyCap.toIntOrNull() ?: 60,
                                childCountryCode, grade, age, childAvatarIcon,
                                childDailyGameLimit.toIntOrNull() ?: 20, if (childReligionEnabled) childStudyTrack else "none"
                            )
                            if (childReligionEnabled && childStudyTrack != "none") {
                                val religiousSubject = repository.findOrCreateSubject(familyId, religiousSubjectTitle(childStudyTrack), childLanguage, childCountryCode)
                                val starterQuestions = fourDTemplatesForSubject(religiousSubject.title, childLanguage, grade, age)
                                repository.ensureTemplateQuestions(religiousSubject.id, starterQuestions)
                            }
                            children = repository.loadChildren(familyId)
                            selectedChild = created
                            screen = AppScreen.CHILDREN
                            notice = "Learning profile saved. The avatar and daily educational-game allowance are ready."
                        }
                    }, enabled = !busy && childName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { AppUiText("Save learning profile") }
                }

                AppScreen.CURRICULUM_LIBRARY -> ScreenFrame(
                    "Country curriculum",
                    onBack = { screen = AppScreen.CHILDREN },
                    notice = notice,
                    busy = busy
                ) {
                    val child = selectedChild
                    if (child == null) {
                        AppUiText("Choose a child profile first.")
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF0FF))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                AppUiText(child.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                AppUiText(
                                    displayCountryName(curriculumCountryCode) + " • " +
                                        (curriculumGradeLevel.toIntOrNull()?.let { if (it == 0) "Kindergarten" else "Grade $it" } ?: "Choose grade") +
                                        " • Age " + curriculumAgeYears + " • " + curriculumLanguageCode.uppercase(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AppUiText("Choose the child's learning context and import ready-made questions. The answer key stays server-side; you do not need to write questions yourself.")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                countryPickerTarget = "curriculum"
                                countrySearch = ""
                                countryPickerOpen = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("🌍  Country: ${displayCountryName(curriculumCountryCode)}") }

                        Field(
                            curriculumGradeLevel,
                            { curriculumGradeLevel = it.filter(Char::isDigit).take(2); curriculumSearchResult = null; curriculumSelectedIds = emptySet() },
                            "School grade (0 = Kindergarten, 1–13)",
                            keyboardType = KeyboardType.Number
                        )
                        Field(
                            curriculumAgeYears,
                            { curriculumAgeYears = it.filter(Char::isDigit).take(2); curriculumSearchResult = null; curriculumSelectedIds = emptySet() },
                            "Child age (3–19 years)",
                            keyboardType = KeyboardType.Number
                        )

                        AppUiText("Learning language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = { languageSearch = ""; languagePickerOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppUiText(
                                "🌐  " + displayLanguageName(curriculumLanguageCode) +
                                    " (" + curriculumLanguageCode.uppercase() + ")  •  Change"
                            )
                        }
                        AppUiText(
                            "All standard ISO language codes are searchable. The question bank reports when it uses an English or international starter fallback.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        AppUiText("Subject", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        buildList {
                            add(Triple("math", "Mathematics", "Numbers, arithmetic, fractions and problem solving"))
                            add(Triple("english", "English", "Vocabulary, reading and grammar"))
                            add(Triple("science", "General Science", "Living things, health, matter and Earth science"))
                            add(Triple("physics", "Physics", "Motion, energy, forces and electricity"))
                            add(Triple("chemistry", "Chemistry", "Atoms, materials, reactions and mixtures"))
                            add(Triple("biology", "Biology", "Cells, organisms, ecosystems and genetics"))
                            add(Triple("geography", "Geography", "Maps, landscapes, climate and people"))
                            add(Triple("social", "Social Studies", "Community, geography and civic life"))
                            add(Triple("computer", "Computer Skills", "Devices, digital skills and online safety"))
                            add(Triple("health", "Health & Safety", "Healthy habits, safety and first aid"))
                            add(Triple("arts", "Art & Design", "Colour, shapes, patterns and composition"))
                            add(Triple("astronomy", "Astronomy", "Planets, stars, orbits and space"))
                            when (curriculumCountryCode.uppercase()) {
                                "PK" -> {
                                    add(Triple("urdu", "Urdu", "Urdu vocabulary, opposites and reading practice"))
                                    add(Triple("sindhi", "Sindhi", "Sindhi vocabulary, opposites and reading practice"))
                                }
                                "IN" -> add(Triple("hindi", "Hindi", "Hindi vocabulary, opposites and reading practice"))
                            }
                        }.forEach { (code, name, detail) ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    curriculumSubjectCode = code
                                    curriculumLanguageCode = when (code) {
                                        "urdu" -> "ur"
                                        "sindhi" -> "sd"
                                        "hindi" -> "hi"
                                        else -> "en"
                                    }
                                    curriculumSearchResult = null
                                    curriculumSelectedIds = emptySet()
                                },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (curriculumSubjectCode == code) Color(0xFFE0F7EF) else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                border = BorderStroke(
                                    if (curriculumSubjectCode == code) 2.dp else 1.dp,
                                    if (curriculumSubjectCode == code) MaterialTheme.colorScheme.secondary else Color(0xFFDDE2F2)
                                )
                            ) {
                                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    AppUiText((if (curriculumSubjectCode == code) "✓  " else "") + name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    AppUiText(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                execute {
                                    val grade = curriculumGradeLevel.toIntOrNull()
                                        ?: throw IllegalArgumentException("Enter a grade from 0 to 13.")
                                    val age = curriculumAgeYears.toIntOrNull()
                                        ?: throw IllegalArgumentException("Enter an age from 3 to 19.")
                                    require(grade in 0..13) { "Grade must be between Kindergarten (0) and 13." }
                                    require(age in 3..19) { "Age must be between 3 and 19." }
                                    val targetChild = selectedChild ?: throw IllegalStateException("Choose a child profile first.")
                                    repository.updateChildCurriculum(
                                        targetChild.id, curriculumCountryCode, grade, age, curriculumLanguageCode
                                    )
                                    selectedChild = targetChild.copy(
                                        countryCode = curriculumCountryCode,
                                        schoolGrade = grade,
                                        ageYears = age,
                                        preferredLanguage = curriculumLanguageCode
                                    )
                                    children = children.map { if (it.id == targetChild.id) selectedChild!! else it }
                                    notice = "Country, grade, age and preferred language saved for ${targetChild.displayName}."
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("Save learning profile") }

                        Button(
                            onClick = {
                                execute {
                                    val grade = curriculumGradeLevel.toIntOrNull()
                                        ?: throw IllegalArgumentException("Enter a grade from 0 to 13.")
                                    val age = curriculumAgeYears.toIntOrNull()
                                        ?: throw IllegalArgumentException("Enter an age from 3 to 19.")
                                    require(grade in 0..13) { "Grade must be between Kindergarten (0) and 13." }
                                    require(age in 3..19) { "Age must be between 3 and 19." }
                                    val targetChild = selectedChild ?: throw IllegalStateException("Choose a child profile first.")
                                    val result = repository.loadCurriculumQuestions(
                                        targetChild.id, curriculumCountryCode, grade, age,
                                        curriculumSubjectCode, curriculumLanguageCode
                                    )
                                    curriculumSearchResult = result
                                    curriculumSelectedIds = result.questions.map { it.id }.toSet()
                                    notice = if (result.questions.isEmpty())
                                        "No questions match this grade and age yet. Try another subject or grade."
                                    else result.message
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("✨  Find age-matched questions") }

                        curriculumSearchResult?.let { result ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF8F4))
                            ) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    AppUiText(
                                        if (result.questions.isEmpty()) "No questions found" else "${result.questions.size} questions for this learning profile",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    AppUiText(result.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (result.questions.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = {
                                                curriculumSelectedIds = if (curriculumSelectedIds.size == result.questions.size) emptySet()
                                                    else result.questions.map { it.id }.toSet()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            AppUiText(if (curriculumSelectedIds.size == result.questions.size) "Clear selection" else "Select all questions")
                                        }
                                    }
                                }
                            }
                            result.questions.forEach { item ->
                                val chosen = item.id in curriculumSelectedIds
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        curriculumSelectedIds = if (chosen) curriculumSelectedIds - item.id
                                            else curriculumSelectedIds + item.id
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(
                                        if (chosen) 2.dp else 1.dp,
                                        if (chosen) MaterialTheme.colorScheme.secondary else Color(0xFFDDE2F2)
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (chosen) Color(0xFFE4F7F0) else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AppUiText(
                                            (if (chosen) "✓ SELECTED  " else "TAP TO SELECT  ") + item.topic,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        AppUiText(item.prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        if (item.options.isNotEmpty()) AppUiText("Choices: " + item.options.joinToString("   •   "))
                                        AppUiText(
                                            "Grade ${item.gradeLevel} • Age ${item.ageMin}–${item.ageMax} • ${item.languageCode.uppercase()} • " + item.curriculumName,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        AppUiText(item.sourceNote, style = MaterialTheme.typography.bodySmall, color = Color(0xFF88601B))
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    execute {
                                        val grade = curriculumGradeLevel.toIntOrNull() ?: throw IllegalArgumentException("Enter a valid grade.")
                                        val age = curriculumAgeYears.toIntOrNull() ?: throw IllegalArgumentException("Enter a valid age.")
                                        val targetChild = selectedChild ?: throw IllegalStateException("Choose a child profile first.")
                                        val imported = repository.importCurriculumQuestions(
                                            targetChild.id, curriculumCountryCode, grade, age,
                                            curriculumSubjectCode, curriculumLanguageCode, curriculumSelectedIds.toList()
                                        )
                                        subjects = repository.loadSubjects(familyId, parentCountryCode)
                                        selectedSubject = subjects.firstOrNull { it.id == imported.subjectId }
                                        val targetSubject = selectedSubject
                                            ?: throw IllegalStateException("Questions imported, but the subject could not be reloaded.")
                                        questions = repository.loadQuestions(targetSubject.id)
                                        questionTranslations = repository.loadQuestionTranslations(questions.map { it.id })
                                        questionSolutions = repository.loadQuestionSolutions(questions.map { it.id })
                                        screen = AppScreen.QUESTIONS
                                        notice = imported.message + " Selected: ${imported.selectedCount}; newly added: ${imported.importedCount}."
                                    }
                                },
                                enabled = !busy && curriculumSelectedIds.isNotEmpty() && curriculumSelectedIds.size <= 50,
                                modifier = Modifier.fillMaxWidth()
                            ) { AppUiText("Add selected questions to Subjects") }
                        }
                    }
                }

                AppScreen.SUBJECTS -> ScreenFrame("Subjects & questions", onBack = { screen = AppScreen.PARENT_HOME }, notice = notice, busy = busy) {
                    AppUiText("Open any subject to add, review, translate, or edit its questions. Each subject has its own separate question bank.")
                    Button(onClick = {
                        editingSubjectId = null
                        subjectTitle = ""; subjectLanguage = "en"
                        subjectCountryCode = parentCountryCode; subjectGradeLevel = "1"
                        screen = AppScreen.ADD_SUBJECT
                    }, modifier = Modifier.fillMaxWidth()) { AppUiText("Create subject") }

                    AppUiText("Suggested subjects for " + displayCountryName(parentCountryCode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    AppUiText("These are starting suggestions, not restrictions. Add extra subjects when needed. Religious education is chosen separately inside each child profile.")
                    val suggestions = suggestedSubjectsForCountry(parentCountryCode).filter { suggestion ->
                        subjects.none { subject ->
                            subject.title.equals(suggestion.title, ignoreCase = true) &&
                                subject.languageCode.equals(suggestion.languageCode, ignoreCase = true) &&
                                (subject.countryCode.equals(parentCountryCode, ignoreCase = true) || subject.countryCode.equals("ZZ", ignoreCase = true))
                        }
                    }
                    suggestions.forEach { suggestion ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                AppUiText(suggestion.title, fontWeight = FontWeight.SemiBold)
                                AppUiText(
                                    if (suggestion.studyTrack == null) {
                                        "Language: " + suggestion.languageCode
                                    } else {
                                        "Optional religious-education subject • " + studyTrackLabel(suggestion.studyTrack)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = {
                                execute {
                                    val suggestedSubject = repository.findOrCreateSubject(familyId, suggestion.title, suggestion.languageCode, parentCountryCode)
                                    val starterQuestions = fourDTemplatesForSubject(
                                        suggestedSubject.title, suggestion.languageCode,
                                        selectedChild?.schoolGrade ?: suggestedSubject.gradeLevel,
                                        selectedChild?.ageYears ?: (suggestedSubject.gradeLevel + 5).coerceIn(3, 19)
                                    )
                                    val addedQuestionCount = repository.ensureTemplateQuestions(suggestedSubject.id, starterQuestions)
                                    subjects = repository.loadSubjects(familyId, parentCountryCode)
                                    notice = if (suggestion.studyTrack == null) {
                                        suggestion.title + " added with " + addedQuestionCount + " 4D starter questions."
                                    } else {
                                        suggestion.title + " added with " + addedQuestionCount + " starter questions. Select " + studyTrackLabel(suggestion.studyTrack) + " in the child's profile to enable matching religious tasks."
                                    }
                                }
                            }, enabled = !busy) { AppUiText("＋ Add") }
                        }
                    }

                    AppUiText("Your subjects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val visibleSubjects = subjects.filter { subject ->
                        val track = religionTrackForSubject(subject.title)
                        track == null || children.any { child -> child.studyTrack == track }
                    }
                    if (visibleSubjects.isEmpty()) AppUiText("No subjects yet. Add a suggestion above or create your own subject.")
                    visibleSubjects.forEach { subject ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedSubject = subject
                                expandedFourDQuestionId = null
                                screen = AppScreen.QUESTIONS
                                execute {
                                    // Repair existing subjects created before the full starter bank was seeded.
                                    val addedStarterCount = repository.ensureTemplateQuestions(
                                        subject.id,
                                        fourDTemplatesForSubject(
                                            subject.title, subject.languageCode, subject.gradeLevel,
                                            selectedChild?.ageYears ?: (subject.gradeLevel + 5).coerceIn(3, 19)
                                        )
                                    )
                                    questions = repository.loadQuestions(subject.id)
                                    questionTranslations = repository.loadQuestionTranslations(questions.map { it.id })
                                    questionSolutions = repository.loadQuestionSolutions(questions.map { it.id })
                                    notice = if (addedStarterCount > 0) {
                                        "Added " + addedStarterCount + " missing 4D starter questions to " + subject.title + ". Existing questions were kept."
                                    } else {
                                        null
                                    }
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                AppUiText(subject.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                AppUiText("Language: " + subject.languageCode + " • " + displayCountryName(subject.countryCode) + " • Grade " + subject.gradeLevel)
                                AppUiText("Tap the card or use the button below to manage this subject's questions.")
                                OutlinedButton(
                                    onClick = {
                                        selectedSubject = subject
                                        expandedFourDQuestionId = null
                                        screen = AppScreen.QUESTIONS
                                        execute {
                                            val addedStarterCount = repository.ensureTemplateQuestions(
                                                subject.id,
                                                fourDTemplatesForSubject(
                                                    subject.title, subject.languageCode, subject.gradeLevel,
                                                    selectedChild?.ageYears ?: (subject.gradeLevel + 5).coerceIn(3, 19)
                                                )
                                            )
                                            questions = repository.loadQuestions(subject.id)
                                            questionTranslations = repository.loadQuestionTranslations(questions.map { it.id })
                                            questionSolutions = repository.loadQuestionSolutions(questions.map { it.id })
                                            notice = if (addedStarterCount > 0) {
                                                "Added " + addedStarterCount + " missing starter questions to " + subject.title + ". Existing questions were kept."
                                            } else null
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { AppUiText("📘 Open subject • Manage questions") }
                                OutlinedButton(onClick = {
                                    editingSubjectId = subject.id
                                    subjectTitle = subject.title
                                    subjectLanguage = subject.languageCode
                                    subjectCountryCode = subject.countryCode
                                    subjectGradeLevel = subject.gradeLevel.toString()
                                    screen = AppScreen.ADD_SUBJECT
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { AppUiText("✏️ Edit subject") }
                            }
                        }
                    }
                }

                AppScreen.LEARNING_TEMPLATES -> ScreenFrame(
                    "Learning templates",
                    onBack = { screen = if (selectedSubject != null) AppScreen.QUESTIONS else AppScreen.PARENT_HOME },
                    notice = notice,
                    busy = busy
                ) {
                    AppUiText(
                        "Ready-to-use 4D question templates matched to the selected child's country, preferred language and optional religious-study track. Parents can change these preferences in the child profile.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Field(
                        templateTranslationLanguage,
                        { templateTranslationLanguage = it.trim().lowercase(Locale.ROOT).take(16) },
                        "Translate visible templates to language code (e.g. ur, ar, hi, fr, es)"
                    )
                    if (children.isNotEmpty()) {
                        AppUiText("Choose the child this template bank should match", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        children.forEach { child ->
                            OutlinedButton(onClick = {
                                selectedChild = child
                                if (templateCategory == "Religious Education" && child.studyTrack == "none") {
                                    notice = "This child has no religious-study track selected. Set one from Child profiles to show religion-specific questions."
                                } else notice = "Template suggestions now use " + child.displayName + "'s country, language and study track."
                            }, modifier = Modifier.fillMaxWidth()) {
                                AppUiText((if (selectedChild?.id == child.id) "✓ " else "") +
                                    child.avatarIcon + " " + child.displayName + " • " +
                                    displayCountryName(child.countryCode) + " • " + studyTrackLabel(child.studyTrack))
                            }
                        }
                    }
                    val templateCountryCode = selectedChild?.countryCode ?: parentCountryCode
                    val templateLanguageCode = selectedChild?.preferredLanguage ?: parentLearningLanguageCode
                    val templateStudyTrack = selectedChild?.studyTrack ?: "none"
                    OutlinedButton(
                        onClick = { selected3DModel = "solar-system"; screen = AppScreen.LEARNING_3D_LAB },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("🌌 Preview all interactive 4D learning models") }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        learningTemplateCategories(templateCountryCode)
                            .filter { it != "Religious Education" || templateStudyTrack != "none" }
                            .chunked(2).forEach { categories ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    categories.forEach { category ->
                                        OutlinedButton(
                                            onClick = { templateCategory = category },
                                            enabled = !busy,
                                            modifier = Modifier.weight(1f)
                                        ) { AppUiText(if (templateCategory == category) "✓ $category" else category) }
                                    }
                                }
                            }
                    }
                    val countryTemplates = LearningTemplates.all.filter { template ->
                        template.category == templateCategory &&
                            (template.countryCode == "ZZ" || template.countryCode.equals(templateCountryCode, ignoreCase = true)) &&
                            (templateCategory != "Religious Education" ||
                                (templateStudyTrack != "none" &&
                                    religionTrackForSubject(template.subjectTitle) == templateStudyTrack))
                    }
                    val languageTemplates = countryTemplates.filter {
                        it.languageCode.equals(templateLanguageCode, ignoreCase = true)
                    }
                    val localLanguageCategories = setOf("Urdu", "Sindhi", "Hindi")
                    val filteredTemplates = when {
                        templateCategory in localLanguageCategories -> countryTemplates
                        languageTemplates.isNotEmpty() -> languageTemplates
                        else -> countryTemplates.filter { it.languageCode.equals("en", ignoreCase = true) }
                    }
                    if (templateCategory == "Religious Education" && templateStudyTrack == "none") {
                        AppUiText("Choose a religious-education track in this child's profile to see matching questions for " + displayCountryName(templateCountryCode) + ".")
                    } else if (countryTemplates.isEmpty()) {
                        AppUiText("No templates are available for this subject in " + displayCountryName(templateCountryCode) + " yet. You can create your own questions.")
                    } else if (templateLanguageCode != "en" && languageTemplates.isEmpty() && templateCategory !in localLanguageCategories) {
                        AppUiText("No " + languageLabel(templateLanguageCode) + " templates are available in this category yet. English starter templates are shown until translated ones are added.")
                    } else if (filteredTemplates.isEmpty()) {
                        AppUiText("No templates are available for this category yet. Parents can create their own questions.")
                    }
                    OutlinedButton(onClick = {
                        languagePickerTarget = "translation"
                        languageSearch = ""
                        languagePickerOpen = true
                    }, modifier = Modifier.fillMaxWidth()) { AppUiText("Choose translation language: " + languageLabel(templateTranslationLanguage)) }
                    OutlinedButton(
                        onClick = {
                            execute {
                                val targetCode = templateTranslationLanguage.trim().lowercase(Locale.ROOT)
                                require(TranslateLanguage.fromLanguageTag(targetCode) != null) {
                                    "That language code is not supported by the on-device translator. Choose a supported language code such as ur, hi, ar, fr or es."
                                }
                                val translated = translatedTemplateRows.toMutableMap()
                                filteredTemplates.distinctBy { templateTranslationKey(it) }.forEach { template ->
                                    if (!template.languageCode.equals(targetCode, ignoreCase = true)) {
                                        translated[templateTranslationKey(template)] = translateTemplate(template, targetCode)
                                    }
                                }
                                translatedTemplateRows = translated
                                notice = "Translated " + filteredTemplates.size + " visible templates to " + targetCode + ". These previews are translated on this device; source templates remain unchanged."
                            }
                        },
                        enabled = !busy && filteredTemplates.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("🌐 Translate all visible templates") }
                    filteredTemplates.distinctBy { templateTranslationKey(it) }.forEach { sourceTemplate ->
                        val template = translatedTemplateRows[templateTranslationKey(sourceTemplate)] ?: sourceTemplate
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0FF)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AppUiText(template.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                AppUiText(
                                    template.subjectTitle + " • " + readableQuestionType(template.questionType),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (template.materials.isNotEmpty()) {
                                    AppUiText("Materials", fontWeight = FontWeight.Bold)
                                    AppUiText(template.materials.joinToString(" • "))
                                }
                                if (template.steps.isNotEmpty()) {
                                    AppUiText("Activity steps", fontWeight = FontWeight.Bold)
                                    template.steps.forEachIndexed { index, step ->
                                        AppUiText("${index + 1}. $step")
                                    }
                                }
                                AppUiText("Question preview", fontWeight = FontWeight.Bold)
                                if (template.conceptKey == "solar-system-planet-id") SolarSystemModelPreview()
                                else if ((template.conceptKey.startsWith("4d:") || template.conceptKey.startsWith("3d:"))) {
                                    val model = template.conceptKey.removePrefix("4d:").removePrefix("3d:").substringBefore(":")
                                    if (model == "solar-system") SolarSystemModelPreview() else Learning3DPreview(model)
                                }
                                AppUiText(template.prompt)
                                if (template.options.isNotEmpty()) {
                                    AppUiText("Answer choices: " + template.options.joinToString(" • "))
                                }
                                AppUiText("Parent's worked solution", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                AppUiText(template.solution)
                                if (template.safety.isNotBlank()) {
                                    AppUiText("Safety", fontWeight = FontWeight.Bold, color = Color(0xFF8B4C00))
                                    AppUiText(template.safety, color = Color(0xFF774300))
                                }
                                Button(
                                    onClick = {
                                        execute {
                                            val targetSubject = repository.findOrCreateSubject(
                                                familyId,
                                                template.subjectTitle,
                                                template.languageCode,
                                                templateCountryCode
                                            )
                                            subjects = repository.loadSubjects(familyId, templateCountryCode)
                                            selectedSubject = targetSubject
                                            questions = repository.loadQuestions(targetSubject.id)
                                            questionTranslations = repository.loadQuestionTranslations(questions.map { it.id })
                                            questionSolutions = repository.loadQuestionSolutions(questions.map { it.id })
                                            questionPrompt = template.prompt
                                            question3DModel = if ((template.conceptKey.startsWith("4d:") || template.conceptKey.startsWith("3d:")))
                                                template.conceptKey.removePrefix("4d:").removePrefix("3d:").substringBefore(":") else "none"
                                            questionType = template.questionType
                                            questionOptions = template.options.joinToString("\n")
                                            questionAnswer = template.answer
                                            questionLanguage = template.languageCode
                                            questionConceptKey = if ((template.conceptKey.startsWith("4d:") || template.conceptKey.startsWith("3d:")))
                                                template.conceptKey.removePrefix("4d:").removePrefix("3d:").substringAfter(":")
                                            else template.conceptKey
                                            questionSolution = buildString {
                                                append(template.solution)
                                                if (template.materials.isNotEmpty()) {
                                                    append("\n\nMaterials: ")
                                                    append(template.materials.joinToString(", "))
                                                }
                                                if (template.steps.isNotEmpty()) {
                                                    append("\n\nSteps:\n")
                                                    append(template.steps.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n"))
                                                }
                                                if (template.safety.isNotBlank()) {
                                                    append("\n\nSafety: ")
                                                    append(template.safety)
                                                }
                                            }
                                            screen = AppScreen.ADD_QUESTION
                                            notice = "Template loaded. Review it, then save the question and private solution."
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { AppUiText("Use template · create question") }
                            }
                        }
                    }
                    AppUiText(
                        "Experiments use ordinary classroom materials only. Results vary with materials, amounts, temperature and technique. Follow the safety notes and use adult supervision where stated; never mix these activities with cleaning products.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AppScreen.ADD_SUBJECT -> ScreenFrame(if (editingSubjectId == null) "Create subject" else "Edit subject", onBack = { screen = AppScreen.SUBJECTS }, notice = notice, busy = busy) {
                    Field(subjectTitle, { subjectTitle = it }, "Subject name")
                    Field(subjectLanguage, { subjectLanguage = it.trim().take(16) }, "Language code (en, ur, sd, hi, or any language)")
                    OutlinedButton(onClick = {
                        countryPickerTarget = "subject"
                        countrySearch = ""
                        countryPickerOpen = true
                    }, modifier = Modifier.fillMaxWidth()) {
                        AppUiText("🌍 Subject country: " + displayCountryName(subjectCountryCode))
                    }
                    Field(subjectGradeLevel, { subjectGradeLevel = it.filter(Char::isDigit).take(2) }, "Grade level (0 = Kindergarten, 1–13)", keyboardType = KeyboardType.Number)
                    Button(onClick = {
                        execute {
                            val grade = subjectGradeLevel.toIntOrNull() ?: throw IllegalArgumentException("Enter a grade from 0 to 13.")
                            require(grade in 0..13) { "Grade must be between Kindergarten (0) and 13." }
                            val currentSubjectId = editingSubjectId
                            val savedSubject = if (currentSubjectId == null) {
                                repository.addSubject(familyId, subjectTitle, subjectLanguage, subjectCountryCode, grade)
                            } else {
                                repository.updateSubject(currentSubjectId, subjectTitle, subjectLanguage, subjectCountryCode, grade)
                            }
                            val starterQuestions = fourDTemplatesForSubject(
                                savedSubject.title, savedSubject.languageCode, grade,
                                selectedChild?.ageYears ?: (grade + 5).coerceIn(3, 19)
                            )
                            val addedQuestionCount = repository.ensureTemplateQuestions(savedSubject.id, starterQuestions)
                            selectedSubject = savedSubject
                            questions = repository.loadQuestions(savedSubject.id)
                            questionTranslations = repository.loadQuestionTranslations(questions.map { it.id })
                            questionSolutions = repository.loadQuestionSolutions(questions.map { it.id })
                            subjects = repository.loadSubjects(familyId, parentCountryCode)
                            editingSubjectId = null
                            screen = AppScreen.QUESTIONS
                            notice = if (currentSubjectId == null) {
                                "Subject created with " + addedQuestionCount + " starter questions. Add, review, or translate questions on this subject page."
                            } else {
                                "Subject updated. " + addedQuestionCount + " missing starter questions added. You can manage its questions here."
                            }
                        }
                    }, enabled = !busy && subjectTitle.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        AppUiText(if (editingSubjectId == null) "Save subject" else "Save subject changes")
                    }
                }

                AppScreen.QUESTIONS -> ScreenFrame(selectedSubject?.title ?: "Questions", onBack = { screen = AppScreen.SUBJECTS }, notice = notice, busy = busy) {
                    val subject = selectedSubject
                    if (subject != null) {
                        AppUiText("Create a question in " + subject.languageCode + " and give related questions the same concept tag. The tag lets Concept Bridge report understanding separately from language.")
                        Field(
                            bulkTranslationLanguage,
                            { bulkTranslationLanguage = it.trim().lowercase(Locale.ROOT).take(16) },
                            "Translate every question to language code (e.g. en, ur, sd, ar, hi, fr, es)"
                        )
                        OutlinedButton(onClick = {
                            languagePickerTarget = "translation"
                            languageSearch = ""
                            languagePickerOpen = true
                        }, modifier = Modifier.fillMaxWidth()) { AppUiText("Choose translation language: " + languageLabel(bulkTranslationLanguage)) }
                        OutlinedButton(
                            onClick = {
                                execute {
                                    val targetCode = bulkTranslationLanguage.trim().lowercase(Locale.ROOT)
                                    require(TranslateLanguage.fromLanguageTag(targetCode) != null) {
                                        "That language code is not supported by the on-device translator. Choose a supported language code such as ur, hi, ar, fr or es."
                                    }
                                    val answerKeys = repository.loadQuestionAnswerKeys(questions.map { it.id })
                                    var saved = 0
                                    var skipped = 0
                                    questions.forEach { question ->
                                        if (question.languageCode.equals(targetCode, ignoreCase = true)) {
                                            skipped++
                                        } else {
                                            val canonicalAnswer = answerKeys[question.id]
                                                ?: throw IllegalStateException("Could not read the parent-only answer key for question: " + question.prompt.take(80))
                                            val answerIndex = question.options.indexOfFirst { it.equals(canonicalAnswer, ignoreCase = true) }
                                            val needsSeparateAnswer = answerIndex < 0
                                            val sourceValues = listOf(question.prompt) + question.options +
                                                if (needsSeparateAnswer) listOf(canonicalAnswer) else emptyList()
                                            val translatedValues = translateManyOnDevice(sourceValues, question.languageCode, targetCode)
                                            val translatedPrompt = translatedValues.first()
                                            val translatedOptions = translatedValues.subList(1, 1 + question.options.size)
                                            val translatedAnswer = if (answerIndex >= 0) translatedOptions[answerIndex]
                                                else translatedValues.last()
                                            repository.saveQuestionTranslation(
                                                question, targetCode, translatedPrompt, translatedOptions, listOf(translatedAnswer)
                                            )
                                            saved++
                                        }
                                    }
                                    questionTranslations = repository.loadQuestionTranslations(questions.map { it.id })
                                    notice = "Translated and saved " + saved + " questions to " + targetCode +
                                        ". Skipped same-language questions: " + skipped + ". Existing original questions were preserved."
                                }
                            },
                            enabled = !busy && questions.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("🌐 Auto-translate all " + questions.size + " questions") }
                        AppUiText("Uses Google's on-device translation models. The first use downloads language models; internet is needed for that download. Available languages depend on the installed model.")
                        AppUiText(
                            questions.size.toString() + " saved questions in this subject",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = { templateCategory = "Mathematics"; screen = AppScreen.LEARNING_TEMPLATES },
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("✨ Browse ready-made templates") }
                        Button(onClick = {
                            questionPrompt = ""; questionOptions = ""; questionAnswer = ""; questionType = "multiple_choice"
                            questionLanguage = subject.languageCode
                            questionConceptKey = ""
                            questionSolution = ""
                            screen = AppScreen.ADD_QUESTION
                        }, modifier = Modifier.fillMaxWidth()) { AppUiText("Add question") }
                        OutlinedButton(
                            onClick = {
                                questionPrompt = ""
                                questionOptions = ""
                                questionAnswer = ""
                                questionSolution = ""
                                questionConceptKey = "custom-3d-question"
                                questionLanguage = subject.languageCode
                                question3DModel = "atom"
                                questionType = "multiple_choice"
                                screen = AppScreen.ADD_QUESTION
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("✨ Create a custom interactive 4D question") }
                        if (questions.isEmpty()) AppUiText("No questions yet.")
                        questions.forEachIndexed { index, question ->
                            CompositionLocalProvider(LocalLayoutDirection provides directionFor(question.languageCode)) {
                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        AppUiText((index + 1).toString() + ". " + question.prompt, fontWeight = FontWeight.SemiBold)
                                        AppUiText("Type: " + readableQuestionType(question.questionType))
                                        AppUiText("Language: " + question.languageCode + " • Concept tag: " + question.conceptKey)
                                        val fourDModel = when {
                                            question.conceptKey.startsWith("4d:", ignoreCase = true) ->
                                                question.conceptKey.substringAfter("4d:").substringBefore(":").takeIf { it.isNotBlank() }
                                            question.conceptKey.startsWith("3d:", ignoreCase = true) ->
                                                question.conceptKey.substringAfter("3d:").substringBefore(":").takeIf { it.isNotBlank() }
                                            else -> null
                                        }
                                        if (fourDModel != null) {
                                            OutlinedButton(
                                                onClick = {
                                                    expandedFourDQuestionId =
                                                        if (expandedFourDQuestionId == question.id) null else question.id
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                AppUiText(
                                                    if (expandedFourDQuestionId == question.id) "Hide interactive 4D model"
                                                    else "🔎 Explore interactive 4D model"
                                                )
                                            }
                                            if (expandedFourDQuestionId == question.id) {
                                                if (fourDModel == "solar-system") SolarSystemModelPreview()
                                                else Learning3DPreview(fourDModel)
                                            }
                                        }
                                        if (question.options.isNotEmpty()) AppUiText("Options: " + question.options.joinToString(" • "))
                                        AppUiText("Translations: " + (questionTranslations[question.id]?.size ?: 0))
                                        questionSolutions[question.id]?.let { solution ->
                                            AppUiText("Worked solution", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            AppUiText(solution)
                                        }
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
                                        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { AppUiText("Add / edit translation") }
                                    }
                                }
                            }
                        }
                    }
                }

                AppScreen.ADD_QUESTION -> ScreenFrame("Add question", onBack = { screen = AppScreen.QUESTIONS }, notice = notice, busy = busy) {
                    val lang = questionLanguage
                    AppUiText("Question language: " + lang + ". Type in Urdu, Sindhi, English or another Unicode language.")
                    AppUiText("Optional interactive 4D model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    AppUiText("Choose a model to attach a touch-friendly learning visual to your question.")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "none" to "No model",
                            "atom" to "⚛ Atom",
                            "molecule" to "🧪 Molecule",
                            "geometry" to "📐 4D exploded shapes",
                            "cell" to "🧬 Cell",
                            "force" to "🏀 Forces",
                            "water-cycle" to "🌧 Water cycle",
                            "earth" to "🌍 Earth",
                            "circuit" to "⚡ Circuit",
                            "solar-system" to "🪐 Solar System"
                        ).chunked(2).forEach { group ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                group.forEach { (model, label) ->
                                    OutlinedButton(onClick = { question3DModel = model }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                        AppUiText(if (question3DModel == model) "✓ $label" else label)
                                    }
                                }
                            }
                        }
                    }
                    if (question3DModel != "none") {
                        if (question3DModel == "solar-system") SolarSystemModelPreview()
                        else Learning3DPreview(question3DModel)
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides directionFor(lang)) {
                        Field(questionPrompt, { questionPrompt = it }, "Question prompt")
                        Field(questionConceptKey, { questionConceptKey = it }, "Concept tag (reuse for same idea across questions)")
                        Field(questionLanguage, { questionLanguage = it }, "Question language code (en, ur, sd, etc.)")
                        AppUiText("Question type")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { questionType = "multiple_choice" }) { AppUiText(if (questionType == "multiple_choice") "✓ MCQ" else "MCQ") }
                            OutlinedButton(onClick = { questionType = "true_false" }) { AppUiText(if (questionType == "true_false") "✓ True/False" else "True/False") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { questionType = "short_answer" }) { AppUiText(if (questionType == "short_answer") "✓ Short" else "Short") }
                            OutlinedButton(onClick = { questionType = "numeric" }) { AppUiText(if (questionType == "numeric") "✓ Numeric" else "Numeric") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { questionType = "matching" }) { AppUiText(if (questionType == "matching") "✓ Matching" else "Matching") }
                            OutlinedButton(onClick = { questionType = "picture" }) { AppUiText(if (questionType == "picture") "✓ Picture" else "Picture") }
                        }
                        if (questionType == "multiple_choice") {
                            Field(questionOptions, { questionOptions = it }, "Options (one per line)")
                        } else if (questionType == "matching") {
                            Field(questionOptions, { questionOptions = it }, "Matching pairs (one per line: left|right)")
                            AppUiText("Correct answer format: left=right;left=right. Example: cat=meow;dog=bark.")
                        } else if (questionType == "picture") {
                            Field(questionOptions, { questionOptions = it }, "Optional picture labels / choices (one per line)")
                            OutlinedButton(
                                onClick = { questionPicturePicker.launch("image/*") },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) { AppUiText(if (questionPictureUri == null) "Choose picture" else "Change picture") }
                            questionPicturePreview?.let { preview ->
                                AndroidView(
                                    factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER } },
                                    update = { it.setImageBitmap(preview); it.adjustViewBounds = true },
                                    modifier = Modifier.fillMaxWidth().height(220.dp)
                                )
                            }
                            questionPictureError?.let { AppUiText(it, color = MaterialTheme.colorScheme.error) }
                            AppUiText("EduLock shrinks this image to a maximum of 1024 px and uploads it to your family's private question-images storage.")
                        } else if (questionType == "true_false") {
                            AppUiText("Save the canonical answer as true or false. A translation can use localized labels and accepted answers.")
                        }
                        Field(questionAnswer, { questionAnswer = it }, "Canonical correct answer (private)")
                        Field(questionSolution, { questionSolution = it }, "Parent-only worked solution / experiment explanation")
                        AppUiText("Worked solutions are stored separately from child-visible questions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = {
                        execute {
                            val subject = selectedSubject ?: throw IllegalStateException("Choose a subject first.")
                            val options = questionOptions.lines().map { it.trim() }.filter { it.isNotEmpty() }
                            val savedConceptKey = if (question3DModel == "none") questionConceptKey else "4d:$question3DModel:$questionConceptKey"
                            val canonicalAnswer = if (questionType == "matching") {
                                options.mapNotNull { raw ->
                                    val pair = raw.split("|", limit = 2)
                                    if (pair.size == 2 && pair[0].isNotBlank() && pair[1].isNotBlank()) pair[0].trim() + "=" + pair[1].trim() else null
                                }.joinToString(";")
                            } else questionAnswer
                            val newQuestionId = repository.addQuestion(subject.id, questionPrompt, questionType, options, canonicalAnswer, questionLanguage, savedConceptKey, questionSolution)
                            if (questionType == "picture") {
                                val bytes = questionPictureBytes ?: throw IllegalArgumentException("Choose a picture before saving this picture question.")
                                val parentChild = selectedChild ?: throw IllegalStateException("Choose the child profile for this question.")
                                try {
                                    repository.uploadQuestionImage(parentChild.familyId, newQuestionId, bytes)
                                } catch (error: Exception) {
                                    questionPictureError = error.message ?: "Picture upload failed."
                                    throw error
                                }
                            }
                            questions = repository.loadQuestions(subject.id)
                            questionTranslations = repository.loadQuestionTranslations(questions.map { it.id })
                            questionSolutions = repository.loadQuestionSolutions(questions.map { it.id })
                            questionSolution = ""
                            screen = AppScreen.QUESTIONS
                            notice = "Question and parent-only worked solution saved. The solution is not sent to the child."
                        }
                    }, enabled = !busy && questionPrompt.isNotBlank() && (questionAnswer.isNotBlank() || (questionType == "matching" && questionOptions.isNotBlank())) && questionConceptKey.isNotBlank(), modifier = Modifier.fillMaxWidth()) { AppUiText("Save question") }
                }

                AppScreen.ADD_TRANSLATION -> ScreenFrame("Concept Bridge — translation", onBack = { screen = AppScreen.QUESTIONS }, notice = notice, busy = busy) {
                    val original = selectedQuestionForTranslation
                    if (original == null) {
                        AppUiText("Choose a question first.")
                    } else {
                        AppUiText("Original: " + original.prompt)
                        if (original.questionType == "picture" && original.picturePath != null) {
                            var originalPictureUrl by remember(original.id) { mutableStateOf<String?>(null) }
                            LaunchedEffect(original.id, original.picturePath) {
                                originalPictureUrl = try { repository.createSignedQuestionImageUrl(original.picturePath) } catch (_: Exception) { null }
                            }
                            originalPictureUrl?.let { url ->
                                AndroidView(
                                    factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = false; webViewClient = WebViewClient() } },
                                    update = { view ->
                                        view.loadDataWithBaseURL(null, "<html><body style='margin:0'><img src='$url' style='max-width:100%;height:auto'/></body></html>", "text/html", "UTF-8", null)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(220.dp)
                                )
                            } ?: AppUiText("Loading or unavailable family picture.")
                        }
                        AppUiText("Concept tag: " + original.conceptKey + " • Original language: " + original.languageCode)
                        AppUiText("Save each translated question with its translated options and accepted correct answers. The child can switch between these language versions without changing the underlying question or concept.")
                        Field(translationLanguage, { translationLanguage = it }, "Target language code (en, ur, sd, etc.)")
                        CompositionLocalProvider(LocalLayoutDirection provides directionFor(translationLanguage)) {
                            Field(translationPrompt, { translationPrompt = it }, "Translated question prompt")
                            if (original.questionType == "multiple_choice" || original.questionType == "true_false") {
                                Field(translationOptions, { translationOptions = it }, "Translated options (one per line)")
                            } else if (original.questionType == "matching") {
                                Field(translationOptions, { translationOptions = it }, "Translated matching pairs (one per line: left|right)")
                                AppUiText("Use localized left|right pairs in the same order as the original pairs. The app will shuffle right-side choices.")
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
                            AppUiText("Save translation")
                        }
                    }
                }

                AppScreen.ASSIGN_TASK -> ScreenFrame("Assign task & reward", onBack = { screen = AppScreen.PARENT_HOME }, notice = notice, busy = busy) {
                    AppUiText("Choose the child")
                    children.forEach { child ->
                        OutlinedButton(onClick = {
                            selectedChild = child
                            selectedSubject = null
                            selectedTaskSubjectIds = emptySet()
                            taskRewardPackageName = ""
                            taskRewardGroupId = ""
                            taskRewardToBank = true
                            execute {
                                subjects = repository.loadSubjects(familyId, child.countryCode)
                                allowedApps = repository.loadAllowedApps(child.id)
                                appGroups = repository.loadAppGroups(child.id)
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            AppUiText((if (selectedChild?.id == child.id) "✓ " else "") + child.displayName)
                        }
                    }
                    val selectedTaskCountry = selectedChild?.countryCode ?: parentCountryCode
                    val selectedChildTrack = selectedChild?.studyTrack ?: "none"
                    AppUiText(
                        "Country curriculum: " + displayCountryName(selectedTaskCountry) +
                            " • Child's religious education: " + studyTrackLabel(selectedChildTrack),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    AppUiText("Choose a subject")
                    val assignableSubjects = subjects.filter { subject ->
                        val religionTrack = religionTrackForSubject(subject.title)
                        val isCountrySubject = subject.countryCode.equals(selectedTaskCountry, ignoreCase = true) ||
                            subject.countryCode.equals("ZZ", ignoreCase = true)
                        isCountrySubject && (religionTrack == null ||
                            (selectedChildTrack != "none" && religionTrack == selectedChildTrack))
                    }
                    val selectedTaskSubjects = assignableSubjects.filter { it.id in selectedTaskSubjectIds }
                    AppUiText("Choose one subject bank per task. Create separate Mathematics, Physics, and other subject tasks so each task contains questions from only its selected subject.")
                    if (assignableSubjects.isEmpty()) {
                        AppUiText("No matching subjects yet. Open the country-specific learning templates, add a subject, then return here to assign its task.")
                        OutlinedButton(
                            onClick = {
                                templateCategory = if (selectedChildTrack == "none") "Mathematics" else "Religious Education"
                                screen = AppScreen.LEARNING_TEMPLATES
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppUiText(if (selectedChildTrack == "none") "🗂️ Open country curriculum templates" else "🕊️ Open matching religious-study templates")
                        }
                    }
                    assignableSubjects.forEach { subject ->
                        OutlinedButton(onClick = {
                            selectedTaskSubjectIds = setOf(subject.id)
                            selectedSubject = subject
                            notice = "Selected " + subject.title + ". Only its questions will be assigned to this task."
                        }, modifier = Modifier.fillMaxWidth()) {
                            AppUiText((if (subject.id in selectedTaskSubjectIds) "✓ Selected • " else "") + subject.title + " (" + subject.languageCode + ")")
                        }
                    }
                    Field(taskTitle, { taskTitle = it }, "Task title")
                    Field(taskQuestionCount, { taskQuestionCount = it.filter(Char::isDigit).take(3) }, "Questions in task (1–500)", keyboardType = KeyboardType.Number)
                    Field(taskPassPercent, { taskPassPercent = it.filter(Char::isDigit).take(3) }, "Passing score percent (0–100)", keyboardType = KeyboardType.Number)
                    Field(taskRewardMinutes, { taskRewardMinutes = it.filter(Char::isDigit).take(4) }, "Reward minutes per correct answer (0–1440)", keyboardType = KeyboardType.Number)
                    AppUiText("Each correct answer earns this many minutes, even if the child does not pass. Rewards can go to general mobile time, one app, or a group of apps, within daily caps.")
                    Field(taskDailyCapMinutes, { taskDailyCapMinutes = it.filter(Char::isDigit).take(4) }, "Task daily reward cap (0–1440)", keyboardType = KeyboardType.Number)
                    Field(taskTimeLimitSeconds, { taskTimeLimitSeconds = it.filter(Char::isDigit).take(4) }, "Total task time in seconds (0 = no timer; 10–3600)", keyboardType = KeyboardType.Number)
                    AppUiText("When the total task timer ends, the task is submitted and marked completed. Use 0 for no task-wide timer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Field(taskQuestionTimeLimitSeconds, { taskQuestionTimeLimitSeconds = it.filter(Char::isDigit).take(3) }, "Time per question in seconds (0 = no timer; 5–600)", keyboardType = KeyboardType.Number)
                    AppUiText("When a question timer ends, that question locks and the next question opens. Use 0 if each question should have no separate timer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppUiText("Reward destination", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        onClick = {
                            taskRewardToBank = !taskRewardToBank
                            if (taskRewardToBank) {
                                taskRewardPackageName = ""
                                taskRewardGroupId = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppUiText(
                            if (taskRewardToBank) "✓ Reward Bank • child chooses an approved app"
                            else "Use a fixed app or group destination"
                        )
                    }
                    AppUiText(
                        if (taskRewardToBank)
                            "Each correct answer adds minutes to the child's Reward Bank. The child can spend them only on apps you have enabled in App access."
                        else
                            "Choose exactly where this task's reward will be sent. The child cannot redirect fixed-destination rewards.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!taskRewardToBank) {
                        OutlinedButton(onClick = { taskRewardPackageName = ""; taskRewardGroupId = "" }, modifier = Modifier.fillMaxWidth()) {
                            AppUiText((if (taskRewardPackageName.isBlank() && taskRewardGroupId.isBlank()) "✓ " else "") + "General mobile screen time")
                        }
                        val rewardAppsForChild = allowedApps.filter { it.childId == selectedChild?.id && it.enabled }
                        rewardAppsForChild.forEach { app ->
                            OutlinedButton(onClick = { taskRewardToBank = false; taskRewardPackageName = app.packageName; taskRewardGroupId = "" }, modifier = Modifier.fillMaxWidth()) {
                                AppUiText((if (taskRewardPackageName == app.packageName && taskRewardGroupId.isBlank()) "✓ " else "") + app.displayName + " only")
                            }
                        }
                        val rewardGroupsForChild = appGroups.filter { it.childId == selectedChild?.id && it.packageNames.isNotEmpty() }
                        rewardGroupsForChild.forEach { group ->
                            OutlinedButton(onClick = { taskRewardToBank = false; taskRewardPackageName = ""; taskRewardGroupId = group.id }, modifier = Modifier.fillMaxWidth()) {
                                AppUiText((if (taskRewardGroupId == group.id) "✓ " else "") + "Group: " + group.name + " • " + group.packageNames.size + " apps")
                            }
                        }
                        if (rewardGroupsForChild.isEmpty()) AppUiText("Create an app group in App access & daily limits to award time to a whole group.")
                    }
                    OutlinedButton(onClick = {
                        val child = selectedChild
                        if (child == null) notice = "Select a child first."
                        else execute {
                            allowedApps = repository.loadAllowedApps(child.id)
                            appGroups = repository.loadAppGroups(child.id)
                        }
                    }, modifier = Modifier.fillMaxWidth()) { AppUiText("Refresh child's available apps") }
                    Button(onClick = {
                        execute {
                            val child = selectedChild ?: throw IllegalArgumentException("Select a child.")
                            val chosenSubjects = assignableSubjects.filter { it.id in selectedTaskSubjectIds }
                            if (chosenSubjects.isEmpty()) throw IllegalArgumentException("Select at least one subject.")
                            repository.addTask(
                                familyId, child, chosenSubjects, taskTitle,
                                taskQuestionCount.toIntOrNull() ?: 0,
                                taskPassPercent.toIntOrNull() ?: -1,
                                taskRewardMinutes.toIntOrNull() ?: -1,
                                taskDailyCapMinutes.toIntOrNull() ?: -1,
                                taskRewardPackageName.takeIf { it.isNotBlank() },
                                taskRewardGroupId.takeIf { it.isNotBlank() },
                                taskTimeLimitSeconds.toIntOrNull()?.takeIf { it in 10..3600 },
                                taskRewardToBank,
                                taskQuestionTimeLimitSeconds.toIntOrNull()?.takeIf { it in 5..600 }
                            )
                            tasks = emptyList()
                            screen = AppScreen.PARENT_HOME
                            taskTitle = ""
                            taskRewardPackageName = ""
                            taskRewardGroupId = ""
                            taskRewardToBank = true
                            selectedTaskSubjectIds = emptySet()
                            notice = "Task assigned to " + child.displayName + ". Each correct answer earns the configured minutes, within task and daily caps."
                        }
                    }, enabled = !busy && selectedChild != null && selectedTaskSubjects.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { AppUiText("Assign task") }
                }

                AppScreen.REPORTS -> ScreenFrame("Progress reports", onBack = { screen = AppScreen.PARENT_HOME }, notice = notice, busy = busy) {
                    val total = attempts.size
                    val average = if (total == 0) 0 else attempts.sumOf { it.scorePercent } / total
                    SummaryCard("Completed tasks", total.toString(), "Only submitted attempts appear here.")
                    SummaryCard("Average score", average.toString() + "%", "Calculated from the recorded task attempts.")
                    SummaryCard("Reward minutes", attempts.sumOf { it.awardedMinutes }.toString(), "Total awarded minutes for the listed attempts.")
                    AppUiText("Concept Bridge — concept understanding by language", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    AppUiText("The same concept tag is grouped across translated question versions. Accuracy is shown separately for each language; a translated question remains the same underlying question.")
                    OutlinedButton(onClick = {
                        execute {
                            attempts = repository.loadAttempts()
                            conceptProgress = repository.loadConceptProgress()
                        }
                    }, modifier = Modifier.fillMaxWidth()) { AppUiText("Refresh report") }
                    if (conceptProgress.isEmpty()) AppUiText("No concept-level answers yet. Create a question with a concept tag, add a translation, and complete a task.")
                    conceptProgress.forEach { item ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                AppUiText(item.conceptKey, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                AppUiText("Language: " + languageLabel(item.languageCode))
                                AppUiText("Correct: " + item.correct + " / " + item.total + " • " + item.percent + "%")
                                LinearProgressIndicator(progress = { item.percent / 100f }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    AppUiText("Task history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (attempts.isEmpty()) AppUiText("No submitted task attempts yet.")
                    attempts.forEach { attempt ->
                        val child = children.firstOrNull { it.id == attempt.childId }
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                AppUiText(child?.displayName ?: "Child profile", fontWeight = FontWeight.SemiBold)
                                AppUiText("Score: " + attempt.scorePercent + "% • " + if (attempt.passed) "Passed" else "Not passed")
                                AppUiText("Earned: " + attempt.awardedMinutes + " minutes")
                                AppUiText("Completed: " + attempt.completedAt.replace("T", " ").take(16))
                            }
                        }
                    }
                }

                AppScreen.ALLOWED_APPS -> ScreenFrame("Allowed apps", onBack = { screen = AppScreen.CHILDREN }, notice = notice, busy = busy) {
                    val child = selectedChild
                    AppUiText("Child: " + (child?.displayName ?: "Not selected"))
                    AppUiText(
                        if (child?.kidsModeEnabled == true)
                            "Kids Mode is ON for this child. Only apps with an enabled rule are allowed; apps without a rule are blocked after the child phone successfully syncs. The launcher, navigation and essential phone/permission components stay available."
                        else
                            "User-facing apps from the child's phone. Apps start allowed in normal mode. Disable an app or set a daily limit to restrict it; changes sync while EduLock is open on the child's phone."
                    )
                    OutlinedButton(
                        onClick = {
                            execute {
                                val selected = child ?: throw IllegalStateException("Select a child profile first.")
                                childInstalledApps = repository.loadInstalledApps(selected.id)
                                allowedApps = repository.loadAllowedApps(selected.id)
                                appGroups = repository.loadAppGroups(selected.id)
                            }
                        },
                        enabled = !busy && child != null,
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("↻ Refresh apps from child's phone") }
                    val cleanedAppCount = childInstalledApps.count { installed ->
                        !installed.packageName.startsWith("com.android.internal.") &&
                            !installed.packageName.startsWith("com.android.providers.") &&
                            installed.packageName !in setOf(
                                "com.android.settings", "com.android.systemui",
                                "com.android.permissioncontroller", "com.google.android.permissioncontroller",
                                "com.android.packageinstaller", "com.google.android.packageinstaller",
                                "com.android.launcher3", "com.google.android.apps.nexuslauncher",
                                "com.miui.home", "com.samsung.android.oneui.home",
                                "com.oplus.launcher", "com.huawei.android.launcher"
                            ) &&
                            installed.packageName != "com.google.android.adservices.api" &&
                            !installed.packageName.contains("agingfunction", ignoreCase = true) &&
                            !installed.displayName.equals("3 Button Navigation Bar", ignoreCase = true) &&
                            !installed.displayName.equals("Ad Privacy", ignoreCase = true) &&
                            !installed.displayName.equals("AgingFunction", ignoreCase = true)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FF))
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            AppUiText("App access overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            AppUiText("$cleanedAppCount apps detected • " + allowedApps.count { it.enabled } + " enabled allowlist entries • " + allowedApps.count { !it.enabled } + " disabled entries")
                            AppUiText(
                                if (child?.kidsModeEnabled == true)
                                    "Kids Mode is ON: an enabled app rule is required. Individual daily limits and weekday windows still apply to enabled apps."
                                else
                                    "Normal mode allows apps unless individually disabled or restricted. Parent controls include daily minute caps and weekday access windows."
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F0FF))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppUiText("App groups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            AppUiText("Group approved apps together, then assign one multi-question task that awards shared time to the group.")
                            OutlinedTextField(
                                value = appGroupName,
                                onValueChange = { appGroupName = it.take(80) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { AppUiText("Group name") },
                                placeholder = { AppUiText("For example, Study break") }
                            )
                            AppUiText("Select allowed apps for this group", fontWeight = FontWeight.SemiBold)
                            val groupEligibleApps = allowedApps.filter { it.childId == child?.id && it.enabled }
                            if (groupEligibleApps.isEmpty()) AppUiText("Enable an app below first, then refresh the app list before adding it to a group.")
                            groupEligibleApps.forEach { app ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Checkbox(
                                        checked = app.packageName in appGroupSelectedPackages,
                                        onCheckedChange = { checked ->
                                            appGroupSelectedPackages = if (checked) appGroupSelectedPackages + app.packageName
                                            else appGroupSelectedPackages - app.packageName
                                        },
                                        enabled = !busy
                                    )
                                    Column(Modifier.weight(1f)) {
                                        AppUiText(app.displayName, fontWeight = FontWeight.SemiBold)
                                        AppUiText(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        execute {
                                            val selected = child ?: throw IllegalStateException("Select a child profile first.")
                                            val group = repository.saveAppGroup(selected.id, appGroupName, appGroupSelectedPackages, appGroupEditingId)
                                            appGroups = repository.loadAppGroups(selected.id)
                                            appGroupEditingId = group.id
                                            appGroupName = group.name
                                            appGroupSelectedPackages = group.packageNames
                                            notice = "App group “${group.name}” saved and verified with ${group.packageNames.size} apps."
                                        }
                                    },
                                    enabled = !busy && child != null && appGroupName.isNotBlank() && appGroupSelectedPackages.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) { AppUiText(if (appGroupEditingId == null) "Save group" else "Update group") }
                                OutlinedButton(
                                    onClick = {
                                        appGroupEditingId = null
                                        appGroupName = ""
                                        appGroupSelectedPackages = emptySet()
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f)
                                ) { AppUiText("New group") }
                            }
                            if (appGroups.isNotEmpty()) {
                                AppUiText("Saved groups", fontWeight = FontWeight.SemiBold)
                                appGroups.filter { it.childId == child?.id }.forEach { group ->
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            AppUiText(group.name, fontWeight = FontWeight.Bold)
                                            AppUiText(group.packageNames.joinToString { pkg ->
                                                allowedApps.firstOrNull { it.packageName == pkg }?.displayName ?: pkg
                                            })
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        appGroupEditingId = group.id
                                                        appGroupName = group.name
                                                        appGroupSelectedPackages = group.packageNames
                                                    },
                                                    enabled = !busy,
                                                    modifier = Modifier.weight(1f)
                                                ) { AppUiText("Edit") }
                                                OutlinedButton(
                                                    onClick = {
                                                        execute {
                                                            repository.deleteAppGroup(group.id)
                                                            appGroups = repository.loadAppGroups(group.childId)
                                                            if (appGroupEditingId == group.id) {
                                                                appGroupEditingId = null
                                                                appGroupName = ""
                                                                appGroupSelectedPackages = emptySet()
                                                            }
                                                            notice = "App group removed."
                                                        }
                                                    },
                                                    enabled = !busy,
                                                    modifier = Modifier.weight(1f)
                                                ) { AppUiText("Delete") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { AppUiText("Search apps") },
                        placeholder = { AppUiText("Find an app by name") }
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Allowed", "Blocked").forEach { filter ->
                            if (appStatusFilter == filter) {
                                Button(onClick = { appStatusFilter = filter }, modifier = Modifier.weight(1f)) { AppUiText(filter) }
                            } else {
                                OutlinedButton(onClick = { appStatusFilter = filter }, modifier = Modifier.weight(1f)) { AppUiText(filter) }
                            }
                        }
                    }
                    val ignoredSystemPackages = setOf(
                        "com.android.settings", "com.android.systemui",
                        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
                        "com.android.packageinstaller", "com.google.android.packageinstaller",
                        "com.android.launcher3", "com.google.android.apps.nexuslauncher",
                        "com.miui.home", "com.samsung.android.oneui.home",
                        "com.oplus.launcher", "com.huawei.android.launcher"
                    )
                    val filteredInstalledApps = childInstalledApps.filter { installed ->
                        val isInternal = installed.packageName.startsWith("com.android.internal.") ||
                            installed.packageName.startsWith("com.android.providers.") ||
                            installed.packageName in ignoredSystemPackages ||
                            installed.packageName == "com.google.android.adservices.api" ||
                            installed.packageName.contains("agingfunction", ignoreCase = true) ||
                            installed.displayName.equals("3 Button Navigation Bar", ignoreCase = true) ||
                            installed.displayName.equals("Ad Privacy", ignoreCase = true) ||
                            installed.displayName.equals("AgingFunction", ignoreCase = true)
                        val rule = allowedApps.firstOrNull { it.packageName == installed.packageName }
                        !isInternal &&
                            (appSearchQuery.isBlank() || installed.displayName.contains(appSearchQuery, ignoreCase = true) || installed.packageName.contains(appSearchQuery, ignoreCase = true)) &&
                            when (appStatusFilter) {
                                "Allowed" -> rule?.enabled != false
                                "Blocked" -> rule?.enabled == false
                                else -> true
                            }
                    }
                    if (childInstalledApps.isEmpty()) {
                        AppUiText("No apps received yet. Open EduLock on the paired child phone and keep it online. Its user-facing app list refreshes automatically every five minutes while EduLock is open.")
                    }
                    filteredInstalledApps.forEach { installed ->
                        val existingRule = allowedApps.firstOrNull { it.packageName == installed.packageName }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                AppUiText(installed.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                AppUiText(installed.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (existingRule == null) {
                                    var limitText = installedAppLimitEdits[installed.packageName] ?: ""
                                    Field(limitText, { value ->
                                        installedAppLimitEdits = installedAppLimitEdits + (installed.packageName to value.filter(Char::isDigit).take(4))
                                    }, "Daily limit in minutes (1–1440)", keyboardType = KeyboardType.Number)
                                    Button(
                                        onClick = {
                                            execute {
                                                val limit = (installedAppLimitEdits[installed.packageName] ?: "").toIntOrNull()
                                                    ?: throw IllegalArgumentException("Enter a daily limit from 1 to 1440 minutes.")
                                                require(limit in 1..1440) { "Daily limit must be between 1 and 1440 minutes." }
                                                val selected = child ?: throw IllegalStateException("Select a child profile first.")
                                                repository.allowInstalledApp(selected.id, installed, limit)
                                                allowedApps = repository.loadAllowedApps(selected.id)
                                                notice = "App rule saved with a $limit-minute daily limit. It remains allowed unless you disable it; the child phone applies updates after syncing."
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { AppUiText("Save app limit") }
                                } else {
                                    AppUiText(if (!existingRule.enabled) "Blocked by parent" else if (existingRule.dailyLimitMinutes >= 1440) "Allowed • no practical daily cap" else "Allowed • ${existingRule.dailyLimitMinutes} minutes/day")
                                    Field(
                                        installedAppLimitEdits[installed.packageName] ?: existingRule.dailyLimitMinutes.toString(),
                                        { value -> installedAppLimitEdits = installedAppLimitEdits + (installed.packageName to value.filter(Char::isDigit).take(4)) },
                                        "Daily limit in minutes (1–1440; 1440 = no practical cap)",
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
                                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { AppUiText("Save limit") }
                                    OutlinedButton(onClick = {
                                        execute {
                                            repository.updateAllowedAppEnabled(existingRule.id, !existingRule.enabled)
                                            allowedApps = child?.let { repository.loadAllowedApps(it.id) } ?: emptyList()
                                            notice = if (existingRule.enabled) "App disabled for child after the next sync." else "App enabled for child after the next sync."
                                        }
                                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                        AppUiText(if (existingRule.enabled) "Disable on child's phone" else "Enable on child's phone")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            weeklyScheduleEditIds = if (existingRule.id in weeklyScheduleEditIds)
                                                weeklyScheduleEditIds - existingRule.id else weeklyScheduleEditIds + existingRule.id
                                            if (existingRule.id !in weeklyScheduleEdits) {
                                                weeklyScheduleEdits = weeklyScheduleEdits + (
                                                    existingRule.id to AppWeeklySchedule.parse(existingRule.weeklyScheduleJson)
                                                )
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { AppUiText(if (existingRule.id in weeklyScheduleEditIds) "Hide weekly timetable" else "🗓 Set weekly timetable (24-hour clock)") }
                                    if (existingRule.id in weeklyScheduleEditIds) {
                                        val schedule = weeklyScheduleEdits[existingRule.id]
                                            ?: AppWeeklySchedule.parse(existingRule.weeklyScheduleJson)
                                        AppUiText("Select allowed weekdays and start/end time. Disabled days block this app all day. Times use the child's phone local clock; overnight windows are supported.")
                                        AppWeeklySchedule.DAY_KEYS.forEachIndexed { dayIndex, dayKey ->
                                            val dayWindow = schedule.days[dayKey] ?: AppDayWindow(enabled = false)
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(containerColor = if (dayWindow.enabled) Color(0xFFE1F7EF) else Color(0xFFFFEEF0))
                                            ) {
                                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    OutlinedButton(
                                                        onClick = { changeWeeklyWindow(existingRule.id, dayKey) { it.copy(enabled = !it.enabled) } },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) { AppUiText((if (dayWindow.enabled) "✓ " else "○ ") + AppWeeklySchedule.DAY_LABELS[dayIndex] +
                                                        if (dayWindow.enabled) " • Allowed hours" else " • Blocked all day") }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        OutlinedTextField(
                                                            value = dayWindow.start,
                                                            onValueChange = { value -> changeWeeklyWindow(existingRule.id, dayKey) { it.copy(start = value.filter { c -> c.isDigit() || c == ':' }.take(5)) } },
                                                            modifier = Modifier.weight(1f), singleLine = true,
                                                            label = { AppUiText("Start (HH:mm)") }, placeholder = { AppUiText("08:00") }
                                                        )
                                                        OutlinedTextField(
                                                            value = dayWindow.end,
                                                            onValueChange = { value -> changeWeeklyWindow(existingRule.id, dayKey) { it.copy(end = value.filter { c -> c.isDigit() || c == ':' }.take(5)) } },
                                                            modifier = Modifier.weight(1f), singleLine = true,
                                                            label = { AppUiText("End (HH:mm)") }, placeholder = { AppUiText("20:00") }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                execute {
                                                    val edited = weeklyScheduleEdits[existingRule.id] ?: AppWeeklySchedule.parse(existingRule.weeklyScheduleJson)
                                                    repository.updateAllowedAppWeeklySchedule(existingRule.id, edited)
                                                    allowedApps = child?.let { repository.loadAllowedApps(it.id) } ?: emptyList()
                                                    weeklyScheduleEditIds = weeklyScheduleEditIds - existingRule.id
                                                    weeklyScheduleEdits = weeklyScheduleEdits - existingRule.id
                                                    notice = "Weekly timetable saved. The child's device applies it after syncing."
                                                }
                                            },
                                            enabled = !busy,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { AppUiText("Save weekly timetable") }
                                    }
                                }
                            }
                        }
                    }
                    if (childInstalledApps.isNotEmpty() && allowedApps.isEmpty()) AppUiText("No saved app rules were returned. Apps remain allowed by default; check the database migration if you cannot save a limit.")
                    allowedApps.filter { app ->
                        childInstalledApps.none { it.packageName == app.packageName } &&
                            (appSearchQuery.isBlank() || app.displayName.contains(appSearchQuery, ignoreCase = true) || app.packageName.contains(appSearchQuery, ignoreCase = true)) &&
                            when (appStatusFilter) {
                                "Allowed" -> app.enabled
                                "Blocked" -> !app.enabled
                                else -> true
                            } &&
                            !app.packageName.startsWith("com.android.internal.") &&
                            !app.packageName.startsWith("com.android.providers.") &&
                            app.packageName !in setOf(
                                "com.android.settings", "com.android.systemui",
                                "com.android.permissioncontroller", "com.google.android.permissioncontroller",
                                "com.android.packageinstaller", "com.google.android.packageinstaller",
                                "com.android.launcher3", "com.google.android.apps.nexuslauncher",
                                "com.miui.home", "com.samsung.android.oneui.home",
                                "com.oplus.launcher", "com.huawei.android.launcher"
                            ) &&
                            app.packageName != "com.google.android.adservices.api" &&
                            !app.packageName.contains("agingfunction", ignoreCase = true) &&
                            !app.displayName.equals("3 Button Navigation Bar", ignoreCase = true) &&
                            !app.displayName.equals("Ad Privacy", ignoreCase = true) &&
                            !app.displayName.equals("AgingFunction", ignoreCase = true)
                    }.forEach { app ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                AppUiText(app.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                AppUiText(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { AppUiText("Save daily limit") }
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
                                ) { AppUiText(if (app.enabled) "Disable this app on child's device" else "Allow this app on child's device") }
                                OutlinedButton(onClick = {
                                    execute {
                                        repository.deleteAllowedApp(app.id)
                                        allowedApps = child?.let { repository.loadAllowedApps(it.id) } ?: emptyList()
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { AppUiText("Remove app") }
                            }
                        }
                    }
                    AppUiText("To enforce these rules on the child's phone, the child must enable EduLock in Android Settings → Accessibility on that device. Android may allow a device owner/admin to enforce stronger controls; a normal app cannot guarantee that it cannot be uninstalled or have its data cleared.")
                }

                AppScreen.CHILD_HOME -> ScreenFrame("Your learning space", notice = notice, busy = busy) {
                    val child = childProfile
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(
                            Modifier.fillMaxWidth()
                                .background(Brush.linearGradient(listOf(Color(0xFF594FEA), Color(0xFF148E91))))
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(
                                    Modifier.size(68.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(23.dp)),
                                    contentAlignment = Alignment.Center
                                ) { AppUiText(child?.avatarIcon ?: "🦊", fontSize = 39.sp) }
                                Column(Modifier.weight(1f)) {
                                    AppUiText("Hey, " + (child?.displayName ?: "super learner") + "!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                    AppUiText("Learn • Play • Earn rewards", color = Color.White.copy(alpha = 0.9f))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppUiText("⭐ " + childStreakDays + " day streak", color = Color.White, fontWeight = FontWeight.Bold)
                                AppUiText("•", color = Color.White.copy(alpha = 0.75f))
                                AppUiText("🪙 " + rewardBankMinutes + " reward min", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Button(
                        onClick = { screen = AppScreen.CHILD_HOME },
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("Learning • Continue tasks") }
                    // Large illustrated tiles make the child dashboard feel like a learning game hub.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardIconTile(
                            icon = "🎮", title = "Learning games", subtitle = "Play and practise",
                            modifier = Modifier.weight(1f), accent = Color(0xFF6957E8),
                            onClick = { openKidsGames() }
                        )
                        DashboardIconTile(
                            icon = "🎨", title = "Draw & colour", subtitle = "Create something",
                            modifier = Modifier.weight(1f), accent = Color(0xFFE88938),
                            onClick = { openKidsDrawingStudio() }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardIconTile(
                            icon = "🪐", title = "4D learning lab", subtitle = "Explore 3D/4D models",
                            modifier = Modifier.weight(1f), accent = Color(0xFF0A9A8B),
                            onClick = { openKids3DLab() }
                        )
                        DashboardIconTile(
                            icon = "🚀", title = "Solar explorer", subtitle = "Discover the planets",
                            modifier = Modifier.weight(1f), accent = Color(0xFFDC4C82),
                            onClick = { openSolarQuiz() }
                        )
                    }
                    if (child?.kidsModeEnabled == true) {
                        val setupAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                        val setupAccessibilityOperational = isAccessibilityServiceOperational(context)
                        val setupManagedActive = isManagedDeviceProtectionActive(context) &&
                            context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE)
                                .getBoolean("lock_task_policy_applied", false)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (setupManagedActive) Color(0xFFE3F8EA) else Color(0xFFFFF0D8)
                            ),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                AppUiText("🛡️ Phone protection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                AppUiText(
                                    when {
                                        setupManagedActive -> "Device-owner kiosk protection is verified on this phone."
                                        setupAccessibilityOperational -> "Accessibility guard is running. Full kiosk-level enforcement still needs Device Owner setup."
                                        setupAccessibilityEnabled -> "Accessibility is enabled, but EduLock has not confirmed a live service heartbeat yet."
                                        else -> "Not active yet. Enable EduLock in Android Accessibility settings on this child's phone."
                                    },
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!setupAccessibilityOperational) {
                                    Button(
                                        onClick = {
                                            accessibilitySettingsLauncher.launch(
                                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { AppUiText("Open Accessibility settings") }
                                }
                                OutlinedButton(
                                    onClick = {
                                        protectionAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                                        protectionAccessibilityOperational = isAccessibilityServiceOperational(context)
                                        protectionDeviceAdminEnabled = isDeviceAdminActive(context)
                                        protectionManagedEnabled = isManagedDeviceProtectionActive(context)
                                        notice = when {
                                            protectionManagedEnabled -> "Device-owner protection was checked on this phone."
                                            protectionAccessibilityOperational -> "Accessibility is enabled and EduLock's service is responding."
                                            protectionAccessibilityEnabled -> "Accessibility setting is enabled, but the service heartbeat is not current."
                                            else -> "Accessibility is disabled. App blocking is not active."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { AppUiText("Refresh protection status") }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            SummaryCard("Minutes earned today", earnedToday.toString(), "Minutes awarded today. Unspent bank minutes remain in the Reward Bank.")
                        }
                        Column(Modifier.weight(1f)) {
                            SummaryCard("Task streak", "$childStreakDays day${if (childStreakDays == 1) "" else "s"}", "Pass at least one task each day to build your streak.")
                        }
                    }
                    RewardBankPanel(
                        balanceMinutes = rewardBankMinutes,
                        approvedApps = allowedApps.filter { it.enabled },
                        selectedPackage = rewardAppSelection,
                        onSelectPackage = { rewardAppSelection = it },
                        minutesText = rewardMinutesToRedeem,
                        onMinutesTextChange = { rewardMinutesToRedeem = it.filter(Char::isDigit).take(4) },
                        busy = busy,
                        onRedeem = {
                            execute {
                                val childNow = childProfile ?: throw IllegalStateException("The child profile is not linked.")
                                val minutes = rewardMinutesToRedeem.toIntOrNull()
                                    ?: throw IllegalArgumentException("Enter how many reward minutes to use.")
                                val selectedApp = allowedApps.firstOrNull { it.enabled && it.packageName == rewardAppSelection }
                                    ?: throw IllegalArgumentException("Choose an app approved by your parent.")
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(selectedApp.packageName)
                                    ?: throw IllegalStateException("This app is not installed on this phone. Ask your parent to refresh approved apps.")
                                val redemption = repository.redeemChildRewardBank(selectedApp.packageName, minutes)
                                rewardBankMinutes = redemption.balanceMinutes
                                earnedToday = repository.refreshChildDeviceRestrictions(childNow)
                                allowedApps = repository.loadAllowedApps(childNow.id)
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(launchIntent)
                                notice = "Reward used: ${redemption.redeemedMinutes} minutes for ${redemption.targetAppName}."
                            }
                        }
                    )
                    AppUiText("My apps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (allowedApps.none { it.enabled }) {
                        AppUiText("No parent-approved apps are currently available.")
                    } else {
                        allowedApps.filter { it.enabled }.forEach { app ->
                            val lockPrefs = context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE)
                            val usedMs = lockPrefs.getLong("app_used_millis_" + app.packageName, 0L).coerceAtLeast(0L)
                            val earnedMs = lockPrefs.getStringSet("app_earned_minutes", emptySet()).orEmpty()
                                .firstOrNull { it.startsWith(app.packageName + "=") }?.substringAfter("=")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                            val remaining = (app.dailyLimitMinutes + earnedMs - (usedMs / 60_000L).toInt()).coerceAtLeast(0)
                            val hasTask = tasks.any { it.rewardToBank } ||
                                tasks.any { it.rewardPackageName == app.packageName } ||
                                tasks.any { task -> !task.rewardGroupId.isNullOrBlank() && appGroups.any { it.id == task.rewardGroupId && app.packageName in it.packageNames } }
                            val status = if (remaining <= 0) "Time's up • answer to unlock" else "$remaining min left"
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        AppUiText(app.displayName, fontWeight = FontWeight.SemiBold)
                                        AppUiText(status, color = if (remaining <= 0) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (hasTask) AppUiText("A learning task unlocks this app.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Button(
                                        onClick = {
                                            val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                            if (launch == null) {
                                                notice = "This app is not installed on the child phone."
                                            } else if (remaining <= 0) {
                                                val task = tasks.firstOrNull { it.rewardPackageName == app.packageName }
                                                    ?: tasks.firstOrNull { t -> !t.rewardGroupId.isNullOrBlank() && appGroups.any { it.id == t.rewardGroupId && app.packageName in it.packageNames } }
                                                    ?: tasks.firstOrNull { it.rewardToBank }
                                                if (task != null) {
                                                    execute {
                                                        val loadedQuestions = repository.loadQuestionsForTask(task.id, task.subjectId, task.questionCount)
                                                        if (loadedQuestions.size != task.questionCount) throw IllegalStateException("This task is missing questions.")
                                                        val loadedTranslations = repository.loadQuestionTranslations(loadedQuestions.map { it.id })
                                                        activeTask = task
                                                        questions = loadedQuestions
                                                        questionTranslations = loadedTranslations
                                                        answerLanguages = loadedQuestions.associate { q -> q.id to (childProfile?.preferredLanguage ?: q.languageCode) }
                                                        answers = emptyMap()
                                                        questionIndex = 0
                                                        taskDeadlineAtMillis = task.timeLimitSeconds?.let { System.currentTimeMillis() + it * 1000L }
                                                        taskSecondsRemaining = task.timeLimitSeconds ?: 0
                                                        screen = AppScreen.PLAY_TASK
                                                    }
                                                } else {
                                                    notice = "Time's up. Ask your parent to assign a learning task to this app."
                                                }
                                            } else {
                                                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(launch)
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.width(116.dp)
                                    ) { AppUiText(if (remaining <= 0) "Unlock" else "Open") }
                                }
                            }
                        }
                    }
                    AppUiText("Assigned tasks")
                    if (tasks.isEmpty()) AppUiText("No active tasks yet. Ask your parent to assign a task.")
                    tasks.forEach { task ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AppUiText(task.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                AppUiText(task.questionCount.toString() + " questions • pass " + task.passPercent + "% • " + task.rewardMinutes + " min per correct answer" + if (task.rewardToBank) " • Reward Bank" else "")
                                Button(onClick = {
                                    execute {
                                        val loadedQuestions = repository.loadQuestionsForTask(task.id, task.subjectId, task.questionCount)
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
                                        val draft = readTaskDraft(task.id)
                                        activeTask = task
                                        answers = draft.first.filterKeys { key -> loadedQuestions.any { it.id == key } }
                                        questionIndex = draft.second.coerceIn(0, (loadedQuestions.size - 1).coerceAtLeast(0))
                                        taskDeadlineAtMillis = null
                                        taskSecondsRemaining = 0
                                        screen = AppScreen.PLAY_TASK
                                    }
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { AppUiText("Start task") }
                            }
                        }
                    }
                    SummaryCard("Learning rewards earned today", earnedToday.toString() + " minutes", "Successful tasks add reward minutes. Rewards are tracked separately from app access.")
                    val kidsModeEnabled = child?.kidsModeEnabled == true
                    val serviceSettingEnabled = isAccessibilityServiceEnabled(context)
                    val serviceOperational = isAccessibilityServiceOperational(context)
                    val lockStatusPrefs = context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE)
                    val blockerError = lockStatusPrefs.getString("last_blocker_error", null)
                    val blockerErrorAt = lockStatusPrefs.getLong("last_blocker_error_at", 0L)
                    val blockerSuccessAt = lockStatusPrefs.getLong("last_blocker_success_at", 0L)
                    val blockerFailedRecently = !blockerError.isNullOrBlank() &&
                        blockerErrorAt > blockerSuccessAt &&
                        System.currentTimeMillis() - blockerErrorAt < 24L * 60L * 60L * 1000L
                    val managedProtectionActive = isManagedDeviceProtectionActive(context)
                    val kioskPolicyApplied = lockStatusPrefs.getBoolean("lock_task_policy_applied", false)
                    SummaryCard(
                        "App access",
                        if (kidsModeEnabled) "Kids Mode • allowlist" else "Available by default",
                        if (kidsModeEnabled)
                            "Only explicitly enabled apps should open. In managed kiosk mode, the normal launcher is excluded; calls and required permission/system prompts remain available. Other apps are blocked after the device confirms its rules sync."
                        else
                            "Apps stay available unless a parent disables an app or its configured daily limit is reached."
                    )
                    SummaryCard(
                        "App blocking",
                        when {
                            blockerFailedRecently -> "NOT CONFIRMED • blocker error"
                            serviceOperational -> "Service responding • overlay not recently tested"
                            serviceSettingEnabled -> "Enabled, not confirmed running"
                            else -> "Needs setup"
                        },
                        when {
                            blockerFailedRecently ->
                                "Android rejected the last restriction overlay, so app blocking is not reported as successful. Error: " + blockerError
                            serviceOperational && kidsModeEnabled ->
                                "Android confirms the Accessibility service is responding. The last restriction overlay succeeded; Kids Mode checks the synced allowlist."
                            serviceOperational ->
                                "Android confirms the Accessibility service is responding. Restrictions are enforced only when Android accepts the blocker overlay."

                            serviceSettingEnabled ->
                                "The Android toggle is on, but EduLock has not confirmed a recent service heartbeat. Refresh status before relying on app blocking."
                            else ->
                                "Automatic blocking is not active. Enable EduLock in Android Accessibility settings on the child's phone."
                        }
                    )
                    if (kidsModeEnabled) {
                        SummaryCard(
                            "Kids Mode enforcement",
                            when {
                                managedProtectionActive && kioskPolicyApplied -> "Managed kiosk policy confirmed"
                                serviceOperational -> "Accessibility fallback active"
                                else -> "NOT ACTIVE • setup required"
                            },
                            when {
                                managedProtectionActive && kioskPolicyApplied ->
                                    "Android confirmed device-owner restrictions and the Lock Task allowlist. Kids Mode excludes the normal launcher, so children stay in EduLock or explicitly allowed apps. System permission prompts and calls remain available."
                                serviceOperational ->
                                    "Accessibility-based blocking is running, but this phone is not confirmed as device owner. It cannot guarantee protection against force-stop, Clear data or system-level escape."
                                else ->
                                    "Kids Mode is saved in Supabase, but this phone has not confirmed active enforcement. Enable Accessibility and sync again. Kiosk-level enforcement requires device-owner provisioning."
                            }
                        )
                    }
                    SummaryCard(
                        "Uninstall & clear-data protection",
                        if (managedProtectionActive) "Managed app-management policy confirmed" else "Limited on this device",
                        if (managedProtectionActive)
                            "Android confirmed device-owner restrictions for uninstall and app-management controls. This status alone does not guarantee Clear-data or factory-reset protection."
                        else
                            "Visible device-admin mode alone does not reliably prevent Clear data or factory reset. Stronger protection requires device-owner provisioning before normal use."
                    )
                    if (childRulesSyncError != null) {
                        SummaryCard("Parental settings sync", "NOT CONFIRMED", childRulesSyncError ?: "Rules sync did not succeed.")
                    } else if (childRulesLastSyncedAt != null) {
                        SummaryCard(
                            "Parental settings sync",
                            "Confirmed",
                            "Last successful rules sync: " + java.text.SimpleDateFormat("HH:mm:ss", Locale.ROOT)
                                .format(java.util.Date(childRulesLastSyncedAt!!)) + ". New parent changes apply after a later successful sync."
                        )
                    }
                    if (serviceOperational) {
                        AppUiText("Device restriction service: enabled and recently responding on this device.")
                    } else {
                        AppUiText(
                            if (serviceSettingEnabled)
                                "Accessibility is enabled in Android settings, but the service has not confirmed it is running. Refresh status before relying on blocking."
                            else
                                "Automatic blocking needs Android Accessibility permission on this child's device."
                        )
                        Button(
                            onClick = { accessibilitySettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppUiText("Open Accessibility settings")
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
                        ) { AppUiText("Fix Android “Restricted setting”") }
                        AppUiText(
                            "If Android shows “Restricted setting”, open this app's App info, tap the ⋮ menu, choose “Allow restricted settings”, then return here and enable EduLock under Accessibility. This is an Android security step for apps installed outside an approved store; EduLock cannot bypass it."
                        )
                    }
                    if (isDeviceAdminActive(context)) {
                        AppUiText("🔒 Device administrator is enabled. It may add a deactivation step before ordinary uninstall, but it does not reliably prevent Clear data or factory reset.")
                    } else {
                        AppUiText("Optional: a parent can enable Android's visible device-administrator setting. This adds uninstall friction on supported devices; it cannot guarantee protection against Clear data or factory reset.")
                        OutlinedButton(onClick = {
                            val component = ComponentName(context, EduLockDeviceAdminReceiver::class.java)
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                                putExtra(
                                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "With a parent's permission, enable Android's visible device-administrator setting. It may add a deactivation step before ordinary uninstall, but does not reliably prevent Clear data or factory reset."
                                )
                            }
                            deviceAdminSettingsLauncher.launch(intent)
                        }, modifier = Modifier.fillMaxWidth()) {
                            AppUiText("🔒 Enable device administrator")
                        }
                    }
                    OutlinedButton(onClick = {
                        refreshProtectionStatus()
                        screen = AppScreen.PROTECTION_STATUS
                    }, modifier = Modifier.fillMaxWidth()) { AppUiText("Protection status & setup") }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FF))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppUiText("📍 Family location sharing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            AppUiText(
                                if (childLocationSharing) "Location sharing is enabled on this child's device. Android shows an ongoing notification while the service is active."
                                else "Location sharing is off. With permission, EduLock can report this device's location to the paired parent."
                            )
                            if (childLocationSharing) {
                                OutlinedButton(onClick = { disableChildLocationSharing() }, modifier = Modifier.fillMaxWidth()) {
                                    AppUiText("Pause location sharing")
                                }
                            } else {
                                Button(onClick = { requestChildLocationSharing() }, modifier = Modifier.fillMaxWidth()) {
                                    AppUiText("Enable location sharing")
                                }
                            }
                            if (Build.VERSION.SDK_INT >= 33 &&
                                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                OutlinedButton(
                                    onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { AppUiText("Enable location status notification") }
                            }
                            AppUiText("Only the latest location is retained on the server. If offline, the previous server location stays visible and new fixes queue locally until connection returns.")
                        }
                    }
                    Button(onClick = {
                        execute {
                            val linked = childProfile ?: throw IllegalStateException("Sign in to your child account again.")
                            tasks = repository.loadChildTasks(linked.id)
                            earnedToday = repository.refreshChildDeviceRestrictions(linked)
                            childRulesLastSyncedAt = context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE)
                                .getLong("rules_synced_at", System.currentTimeMillis())
                            childRulesSyncError = null
                            ChildDeviceKioskPolicy.enterOrExitLockTask(context, linked.kidsModeEnabled)
                            repository.syncAppUsage(linked.id)
                            screen = AppScreen.CHILD_HOME
                        }
                    }, modifier = Modifier.fillMaxWidth()) { AppUiText("Sync tasks and screen-time settings") }
                    AppUiText("This device is paired with your parent. Keep EduLock installed and its app data intact so this device can reconnect securely.")
                }

                AppScreen.PROTECTION_STATUS -> ScreenFrame(
                    "Protection & device setup",
                    onBack = { screen = if (childProfile != null) AppScreen.CHILD_HOME else AppScreen.PARENT_HOME },
                    notice = notice,
                    busy = busy
                ) {
                    AppUiText(
                        "EduLock only reports protections that Android says are enabled on this phone. " +
                            "For restrictions to protect a child, complete setup on the child's device."
                    )
                    SummaryCard(
                        "App blocking overlay",
                        when {
                            protectionAccessibilityOperational -> "Active • service responding"
                            protectionAccessibilityEnabled -> "Setting enabled; service not confirmed"
                            else -> "Needs setup"
                        },
                        when {
                            protectionAccessibilityOperational -> "Android confirms the Accessibility setting is enabled and EduLock has a recent service heartbeat."
                            protectionAccessibilityEnabled -> "Android reports the setting enabled, but the running service has not been confirmed."
                            else -> "Enable EduLock in Android Accessibility settings on the child's device to allow app-level restriction screens."
                        }
                    )
                    if (!protectionAccessibilityEnabled || !protectionAccessibilityOperational) {
                        Button(
                            onClick = {
                                accessibilitySettingsLauncher.launch(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("Open Accessibility settings") }
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
                                deviceAdminSettingsLauncher.launch(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("Enable visible device administrator") }
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
                        AppUiText(
                            "To use device-owner protection, a parent or device administrator must provision the phone as a fully managed device. This often has to be done during initial setup and may require a factory reset. Do not rely on Accessibility or ordinary device-admin mode to prevent clearing app data.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val requested = applyManagedDeviceProtection(context)
                            refreshProtectionStatus()
                            val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                            val confirmed = isManagedDeviceProtectionActive(context)
                            notice = when {
                                confirmed -> "Android verified the managed-device app-management restrictions on this phone."
                                !manager.isDeviceOwnerApp(context.packageName) ->
                                    "Not applied: Android does not identify EduLock as this phone's device owner. Strong kiosk protection is not active."
                                requested ->
                                    "The policy request returned, but Android did not confirm all required restrictions. EduLock is not reporting managed protection as active."
                                else ->
                                    "Android rejected or did not confirm the managed-device policy. No successful protection status is reported."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("Refresh status and apply supported device-owner rules") }
                    AppUiText(
                        "Safety note: Accessibility-based blocking cannot guarantee protection against force-stop, Clear data or factory reset on a normal phone. Kids Mode blocks Settings while the service is running. Device-owner kiosk policy is stronger and must be provisioned before normal use.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AppScreen.CHILD_LOCATIONS -> ScreenFrame(
                    "Family location",
                    onBack = { screen = AppScreen.PARENT_HOME },
                    notice = notice,
                    busy = busy
                ) {
                    AppUiText(
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
                    ) { AppUiText("↻ Refresh locations") }
                    if (children.isEmpty()) AppUiText("No child profiles are linked yet.")
                    children.forEach { child ->
                        val snapshot = childLocationRows.firstOrNull { it.childId == child.id }
                        val lastSeenMillis = snapshot?.lastSeenAt?.let { raw ->
                            runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
                        }
                        val minutesSinceSeen = lastSeenMillis?.let { ((System.currentTimeMillis() - it).coerceAtLeast(0L) / 60_000L) }
                        val statusText = when {
                            snapshot == null || snapshot.lastSeenAt == null -> "Waiting for the child's first device check-in"
                            minutesSinceSeen != null && minutesSinceSeen >= 10 -> "Not reporting for about $minutesSinceSeen minutes. The phone may be offline, switched off, or EduLock may be stopped."
                            !snapshot.sharingEnabled -> "Location sharing is paused; device-status check-ins may still be available"
                            else -> "Device checking in • last contact ${snapshot.lastSeenAt.replace("T", " ").take(19)}"
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppUiText(child.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                AppUiText(statusText, color = if (minutesSinceSeen != null && minutesSinceSeen >= 15 && snapshot?.sharingEnabled == true) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (snapshot?.batteryPercent != null) AppUiText("Child-device battery: ${snapshot.batteryPercent}%")
                                if (snapshot?.latitude != null && snapshot.longitude != null) {
                                    AppUiText("Last saved location: ${snapshot.latitude}, ${snapshot.longitude}", fontWeight = FontWeight.SemiBold)
                                    ChildLocationMap(snapshot.latitude, snapshot.longitude)
                                    if (snapshot.accuracyMeters != null) AppUiText("Reported accuracy: about ${snapshot.accuracyMeters.toInt()} m")
                                    AppUiText(
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
                                    }, modifier = Modifier.fillMaxWidth()) { AppUiText("Open last location in map") }
                                } else {
                                    AppUiText("No location has reached the server yet. Keep location sharing enabled on the child's phone and make sure it has an internet connection.")
                                }
                                AppUiText("The server retains the last successful point when the child device goes offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    AppUiText(
                        "Offline alerts need notification permission on this parent phone and an active internet connection. Android schedules checks periodically, so an alert may be delayed; it cannot tell whether a device is truly powered off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AppScreen.APP_USAGE_REPORT -> ScreenFrame("App usage report", onBack = { screen = AppScreen.CHILDREN }, notice = notice, busy = busy) {
                    val child = appUsageChild
                    AppUiText("Daily app usage for " + (child?.displayName ?: "this child"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    AppUiText("Shows package-level foreground time recorded by EduLock. Reports sync when the child opens EduLock or taps Sync; this is not a live remote screen view.")
                    OutlinedButton(onClick = {
                        execute {
                            val selected = child ?: throw IllegalStateException("Select a child profile first.")
                            appUsageReports = repository.loadAppUsageReports(selected.id)
                        }
                    }, enabled = !busy && child != null, modifier = Modifier.fillMaxWidth()) { AppUiText("↻  Refresh report") }
                    val totalSeconds = appUsageReports.sumOf { it.usedSeconds }
                    SummaryCard("Recorded use (reported days)", (totalSeconds / 3600).toString() + " h " + ((totalSeconds % 3600) / 60).toString() + " min", "Totals reflect the last seven days of reported, approved-app use.")
                    if (appUsageReports.isEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF5FF))) {
                            AppUiText("No usage reports yet. On the child device, open EduLock and tap “Sync tasks and screen-time settings”. Make sure the parent-approved Accessibility service is enabled.", modifier = Modifier.padding(18.dp), color = Color(0xFF244568))
                        }
                    } else {
                        appUsageReports.groupBy { it.usageDate }.forEach { (day, dailyRows) ->
                            AppUiText(day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            dailyRows.forEach { row ->
                                Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Color(0xFFE1E6F4)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(48.dp).background(Color(0xFFFFE9B5), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { AppUiText("📱", fontSize = 23.sp) }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            AppUiText(row.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            AppUiText(row.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            AppUiText((row.usedSeconds / 3600).toString() + " h " + ((row.usedSeconds % 3600) / 60).toString() + " min", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    AppUiText("Privacy: EduLock records app package names and total foreground time for approved apps only. It does not collect screen contents, keystrokes, messages, or browser history.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        AppUiText("Question " + (questionIndex + 1) + " of " + questions.size, style = MaterialTheme.typography.titleMedium)
                        if (task.timeLimitSeconds != null) {
                            SummaryCard(
                                "Time remaining",
                                String.format(java.util.Locale.ROOT, "%d:%02d", taskSecondsRemaining / 60, taskSecondsRemaining % 60),
                                "Answers submit automatically when the countdown reaches zero."
                            )
                        }
                        LinearProgressIndicator(progress = { (questionIndex + 1).toFloat() / questions.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                        AppUiText("Concept: " + question.conceptKey, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (question.conceptKey == "solar-system-planet-id") SolarSystemModelPreview()
                        else if ((question.conceptKey.startsWith("4d:") || question.conceptKey.startsWith("3d:"))) {
                            val model = question.conceptKey.removePrefix("4d:").removePrefix("3d:").substringBefore(":")
                            if (model == "solar-system") SolarSystemModelPreview() else Learning3DPreview(model)
                        }
                        AppUiText("Try this same concept in another language:", style = MaterialTheme.typography.bodyMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            languageOptions.forEach { lang ->
                                if (lang.equals(selectedLanguage, ignoreCase = true)) {
                                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { AppUiText("✓ " + languageLabel(lang)) }
                                } else {
                                    OutlinedButton(onClick = {
                                        answerLanguages = answerLanguages + (question.id to lang)
                                        answers = answers - question.id
                                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { AppUiText(languageLabel(lang)) }
                                }
                            }
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides directionFor(selectedLanguage)) {
                            AppUiText(promptForLanguage, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            if (question.questionType == "picture") {
                                when {
                                    questionImageLoading -> AppUiText("Loading family picture…")
                                    displayedQuestionImageUrl != null -> AndroidView(
                                        factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = false; webViewClient = WebViewClient() } },
                                        update = { view ->
                                            view.loadDataWithBaseURL(
                                                null,
                                                "<html><body style='margin:0'><img src='$displayedQuestionImageUrl' style='max-width:100%;height:auto'/></body></html>",
                                                "text/html", "UTF-8", null
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth().height(250.dp)
                                    )
                                    else -> AppUiText("The family picture is not available.")
                                }
                                questionOptions.filterNot { it.startsWith("L|") || it.startsWith("R|") }.forEach { option ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { answers = answers + (question.id to option) }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = answers[question.id] == option, onClick = { answers = answers + (question.id to option) })
                                        AppUiText(option)
                                    }
                                }
                            } else if (question.questionType == "matching") {
                                val leftItems = question.options.filter { it.startsWith("L|") }.map { it.substringAfter("L|") }
                                val rightItems = question.options.filter { it.startsWith("R|") }.map { it.substringAfter("R|") }
                                val selectedPairs = answers[question.id].orEmpty().split(";").filter { it.contains("=") }
                                    .associate { it.substringBefore("=") to it.substringAfter("=") }
                                if (leftItems.isEmpty() || rightItems.isEmpty()) {
                                    AppUiText("This matching question is missing its choices. Ask your parent to resave it.")
                                } else {
                                    leftItems.forEach { left ->
                                        val selectedRight = selectedPairs[left]
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            AppUiText(left, fontWeight = FontWeight.SemiBold)
                                            rightItems.forEach { right ->
                                                OutlinedButton(
                                                    onClick = {
                                                        val updated = selectedPairs.toMutableMap()
                                                        updated[left] = right
                                                        val serialized = leftItems.filter { updated.containsKey(it) }
                                                            .joinToString(";") { key -> "$key=${updated[key]}" }
                                                        answers = answers + (question.id to serialized)
                                                    },
                                                    enabled = !busy,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) { AppUiText((if (selectedRight == right) "✓ " else "") + right) }
                                            }
                                        }
                                    }
                                }
                            } else if (question.questionType == "multiple_choice") {
                                questionOptions.forEach { option ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { answers = answers + (question.id to option) }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = answers[question.id] == option, onClick = { answers = answers + (question.id to option) })
                                        AppUiText(option)
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
                                            AppUiText(option)
                                        }
                                    }
                                } else {
                                    listOf("true", "false").forEach { value ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable { answers = answers + (question.id to value) }.padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = answers[question.id] == value, onClick = { answers = answers + (question.id to value) })
                                            AppUiText(if (value == "true") "True / صحيح" else "False / غلط")
                                        }
                                    }
                                }
                            } else {
                                Field(answers[question.id].orEmpty(), { answers = answers + (question.id to it) }, if (question.questionType == "numeric") "Your numeric answer" else "Your answer")
                            }
                        }
                        if (questionIndex > 0) {
                            OutlinedButton(
                                onClick = { questionIndex = (questionIndex - 1).coerceAtLeast(0) },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) { AppUiText("Previous question") }
                        }
                        if (questionIndex < questions.lastIndex) {
                            Button(
                                onClick = { questionIndex = (questionIndex + 1).coerceAtMost(questions.lastIndex) },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) { AppUiText("Next question") }
                        }
                        val allAnswered = questions.isNotEmpty() && questions.all { !answers[it.id].isNullOrBlank() }
                        Button(
                            onClick = {
                                execute {
                                    val unanswered = questions.firstOrNull { answers[it.id].isNullOrBlank() }
                                    if (unanswered != null) {
                                        questionIndex = questions.indexOf(unanswered).coerceAtLeast(0)
                                        throw IllegalArgumentException("Please answer every question before submitting.")
                                    }
                                    val submitted = repository.submitTask(task.id, answers, answerLanguages)
                                    taskResult = submitted
                                    taskDraftPrefs.edit().remove(task.id).apply()
                                    val linked = childProfile
                                    if (linked != null) {
                                        try {
                                            earnedToday = repository.refreshChildDeviceRestrictions(linked)
                                            rewardBankMinutes = repository.loadChildRewardBankMinutes(linked.id)
                                        } catch (_: Exception) {
                                            earnedToday = submitted.dailyUsedMinutes
                                            notice = "Your answers were submitted, but the updated reward balance could not be refreshed. Open the learning space while connected and sync again."
                                        }
                                    }
                                    screen = AppScreen.TASK_RESULT
                                }
                            },
                            enabled = !busy && allAnswered,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppUiText(if (busy) "Submitting answers…" else "Submit answers (" + answers.count { it.value.isNotBlank() } + "/" + questions.size + ")")
                        }
                    } else {
                        AppUiText("No question is available. Return to your task list and sync again.")
                    }
                }

                AppScreen.LEARNING_3D_LAB -> ScreenFrame("4D Learning Lab", onBack = { screen = if (solarQuizReturnScreen == AppScreen.CHILD_HOME) AppScreen.CHILD_HOME else AppScreen.PARENT_HOME }, notice = notice, busy = busy) {
                    if (solarQuizReturnScreen == AppScreen.CHILD_HOME) {
                        AppUiText("Your educational game time left today: " + (kidsGameSecondsRemaining / 60) + " min " + (kidsGameSecondsRemaining % 60) + " sec.")
                    }
                    val learnerForLab = childProfile ?: selectedChild ?: children.singleOrNull()
                    Learning4DLabScreen(
                        selectedModel = selected3DModel,
                        onSelectModel = { selected3DModel = it },
                        religiousStudyTrack = learnerForLab?.studyTrack ?: "none",
                        gradeLevel = learnerForLab?.schoolGrade ?: 3,
                        ageYears = learnerForLab?.ageYears ?: 9,
                        isChildMode = solarQuizReturnScreen == AppScreen.CHILD_HOME,
                        onStartSolarQuiz = {
                            solarQuizReturnScreen = if (solarQuizReturnScreen == AppScreen.CHILD_HOME) AppScreen.CHILD_HOME else AppScreen.LEARNING_3D_LAB
                            screen = AppScreen.SOLAR_SYSTEM_QUIZ
                        },
                        onCreateQuestion = { option ->
                            execute {
                                val targetSubject = repository.findOrCreateSubject(familyId, option.subject, "en", parentCountryCode)
                                selectedSubject = targetSubject
                                subjects = repository.loadSubjects(familyId, parentCountryCode)
                                questions = repository.loadQuestions(targetSubject.id)
                                questionTranslations = repository.loadQuestionTranslations(questions.map { it.id })
                                questionSolutions = repository.loadQuestionSolutions(questions.map { it.id })
                                val starter: Triple<String, List<String>, String> = when (option.id) {
                                    "solar-system" -> Triple("Which planet has the most conspicuous ring system?", listOf("Mercury", "Saturn", "Earth"), "Saturn")
                                    "atom" -> Triple("Which particle has a negative electric charge?", listOf("Electron", "Proton", "Neutron"), "Electron")
                                    "molecule" -> Triple("Which elements form a water molecule?", listOf("Hydrogen and oxygen", "Carbon and oxygen", "Sodium and chlorine"), "Hydrogen and oxygen")
                                    "geometry" -> Triple("How many square faces does a cube have?", listOf("4", "6", "8"), "6")
                                    "cell" -> Triple("Which cell structure contains most genetic material?", listOf("Nucleus", "Cell membrane", "Cytoplasm"), "Nucleus")
                                    "force" -> Triple("What can an unbalanced force change?", listOf("An object's motion", "Its mass always", "The laws of gravity"), "An object's motion")
                                    "water-cycle" -> Triple("What process changes surface water into vapour?", listOf("Evaporation", "Condensation", "Freezing"), "Evaporation")
                                    "earth" -> Triple("What causes day and night?", listOf("Earth's rotation", "The Moon turning off the Sun", "Clouds"), "Earth's rotation")
                                    "circuit" -> Triple("What does a simple circuit need to carry current continuously?", listOf("A closed conducting path and a source", "A broken wire", "Only a plastic path"), "A closed conducting path and a source")
                                    "magnet" -> Triple("Which magnetic poles attract each other?", listOf("Opposite poles", "Like poles", "No poles"), "Opposite poles")
                                    "volcano" -> Triple("What is molten rock called after it reaches the surface?", listOf("Lava", "Magma", "Granite"), "Lava")
                                    "flower" -> Triple("Which flower part often attracts pollinators?", listOf("Petals", "Roots", "Stem only"), "Petals")
                                    "social-studies" -> Triple("How do maps help us understand a community?", listOf("They show places and relationships", "They measure a person's temperature", "They replace all local knowledge"), "They show places and relationships")
                                    "computer-skills" -> Triple("Which password is generally safer?", listOf("A long, unique passphrase", "123456", "Your first name only"), "A long, unique passphrase")
                                    "health-safety" -> Triple("Which habit helps reduce the spread of germs?", listOf("Wash hands with soap", "Share used tissues", "Touch food with dirty hands"), "Wash hands with soap")
                                    "art-design" -> Triple("What can highlights and shadows do in a drawing?", listOf("Suggest a three-dimensional form", "Turn a shape into a sound", "Measure an object's age"), "Suggest a three-dimensional form")
                                    "drawing-colour" -> Triple("What can overlapping shapes show in a drawing?", listOf("Depth and which object is in front", "The sound of a colour", "The weight of paper"), "Depth and which object is in front")
                                    "english" -> Triple("Which word is a verb?", listOf("Run", "Garden", "Blue"), "Run")
                                    "urdu" -> Triple("جملے میں فعل کس چیز کو ظاہر کرتا ہے؟", listOf("کام یا عمل", "صرف رنگ", "صرف جگہ"), "کام یا عمل")
                                    "sindhi" -> Triple("جملو ۾ فعل ڇا ظاهر ڪري ٿو؟", listOf("ڪم يا عمل", "صرف رنگ", "صرف جاءِ"), "ڪم يا عمل")
                                    "hindi" -> Triple("वाक्य में क्रिया क्या बताती है?", listOf("काम या कार्य", "केवल रंग", "केवल जगह"), "काम या कार्य")
                                    "faith-islamic" -> Triple("Which value is encouraged in many Islamic teachings?", listOf("Charity and care for others", "Dishonesty", "Wastefulness"), "Charity and care for others")
                                    "faith-hindu" -> Triple("Which festival is widely known as the festival of lights?", listOf("Diwali", "Vesak", "Vaisakhi"), "Diwali")
                                    "faith-christian" -> Triple("What does Easter commemorate in Christian belief?", listOf("The resurrection of Jesus", "The start of the school year", "A planet's orbit"), "The resurrection of Jesus")
                                    "faith-sikh" -> Triple("What does seva mean in Sikh tradition?", listOf("Selfless service", "A planet", "A colour"), "Selfless service")
                                    "faith-buddhist" -> Triple("What is mindfulness?", listOf("Paying attention to present experience", "Ignoring everything", "A type of fuel"), "Paying attention to present experience")
                                    "faith-jewish" -> Triple("What is Shabbat in Jewish tradition?", listOf("A weekly day of rest and observance", "A planet", "A drawing tool"), "A weekly day of rest and observance")
                                    "faith-jain" -> Triple("What does ahimsa mean in Jain traditions?", listOf("Avoiding harm", "Being cruel", "Wasting things"), "Avoiding harm")
                                    "faith-world" -> Triple("Why should we learn about different traditions?", listOf("To understand and respect people", "To mock them", "To assume everyone is the same"), "To understand and respect people")
                                    "chemistry-lab" -> Triple("In a virtual chemistry lab, what can a simulated indicator colour change suggest?", listOf("A change in solution properties", "That all chemicals are safe", "That temperature is zero"), "A change in solution properties")
                                    "nucleus" -> Triple("Where are protons and neutrons located in an atom?", listOf("Nucleus", "Outer electron region", "Outside the atom only"), "Nucleus")
                                    "proton" -> Triple("What electric charge does a proton have?", listOf("Positive", "Negative", "No net charge"), "Positive")
                                    "molecule-vibration" -> Triple("What does a molecular vibration model show?", listOf("Atoms moving relative to their bonds", "A planet changing its orbit", "A cell splitting into organs"), "Atoms moving relative to their bonds")
                                    "acid-base" -> Triple("What can a suitable indicator help us investigate?", listOf("Whether a solution is acidic or basic", "A liquid's age", "The liquid's weight only"), "Whether a solution is acidic or basic")
                                    "gas-mixture" -> Triple("What is a gas mixture?", listOf("Different gases sharing the same space", "Only one solid element", "A single water molecule"), "Different gases sharing the same space")
                                    "periodic-table" -> Triple("What does the periodic table organise?", listOf("Chemical elements", "Planets by colour", "Countries by population"), "Chemical elements")
                                    "friction" -> Triple("Which force usually opposes sliding between two surfaces?", listOf("Friction", "Gravity only", "Magnetic charge only"), "Friction")
                                    "rough-surface" -> Triple("Why can a rough track slow a sliding block?", listOf("Greater friction", "It removes the object's mass", "It turns off gravity"), "Greater friction")
                                    "force-meter" -> Triple("What does a force meter measure?", listOf("Force", "Temperature", "Volume"), "Force")
                                    "motion-cart" -> Triple("What can a net force change about a cart's motion?", listOf("Speed or direction", "Its mass automatically", "Its material automatically"), "Speed or direction")
                                    "energy-ball" -> Triple("What happens to a lifted ball's gravitational potential energy?", listOf("It increases as height increases", "It always becomes zero", "It changes the ball into a magnet"), "It increases as height increases")
                                    "broken-circuit" -> Triple("What happens when a wire breaks in a simple circuit?", listOf("The circuit opens and the lamp goes out", "Current flows forever", "The wire becomes a battery"), "The circuit opens and the lamp goes out")
                                    "inertia" -> Triple("Which object usually has greater inertia?", listOf("A heavier cart", "A lighter ball", "An object with no mass"), "A heavier cart")
                                    "cube-volume" -> Triple("How does doubling a cube's edge length affect its volume?", listOf("It becomes eight times as large", "It doubles only", "It becomes four times as large"), "It becomes eight times as large")
                                    "cube-scale" -> Triple("Which measurements determine a cuboid's volume?", listOf("Length, width and height", "Colour and name", "Only its temperature"), "Length, width and height")
                                    "box-volume" -> Triple("How do you calculate a rectangular box's volume?", listOf("Length × width × height", "Length + colour", "Width ÷ temperature"), "Length × width × height")
                                    "sphere" -> Triple("Which description fits a sphere?", listOf("A round solid with no flat faces", "A solid with six square faces", "A solid with two circular ends"), "A round solid with no flat faces")
                                    "cylinder" -> Triple("How many flat circular ends does a cylinder have?", listOf("Two", "One", "None"), "Two")
                                    "cone" -> Triple("Which solid narrows from a circular base to one point?", listOf("Cone", "Cylinder", "Cuboid"), "Cone")
                                    "cuboid" -> Triple("How many faces does a cuboid have?", listOf("Six", "Four", "Eight"), "Six")
                                    "prism" -> Triple("How many triangular ends does a triangular prism have?", listOf("Two", "One", "Three"), "Two")
                                    else -> Triple(option.question, listOf("Option A", "Option B", "Option C"), "Option A")
                                }
                                questionPrompt = starter.first
                                questionOptions = starter.second.joinToString(", ")
                                questionAnswer = starter.third
                                questionSolution = option.detail
                                question3DModel = option.id
                                questionType = "multiple_choice"
                                questionConceptKey = "learning-lab"
                                questionLanguage = "en"
                                screen = AppScreen.ADD_QUESTION
                                notice = "A draft question is ready with the selected interactive 4D model. Review its answer and save it for your learners."
                            }
                        }
                    )
                }

                AppScreen.DRAWING_STUDIO -> ScreenFrame("Drawing & Colour Studio", onBack = { screen = if (childProfile != null) AppScreen.CHILD_HOME else AppScreen.PARENT_HOME }, notice = notice, busy = busy) {
                    DrawingStudioScreen(onBack = { screen = if (childProfile != null) AppScreen.CHILD_HOME else AppScreen.PARENT_HOME })
                }

                AppScreen.KIDS_GAMES -> ScreenFrame("Kids' learning arcade", onBack = { screen = AppScreen.CHILD_HOME }, notice = notice, busy = busy) {
                    KidsGamesScreen(
                        childName = childProfile?.displayName ?: "learner",
                        avatarIcon = childProfile?.avatarIcon ?: "🦊",
                        remainingSeconds = kidsGameSecondsRemaining,
                        dailyLimitMinutes = childProfile?.dailyGameLimitMinutes ?: 0,
                        onBack = { screen = AppScreen.CHILD_HOME }
                    )
                }

                AppScreen.SOLAR_SYSTEM_QUIZ -> ScreenFrame("4D Solar System Explorer", onBack = { screen = solarQuizReturnScreen }, notice = notice, busy = busy) {
                    if (solarQuizReturnScreen == AppScreen.CHILD_HOME) {
                        AppUiText("Your educational game time left today: " + (kidsGameSecondsRemaining / 60) + " min " + (kidsGameSecondsRemaining % 60) + " sec.")
                    }
                    AppUiText("Explore the moving solar system. Tap the planet described by the clue, then type its name. Your practice score stays on this device.")
                    SolarSystemQuizContent()
                }

                AppScreen.TASK_RESULT -> ScreenFrame("Task result", onBack = { screen = AppScreen.CHILD_HOME }, notice = notice, busy = busy) {
                    val result = taskResult
                    if (result != null) {
                        SummaryCard("Your score", result.scorePercent.toString() + "%", if (result.passed) "Amazing work — you passed!" else "Keep practising. Every correct answer can earn reward minutes, even when you do not pass.")
                        SummaryCard("Reward earned", result.awardedMinutes.toString() + " minutes", if (result.rewardToBank) "Added to your Reward Bank." else "Sent to the destination selected by your parent.")
                        SummaryCard("Daily reward cap", result.dailyUsedMinutes.toString() + " / " + result.dailyCapMinutes + " minutes", "The server enforces this cap so answers cannot create unlimited rewards.")
                    }
                    RewardBankPanel(
                        balanceMinutes = rewardBankMinutes,
                        approvedApps = allowedApps.filter { it.enabled },
                        selectedPackage = rewardAppSelection,
                        onSelectPackage = { rewardAppSelection = it },
                        minutesText = rewardMinutesToRedeem,
                        onMinutesTextChange = { rewardMinutesToRedeem = it.filter(Char::isDigit).take(4) },
                        busy = busy,
                        onRedeem = {
                            execute {
                                val childNow = childProfile ?: throw IllegalStateException("The child profile is not linked.")
                                val minutes = rewardMinutesToRedeem.toIntOrNull()
                                    ?: throw IllegalArgumentException("Enter how many reward minutes to use.")
                                val selectedApp = allowedApps.firstOrNull { it.enabled && it.packageName == rewardAppSelection }
                                    ?: throw IllegalArgumentException("Choose an app approved by your parent.")
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(selectedApp.packageName)
                                    ?: throw IllegalStateException("This app is not installed on this phone. Ask your parent to refresh approved apps.")
                                val redemption = repository.redeemChildRewardBank(selectedApp.packageName, minutes)
                                rewardBankMinutes = redemption.balanceMinutes
                                earnedToday = repository.refreshChildDeviceRestrictions(childNow)
                                allowedApps = repository.loadAllowedApps(childNow.id)
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(launchIntent)
                                notice = "Reward used: ${redemption.redeemedMinutes} minutes for ${redemption.targetAppName}."
                            }
                        }
                    )
                    Button(
                        onClick = {
                            val task = activeTask
                            if ((result?.awardedMinutes ?: 0) > 0 && task != null && !task.rewardToBank) {
                                execute {
                                    val refreshedChild = childProfile
                                    if (refreshedChild != null) {
                                        try {
                                            earnedToday = repository.refreshChildDeviceRestrictions(refreshedChild)
                                            rewardBankMinutes = repository.loadChildRewardBankMinutes(refreshedChild.id)
                                        } catch (_: Exception) { /* keep result available and show the last confirmed balance */ }
                                    }
                                    val targetPackage = task.rewardPackageName
                                        ?: task.rewardGroupId?.let { groupId ->
                                            appGroups.firstOrNull { it.id == groupId }?.packageNames?.firstOrNull()
                                        }
                                    if (!targetPackage.isNullOrBlank()) {
                                        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
                                        if (launchIntent != null) {
                                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(launchIntent)
                                        } else {
                                            notice = "The reward was recorded, but the destination app is not installed on this phone."
                                        }
                                    } else {
                                        screen = AppScreen.CHILD_HOME
                                    }
                                }
                            } else {
                                screen = AppScreen.CHILD_HOME
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppUiText(
                            if ((result?.awardedMinutes ?: 0) > 0 && activeTask?.rewardToBank == false &&
                                (activeTask?.rewardPackageName != null || activeTask?.rewardGroupId != null))
                                "Continue to " + (
                                    allowedApps.firstOrNull { it.packageName == activeTask?.rewardPackageName }?.displayName
                                        ?: activeTask?.rewardPackageName
                                        ?: appGroups.firstOrNull { it.id == activeTask?.rewardGroupId }?.name
                                        ?: "approved app"
                                )
                            else "Back to my learning space"
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun RewardBankPanel(
    balanceMinutes: Int,
    approvedApps: List<AllowedApp>,
    selectedPackage: String,
    onSelectPackage: (String) -> Unit,
    minutesText: String,
    onMinutesTextChange: (String) -> Unit,
    busy: Boolean,
    onRedeem: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6D9))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(52.dp).background(Color(0xFFFFE6A0), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) { AppUiText("🪙", fontSize = 29.sp) }
                Column(Modifier.weight(1f)) {
                    AppUiText("My Reward Bank", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF72500A))
                    AppUiText("Spend earned minutes on an app your parent approved.", color = Color(0xFF72500A))
                }
            }
            AppUiText(balanceMinutes.toString() + " minutes available", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color(0xFF72500A))
            if (balanceMinutes <= 0) {
                AppUiText("Complete a learning task and answer correctly to earn minutes. Your balance stays here until you spend it.", color = Color(0xFF72500A))
            } else if (approvedApps.isEmpty()) {
                AppUiText("Your parent has not enabled any apps for reward time yet. Ask them to approve an app in App access.", color = Color(0xFF72500A))
            } else {
                AppUiText("Choose where to use your reward", fontWeight = FontWeight.Bold, color = Color(0xFF72500A))
                approvedApps.forEach { app ->
                    OutlinedButton(
                        onClick = { onSelectPackage(app.packageName) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) {
                        AppUiText((if (selectedPackage == app.packageName) "✓  " else "") + app.displayName)
                    }
                }
                Field(
                    minutesText,
                    onMinutesTextChange,
                    "Minutes to spend (1–${balanceMinutes.coerceAtMost(1440)})",
                    keyboardType = KeyboardType.Number
                )
                Button(
                    onClick = onRedeem,
                    enabled = !busy &&
                        approvedApps.any { it.packageName == selectedPackage } &&
                        (minutesText.toIntOrNull() ?: 0) in 1..balanceMinutes.coerceAtMost(1440),
                    modifier = Modifier.fillMaxWidth()
                ) { AppUiText("🪙 Use reward & open app") }
                AppUiText("The reward is deducted only after the server confirms the selected app is parent-approved.", color = Color(0xFF72500A), style = MaterialTheme.typography.bodySmall)
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
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) {
                AppUiText("← Back to safety", fontWeight = FontWeight.SemiBold)
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
                ) { AppUiText(screenEmoji(title), fontSize = 29.sp) }
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppUiText(title, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    AppUiText(screenTagline(title), fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFFE2E7FF))
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
                AppUiText(
                    notice, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) Color(0xFF9F263B) else Color(0xFF176A58)
                )
            }
        }
        content()
        Spacer(Modifier.height(14.dp))
    }
}

private fun displayLanguageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code.lowercase(Locale.ROOT) }

private fun displayCountryName(code: String): String =
    Locale("", code.uppercase()).getDisplayCountry(Locale.ENGLISH).ifBlank { code.uppercase() }

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
        label = { AppUiText(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = !label.contains("prompt", true) && !label.contains("options", true),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (label.contains("answer", true) || label.contains("numeric", true))
                androidx.compose.ui.text.input.ImeAction.Done
            else androidx.compose.ui.text.input.ImeAction.Default
        ),
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
            ) { AppUiText(summaryEmoji(title), fontSize = 23.sp) }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppUiText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                AppUiText(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                AppUiText(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val prefs = context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE)
    return ChildDeviceKioskPolicy.apply(
        context,
        prefs.getBoolean("kids_mode_enabled", false),
        prefs.getStringSet("allowed_packages", emptySet()).orEmpty()
    )
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

private fun isAccessibilityServiceOperational(context: Context): Boolean {
    if (!isAccessibilityServiceEnabled(context)) return false
    val lastTick = context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE)
        .getLong("last_service_tick_at", 0L)
    val age = System.currentTimeMillis() - lastTick
    return lastTick > 0L && age in 0L..30_000L
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
