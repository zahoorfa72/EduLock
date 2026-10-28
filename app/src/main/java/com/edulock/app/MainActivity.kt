package com.edulock.app

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.ByteArrayOutputStream
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.provider.Settings.Secure
import android.widget.VideoView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.resume
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ExportException
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Effects
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.VideoEncoderSettings
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaItem
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
private fun subjectsForChildContext(
    allSubjects: List<SubjectRow>,
    child: ChildProfile?,
    childSelections: Map<String, Set<String>>,
    defaultCountryCode: String
): List<SubjectRow> {
    val visible = allSubjects.filter { subject ->
        val track = religionTrackForSubject(subject.title)
        val countryAndGradeMatch = if (child == null) {
            subject.countryCode.equals(defaultCountryCode, ignoreCase = true) ||
                subject.countryCode.equals("ZZ", ignoreCase = true)
        } else {
            (subject.countryCode.equals(child.countryCode, ignoreCase = true) ||
                subject.countryCode.equals("ZZ", ignoreCase = true)) &&
                subject.gradeLevel == child.schoolGrade
        }
        val selectedIds = child?.id?.let { childSelections[it] }
        val explicitlySelected = selectedIds == null || subject.id in selectedIds
        val trackMatches = track == null || (child != null && child.studyTrack == track)
        countryAndGradeMatch && explicitlySelected && trackMatches
    }
    return visible.distinctBy { subject ->
        listOf(
            subject.title.trim().lowercase(Locale.ROOT),
            subject.languageCode.trim().lowercase(Locale.ROOT),
            subject.countryCode.trim().uppercase(Locale.ROOT),
            subject.gradeLevel.toString(),
            subject.curriculumSubjectCode.trim().lowercase(Locale.ROOT)
        ).joinToString("|")
    }
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

private fun compactAppLabel(value: String, maxChars: Int = 24): String {
    val clean = value.trim()
    if (clean.length <= maxChars) return clean
    return clean.take((maxChars - 1).coerceAtLeast(1)).trimEnd() + "…"
}

private fun appIconGlyph(packageName: String, displayName: String): String = when {
    packageName.contains("chrome", true) || packageName.contains("browser", true) || displayName.contains("browser", true) -> "🌐"
    packageName.contains("youtube", true) || packageName.contains("video", true) -> "▶️"
    packageName.contains("camera", true) || packageName.contains("photo", true) -> "📷"
    packageName.contains("whatsapp", true) || packageName.contains("message", true) || packageName.contains("sms", true) -> "💬"
    packageName.contains("phone", true) || packageName.contains("dialer", true) -> "📞"
    packageName.contains("game", true) || displayName.contains("game", true) -> "🎮"
    packageName.contains("maps", true) || packageName.contains("navigation", true) -> "📍"
    packageName.contains("music", true) || packageName.contains("spotify", true) -> "🎵"
    packageName.contains("edu", true) || displayName.contains("learn", true) || displayName.contains("classroom", true) -> "📚"
    packageName.contains("assistant", true) -> "🤖"
    packageName.contains("calculator", true) -> "🧮"
    packageName.contains("calendar", true) -> "🗓️"
    packageName.contains("settings", true) -> "⚙️"
    packageName.contains("mail", true) || packageName.contains("gmail", true) -> "✉️"
    else -> "📱"
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

private data class QuestionEditDraft(
    val prompt: String,
    val options: String,
    val answer: String,
    val languageCode: String,
    val conceptKey: String,
    val solution: String
)

private data class LocalTaskDraft(
    val answers: Map<String, String> = emptyMap(),
    val questionIndex: Int = 0,
    val expiredQuestionIds: Set<String> = emptySet(),
    val taskDeadlineAtMillis: Long? = null,
    val questionDeadlineAtMillis: Long? = null,
    val questionDeadlineQuestionId: String? = null,
    val pausedTaskSecondsRemaining: Int? = null,
    val pausedQuestionSecondsRemaining: Int? = null
)

private suspend fun compressChallengeVideo(context: Context, source: Uri): ByteArray {
    // Reject long recordings before transcoding to protect kids' phones from
    // excessive memory/battery use and keep evidence small for database storage.
    val durationMs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(context, source)
            metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: throw IOException("Could not read the video duration. Choose a short MP4 clip.")
        } finally {
            metadata.release()
        }
    }
    if (durationMs <= 0L) throw IOException("This video has no readable duration. Choose another clip.")
    if (durationMs > 15_000L) throw IOException("Please trim the video to 15 seconds or less before uploading.")

    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val output = File(context.cacheDir, "edulock_challenge_" + System.currentTimeMillis() + ".mp4")
            val encoderFactory = DefaultEncoderFactory.Builder(context.applicationContext)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder().setBitrate(450_000).build()
                )
                .setRequestedAudioEncoderSettings(
                    AudioEncoderSettings.Builder().setBitrate(64_000).build()
                )
                .build()
            val transformer = Transformer.Builder(context)
                .setEncoderFactory(encoderFactory)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        try {
                            val bytes = output.readBytes()
                            if (bytes.isEmpty() || bytes.size > 1_800_000) {
                                if (continuation.isActive) continuation.resumeWithException(
                                    IOException("The compressed video is over 1.8 MB. Choose a shorter clip or reduce video quality before selecting it.")
                                )
                            } else if (continuation.isActive) continuation.resume(bytes)
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        } finally {
                            output.delete()
                        }
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        output.delete()
                        if (continuation.isActive) continuation.resumeWithException(
                            IOException("Could not compress this video on this device. Try a short MP4 clip.", exportException)
                        )
                    }
                })
                .build()
            try {
                val edited = EditedMediaItem.Builder(MediaItem.fromUri(source))
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(480))))
                    .build()
                transformer.start(edited, output.absolutePath)
                continuation.invokeOnCancellation { transformer.cancel(); output.delete() }
            } catch (error: Exception) {
                output.delete()
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }
}

private enum class AppScreen {
    DEVICE_SETUP, AUTH, VERIFY_OTP, PAIR_CHILD, PARENT_HOME, CHILD_HOME, CHILDREN, ADD_CHILD, CURRICULUM_LIBRARY, LEARNING_TEMPLATES, SOLAR_SYSTEM_QUIZ, KIDS_GAMES, LEARNING_3D_LAB, DRAWING_STUDIO,
    SUBJECTS, ADD_SUBJECT, QUESTIONS, ADD_QUESTION, ADD_TRANSLATION, ASSIGN_TASK, PHYSICAL_CHALLENGES, PRACTICAL_TASKS, LEARNING_FEEDBACK,
    REPORTS, ALLOWED_APPS, PROTECTION_STATUS, CHILD_LOCATIONS, APP_USAGE_REPORT, APP_SETTINGS, CHILD_SECURITY, PLAY_TASK, TASK_RESULT
}

@Composable
private fun EduLockApp(repository: EduLockRepository) {
    val context = LocalContext.current

    val scope = rememberCoroutineScope()



    val devicePreferences = remember { context.getSharedPreferences("edulock_device_mode", Context.MODE_PRIVATE) }
    var deviceMode by remember { mutableStateOf(devicePreferences.getString("mode", "unknown") ?: "unknown") }
    var screen by remember { mutableStateOf(if (deviceMode == "unknown") AppScreen.DEVICE_SETUP else AppScreen.AUTH) }
    var isRestoringSession by remember { mutableStateOf(true) }
    var solarQuizReturnScreen by remember { mutableStateOf(AppScreen.PARENT_HOME) }
    var selected3DModel by remember { mutableStateOf("solar-system") }
    // Never populate the UI from an unverified cached session.
    var session by remember { mutableStateOf<AuthSession?>(null) }
    var accountRole by remember { mutableStateOf("parent") }
    var isRegistering by remember { mutableStateOf(false) }
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
    var childProfileSearchQuery by remember { mutableStateOf("") }
    var dailyTaskSettingsChild by remember { mutableStateOf<ChildProfile?>(null) }
    var dailyTasksEnabled by remember { mutableStateOf(false) }
    var dailyTaskCountText by remember { mutableStateOf("1") }
    var dailyTaskQuestionCountText by remember { mutableStateOf("4") }
    var dailyTaskPassPercentText by remember { mutableStateOf("70") }
    var dailyTaskRewardMinutesText by remember { mutableStateOf("1") }
    var dailyTaskCapMinutesText by remember { mutableStateOf("30") }
    var kindergartenModeEnabled by remember { mutableStateOf(false) }
    var gentleLearningRemindersEnabled by remember { mutableStateOf(true) }
    var practicalTasksEnabled by remember { mutableStateOf(true) }
    var dailyWrittenLimitText by remember { mutableStateOf("8") }
    var subjects by remember { mutableStateOf(emptyList<SubjectRow>()) }
    var subjectSearchQuery by remember { mutableStateOf("") }
    var questionSearchQuery by remember { mutableStateOf("") }
    var groupRewardSearchQuery by remember { mutableStateOf("") }
    var settingsSearchQuery by remember { mutableStateOf("") }
    var childSecurityAppSearchQuery by remember { mutableStateOf("") }
    var templateSearchQuery by remember { mutableStateOf("") }
    var curriculumQuestionSearchQuery by remember { mutableStateOf("") }
    var selectedChild by remember { mutableStateOf<ChildProfile?>(null) }
    var childSecurityApps by remember { mutableStateOf(emptyList<AllowedApp>()) }
    var childEmergencyEnabled by remember { mutableStateOf(false) }
    var childBlockEmergencyAfterDailyLimit by remember { mutableStateOf(false) }
    var childEmergencyAllowedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var childEmergencyMinutesText by remember { mutableStateOf("10") }
    var childSecurityCodeDisplay by remember { mutableStateOf("") }
    var childSecurityCodeType by remember { mutableStateOf("") }
    var childEmergencyOptions by remember { mutableStateOf(ChildEmergencyOptions()) }
    var childEmergencyCodeInput by remember { mutableStateOf("") }
    var childEmergencyTargetPackage by remember { mutableStateOf("") }
    var childEmergencyAppMenuExpanded by remember { mutableStateOf(false) }
    var childLogoutDialogOpen by remember { mutableStateOf(false) }
    var childLogoutCodeInput by remember { mutableStateOf("") }
    var childGateCode by remember { mutableStateOf("") }
    val lockStatePreferences = remember { context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE) }
    var childLogoutLocked by remember { mutableStateOf(lockStatePreferences.getBoolean("child_logout_locked", false)) }
    var childSubjectSelections by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var childSubjectDraft by remember { mutableStateOf<Set<String>>(emptySet()) }
    var childSubjectDraftChildId by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf<SubjectRow?>(null) }
    var subjectDeleteConfirmation by remember { mutableStateOf<SubjectRow?>(null) }
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
        deadlineQuestionId: String? = questionDeadlineQuestionId,
        pausedTaskSecondsRemaining: Int? = null,
        pausedQuestionSecondsRemaining: Int? = null
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
            .put("paused_task_seconds_remaining", pausedTaskSecondsRemaining ?: -1)
            .put("paused_question_seconds_remaining", pausedQuestionSecondsRemaining ?: -1)
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
                    .takeIf { it.isNotBlank() && it != "null" },
                pausedTaskSecondsRemaining = saved.optInt("paused_task_seconds_remaining", -1).takeIf { it >= 0 },
                pausedQuestionSecondsRemaining = saved.optInt("paused_question_seconds_remaining", -1).takeIf { it >= 0 }
            )
        } catch (_: Exception) { LocalTaskDraft() }
    }
    LaunchedEffect(
        screen, activeTask?.id, answers, questionIndex, expiredQuestionIds,
        taskDeadlineAtMillis, questionDeadlineAtMillis, questionDeadlineQuestionId
    ) {
        if (screen == AppScreen.PLAY_TASK) {
            activeTask?.id?.let {
                saveTaskDraft(
                    it, answers, questionIndex, expiredQuestionIds,
                    taskDeadlineAtMillis, questionDeadlineAtMillis, questionDeadlineQuestionId
                )
            }
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
    var taskQuestionCount by remember { mutableStateOf("4") }
    var taskPassPercent by remember { mutableStateOf("70") }
    var taskRewardMinutes by remember { mutableStateOf("3") }
    var taskDailyCapMinutes by remember { mutableStateOf("60") }
    var taskTimeLimitSeconds by remember { mutableStateOf("0") }
    var taskQuestionTimeLimitSeconds by remember { mutableStateOf("0") }
    var taskRewardToBank by remember { mutableStateOf(true) }
    var taskRewardPackageName by remember { mutableStateOf("") }
    var taskRewardGroupId by remember { mutableStateOf("") }
    var physicalChallenges by remember { mutableStateOf(emptyList<PhysicalChallenge>()) }
    var practicalTasks by remember { mutableStateOf(emptyList<PracticalTaskRow>()) }
    var practicalSubmissions by remember { mutableStateOf(emptyList<PracticalTaskSubmissionReview>()) }
    var practicalReviewNotes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var practicalCompletionNotes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var practicalTaskTitle by remember { mutableStateOf("") }
    var practicalTaskInstructions by remember { mutableStateOf("") }
    var practicalTaskMaterials by remember { mutableStateOf("") }
    var practicalTaskSafety by remember { mutableStateOf("") }
    var practicalTaskKind by remember { mutableStateOf("practical") }
    var practicalTaskMinutes by remember { mutableStateOf("10") }
    var writtenAnswerFeedback by remember { mutableStateOf(emptyList<WrittenAnswerFeedback>()) }
    var challengeSubmissions by remember { mutableStateOf(emptyList<ChallengeSubmission>()) }
    var challengeVideoPreviewFiles by remember { mutableStateOf<Map<String, File>>(emptyMap()) }
    var previewingSubmissionId by remember { mutableStateOf<String?>(null) }
    var challengeTitle by remember { mutableStateOf("") }
    var challengeDescription by remember { mutableStateOf("") }
    var challengeTargetChildId by remember { mutableStateOf("all") }
    var challengeRewardMinutes by remember { mutableStateOf("10") }
    var challengeEvidenceType by remember { mutableStateOf("either") }
    var selectedChallenge by remember { mutableStateOf<PhysicalChallenge?>(null) }
    var evidenceBase64 by remember { mutableStateOf<String?>(null) }
    var evidenceMimeType by remember { mutableStateOf<String?>(null) }
    var evidenceKind by remember { mutableStateOf<String?>(null) }
    var evidenceNote by remember { mutableStateOf("") }
    var questionRewardEdits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var questionGroupRewardEdits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var questionRewardGroups by remember { mutableStateOf(emptyList<QuestionRewardGroup>()) }
    var groupRewardsExpanded by remember { mutableStateOf(false) }
    var translationEditorQuestionId by remember { mutableStateOf<String?>(null) }
    var questionDeleteConfirmation by remember { mutableStateOf<QuestionRow?>(null) }
    var questionEditExpandedId by remember { mutableStateOf<String?>(null) }
    var questionEditDrafts by remember { mutableStateOf<Map<String, QuestionEditDraft>>(emptyMap()) }
    var selectedTaskSubjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var taskQuestionChoices by remember { mutableStateOf(emptyList<QuestionRow>()) }
    var selectedTaskQuestionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var taskQuestionSearchQuery by remember { mutableStateOf("") }
    var appGroups by remember { mutableStateOf(emptyList<AppGroup>()) }
    var appGroupName by remember { mutableStateOf("") }
    var appGroupEditingId by remember { mutableStateOf<String?>(null) }
    var appGroupSelectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var appPackage by remember { mutableStateOf("") }
    var appDisplayName by remember { mutableStateOf("") }
    var appDailyLimit by remember { mutableStateOf("0") }
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

    fun openChildTask(task: TaskRow) {
        execute {
            val loadedQuestions = repository.loadQuestionsForTask(task.id, task.subjectId, task.questionCount, task.targetGradeLevel, task.targetAgeYears)
            if (loadedQuestions.isEmpty()) {
                throw IllegalStateException("This task has no linked questions. Ask your parent to repair it.")
            }
            // A stale task_questions link or a deleted question must not make the whole task impossible to start.
            // Start with the available linked questions and show the actual count in the player.
            if (loadedQuestions.size < task.questionCount) {
                notice = "Some questions in this task are no longer available. Starting with " +
                    loadedQuestions.size + " available question(s). Ask your parent to refresh the task."
            }
            val loadedTranslations = repository.loadQuestionTranslations(loadedQuestions.map { it.id })
            val draft = readTaskDraft(task.id)
            questions = loadedQuestions
            questionTranslations = loadedTranslations
            answerLanguages = loadedQuestions.associate { question ->
                val preferred = childProfile?.preferredLanguage ?: question.languageCode
                val translated = loadedTranslations[question.id].orEmpty().any {
                    it.languageCode.equals(preferred, ignoreCase = true)
                }
                question.id to if (translated) preferred.lowercase() else question.languageCode
            }
            activeTask = task
            answers = draft.answers.filterKeys { key -> loadedQuestions.any { it.id == key } }
            questionIndex = draft.questionIndex.coerceIn(0, (loadedQuestions.size - 1).coerceAtLeast(0))
            expiredQuestionIds = draft.expiredQuestionIds.intersect(loadedQuestions.map { it.id }.toSet())
            val now = System.currentTimeMillis()
            val restoredQuestionId = loadedQuestions.getOrNull(questionIndex)?.id
            taskDeadlineAtMillis = draft.pausedTaskSecondsRemaining?.let { now + it * 1000L }
                ?: draft.taskDeadlineAtMillis
            questionDeadlineAtMillis = if (draft.pausedQuestionSecondsRemaining != null &&
                draft.questionDeadlineQuestionId == restoredQuestionId) {
                now + draft.pausedQuestionSecondsRemaining * 1000L
            } else draft.questionDeadlineAtMillis
            questionDeadlineQuestionId = draft.questionDeadlineQuestionId
            taskSecondsRemaining = draft.pausedTaskSecondsRemaining ?: draft.taskDeadlineAtMillis?.let {
                (((it - now + 999L) / 1000L).toInt()).coerceAtLeast(0)
            } ?: 0
            questionSecondsRemaining = draft.pausedQuestionSecondsRemaining ?: draft.questionDeadlineAtMillis?.let {
                (((it - now + 999L) / 1000L).toInt()).coerceAtLeast(0)
            } ?: 0
            taskResult = null
            screen = AppScreen.PLAY_TASK
            notice = if (draft.answers.isNotEmpty() || draft.questionIndex > 0) {
                "Your saved progress is restored. Continue where you stopped."
            } else {
                "Task opened. Your answers are saved as you work, so you can return later."
            }
        }
    }

    fun leaveChildTask() {
        val task = activeTask
        if (task != null && screen == AppScreen.PLAY_TASK) {
            val now = System.currentTimeMillis()
            val pausedTaskRemaining = taskDeadlineAtMillis?.let {
                (((it - now + 999L) / 1000L).toInt()).coerceAtLeast(0)
            }
            val currentQuestionId = questions.getOrNull(questionIndex)?.id
            val pausedQuestionRemaining = if (questionDeadlineAtMillis != null &&
                currentQuestionId != null && currentQuestionId == questionDeadlineQuestionId &&
                currentQuestionId !in expiredQuestionIds) {
                (((questionDeadlineAtMillis!! - now + 999L) / 1000L).toInt()).coerceAtLeast(0)
            } else null
            saveTaskDraft(
                task.id, answers, questionIndex, expiredQuestionIds,
                null, null, questionDeadlineQuestionId,
                pausedTaskRemaining, pausedQuestionRemaining
            )
            taskDeadlineAtMillis = null
            questionDeadlineAtMillis = null
            taskSecondsRemaining = pausedTaskRemaining ?: 0
            questionSecondsRemaining = pausedQuestionRemaining ?: 0
        }
        screen = AppScreen.CHILD_HOME
        notice = "Progress saved. Open Learning again whenever you are ready to continue."
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
    val challengePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) execute {
            val resizedAndCompressed = withContext(kotlinx.coroutines.Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    ?: throw IOException("Could not read the selected photo.")
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Choose a supported photo.")
                val factor = maxOf(bounds.outWidth / 1280f, bounds.outHeight / 1280f, 1f)
                val sample = Integer.highestOneBit(factor.toInt().coerceAtLeast(1))
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: throw IOException("Could not decode the photo.")
                val scale = minOf(1f, 1280f / maxOf(bitmap.width, bitmap.height).toFloat())
                val resized = if (scale < 1f) Bitmap.createScaledBitmap(
                    bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1), true
                ) else bitmap
                var quality = 82
                var bytes: ByteArray
                do {
                    val output = ByteArrayOutputStream()
                    if (!resized.compress(Bitmap.CompressFormat.JPEG, quality, output)) throw IOException("Photo compression failed.")
                    bytes = output.toByteArray()
                    quality -= 10
                } while (bytes.size > 1_800_000 && quality >= 32)
                if (bytes.size > 1_800_000) throw IOException("Photo is still too large after compression. Choose another photo.")
                bytes
            }
            evidenceBase64 = android.util.Base64.encodeToString(resizedAndCompressed, android.util.Base64.NO_WRAP)
            evidenceMimeType = "image/jpeg"
            evidenceKind = "photo"
            notice = "Photo compressed to " + (resizedAndCompressed.size / 1024).coerceAtLeast(1) + " KB on this device."
        }
    }
    val challengeVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) execute {
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (!mime.startsWith("video/")) throw IOException("Choose a supported video.")
            val bytes = compressChallengeVideo(context, uri)
            evidenceBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            evidenceMimeType = "video/mp4"
            evidenceKind = "video"
            notice = "Video compressed on this device to " + (bytes.size / 1024).coerceAtLeast(1) + " KB."
        }
    }

    val launchPreferences = remember { context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE) }
    LaunchedEffect(childProfile?.id, screen, tasks.map { it.id }, busy) {
        val requestedTaskId = launchPreferences.getString("pending_task_id", null)
        val openAnyTask = launchPreferences.getBoolean("pending_open_any_task", false)
        val linkedChild = childProfile
        if ((requestedTaskId != null || openAnyTask) && linkedChild != null &&
            screen == AppScreen.CHILD_HOME && repository.currentSession()?.accountRole == "child" && !busy) {
            val targeted = requestedTaskId?.let { id -> tasks.firstOrNull { it.id == id && it.active } }
            val target = targeted ?: tasks.firstOrNull { it.active }
            if (target != null) {
                launchPreferences.edit()
                    .remove("pending_task_id")
                    .remove("pending_open_any_task")
                    .apply()
                notice = "This app is blocked. Complete this learning task first."
                openChildTask(target)
            } else if (tasks.isNotEmpty()) {
                launchPreferences.edit()
                    .remove("pending_task_id")
                    .remove("pending_open_any_task")
                    .apply()
                notice = "This app is blocked, but no active learning task is ready. Ask your parent to assign one."
            }
            // When the task list is still loading, keep the request. The effect runs again after sync.
        }
    }


    if (childLogoutDialogOpen) {
        AlertDialog(
            onDismissRequest = { childLogoutDialogOpen = false; childLogoutCodeInput = "" },
            title = { AppUiText("Log out of this child profile?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppUiText("Enter the eight-digit code created by your parent. Logging out keeps this child profile and its parent link intact.")
                    Field(
                        childLogoutCodeInput,
                        { childLogoutCodeInput = it.filter(Char::isDigit).take(8) },
                        "Parent authorization code",
                        keyboardType = KeyboardType.Number
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && childLogoutCodeInput.length == 8,
                    onClick = {
                        val code = childLogoutCodeInput
                        execute {
                            repository.redeemChildLogoutCode(code)
                            childLogoutDialogOpen = false
                            childLogoutCodeInput = ""
                            childLogoutLocked = true
                            lockStatePreferences.edit().putBoolean("child_logout_locked", true).apply()
                            screen = AppScreen.PAIR_CHILD
                            notice = "Logged out. Your profile remains linked. Ask your parent for a new code to unlock it; used codes cannot be reused."
                        }
                    }
                ) { AppUiText("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { childLogoutDialogOpen = false; childLogoutCodeInput = "" }) { AppUiText("Cancel") }
            }
        )
    }

    pendingChildAction?.let { (targetChild, action) ->
        val isDelete = action == "delete"
        val isDelink = action == "delink"
        AlertDialog(
            onDismissRequest = { pendingChildAction = null },
            title = {
                AppUiText(when {
                    isDelete -> "Delete child profile?"
                    isDelink -> "Delink this child's device?"
                    else -> "Deactivate device link?"
                })
            },
            text = {
                AppUiText(
                    when {
                        isDelete -> "Permanently delete ${targetChild.displayName}'s profile and its linked EduLock records. This cannot be undone."
                        else -> "Delink ${targetChild.displayName}'s phone only. The child profile, questions, rewards and history remain saved. The phone must use a new parent pairing code to link again."
                    }
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
                            notice = "Device delinked. The child profile and learning records were preserved; use a new pairing code to relink a phone."
                        }
                        children = repository.loadChildren(familyId)
                        childInstalledApps = emptyList()
                        allowedApps = emptyList()
                    }
                }) { AppUiText(if (isDelete) "Delete permanently" else if (isDelink) "Delink device" else "Deactivate link") }
            },
            dismissButton = {
                TextButton(onClick = { pendingChildAction = null }) { AppUiText("Cancel") }
            }
        )
    }

    dailyTaskSettingsChild?.let { taskChild ->
        AlertDialog(
            onDismissRequest = { dailyTaskSettingsChild = null },
            title = { AppUiText("Automatic daily tasks • ${taskChild.displayName}") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppUiText(
                        "EduLock can create new tasks automatically once per day when this child opens the learning space. Existing tasks are not duplicated.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppUiText("Give daily tasks automatically", fontWeight = FontWeight.SemiBold)
                            AppUiText("Uses this child's saved subject choices and grade/age.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = dailyTasksEnabled, onCheckedChange = { dailyTasksEnabled = it })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppUiText("Kindergarten / easy learning", fontWeight = FontWeight.SemiBold)
                            AppUiText("Shorter sessions and simpler daily practice.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = kindergartenModeEnabled, onCheckedChange = { kindergartenModeEnabled = it })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppUiText("Gentle focus reminders", fontWeight = FontWeight.SemiBold)
                            AppUiText("Encouraging reminders to revisit weak concepts.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = gentleLearningRemindersEnabled, onCheckedChange = { gentleLearningRemindersEnabled = it })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppUiText("Practical tasks", fontWeight = FontWeight.SemiBold)
                            AppUiText("Allow a separate hands-on task portal for this child.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = practicalTasksEnabled, onCheckedChange = { practicalTasksEnabled = it })
                    }
                    OutlinedTextField(
                        value = dailyWrittenLimitText,
                        onValueChange = { dailyWrittenLimitText = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { AppUiText("Maximum written questions per day (1–100)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = dailyTaskCountText,
                        onValueChange = { dailyTaskCountText = it.filter(Char::isDigit).take(1) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { AppUiText("Tasks each day (1–5)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = dailyTaskQuestionCountText,
                        onValueChange = { dailyTaskQuestionCountText = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { AppUiText("Questions in each task (1–20)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = dailyTaskPassPercentText,
                        onValueChange = { dailyTaskPassPercentText = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { AppUiText("Pass score percent (0–100)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = dailyTaskRewardMinutesText,
                        onValueChange = { dailyTaskRewardMinutesText = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { AppUiText("Reward per correct answer (0–60 min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = dailyTaskCapMinutesText,
                        onValueChange = { dailyTaskCapMinutesText = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { AppUiText("Daily task reward cap (0–1440 min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    AppUiText(
                        "Each automatic task uses 4 questions by default and adds correct-answer rewards to the child's Reward Bank. If there are not enough matching questions, EduLock skips that task and tries again on the next day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        execute {
                            val cfg = DailyTaskSettings(
                                childId = taskChild.id,
                                enabled = dailyTasksEnabled,
                                dailyTaskCount = dailyTaskCountText.toIntOrNull() ?: 1,
                                questionsPerTask = dailyTaskQuestionCountText.toIntOrNull() ?: 4,
                                passPercent = dailyTaskPassPercentText.toIntOrNull() ?: 70,
                                rewardMinutes = dailyTaskRewardMinutesText.toIntOrNull() ?: 1,
                                dailyCapMinutes = dailyTaskCapMinutesText.toIntOrNull() ?: 30
                            )
                            repository.saveChildDailyTaskSettings(cfg)
                            repository.saveChildLearningPreferences(
                                ChildLearningPreferences(
                                    childId = taskChild.id,
                                    kindergartenMode = kindergartenModeEnabled,
                                    gentleReminders = gentleLearningRemindersEnabled,
                                    dailyWrittenQuestionLimit = dailyWrittenLimitText.toIntOrNull()?.coerceIn(1,100) ?: 8,
                                    practicalTasksEnabled = practicalTasksEnabled
                                )
                            )
                            dailyTaskSettingsChild = null
                            notice = if (cfg.enabled) {
                                "Automatic daily tasks saved for ${taskChild.displayName}. New tasks are generated when the child opens EduLock each day."
                            } else "Automatic daily tasks are off for ${taskChild.displayName}."
                        }
                    },
                    enabled = !busy
                ) { AppUiText("Save daily task settings") }
            },
            dismissButton = {
                TextButton(onClick = { dailyTaskSettingsChild = null }) { AppUiText("Cancel") }
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
        subjects = repository.loadSubjects(familyId)
    }

    suspend fun finishChildLogin(newSession: AuthSession, suppliedCode: String) {
        session = newSession
        screen = AppScreen.PAIR_CHILD
        if (suppliedCode.isNotBlank()) repository.claimChildPairing(suppliedCode)
        val linked = repository.loadCurrentChild()
        if (linked == null) {
            childProfile = null
            childLogoutLocked = false
            lockStatePreferences.edit()
                .putBoolean("child_logout_locked", false)
                .putBoolean("child_active", false)
                .putBoolean("kids_mode_enabled", false)
                .putStringSet("allowed_packages", emptySet())
                .putStringSet("blocked_packages", emptySet())
                .putStringSet("app_daily_limits", emptySet())
                .putStringSet("app_weekly_schedules", emptySet())
                .remove("child_id")
                .apply()
            ChildDeviceKioskPolicy.enterOrExitLockTask(context, false)
            context.stopService(Intent(context, ChildLocationService::class.java))
            context.getSharedPreferences(ChildLocationService.PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(ChildLocationService.KEY_SHARING_ENABLED, false)
                .remove(ChildLocationService.KEY_CHILD_ID)
                .remove(ChildLocationService.KEY_PENDING)
                .remove(ChildLocationService.KEY_LATITUDE)
                .remove(ChildLocationService.KEY_LONGITUDE)
                .remove(ChildLocationService.KEY_ACCURACY)
                .remove(ChildLocationService.KEY_POINT_TIME)
                .apply()
            notice = "This device is not linked to a child profile. Pair it with a parent's new six-digit code."
            return
        }
        childProfile = linked
        runCatching {
            val learningPrefs = repository.loadCurrentChildLearningPreferences(linked.id)
            LearningReminderScheduler.setEnabled(context, learningPrefs.gentleReminders, linked.displayName)
        }.onFailure { error ->
            android.util.Log.w("EduLockLearningReminder", "Could not sync learning reminder preference", error)
        }
        if (childLogoutLocked || lockStatePreferences.getBoolean("child_logout_locked", false)) {
            childLogoutLocked = true
            screen = AppScreen.PAIR_CHILD
            notice = "This child profile is logged out but remains linked. Enter a fresh parent authorization code to unlock it."
            return
        }
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
        try { repository.ensureDailyTasks() } catch (_: Exception) {
            // A temporary network failure must not prevent the child from opening the app.
            notice = "Today's automatic task could not sync yet. Reopen the learning space while online to retry."
        }
        tasks = repository.loadChildTasks(linked.id, linked.schoolGrade, linked.ageYears)
        try {
            childEmergencyOptions = repository.loadChildEmergencyOptions()
            childEmergencyTargetPackage = childEmergencyOptions.allowedPackages.firstOrNull().orEmpty()
        } catch (_: Exception) {
            childEmergencyOptions = ChildEmergencyOptions()
            childEmergencyTargetPackage = ""
        }
        try {
            earnedToday = repository.refreshChildDeviceRestrictions(linked)
            childRulesLastSyncedAt = context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE)
                .getLong("rules_synced_at", System.currentTimeMillis())
            childRulesSyncError = null
            ChildDeviceKioskPolicy.enterOrExitLockTask(context, linked.kidsModeEnabled)
            repository.syncAppUsage(linked.id)
        } catch (e: Exception) {
            childRulesSyncError = e.message ?: "The child-device rules could not be confirmed by the server."
            notice = "Signed in, but device settings or usage could not sync: " + childRulesSyncError
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
                                        tasks = repository.loadChildTasks(linked.id, linked.schoolGrade, linked.ageYears)
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
                                            tasks = repository.loadChildTasks(linked.id, linked.schoolGrade, linked.ageYears)
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
                        tasks = repository.loadChildTasks(activeChildId, currentProfile.schoolGrade, currentProfile.ageYears)
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
                    childRulesSyncError = e.message ?: "The child-device rules could not be confirmed by the server."
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
                        val isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                            (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                        if (label.isBlank() ||
                            label.equals("3 Button Navigation Bar", ignoreCase = true) ||
                            label.equals("Ad Privacy", ignoreCase = true) ||
                            label.equals("AgingFunction", ignoreCase = true) ||
                            (isSystemApp && pkg != "com.android.settings")) null else pkg to label
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
            isRestoringSession = false
            screen = AppScreen.DEVICE_SETUP
            if (!repository.isConfigured) {
                notice = "App setup is incomplete. Ask the app administrator to finish the connection settings before building."
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
            notice = "The app connection is not configured yet. Ask the app administrator to finish setup before building."
        }
        isRestoringSession = false
    }
    BackHandler(enabled = screen != AppScreen.AUTH) {
        if (screen == AppScreen.PLAY_TASK) {
            leaveChildTask()
        } else {
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
                AppScreen.PHYSICAL_CHALLENGES, AppScreen.PRACTICAL_TASKS, AppScreen.LEARNING_FEEDBACK -> if (session?.accountRole == "child") AppScreen.CHILD_HOME else AppScreen.PARENT_HOME
                AppScreen.PLAY_TASK, AppScreen.TASK_RESULT -> AppScreen.CHILD_HOME
                AppScreen.SOLAR_SYSTEM_QUIZ -> solarQuizReturnScreen
                AppScreen.KIDS_GAMES -> AppScreen.CHILD_HOME
                AppScreen.APP_SETTINGS -> AppScreen.PARENT_HOME
                AppScreen.CHILD_SECURITY -> AppScreen.CHILDREN
                AppScreen.LEARNING_3D_LAB -> if (solarQuizReturnScreen == AppScreen.CHILD_HOME) AppScreen.CHILD_HOME else AppScreen.PARENT_HOME
                AppScreen.DRAWING_STUDIO -> if (childProfile != null) AppScreen.CHILD_HOME else AppScreen.PARENT_HOME
            }
            notice = null
        }
    }

    LaunchedEffect(familyId, children.map { it.id }) {
        if (familyId.isNotBlank() && session?.accountRole == "parent") {
            try {
                childSubjectSelections = repository.loadChildSubjectSelections(familyId)
            } catch (_: Exception) {
                // Keep the last confirmed per-child subject choices if temporarily offline.
            }
        }
    }

    LaunchedEffect(screen, selectedChild?.id, subjects) {
        val child = selectedChild
        if (screen == AppScreen.CURRICULUM_LIBRARY && child != null) {
            childSubjectDraftChildId = child.id
            try {
                val saved = repository.loadChildSubjectSelections(familyId)
                childSubjectSelections = saved
                val available = subjects.filter { subject ->
                    subject.gradeLevel == child.schoolGrade &&
                        (subject.countryCode.equals(child.countryCode, ignoreCase = true) ||
                            subject.countryCode.equals("ZZ", ignoreCase = true))
                }.map { it.id }.toSet()
                childSubjectDraft = saved[child.id] ?: available
            } catch (_: Exception) {
                childSubjectDraft = childSubjectSelections[child.id] ?: subjects.filter {
                    it.gradeLevel == child.schoolGrade &&
                        (it.countryCode.equals(child.countryCode, ignoreCase = true) ||
                            it.countryCode.equals("ZZ", ignoreCase = true))
                }.map { it.id }.toSet()
            }
        }
    }

    LaunchedEffect(screen, childProfile?.id) {
        val linked = childProfile
        if (linked != null && session?.accountRole == "child" && screen == AppScreen.CHILD_HOME) {
            try {
                repository.ensureDailyTasks()
                val refreshedTasks = repository.loadChildTasks(linked.id, linked.schoolGrade, linked.ageYears)
                tasks = refreshedTasks
                val hasPendingRedirect = launchPreferences.getString("pending_task_id", null) != null ||
                    launchPreferences.getBoolean("pending_open_any_task", false)
                if (refreshedTasks.isEmpty() && hasPendingRedirect) {
                    launchPreferences.edit()
                        .remove("pending_task_id")
                        .remove("pending_open_any_task")
                        .apply()
                    notice = "This app is blocked. No learning task is ready yet; ask your parent to create tasks or enable daily tasks."
                }
            } catch (_: Exception) {
                // Keep the previous task list visible. The next home entry retries generation.
            }
        }
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
                                                    subjects = repository.loadSubjects(familyId)
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
                val supportedUiLanguages = (TranslateLanguage.getAllLanguages() + listOf("en", "ur", "sd", "ps")).distinct()
                val optionCodes = when (languagePickerTarget) {
                    "display" -> supportedUiLanguages.filter { code ->
                        code == "en" || code == "sd" || TranslateLanguage.fromLanguageTag(code) != null
                    }
                    "translation", "question-translation" -> supportedUiLanguages.filter { code -> code == "sd" || TranslateLanguage.fromLanguageTag(code) != null }
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
                    "translation", "question-translation" -> "Choose translation language"
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
                                        "question-translation" -> {
                                            translationLanguage = code.lowercase(Locale.ROOT)
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
                                            subjects = repository.loadSubjects(familyId)
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
                AppScreen.AUTH -> if (isRestoringSession) {
                    ScreenFrame("Welcome back", notice = null, busy = true) {
                        CircularProgressIndicator()
                        AppUiText("Restoring your signed-in account…")
                    }
                } else ScreenFrame(if (isRegistering) "Create your parent account" else "Welcome back", notice = notice, busy = busy) {
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
                    if (isRegistering) Button(
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
                    if (!isRegistering) Button(
                        onClick = {
                            execute {
                                val signedIn = repository.signIn(email, password, "parent")
                                resolveSignedIn(signedIn)
                            }
                        },
                        enabled = !busy,
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
                        AppUiText("App setup is incomplete. Ask the app administrator to finish connection settings before building. Never put private server keys in the app.")
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
                AppScreen.PAIR_CHILD -> ScreenFrame(
                    if (childLogoutLocked) "Child profile locked" else "Pair this child's device",
                    onBack = {
                        if (childLogoutLocked) {
                            notice = "Parent authorization is required to unlock this linked profile."
                        } else {
                            repository.signOut()
                            session = null
                            childProfile = null
                            devicePreferences.edit().remove("mode").apply()
                            deviceMode = "unknown"
                            screen = AppScreen.DEVICE_SETUP
                        }
                    },
                    notice = notice,
                    busy = busy
                ) {
                    if (childLogoutLocked) {
                        AppUiText("This child profile is logged out. The parent-child link and learning records are preserved.")
                        AppUiText("Ask your parent to generate a new eight-digit code. It has no time expiry, works once, and can be replaced by the parent at any time.")
                        Field(
                            childGateCode,
                            { childGateCode = it.filter(Char::isDigit).take(8) },
                            "8-digit parent authorization code",
                            keyboardType = KeyboardType.Number
                        )
                        Button(
                            onClick = {
                                execute {
                                    val current = repository.currentSession()
                                        ?: throw IllegalStateException("This linked session is missing. Contact the parent before pairing this device again.")
                                    repository.redeemChildLogoutCode(childGateCode)
                                    childLogoutLocked = false
                                    lockStatePreferences.edit().putBoolean("child_logout_locked", false).apply()
                                    childGateCode = ""
                                    finishChildLogin(current, "")
                                    notice = "Parent authorization accepted. Welcome back!"
                                }
                            },
                            enabled = !busy && childGateCode.length == 8,
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("Unlock with parent code") }
                    } else {
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
                                    "If pairing says guest sign-in is disabled, ask the app administrator to enable guest sign-in in the authentication settings, then retry pairing. Reinstalling the app will not change this setting.",
                                    color = Color(0xFF704B00)
                                )
                                AppUiText(
                                    "Never put a private server key in the Android app. The child session is linked only after the parent-generated one-time code is claimed.",
                                    color = Color(0xFF704B00),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                AppScreen.CHILD_SECURITY -> ScreenFrame(
                    "Security & emergency access",
                    onBack = { screen = AppScreen.CHILDREN; childSecurityCodeDisplay = ""; childSecurityCodeType = "" },
                    notice = notice,
                    busy = busy
                ) {
                    val child = selectedChild
                    if (child == null) {
                        AppUiText("Choose a child profile first.")
                    } else {
                        AppUiText("Settings for " + child.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        AppUiText("Emergency access works when your phone is unavailable: the child uses a code created here. The child's phone needs internet to verify it securely.")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0FF))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = childEmergencyEnabled,
                                        onCheckedChange = { childEmergencyEnabled = it }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        AppUiText("Allow emergency app access", fontWeight = FontWeight.Bold)
                                        AppUiText("One successful use per day for this child.", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Field(
                                    childEmergencyMinutesText,
                                    { childEmergencyMinutesText = it.filter(Char::isDigit).take(2) },
                                    "Emergency access minutes (1–30)",
                                    keyboardType = KeyboardType.Number
                                )
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        childBlockEmergencyAfterDailyLimit = !childBlockEmergencyAfterDailyLimit
                                    },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = childBlockEmergencyAfterDailyLimit,
                                        onCheckedChange = { childBlockEmergencyAfterDailyLimit = it }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        AppUiText("Never bypass an app's daily limit", fontWeight = FontWeight.Bold)
                                        AppUiText(
                                            "When enabled, emergency access is blocked after the selected app reaches its daily time limit.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                AppUiText("Choose the apps that can be temporarily opened. Only currently enabled parent-approved apps can be selected.", style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = childSecurityAppSearchQuery,
                                    onValueChange = { childSecurityAppSearchQuery = it.take(120) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { AppUiText("Search emergency apps") },
                                    placeholder = { AppUiText("Find an app by name or package") }
                                )
                                val visibleChildSecurityApps = childSecurityApps.filter { app ->
                                    childSecurityAppSearchQuery.isBlank() ||
                                        app.displayName.contains(childSecurityAppSearchQuery, ignoreCase = true) ||
                                        app.packageName.contains(childSecurityAppSearchQuery, ignoreCase = true)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { childEmergencyAllowedPackages = childEmergencyAllowedPackages + visibleChildSecurityApps.map { it.packageName } },
                                        enabled = visibleChildSecurityApps.isNotEmpty(),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                    ) { AppUiText("Select shown", fontSize = 12.sp) }
                                    OutlinedButton(
                                        onClick = { childEmergencyAllowedPackages = emptySet() },
                                        enabled = childEmergencyAllowedPackages.isNotEmpty(),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                    ) { AppUiText("Clear", fontSize = 12.sp) }
                                }
                                AppUiText("${childEmergencyAllowedPackages.size} app(s) selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                if (childSecurityApps.isEmpty()) {
                                    AppUiText("No parent-approved apps are enabled yet. Enable apps in Apps & limits first.")
                                } else if (visibleChildSecurityApps.isEmpty()) {
                                    AppUiText("No approved apps match '${childSecurityAppSearchQuery}'.")
                                } else {
                                    visibleChildSecurityApps.forEach { app ->
                                        Row(
                                            Modifier.fillMaxWidth().clickable {
                                                childEmergencyAllowedPackages = if (app.packageName in childEmergencyAllowedPackages) {
                                                    childEmergencyAllowedPackages - app.packageName
                                                } else {
                                                    childEmergencyAllowedPackages + app.packageName
                                                }
                                            }.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = app.packageName in childEmergencyAllowedPackages,
                                                onCheckedChange = { checked ->
                                                    childEmergencyAllowedPackages = if (checked) {
                                                        childEmergencyAllowedPackages + app.packageName
                                                    } else {
                                                        childEmergencyAllowedPackages - app.packageName
                                                    }
                                                }
                                            )
                                            Column(Modifier.weight(1f)) {
                                                AppUiText(compactAppLabel(app.displayName, 23), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                                AppUiText(compactAppLabel(app.packageName, 25), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        execute {
                                            val minutes = childEmergencyMinutesText.toIntOrNull()
                                                ?: throw IllegalArgumentException("Enter emergency access minutes from 1 to 30.")
                                            repository.saveChildEmergencySettings(
                                                familyId, child.id, childEmergencyEnabled,
                                                childEmergencyAllowedPackages, minutes,
                                                childBlockEmergencyAfterDailyLimit
                                            )
                                            notice = "Emergency access settings saved for " + child.displayName + "."
                                        }
                                    },
                                    enabled = !busy && childEmergencyMinutesText.toIntOrNull() in 1..30 &&
                                        (!childEmergencyEnabled || childEmergencyAllowedPackages.isNotEmpty()),
                                    modifier = Modifier.fillMaxWidth()
                                ) { AppUiText("Save emergency settings") }
                                Button(
                                    onClick = {
                                        execute {
                                            val minutes = childEmergencyMinutesText.toIntOrNull()
                                                ?: throw IllegalArgumentException("Enter emergency access minutes from 1 to 30.")
                                            val code = repository.generateChildEmergencyCode(
                                                familyId, child.id, childEmergencyAllowedPackages, minutes,
                                                childBlockEmergencyAfterDailyLimit
                                            )
                                            childEmergencyEnabled = true
                                            childSecurityCodeDisplay = code
                                            childSecurityCodeType = "Emergency code • once per day • valid 30 days"
                                            notice = "Emergency access is enabled. Share this code privately with the child."
                                        }
                                    },
                                    enabled = !busy && childEmergencyMinutesText.toIntOrNull() in 1..30 &&
                                        childEmergencyAllowedPackages.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth()
                                ) { AppUiText("Generate emergency code") }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                execute {
                                    childSecurityCodeDisplay = repository.generateChildLogoutCode(child.id)
                                    childSecurityCodeType = "Parent logout / unlock code • one use • no expiry"
                                    notice = "Share this code privately. It has no time expiry, works once, and generating another code replaces it. It logs the child out without unlinking the profile."
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("Generate / change parent logout code") }
                        if (childSecurityCodeDisplay.isNotBlank()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))
                            ) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    AppUiText(childSecurityCodeType, fontWeight = FontWeight.SemiBold)
                                    AppUiText(childSecurityCodeDisplay, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                                    AppUiText("This code is shown only now. It stays valid until used or replaced by a parent; it does not expire by time.", style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("EduLock security code", childSecurityCodeDisplay))
                                            notice = "Security code copied. Share it privately with the child."
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { AppUiText("Copy code") }
                                    OutlinedButton(
                                        onClick = { childSecurityCodeDisplay = ""; childSecurityCodeType = "" },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { AppUiText("Hide code") }
                                }
                            }
                        }
                        AppUiText(
                            "Emergency use is recorded on the server and is limited to one successful redemption per Pakistan calendar day. A new code cannot bypass the selected-app list.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AppScreen.APP_SETTINGS -> ScreenFrame("App settings", onBack = { screen = AppScreen.PARENT_HOME }, notice = notice, busy = busy) {
                    AppUiText("Everything in one place", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    AppUiText("Choose the curriculum country, learning language and app language. Religious-education subjects are chosen separately for each child profile.")
                    OutlinedTextField(
                        value = settingsSearchQuery,
                        onValueChange = { settingsSearchQuery = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { AppUiText("Search settings") },
                        placeholder = { AppUiText("Find country, languages, limits, emergency, location or protection") }
                    )
                    if (settingsSearchQuery.isBlank()) {
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
                        DashboardIconTile(
                            icon = "🆘", title = "Emergency access", subtitle = "Parent-approved app access, once per day",
                            accent = Color(0xFFDA6195),
                            onClick = { screen = AppScreen.CHILDREN; execute { children = repository.loadChildren(familyId) } }
                        )
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
                    } else {
                        val query = settingsSearchQuery.trim()
                        val settingsEntries = listOf(
                            "Country" to "Curriculum country and regional question suggestions",
                            "Learning language" to "Language used for learning content",
                            "App language" to "Language used by the app interface",
                            "Child profiles" to "Child profiles, country, grade, language, daily reward cap, game limit, Kids Mode, pairing codes and delink",
                            "App limits" to "Allowed apps, blocked apps, daily screen-time limits and weekly schedules",
                            "Emergency & logout codes" to "Parent-generated one-time logout PIN, no time expiry, emergency access and strict daily limits",
                            "Question rewards" to "Question reward minutes, concept group bonuses and learning tasks",
                            "Location & alerts" to "Child location sharing and family alerts",
                            "Device protection" to "Accessibility and managed-device protection",
                            "Notifications" to "Android notification permission",
                            "Sign out" to "Sign out of the parent account"
                        ).filter { (label, description) ->
                            label.contains(query, ignoreCase = true) || description.contains(query, ignoreCase = true)
                        }
                        if (settingsEntries.isEmpty()) {
                            AppUiText("No settings match '${query}'. Try words such as apps, limits, language or protection.")
                        }
                        settingsEntries.forEach { (label, description) ->
                            OutlinedButton(
                                onClick = {
                                    when (label) {
                                        "Country" -> { countryPickerTarget = "parent"; countrySearch = ""; countryPickerOpen = true }
                                        "Learning language" -> { languagePickerTarget = "parent-learning"; languageSearch = ""; languagePickerOpen = true }
                                        "App language" -> { languagePickerTarget = "display"; languageSearch = ""; languagePickerOpen = true }
                                        "Child profiles", "App limits", "Emergency access", "Emergency & logout codes" -> {
                                            screen = AppScreen.CHILDREN
                                            execute { children = repository.loadChildren(familyId) }
                                        }
                                        "Question rewards" -> {
                                            screen = AppScreen.SUBJECTS
                                            execute { subjects = repository.loadSubjects(familyId) }
                                        }
                                        "Location & alerts" -> {
                                            screen = AppScreen.CHILD_LOCATIONS
                                            execute {
                                                val latestChildren = repository.loadChildren(familyId)
                                                children = latestChildren
                                                childLocationRows = latestChildren.map { repository.loadChildLocationSnapshot(it.id) }
                                            }
                                        }
                                        "Device protection" -> { refreshProtectionStatus(); screen = AppScreen.PROTECTION_STATUS }
                                        "Notifications" -> {
                                            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                notice = "Notification permission is already granted or is managed by Android on this device."
                                            }
                                        }
                                        "Sign out" -> {
                                            repository.signOut()
                                            session = null
                                            childProfile = null
                                            screen = AppScreen.AUTH
                                            isRegistering = false
                                            notice = "Signed out."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    AppUiText(label, fontWeight = FontWeight.SemiBold)
                                    AppUiText(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                AppScreen.PARENT_HOME -> ScreenFrame("Parent dashboard", notice = notice, busy = busy) {
                    val dashboardSubjects = subjectsForChildContext(subjects, selectedChild, childSubjectSelections, parentCountryCode)
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
                            title = dashboardSubjects.size.toString() + if (dashboardSubjects.size == 1) " subject" else " subjects",
                            subtitle = if (selectedChild != null) {
                                "Subjects for " + selectedChild!!.displayName
                            } else {
                                "Questions for " + displayCountryName(parentCountryCode)
                            },
                            modifier = Modifier.weight(1f),
                            accent = Color(0xFF079B91),
                            onClick = { screen = AppScreen.SUBJECTS; execute { subjects = repository.loadSubjects(familyId) } }
                        )
                    }
                    AppUiText("Your learning toolkit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardIconTile(
                                icon = "🧪", title = "Subjects & questions", subtitle = "Create and manage question banks",
                                modifier = Modifier.weight(1f), accent = Color(0xFF5147E8),
                                onClick = { screen = AppScreen.SUBJECTS; execute { subjects = repository.loadSubjects(familyId) } }
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
                                icon = "🏃", title = "Physical challenges", subtitle = "Set activities, review evidence and approve rewards",
                                modifier = Modifier.weight(1f), accent = Color(0xFF20A46B),
                                onClick = {
                                    screen = AppScreen.PHYSICAL_CHALLENGES
                                    execute {
                                        physicalChallenges = repository.loadPhysicalChallenges(familyId)
                                        challengeSubmissions = repository.loadChallengeSubmissions(familyId)
                                    }
                                }
                            )
                            DashboardIconTile(
                                icon = "🧪", title = "Practical tasks", subtitle = "Save hands-on learning tasks for children",
                                modifier = Modifier.weight(1f), accent = Color(0xFF7C5CE7),
                                onClick = {
                                    val child = selectedChild ?: children.firstOrNull()
                                    if (child == null) {
                                        notice = "Add a child profile before saving practical tasks."
                                    } else {
                                        selectedChild = child
                                        screen = AppScreen.PRACTICAL_TASKS
                                        execute {
                                            practicalTasks = repository.loadPracticalTasksForParent(familyId, child.id)
                                            practicalSubmissions = repository.loadPracticalSubmissionsForParent(familyId, child.id)
                                        }
                                    }
                                }
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
                    OutlinedTextField(
                        value = childProfileSearchQuery,
                        onValueChange = { childProfileSearchQuery = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { AppUiText("Search child profiles") },
                        placeholder = { AppUiText("Find by name, country, grade or language") }
                    )
                    Button(onClick = {
                        childName = ""; childLanguage = parentLearningLanguageCode; childDailyCap = "60"
                        childCountryCode = parentCountryCode; childSchoolGrade = "1"; childAgeYears = "8"
                        childAvatarIcon = "🦊"; childStudyTrack = "none"; childReligionEnabled = false; childDailyGameLimit = "20"
                        screen = AppScreen.ADD_CHILD
                    }, modifier = Modifier.fillMaxWidth()) { AppUiText("Add child profile") }
                    val filteredChildren = children.filter { child ->
                        childProfileSearchQuery.isBlank() ||
                            child.displayName.contains(childProfileSearchQuery, ignoreCase = true) ||
                            child.preferredLanguage.contains(childProfileSearchQuery, ignoreCase = true) ||
                            child.countryCode.contains(childProfileSearchQuery, ignoreCase = true) ||
                            child.schoolGrade.toString().contains(childProfileSearchQuery, ignoreCase = true) ||
                            child.ageYears.toString().contains(childProfileSearchQuery, ignoreCase = true)
                    }
                    if (children.isEmpty()) {
                        AppUiText("No child profiles yet. Add a child to create the protected parent-child link.")
                    } else if (filteredChildren.isEmpty()) {
                        AppUiText("No child profiles match '${childProfileSearchQuery}'.")
                    }
                    filteredChildren.forEach { child ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(Modifier.size(42.dp).background(Color(0xFFE8E7FF), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                                        AppUiText(child.avatarIcon, fontSize = 24.sp)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        AppUiText(child.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        AppUiText("🎮 Games: ${child.dailyGameLimitMinutes} min/day", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                AppUiText(
                                    "Language: " + child.preferredLanguage + " • Daily reward cap: " + child.dailyCapMinutes + " minutes"
                                )
                                AppUiText(
                                    if (child.deviceLinked) "📱 Child device linked" else "📱 No device linked • generate a pairing code",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                OutlinedButton(
                                    onClick = {
                                        execute {
                                            val cfg = repository.loadChildDailyTaskSettings(child.id)
                                            dailyTasksEnabled = cfg.enabled
                                            dailyTaskCountText = cfg.dailyTaskCount.toString()
                                            dailyTaskQuestionCountText = cfg.questionsPerTask.toString()
                                            dailyTaskPassPercentText = cfg.passPercent.toString()
                                            dailyTaskRewardMinutesText = cfg.rewardMinutes.toString()
                                            dailyTaskCapMinutesText = cfg.dailyCapMinutes.toString()
                                            val learningPrefs = repository.loadChildLearningPreferences(child.id)
                                            kindergartenModeEnabled = learningPrefs.kindergartenMode
                                            gentleLearningRemindersEnabled = learningPrefs.gentleReminders
                                            dailyWrittenLimitText = learningPrefs.dailyWrittenQuestionLimit.toString()
                                            practicalTasksEnabled = learningPrefs.practicalTasksEnabled
                                            dailyTaskSettingsChild = child
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AppUiText("🤖 Daily learning tasks • Configure")
                                }
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
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AppUiText(
                                            if (child.kidsModeEnabled) "🔒 Kids Mode ON" else "🔓 Kids Mode OFF",
                                            modifier = Modifier.weight(1f),
                                            fontWeight = FontWeight.Bold
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                execute {
                                                    val requested = !child.kidsModeEnabled
                                                    val existingRules = repository.loadAllowedApps(child.id)
                                                    val confirmed = repository.updateChildKidsMode(child.id, requested)
                                                    if (confirmed != requested) {
                                                        throw IllegalStateException("The server did not confirm the requested Kids Mode setting.")
                                                    }
                                                    val latest = repository.loadChildren(familyId)
                                                    val updated = latest.firstOrNull { it.id == child.id }
                                                        ?: throw IllegalStateException("The server did not return this child profile after saving Kids Mode.")
                                                    if (updated.kidsModeEnabled != requested) {
                                                        throw IllegalStateException("The server returned a different Kids Mode value. No success was reported.")
                                                    }
                                                    children = latest
                                                    selectedChild = updated
                                                    notice = when {
                                                        requested && existingRules.none { it.enabled } ->
                                                            "Kids Mode was saved and verified, but no apps are enabled yet. Third-party apps will be blocked after the child phone syncs. Add permitted apps first."
                                                        requested ->
                                                            "Kids Mode was saved and verified. The child phone applies it after its next successful sync; verify Accessibility and device-owner status on that phone."
                                                        else ->
                                                            "Kids Mode was disabled and verified. Individual app rules apply again after the next child-device sync."
                                                    }
                                                }
                                            },
                                            enabled = !busy,
                                            modifier = Modifier.height(36.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                        ) {
                                            AppUiText(if (child.kidsModeEnabled) "Turn off" else "Enable")
                                        }
                                    }
                                }
                                Field(
                                    childGameLimitEdits[child.id] ?: child.dailyGameLimitMinutes.toString(),
                                    { value -> childGameLimitEdits = childGameLimitEdits + (child.id to value.filter(Char::isDigit).take(4)) },
                                    "Daily educational game limit in minutes (0 disables games)",
                                    keyboardType = KeyboardType.Number
                                )
                                LaunchedEffect(child.id, childGameLimitEdits[child.id], child.dailyGameLimitMinutes) {
                                    delay(700)
                                    val rawLimit = childGameLimitEdits[child.id] ?: return@LaunchedEffect
                                    val limit = rawLimit.toIntOrNull() ?: return@LaunchedEffect
                                    if (limit !in 0..1440) {
                                        notice = "Enter a daily educational game limit from 0 to 1440 minutes."
                                        return@LaunchedEffect
                                    }
                                    if (limit == child.dailyGameLimitMinutes) return@LaunchedEffect
                                    execute {
                                        repository.updateChildGameLimit(child.id, limit)
                                        children = repository.loadChildren(familyId)
                                        childGameLimitEdits = childGameLimitEdits - child.id
                                        notice = "Daily educational game allowance auto-saved for ${child.displayName}."
                                    }
                                }
                                AppUiText("Device & learning controls", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp)
                                    ) { AppUiText("📚 Curriculum", fontSize = 12.sp) }
                                    OutlinedButton(
                                        onClick = {
                                            selectedChild = child
                                            selectedSubject = null
                                            questions = emptyList()
                                            execute {
                                                subjects = repository.loadSubjects(familyId)
                                                childSubjectSelections = repository.loadChildSubjectSelections(familyId)
                                                screen = AppScreen.SUBJECTS
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp)
                                    ) { AppUiText("❓ Questions", fontSize = 12.sp) }
                                    OutlinedButton(
                                        onClick = {
                                            selectedChild = child
                                            lastPairingCode = ""
                                            execute {
                                                val generated = repository.createPairingCode(child.id)
                                                lastPairingCode = generated.first
                                                pairingCodeExpiry = generated.second
                                                pairingChildName = child.displayName
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp)
                                    ) { AppUiText("🔗 Pairing code", fontSize = 12.sp) }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedChild = child
                                            screen = AppScreen.ALLOWED_APPS
                                            execute {
                                                allowedApps = repository.loadAllowedApps(child.id)
                                                childInstalledApps = repository.loadInstalledApps(child.id)
                                                appGroups = repository.loadAppGroups(child.id)
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp)
                                    ) { AppUiText("🎮 Apps & limits", fontSize = 12.sp) }
                                    OutlinedButton(
                                        onClick = {
                                            appUsageChild = child
                                            screen = AppScreen.APP_USAGE_REPORT
                                            execute { appUsageReports = repository.loadAppUsageReports(child.id) }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp)
                                    ) { AppUiText("📊 Usage report", fontSize = 12.sp) }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedChild = child
                                            childSecurityCodeDisplay = ""
                                            childSecurityCodeType = ""
                                            screen = AppScreen.CHILD_SECURITY
                                            execute {
                                                childSecurityApps = repository.loadAllowedApps(child.id)
                                                    .filter { it.enabled }
                                                    .distinctBy { it.packageName.lowercase(Locale.ROOT) }
                                                    .sortedBy { it.displayName.lowercase(Locale.ROOT) }
                                                val config = repository.loadChildEmergencyAccess(child.id)
                                                childEmergencyEnabled = config.enabled
                                                childBlockEmergencyAfterDailyLimit = config.blockEmergencyAfterDailyLimit
                                                childEmergencyAllowedPackages = config.allowedPackages.intersect(childSecurityApps.map { it.packageName }.toSet())
                                                childEmergencyMinutesText = config.accessMinutes.toString()
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp)
                                    ) { AppUiText("🔐 Security", fontSize = 12.sp) }
                                    OutlinedButton(
                                        onClick = { pendingChildAction = child to "delink" },
                                        enabled = !busy && child.deviceLinked,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp)
                                    ) { AppUiText("Delink device", fontSize = 11.sp) }
                                    OutlinedButton(
                                        onClick = { pendingChildAction = child to "delete" },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp)
                                    ) { AppUiText("Delete profile", fontSize = 11.sp) }
                                    }
                                }
                            }
                        }
                    }
                    if (lastPairingCode.isNotBlank()) {
                        Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppUiText("One-time pairing code for " + pairingChildName, fontWeight = FontWeight.SemiBold)
                                AppUiText(lastPairingCode, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                                AppUiText("Valid for " + (pairingCodeExpiry / 60) + " minutes. Share privately with the child; only its hash is stored.")
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("EduLock pairing code", lastPairingCode))
                                        notice = "Pairing code copied. Share it privately with the child."
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { AppUiText("Copy pairing code") }
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
                    AppUiText("Lessons use the child's country, class, age and language.")
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

                        val childSubjectsAvailable = subjects.filter { subject ->
                            subject.gradeLevel == child.schoolGrade &&
                                (subject.countryCode.equals(child.countryCode, ignoreCase = true) ||
                                    subject.countryCode.equals("ZZ", ignoreCase = true))
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0FF))
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                AppUiText("Subjects for " + child.displayName, fontWeight = FontWeight.Bold)
                                AppUiText("Select the subject banks this child should use.", style = MaterialTheme.typography.bodySmall)
                                if (childSubjectsAvailable.isEmpty()) {
                                    AppUiText("No saved subjects match this child's country and grade yet.", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    childSubjectsAvailable.forEach { subject ->
                                        Row(
                                            Modifier.fillMaxWidth().clickable {
                                                childSubjectDraft = if (subject.id in childSubjectDraft) childSubjectDraft - subject.id else childSubjectDraft + subject.id
                                            }.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = subject.id in childSubjectDraft,
                                                onCheckedChange = { checked ->
                                                    childSubjectDraft = if (checked) childSubjectDraft + subject.id else childSubjectDraft - subject.id
                                                }
                                            )
                                            Column(Modifier.weight(1f)) {
                                                AppUiText(subject.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                                AppUiText(subject.languageCode.uppercase() + " • Grade " + subject.gradeLevel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            execute {
                                                require(childSubjectDraft.isNotEmpty()) { "Select at least one subject for this child." }
                                                repository.saveChildSubjectSelection(familyId, child.id, childSubjectDraft)
                                                childSubjectSelections = childSubjectSelections + (child.id to childSubjectDraft)
                                                notice = "Subjects saved for " + child.displayName + "."
                                            }
                                        },
                                        enabled = !busy && childSubjectDraft.isNotEmpty() && childSubjectDraftChildId == child.id,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                                    ) { AppUiText("Save subjects") }
                                }
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
                                    curriculumQuestionSearchQuery = ""
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
                            OutlinedTextField(
                                value = curriculumQuestionSearchQuery,
                                onValueChange = { curriculumQuestionSearchQuery = it.take(200) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { AppUiText("Search curriculum questions") },
                                placeholder = { AppUiText("Find by question, topic, language or source") }
                            )
                            val visibleCurriculumQuestions = result.questions.filter { item ->
                                curriculumQuestionSearchQuery.isBlank() ||
                                    item.prompt.contains(curriculumQuestionSearchQuery, ignoreCase = true) ||
                                    item.topic.contains(curriculumQuestionSearchQuery, ignoreCase = true) ||
                                    item.subjectName.contains(curriculumQuestionSearchQuery, ignoreCase = true) ||
                                    item.languageCode.contains(curriculumQuestionSearchQuery, ignoreCase = true) ||
                                    item.curriculumName.contains(curriculumQuestionSearchQuery, ignoreCase = true) ||
                                    item.options.any { option -> option.contains(curriculumQuestionSearchQuery, ignoreCase = true) }
                            }
                            if (visibleCurriculumQuestions.isEmpty()) {
                                AppUiText("No curriculum questions match '${curriculumQuestionSearchQuery}'.")
                            }
                            val visibleCurriculumQuestionIds = visibleCurriculumQuestions.map { it.id }.toSet()
                            val allVisibleQuestionsSelected = visibleCurriculumQuestionIds.isNotEmpty() &&
                                visibleCurriculumQuestionIds.all { it in curriculumSelectedIds }
                            if (curriculumQuestionSearchQuery.isNotBlank() && visibleCurriculumQuestions.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        curriculumSelectedIds = if (allVisibleQuestionsSelected) {
                                            curriculumSelectedIds - visibleCurriculumQuestionIds
                                        } else {
                                            curriculumSelectedIds + visibleCurriculumQuestionIds
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AppUiText(
                                        if (allVisibleQuestionsSelected) {
                                            if (curriculumQuestionSearchQuery.isBlank()) "Clear selection" else "Clear matching selection"
                                        } else {
                                            if (curriculumQuestionSearchQuery.isBlank()) "Select all questions" else "Select matching questions"
                                        }
                                    )
                                }
                            }
                            visibleCurriculumQuestions.forEach { item ->
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
                                        subjects = repository.loadSubjects(familyId)
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
                    OutlinedTextField(
                        value = subjectSearchQuery,
                        onValueChange = { subjectSearchQuery = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { AppUiText("Search subjects") },
                        placeholder = { AppUiText("Find by subject, language or curriculum") }
                    )
                    Button(onClick = {
                        editingSubjectId = null
                        subjectTitle = ""
                        subjectLanguage = selectedChild?.preferredLanguage ?: parentLearningLanguageCode
                        subjectCountryCode = selectedChild?.countryCode ?: parentCountryCode
                        subjectGradeLevel = (selectedChild?.schoolGrade ?: children.firstOrNull()?.schoolGrade ?: 1).toString()
                        screen = AppScreen.ADD_SUBJECT
                    }, modifier = Modifier.fillMaxWidth()) { AppUiText("Create subject") }

                    AppUiText("Your selected subjects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    AppUiText(if (selectedChild != null) "Subjects for " + selectedChild!!.displayName + ". Each subject has its own questions." else "Open a subject to view its questions and translations.")
                    val visibleSubjects = subjectsForChildContext(subjects, selectedChild, childSubjectSelections, parentCountryCode)
                    val searchedSubjects = visibleSubjects.filter { subject ->
                        subjectSearchQuery.isBlank() ||
                            subject.title.contains(subjectSearchQuery, ignoreCase = true) ||
                            subject.languageCode.contains(subjectSearchQuery, ignoreCase = true) ||
                            subject.countryCode.contains(subjectSearchQuery, ignoreCase = true) ||
                            subject.curriculumSubjectCode.contains(subjectSearchQuery, ignoreCase = true) ||
                            subject.gradeLevel.toString().contains(subjectSearchQuery, ignoreCase = true)
                    }
                    if (visibleSubjects.isEmpty() && subjectSearchQuery.isBlank()) {
                        AppUiText(
                            if (subjects.isEmpty()) "No subjects yet. Create a subject to begin."
                            else if (selectedChild != null) "No subjects match ${selectedChild!!.displayName}'s country, grade and selected-subject settings yet. Create or choose a suggested subject below."
                            else "No subjects available for ${displayCountryName(parentCountryCode)} yet. Create a subject to begin."
                        )
                    } else if (searchedSubjects.isEmpty()) {
                        AppUiText("No subjects match '${subjectSearchQuery}'.")
                    }
                    searchedSubjects.forEach { subject ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedSubject = subject
                                questions = emptyList()
                                questionTranslations = emptyMap()
                                questionSolutions = emptyMap()
                                expandedFourDQuestionId = null
                                translationEditorQuestionId = null
                                questionEditExpandedId = null
                                questionEditDrafts = emptyMap()
                                questionRewardEdits = emptyMap()
                                questionRewardGroups = emptyList()
                                questionGroupRewardEdits = emptyMap()
                                groupRewardsExpanded = false
                                screen = AppScreen.QUESTIONS
                                execute {
                                    val loadedQuestions = repository.loadQuestions(subject.id)
                                    questions = loadedQuestions
                                    val questionIds = loadedQuestions.map { it.id }
                                    coroutineScope {
                                        val translationsDeferred = async { repository.loadQuestionTranslations(questionIds) }
                                        val solutionsDeferred = async { repository.loadQuestionSolutions(questionIds) }
                                        questionTranslations = translationsDeferred.await()
                                        questionSolutions = solutionsDeferred.await()
                                    }
                                    notice = null
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
                                        questions = emptyList()
                                        questionTranslations = emptyMap()
                                        questionSolutions = emptyMap()
                                        expandedFourDQuestionId = null
                                        translationEditorQuestionId = null
                                        questionEditExpandedId = null
                                        questionEditDrafts = emptyMap()
                                        questionRewardEdits = emptyMap()
                                        questionRewardGroups = emptyList()
                                        questionGroupRewardEdits = emptyMap()
                                        groupRewardsExpanded = false
                                        screen = AppScreen.QUESTIONS
                                        execute {
                                            val loadedQuestions = repository.loadQuestions(subject.id)
                                            questions = loadedQuestions
                                            val questionIds = loadedQuestions.map { it.id }
                                            coroutineScope {
                                                val translationsDeferred = async { repository.loadQuestionTranslations(questionIds) }
                                                val solutionsDeferred = async { repository.loadQuestionSolutions(questionIds) }
                                                questionTranslations = translationsDeferred.await()
                                                questionSolutions = solutionsDeferred.await()
                                            }
                                            notice = null
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
                                OutlinedButton(
                                    onClick = { subjectDeleteConfirmation = subject },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                                ) { AppUiText("Delete subject", color = MaterialTheme.colorScheme.error) }
                                if (subjectDeleteConfirmation?.id == subject.id) {
                                    AlertDialog(
                                        onDismissRequest = { subjectDeleteConfirmation = null },
                                        title = { AppUiText("Delete subject?") },
                                        text = { AppUiText("This removes “${subject.title}” and its questions and translations. This cannot be undone.") },
                                        confirmButton = {
                                            TextButton(
                                                enabled = !busy,
                                                onClick = {
                                                    execute {
                                                        repository.deleteSubject(subject.id)
                                                        subjects = repository.loadSubjects(familyId)
                                                        if (selectedSubject?.id == subject.id) {
                                                            selectedSubject = null
                                                            questions = emptyList()
                                                            questionTranslations = emptyMap()
                                                            questionSolutions = emptyMap()
                                                            screen = AppScreen.SUBJECTS
                                                        }
                                                        subjectDeleteConfirmation = null
                                                        notice = "Subject deleted."
                                                    }
                                                }
                                            ) { AppUiText("Delete permanently", color = MaterialTheme.colorScheme.error) }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { subjectDeleteConfirmation = null }) { AppUiText("Cancel") }
                                        }
                                    )
                                }
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
                        val templateCategoriesForContext = if (selectedSubject != null) {
                            val selectedTitle = selectedSubject!!.title.substringBefore(" — Grade ").trim()
                            LearningTemplates.all
                                .filter { it.subjectTitle.equals(selectedTitle, ignoreCase = true) }
                                .map { it.category }
                                .distinct()
                        } else learningTemplateCategories(templateCountryCode)
                        templateCategoriesForContext
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
                            (selectedSubject == null || template.subjectTitle.equals(
                                selectedSubject!!.title.substringBefore(" — Grade ").trim(), ignoreCase = true
                            )) &&
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
                    OutlinedTextField(
                        value = templateSearchQuery,
                        onValueChange = { templateSearchQuery = it.take(200) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { AppUiText("Search learning templates") },
                        placeholder = { AppUiText("Find by title, subject, question or concept") }
                    )
                    val searchableTemplates = filteredTemplates.filter { template ->
                        templateSearchQuery.isBlank() ||
                            template.title.contains(templateSearchQuery, ignoreCase = true) ||
                            template.subjectTitle.contains(templateSearchQuery, ignoreCase = true) ||
                            template.prompt.contains(templateSearchQuery, ignoreCase = true) ||
                            template.conceptKey.contains(templateSearchQuery, ignoreCase = true) ||
                            template.questionType.contains(templateSearchQuery, ignoreCase = true) ||
                            template.options.any { it.contains(templateSearchQuery, ignoreCase = true) }
                    }
                    if (templateSearchQuery.isNotBlank() && searchableTemplates.isEmpty()) {
                        AppUiText("No learning templates match '${templateSearchQuery}'.")
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
                                require(targetCode != "sd" && TranslateLanguage.fromLanguageTag(targetCode) != null) {
                                    if (targetCode == "sd") "Automatic on-device translation to Sindhi is not supported by Google's model. Choose Sindhi in the question's Add / edit translation editor to enter a Sindhi version manually."
                                    else "That language code is not supported by the on-device translator. Choose a supported language from the list."
                                }
                                val translated = translatedTemplateRows.toMutableMap()
                                searchableTemplates.distinctBy { templateTranslationKey(it) }.forEach { template ->
                                    if (!template.languageCode.equals(targetCode, ignoreCase = true)) {
                                        translated[templateTranslationKey(template)] = translateTemplate(template, targetCode)
                                    }
                                }
                                translatedTemplateRows = translated
                                notice = "Translated " + searchableTemplates.size + " visible templates to " + targetCode + ". These previews are translated on this device; source templates remain unchanged."
                            }
                        },
                        enabled = !busy && searchableTemplates.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("🌐 Translate all visible templates") }
                    searchableTemplates.distinctBy { templateTranslationKey(it) }.forEach { sourceTemplate ->
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
                                            val currentSubject = selectedSubject
                                            val targetSubject = if (currentSubject != null &&
                                                currentSubject.title.equals(template.subjectTitle, ignoreCase = true)) {
                                                currentSubject
                                            } else {
                                                repository.findOrCreateSubject(
                                                    familyId,
                                                    template.subjectTitle,
                                                    template.languageCode,
                                                    templateCountryCode
                                                )
                                            }
                                            subjects = repository.loadSubjects(familyId)
                                            selectedSubject = targetSubject
                                            questions = repository.loadQuestions(targetSubject.id)
                                            questionTranslations = repository.loadQuestionTranslations(questions.map { it.id })
                                            questionSolutions = repository.loadQuestionSolutions(questions.map { it.id })
                                            questionPictureUri = null; questionPictureBytes = null; questionPicturePreview = null; questionPictureError = null
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
                    val gradeForSubject = subjectGradeLevel.toIntOrNull()
                    val normalizedSubjectCountry = subjectCountryCode.trim().uppercase(Locale.ROOT)
                    val normalizedSubjectLanguage = subjectLanguage.trim().lowercase(Locale.ROOT)
                    val canSaveSubject = !busy && subjectTitle.isNotBlank() &&
                        normalizedSubjectLanguage.matches(Regex("[a-z][a-z0-9-]{1,15}")) &&
                        normalizedSubjectCountry.matches(Regex("[A-Z]{2}")) &&
                        gradeForSubject != null && gradeForSubject in 0..13
                    val saveSubjectAction: () -> Unit = {
                        execute {
                            val grade = subjectGradeLevel.toIntOrNull()
                                ?: throw IllegalArgumentException("Enter a grade from 0 to 13.")
                            require(grade in 0..13) { "Grade must be between Kindergarten (0) and 13." }
                            val title = subjectTitle.trim().take(160)
                            require(title.isNotBlank()) { "Enter a subject name." }
                            val language = subjectLanguage.trim().lowercase(Locale.ROOT)
                            require(language.matches(Regex("[a-z][a-z0-9-]{1,15}"))) {
                                "Use a valid language code such as en, ur or sd."
                            }
                            val country = subjectCountryCode.trim().uppercase(Locale.ROOT)
                            require(country.matches(Regex("[A-Z]{2}"))) { "Choose a valid country." }
                            val childAtSave = selectedChild
                            if (childAtSave != null) {
                                require(
                                    (country.equals(childAtSave.countryCode, ignoreCase = true) || country == "ZZ") &&
                                        grade == childAtSave.schoolGrade
                                ) {
                                    "For ${childAtSave.displayName}, use their country and Grade ${childAtSave.schoolGrade}. You can create other subjects from the general Subjects screen."
                                }
                            }
                            val currentSubjectId = editingSubjectId
                            val allSubjectsBeforeSave = repository.loadSubjects(familyId)
                            val normalizedTitle = title.substringBefore(" — Grade ").trim()
                            val existingMatchingSubject = if (currentSubjectId == null) {
                                allSubjectsBeforeSave.firstOrNull { existing ->
                                    existing.title.trim().substringBefore(" — Grade ").trim()
                                        .equals(normalizedTitle, ignoreCase = true) &&
                                        existing.languageCode.equals(language, ignoreCase = true) &&
                                        (existing.countryCode.equals(country, ignoreCase = true) ||
                                            existing.countryCode.equals("ZZ", ignoreCase = true)) &&
                                        existing.gradeLevel == grade
                                }
                            } else null
                            val savedSubject = when {
                                currentSubjectId != null ->
                                    repository.updateSubject(currentSubjectId, title, language, country, grade)
                                existingMatchingSubject != null -> existingMatchingSubject
                                else -> repository.addSubject(familyId, title, language, country, grade)
                            }
                            var selectionWarning: String? = null
                            if (childAtSave != null) {
                                try {
                                    val latestSelections = repository.loadChildSubjectSelections(familyId)
                                    val configuredSelection = latestSelections[childAtSave.id]
                                    if (configuredSelection != null && savedSubject.id !in configuredSelection) {
                                        val updatedSelection = configuredSelection + savedSubject.id
                                        repository.saveChildSubjectSelection(familyId, childAtSave.id, updatedSelection)
                                        childSubjectSelections = latestSelections + (childAtSave.id to updatedSelection)
                                    } else {
                                        childSubjectSelections = latestSelections
                                    }
                                } catch (selectionError: Exception) {
                                    selectionWarning = selectionError.message
                                        ?: "Could not update the selected-subject list for this child."
                                }
                            }
                            selectedSubject = savedSubject
                            questions = repository.loadQuestions(savedSubject.id)
                            questionTranslations = repository.loadQuestionTranslations(questions.map { it.id })
                            questionSolutions = repository.loadQuestionSolutions(questions.map { it.id })
                            subjects = repository.loadSubjects(familyId)
                            editingSubjectId = null
                            screen = AppScreen.QUESTIONS
                            notice = when {
                                selectionWarning != null ->
                                    "Subject saved and opened, but it could not be added to ${childAtSave?.displayName}'s selected subjects: $selectionWarning"
                                existingMatchingSubject != null ->
                                    "This subject already existed, so EduLock reused it instead of creating a duplicate. You can now manage its questions."
                                currentSubjectId == null ->
                                    "Subject saved successfully. Add questions individually from this subject page."
                                else ->
                                    "Subject changes saved successfully. Manage its questions here."
                            }
                        }
                    }
                    Button(
                        onClick = saveSubjectAction,
                        enabled = canSaveSubject,
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText(if (editingSubjectId == null) "Save subject" else "Save subject changes") }
                    Field(subjectTitle, { subjectTitle = it }, "Subject name")
                    OutlinedButton(onClick = {
                        countryPickerTarget = "subject"
                        countrySearch = ""
                        countryPickerOpen = true
                    }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                        AppUiText("🌍 Subject country: " + displayCountryName(subjectCountryCode))
                    }
                    Field(subjectGradeLevel, { subjectGradeLevel = it.filter(Char::isDigit).take(2) }, "Grade level (0 = Kindergarten, 1–13)", keyboardType = KeyboardType.Number)
                    AppUiText("Suggested subjects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    AppUiText(
                        "Tap a suggestion to fill the subject name and language. Existing matching subjects are reused instead of duplicated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    suggestedSubjectsForCountry(subjectCountryCode).chunked(2).forEach { suggestionRow ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestionRow.forEach { suggestion ->
                                val suggestionExists = subjects.any { existing ->
                                    existing.title.trim().substringBefore(" — Grade ").trim()
                                        .equals(suggestion.title, ignoreCase = true) &&
                                        existing.languageCode.equals(suggestion.languageCode, ignoreCase = true) &&
                                        (existing.countryCode.equals(subjectCountryCode, ignoreCase = true) ||
                                            existing.countryCode.equals("ZZ", ignoreCase = true)) &&
                                        existing.gradeLevel == (subjectGradeLevel.toIntOrNull() ?: -1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        subjectTitle = suggestion.title
                                        subjectLanguage = suggestion.languageCode
                                        notice = null
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp)
                                ) {
                                    AppUiText((if (suggestionExists) "✓ " else "＋ ") + suggestion.title, fontSize = 12.sp)
                                }
                            }
                            if (suggestionRow.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Field(subjectLanguage, { subjectLanguage = it.trim().take(16) }, "Language code (en, ur, sd, hi, or any language)")
                    Button(
                        onClick = saveSubjectAction,
                        enabled = canSaveSubject,
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText(if (editingSubjectId == null) "Save subject" else "Save subject changes") }
                }

                AppScreen.QUESTIONS -> ScreenFrame(selectedSubject?.title ?: "Questions", onBack = { screen = AppScreen.SUBJECTS }, notice = notice, busy = busy) {
                    val subject = selectedSubject
                    if (subject != null) {
                        val subjectQuestions = questions.filter { it.subjectId == subject.id }
                        val filteredSubjectQuestions = subjectQuestions.filter { question ->
                            questionSearchQuery.isBlank() ||
                                question.prompt.contains(questionSearchQuery, ignoreCase = true) ||
                                question.conceptKey.contains(questionSearchQuery, ignoreCase = true) ||
                                question.languageCode.contains(questionSearchQuery, ignoreCase = true) ||
                                question.questionType.contains(questionSearchQuery, ignoreCase = true) ||
                                question.options.any { it.contains(questionSearchQuery, ignoreCase = true) }
                        }
                        AppUiText("Create a question in " + subject.languageCode + " and give related questions the same concept tag. The tag lets Concept Bridge report understanding separately from language.")
                        AppUiText("Questions are added one at a time. Choosing a template only fills the form; it is not saved until you press Save question.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val eligibleRewardConcepts = subjectQuestions
                            .map { it.conceptKey.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .sorted()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                AppUiText("🏅 Concept group rewards", fontWeight = FontWeight.Bold)
                                AppUiText(
                                    "Extra minutes for a related-question group. The child earns the bonus only when every question with that concept tag in the assigned task is correct.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    val opening = !groupRewardsExpanded
                                    groupRewardsExpanded = opening
                                    if (opening) execute {
                                        val loadedGroups = repository.loadQuestionRewardGroups(subject.id)
                                        questionRewardGroups = loadedGroups
                                        questionGroupRewardEdits = loadedGroups.associate { it.conceptKey to it.rewardMinutes.toString() }
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.height(38.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp)
                            ) { AppUiText(if (groupRewardsExpanded) "Hide" else "Configure (" + eligibleRewardConcepts.size + ")", fontSize = 12.sp) }
                        }
                        if (groupRewardsExpanded) {
                            AppUiText(
                                "How it works: give each concept tag a bonus. Example: set 'place-value' to 5 minutes. In a task, if the child answers every place-value question correctly, EduLock adds 5 extra minutes once. If any of those questions is wrong, that group bonus is not awarded. This is not 5 minutes per question.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = groupRewardSearchQuery,
                                onValueChange = { groupRewardSearchQuery = it.take(120) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { AppUiText("Search concept tags") },
                                placeholder = { AppUiText("Find a question group, e.g. place-value") }
                            )
                            val filteredRewardConcepts = eligibleRewardConcepts.filter {
                                it.contains(groupRewardSearchQuery, ignoreCase = true)
                            }
                            if (eligibleRewardConcepts.isEmpty()) {
                                AppUiText("No concept tags are available yet. Add questions with a concept tag to configure group bonuses.")
                            } else if (filteredRewardConcepts.isEmpty()) {
                                AppUiText("No concept tags match '${groupRewardSearchQuery}'.")
                            } else {
                                filteredRewardConcepts.forEach { concept ->
                                    val savedGroupMinutes = questionRewardGroups.firstOrNull { it.conceptKey == concept }?.rewardMinutes ?: 0
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                AppUiText(concept, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                                AppUiText("Group bonus · " + subjectQuestions.count { it.conceptKey.trim() == concept } + (if (subjectQuestions.count { it.conceptKey.trim() == concept } == 1) " question" else " questions"),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.labelSmall)
                                            }
                                            OutlinedTextField(
                                                value = questionGroupRewardEdits[concept] ?: savedGroupMinutes.toString(),
                                                onValueChange = { value ->
                                                    questionGroupRewardEdits = questionGroupRewardEdits + (concept to value.filter(Char::isDigit).take(4))
                                                },
                                                modifier = Modifier.width(100.dp).height(58.dp),
                                                singleLine = true,
                                                label = { AppUiText("Minutes", fontSize = 11.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            execute {
                                                val value = (questionGroupRewardEdits[concept] ?: savedGroupMinutes.toString()).toIntOrNull()
                                                    ?: throw IllegalArgumentException("Enter a group bonus from 0 to 1440 minutes.")
                                                require(value in 0..1440) { "Group bonus must be between 0 and 1440 minutes." }
                                                repository.saveQuestionRewardGroup(subject.id, concept, value)
                                                questionRewardGroups = repository.loadQuestionRewardGroups(subject.id)
                                                questionGroupRewardEdits = questionGroupRewardEdits + (concept to value.toString())
                                                notice = "Group bonus saved for " + concept + "."
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { AppUiText("Save group bonus") }
                                }
                            }
                        }
                        Field(
                            bulkTranslationLanguage,
                            { bulkTranslationLanguage = it.trim().lowercase(Locale.ROOT).take(16) },
                            "Translate every question to language code (e.g. en, ur, ar, hi, fr, es)"
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
                                    require(targetCode != "sd" && TranslateLanguage.fromLanguageTag(targetCode) != null) {
                                        if (targetCode == "sd") "Automatic on-device translation to Sindhi is not supported by Google's model. Choose Sindhi in the question's Add / edit translation editor to enter a Sindhi version manually."
                                        else "That language code is not supported by the on-device translator. Choose a supported language from the list."
                                    }
                                    val answerKeys = repository.loadQuestionAnswerKeys(subjectQuestions.map { it.id })
                                    var saved = 0
                                    var skipped = 0
                                    subjectQuestions.forEach { question ->
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
                            enabled = !busy && subjectQuestions.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("🌐 Auto-translate all " + subjectQuestions.size + " questions") }
                        AppUiText("Automatic translation uses Google's supported on-device languages. Sindhi (sd) is available for manually entered translations; Google's on-device model does not currently provide automatic Sindhi translation.")
                        AppUiText(
                            subjectQuestions.size.toString() + " saved questions in this subject",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = {
                                val normalizedTitle = subject.title.substringBefore(" — Grade ").trim()
                                templateCategory = LearningTemplates.all.firstOrNull {
                                    it.subjectTitle.equals(normalizedTitle, ignoreCase = true) &&
                                        it.countryCode in setOf("ZZ", subject.countryCode)
                                }?.category ?: LearningTemplates.all.firstOrNull {
                                    it.subjectTitle.equals(normalizedTitle, ignoreCase = true)
                                }?.category ?: "Mathematics"
                                screen = AppScreen.LEARNING_TEMPLATES
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("✨ Templates & 4D questions") }
                        Button(onClick = {
                            questionPrompt = ""; questionOptions = ""; questionAnswer = ""; questionType = "multiple_choice"
                            questionLanguage = subject.languageCode
                            questionConceptKey = ""
                            questionSolution = ""
                            question3DModel = "none"
                            questionPictureUri = null; questionPictureBytes = null; questionPicturePreview = null; questionPictureError = null
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
                                questionPictureUri = null; questionPictureBytes = null; questionPicturePreview = null; questionPictureError = null
                                questionType = "multiple_choice"
                                screen = AppScreen.ADD_QUESTION
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("✨ Create a custom interactive 4D question") }
                        OutlinedTextField(
                            value = questionSearchQuery,
                            onValueChange = { questionSearchQuery = it.take(200) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { AppUiText("Search questions") },
                            placeholder = { AppUiText("Find by prompt, concept tag, language or type") }
                        )
                        if (subjectQuestions.isEmpty()) {
                            AppUiText("No questions yet.")
                        } else if (filteredSubjectQuestions.isEmpty()) {
                            AppUiText("No questions match '${questionSearchQuery}'.")
                        }
                        filteredSubjectQuestions.forEachIndexed { index, question ->
                            CompositionLocalProvider(LocalLayoutDirection provides directionFor(question.languageCode)) {
                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        AppUiText((index + 1).toString() + ". " + question.prompt, fontWeight = FontWeight.SemiBold)
                                        Field(
                                            questionRewardEdits[question.id] ?: (question.rewardMinutes?.toString() ?: ""),
                                            { value -> questionRewardEdits = questionRewardEdits + (question.id to value.filter(Char::isDigit).take(4)) },
                                            "Reward minutes (blank = use task default; 0–1440)",
                                            keyboardType = KeyboardType.Number
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                execute {
                                                    val rawReward = (questionRewardEdits[question.id]
                                                        ?: question.rewardMinutes?.toString().orEmpty()).trim()
                                                    val parsed: Int? = if (rawReward.isBlank()) {
                                                        null
                                                    } else {
                                                        rawReward.toIntOrNull()
                                                            ?: throw IllegalArgumentException("Enter a whole-number reward from 0 to 1440, or leave it blank to use the task default.")
                                                    }
                                                    require(parsed == null || parsed in 0..1440) {
                                                        "Question reward must be between 0 and 1440 minutes."
                                                    }
                                                    repository.setQuestionReward(question.id, parsed)
                                                    questions = repository.loadQuestions(subject.id)
                                                    questionRewardEdits = questionRewardEdits - question.id
                                                    notice = if (parsed == null) {
                                                        "Question reward cleared. This question will use the task default."
                                                    } else {
                                                        "Question reward saved: " + parsed + " minutes."
                                                    }
                                                }
                                            },
                                            enabled = !busy,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { AppUiText("Save question reward") }
                                        val savedTranslationsForQuestion = questionTranslations[question.id].orEmpty()
                                        if (savedTranslationsForQuestion.isNotEmpty()) {
                                            savedTranslationsForQuestion.forEach { savedTranslation ->
                                                CompositionLocalProvider(LocalLayoutDirection provides directionFor(savedTranslation.languageCode)) {
                                                    Surface(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = MaterialTheme.colorScheme.surface,
                                                        tonalElevation = 1.dp
                                                    ) {
                                                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Row(
                                                                Modifier.fillMaxWidth(),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Column(Modifier.weight(1f)) {
                                                                    AppUiText(
                                                                        "Translation • " + languageLabel(savedTranslation.languageCode) + " (" + savedTranslation.languageCode + ")",
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.primary
                                                                    )
                                                                }
                                                                TextButton(
                                                                    onClick = {
                                                                        questionEditExpandedId = null
                                                                        questionEditDrafts = questionEditDrafts - question.id
                                                                        translationEditorQuestionId = question.id
                                                                        translationLanguage = savedTranslation.languageCode
                                                                        translationPrompt = savedTranslation.prompt
                                                                        translationOptions = if (question.questionType == "matching") "" else savedTranslation.options.joinToString("\n")
                                                                        translationAcceptedAnswers = ""
                                                                    },
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)
                                                                ) { AppUiText("Edit", fontSize = 12.sp) }
                                                            }
                                                            AppUiText(savedTranslation.prompt, fontWeight = FontWeight.SemiBold)
                                                            if (savedTranslation.options.isNotEmpty()) {
                                                                AppUiText(savedTranslation.options.mapIndexed { optionIndex, option ->
                                                                    (optionIndex + 1).toString() + ". " + option
                                                                }.joinToString("\\n"))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        AppUiText("Type: " + readableQuestionType(question.questionType))
                                        AppUiText("Level: Grade " + question.gradeLevel + " • Suitable ages " + question.ageMin + "–" + question.ageMax)
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
                                        AppUiText(
                                            if (savedTranslationsForQuestion.isEmpty()) "Translations: none saved yet"
                                            else "Translations available above: " + savedTranslationsForQuestion.joinToString(", ") { it.languageCode },
                                            color = if (savedTranslationsForQuestion.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                        )
                                        questionSolutions[question.id]?.let { solution ->
                                            AppUiText("Worked solution", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            AppUiText(solution)
                                        }
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    if (questionEditExpandedId == question.id) {
                                                        questionEditExpandedId = null
                                                        questionEditDrafts = questionEditDrafts - question.id
                                                    } else execute {
                                                        val answerKey = repository.loadQuestionAnswerKeys(listOf(question.id))[question.id].orEmpty()
                                                        val editableOptions = if (question.questionType == "matching") {
                                                            answerKey.split(";").mapNotNull { pair ->
                                                                val parts = pair.split("=", limit = 2)
                                                                if (parts.size == 2) parts[0] + "|" + parts[1] else null
                                                            }.joinToString("\n")
                                                        } else question.options.joinToString("\n")
                                                        questionEditDrafts = questionEditDrafts + (question.id to QuestionEditDraft(
                                                            prompt = question.prompt,
                                                            options = editableOptions,
                                                            answer = if (question.questionType == "matching") "" else answerKey,
                                                            languageCode = question.languageCode,
                                                            conceptKey = question.conceptKey,
                                                            solution = questionSolutions[question.id].orEmpty()
                                                        ))
                                                        questionEditExpandedId = question.id
                                                        translationEditorQuestionId = null
                                                    }
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.weight(0.8f),
                                                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp)
                                            ) { AppUiText(if (questionEditExpandedId == question.id) "Close edit" else "✏ Edit", fontSize = 12.sp) }
                                            OutlinedButton(
                                                onClick = {
                                                    if (translationEditorQuestionId == question.id) {
                                                        translationEditorQuestionId = null
                                                    } else {
                                                        questionEditExpandedId = null
                                                        questionEditDrafts = questionEditDrafts - question.id
                                                        translationEditorQuestionId = question.id
                                                        val existingTranslation = savedTranslationsForQuestion.firstOrNull()
                                                        translationLanguage = existingTranslation?.languageCode ?: when (question.languageCode.lowercase()) {
                                                            "en" -> "ur"
                                                            "ur" -> "sd"
                                                            else -> "en"
                                                        }
                                                        translationPrompt = existingTranslation?.prompt.orEmpty()
                                                        translationOptions = if (question.questionType == "matching") "" else existingTranslation?.options?.joinToString("\n").orEmpty()
                                                        // Accepted answer keys are intentionally never fetched into the child-readable client.
                                                        // Parents re-enter the accepted answer(s) when creating or updating a translation.
                                                        translationAcceptedAnswers = ""
                                                    }
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 5.dp)
                                            ) {
                                                AppUiText(
                                                    if (translationEditorQuestionId == question.id) "Close translation editor" else "🌐 Add / edit translation inline",
                                                    fontSize = 12.sp
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { questionDeleteConfirmation = question },
                                                enabled = !busy,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                                            ) { AppUiText("🗑 Remove", fontSize = 12.sp) }
                                        }
                                        val questionEditDraft = questionEditDrafts[question.id]
                                        if (questionEditExpandedId == question.id && questionEditDraft != null) {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                            ) {
                                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                                    AppUiText("Edit question", fontWeight = FontWeight.Bold)
                                                    Field(
                                                        questionEditDraft.prompt,
                                                        { value -> questionEditDrafts = questionEditDrafts + (question.id to questionEditDraft.copy(prompt = value)) },
                                                        "Question prompt"
                                                    )
                                                    if (question.questionType == "multiple_choice" || question.questionType == "matching" || question.questionType == "picture") {
                                                        Field(
                                                            questionEditDraft.options,
                                                            { value -> questionEditDrafts = questionEditDrafts + (question.id to questionEditDraft.copy(options = value)) },
                                                            if (question.questionType == "matching") "Matching pairs (one per line: left|right)" else "Options (one per line)"
                                                        )
                                                    }
                                                    if (question.questionType != "matching") {
                                                        Field(
                                                            questionEditDraft.answer,
                                                            { value -> questionEditDrafts = questionEditDrafts + (question.id to questionEditDraft.copy(answer = value)) },
                                                            "Canonical correct answer (private)"
                                                        )
                                                    } else {
                                                        AppUiText("Matching answers are rebuilt from the pairs above.", style = MaterialTheme.typography.bodySmall)
                                                    }
                                                    Field(
                                                        questionEditDraft.languageCode,
                                                        { value -> questionEditDrafts = questionEditDrafts + (question.id to questionEditDraft.copy(languageCode = value.trim().lowercase(Locale.ROOT).take(16))) },
                                                        "Question language code"
                                                    )
                                                    Field(
                                                        questionEditDraft.conceptKey,
                                                        { value -> questionEditDrafts = questionEditDrafts + (question.id to questionEditDraft.copy(conceptKey = value)) },
                                                        "Concept tag (same tag groups related questions)"
                                                    )
                                                    Field(
                                                        questionEditDraft.solution,
                                                        { value -> questionEditDrafts = questionEditDrafts + (question.id to questionEditDraft.copy(solution = value)) },
                                                        "Parent-only worked solution (blank removes it)"
                                                    )
                                                    Button(
                                                        onClick = {
                                                            execute {
                                                                val draft = questionEditDrafts[question.id]
                                                                    ?: throw IllegalStateException("The question edit draft is missing.")
                                                                repository.updateQuestion(
                                                                    question,
                                                                    draft.prompt,
                                                                    draft.options.lines().map { it.trim() }.filter { it.isNotEmpty() },
                                                                    draft.answer,
                                                                    draft.languageCode,
                                                                    draft.conceptKey,
                                                                    draft.solution
                                                                )
                                                                val refreshedQuestions = repository.loadQuestions(subject.id)
                                                                questions = refreshedQuestions
                                                                questionTranslations = repository.loadQuestionTranslations(refreshedQuestions.map { it.id })
                                                                questionSolutions = repository.loadQuestionSolutions(refreshedQuestions.map { it.id })
                                                                questionRewardGroups = repository.loadQuestionRewardGroups(subject.id)
                                                                questionGroupRewardEdits = questionRewardGroups.associate { it.conceptKey to it.rewardMinutes.toString() }
                                                                questionEditExpandedId = null
                                                                questionEditDrafts = questionEditDrafts - question.id
                                                                notice = "Question updated. Its original ID and existing progress references were preserved."
                                                            }
                                                        },
                                                        enabled = !busy && questionEditDraft.prompt.isNotBlank() &&
                                                            questionEditDraft.languageCode.isNotBlank() && questionEditDraft.conceptKey.isNotBlank() &&
                                                            (question.questionType == "matching" || questionEditDraft.answer.isNotBlank()),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) { AppUiText("Save question changes") }
                                                }
                                            }
                                        }
                                        if (translationEditorQuestionId == question.id) {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                            ) {
                                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                                    AppUiText("Edit translation on this page", fontWeight = FontWeight.Bold)
                                                    AppUiText(
                                                        "This changes the language version only; it does not change the original question. For privacy, re-enter the accepted correct answer(s) before saving.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Field(
                                                        translationLanguage,
                                                        { translationLanguage = it.trim().lowercase(Locale.ROOT).take(16) },
                                                        "Target language code (en, ur, sd, etc.)"
                                                    )
                                                    CompositionLocalProvider(LocalLayoutDirection provides directionFor(translationLanguage)) {
                                                        Field(translationPrompt, { translationPrompt = it }, "Translated question prompt")
                                                        if (question.questionType == "multiple_choice" || question.questionType == "true_false") {
                                                            Field(translationOptions, { translationOptions = it }, "Translated options (one per line)")
                                                        } else if (question.questionType == "matching") {
                                                            Field(translationOptions, { translationOptions = it }, "Translated matching pairs (one per line: left|right)")
                                                        }
                                                        Field(translationAcceptedAnswers, { translationAcceptedAnswers = it }, "Accepted correct answers (one per line)")
                                                    }
                                                    Button(
                                                        onClick = {
                                                            execute {
                                                                val targetCode = translationLanguage.trim().lowercase(Locale.ROOT)
                                                                val opts = translationOptions.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                                                val accepted = translationAcceptedAnswers.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                                                repository.saveQuestionTranslation(question, targetCode, translationPrompt, opts, accepted)
                                                                questionTranslations = repository.loadQuestionTranslations(questions.map { it.id })
                                                                translationEditorQuestionId = null
                                                                translationAcceptedAnswers = ""
                                                                notice = "Translation saved. The original question and its progress remain unchanged."
                                                            }
                                                        },
                                                        enabled = !busy && translationPrompt.isNotBlank() && translationAcceptedAnswers.isNotBlank() &&
                                                            !translationLanguage.equals(question.languageCode, ignoreCase = true),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) { AppUiText("Save translation") }
                                                }
                                            }
                                        }
                                        if (questionDeleteConfirmation?.id == question.id) {
                                            AlertDialog(
                                                onDismissRequest = { questionDeleteConfirmation = null },
                                                title = { AppUiText("Remove this question?") },
                                                text = {
                                                    AppUiText(
                                                        "This permanently deletes the question, its translations and worked solution. Historical answer details linked to this question may also be removed. This action cannot be undone."
                                                    )
                                                },
                                                confirmButton = {
                                                    TextButton(onClick = {
                                                        execute {
                                                            repository.deleteQuestion(question.id)
                                                            val refreshedQuestions = repository.loadQuestions(subject.id)
                                                            questions = refreshedQuestions
                                                            questionTranslations = repository.loadQuestionTranslations(refreshedQuestions.map { it.id })
                                                            questionSolutions = repository.loadQuestionSolutions(refreshedQuestions.map { it.id })
                                                            questionRewardGroups = repository.loadQuestionRewardGroups(subject.id)
                                                            questionGroupRewardEdits = questionRewardGroups.associate { it.conceptKey to it.rewardMinutes.toString() }
                                                            questionRewardEdits = questionRewardEdits - question.id
                                                            if (translationEditorQuestionId == question.id) translationEditorQuestionId = null
                                                            if (expandedFourDQuestionId == question.id) expandedFourDQuestionId = null
                                                            questionDeleteConfirmation = null
                                                            notice = "Question removed. Remaining questions and settings are unchanged."
                                                        }
                                                    }) { AppUiText("Delete permanently", color = MaterialTheme.colorScheme.error) }
                                                },
                                                dismissButton = {
                                                    TextButton(onClick = { questionDeleteConfirmation = null }) { AppUiText("Cancel") }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AppScreen.ADD_QUESTION -> ScreenFrame("Add question", onBack = { screen = AppScreen.QUESTIONS }, notice = notice, busy = busy) {
                    val lang = questionLanguage
                    val currentQuestionOptions = questionOptions.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    val answerFieldsValid = when (questionType) {
                        "multiple_choice" -> questionAnswer.isNotBlank() &&
                            currentQuestionOptions.size >= 2 &&
                            currentQuestionOptions.any { it.equals(questionAnswer.trim(), ignoreCase = true) }
                        "true_false" -> questionAnswer.trim().equals("true", ignoreCase = true) ||
                            questionAnswer.trim().equals("false", ignoreCase = true)
                        "matching" -> {
                            val pairs = currentQuestionOptions.map { it.split("|", limit = 2) }
                            pairs.size >= 2 &&
                                pairs.all { it.size == 2 && it[0].isNotBlank() && it[1].isNotBlank() } &&
                                pairs.map { it[0].trim() }.distinct().size == pairs.size &&
                                pairs.map { it[1].trim() }.distinct().size == pairs.size
                        }
                        "picture" -> questionAnswer.isNotBlank() && questionPictureBytes != null
                        else -> questionAnswer.isNotBlank()
                    }
                    val canSaveQuestion = !busy && selectedSubject != null &&
                        questionPrompt.isNotBlank() && questionConceptKey.isNotBlank() &&
                        questionLanguage.trim().matches(Regex("[A-Za-z][A-Za-z0-9-]{1,15}")) &&
                        answerFieldsValid
                    val saveQuestionAction: () -> Unit = {
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
                            val pictureBytes = if (questionType == "picture") {
                                questionPictureBytes ?: throw IllegalArgumentException("Choose a picture before saving this picture question.")
                            } else null
                            val newQuestionId = repository.addQuestion(
                                subject.id, questionPrompt, questionType, options, canonicalAnswer,
                                questionLanguage, savedConceptKey, questionSolution
                            )
                            if (pictureBytes != null) {
                                try {
                                    repository.uploadQuestionImage(subject.familyId, newQuestionId, pictureBytes)
                                } catch (error: Exception) {
                                    questionPictureError = error.message ?: "Picture upload failed."
                                    try {
                                        repository.deleteQuestion(newQuestionId)
                                    } catch (_: Exception) {
                                        // Keep the original upload error visible; the cleanup is best-effort.
                                    }
                                    throw error
                                }
                            }
                            val refreshedQuestions = repository.loadQuestions(subject.id)
                            questions = refreshedQuestions
                            questionTranslations = repository.loadQuestionTranslations(refreshedQuestions.map { it.id })
                            questionSolutions = repository.loadQuestionSolutions(refreshedQuestions.map { it.id })
                            questionSolution = ""
                            screen = AppScreen.QUESTIONS
                            notice = "Question and parent-only worked solution saved. The solution is not sent to the child."
                        }
                    }
                    AppUiText("Fill in the question below, then use Save question here or at the bottom. For multiple choice, the correct answer must match one of the options.")
                    Button(
                        onClick = saveQuestionAction,
                        enabled = canSaveQuestion,
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("Save question") }
                    AppUiText("Question language: " + lang + ". Type in Urdu, Sindhi, English or another Unicode language.")
                    AppUiText("Optional interactive 4D model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    AppUiText("Choose a model to attach a touch-friendly learning visual to your question.")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val subjectTitleForModel = selectedSubject?.title.orEmpty()
                            .substringBefore(" — Grade ").trim().lowercase(Locale.ROOT)
                        val subjectModels = when {
                            subjectTitleForModel.contains("math") -> listOf("geometry" to "📐 4D exploded shapes")
                            subjectTitleForModel.contains("chem") -> listOf("atom" to "⚛ Atom", "molecule" to "🧪 Molecule")
                            subjectTitleForModel.contains("physics") -> listOf("force" to "🏀 Forces", "circuit" to "⚡ Circuit")
                            subjectTitleForModel.contains("biology") -> listOf("cell" to "🧬 Cell")
                            subjectTitleForModel.contains("geography") || subjectTitleForModel.contains("earth") -> listOf("earth" to "🌍 Earth", "water-cycle" to "🌧 Water cycle")
                            subjectTitleForModel.contains("science") -> listOf("atom" to "⚛ Atom", "molecule" to "🧪 Molecule", "cell" to "🧬 Cell", "water-cycle" to "🌧 Water cycle", "earth" to "🌍 Earth")
                            else -> emptyList()
                        }
                        val questionModelOptions = listOf("none" to "No model") + subjectModels
                        if (question3DModel != "none" && subjectModels.none { it.first == question3DModel }) {
                            question3DModel = "none"
                        }
                        questionModelOptions.chunked(2).forEach { group ->
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
                    Button(
                        onClick = saveQuestionAction,
                        enabled = canSaveQuestion,
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("Save question") }
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
                        OutlinedButton(
                            onClick = {
                                languagePickerTarget = "question-translation"
                                languageSearch = ""
                                languagePickerOpen = true
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("🌐 Choose target language: " + languageLabel(translationLanguage)) }
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
                                subjects = repository.loadSubjects(familyId)
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
                        "Learner level: Grade " + (selectedChild?.schoolGrade ?: 1) +
                            " • Age " + (selectedChild?.ageYears ?: 8) + " • " + displayCountryName(selectedTaskCountry) +
                            " • Religious education: " + studyTrackLabel(selectedChildTrack),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    AppUiText("Choose one or more subjects. You can combine questions from the selected subject banks in one task.")
                    val savedChildSubjectIds = selectedChild?.id?.let { childSubjectSelections[it] }
                    val assignableSubjects = subjects.filter { subject ->
                        val allowedByChildSelection = savedChildSubjectIds == null || subject.id in savedChildSubjectIds
                        val religionTrack = religionTrackForSubject(subject.title)
                        val isCountrySubject = subject.countryCode.equals(selectedTaskCountry, ignoreCase = true) ||
                            subject.countryCode.equals("ZZ", ignoreCase = true)
                        allowedByChildSelection &&
                            isCountrySubject &&
                            subject.gradeLevel == selectedChild?.schoolGrade &&
                            (religionTrack == null || (selectedChildTrack != "none" && religionTrack == selectedChildTrack))
                    }
                    val selectedTaskSubjects = assignableSubjects.filter { it.id in selectedTaskSubjectIds }
                    AppUiText("Select questions below for an exact task, or leave them unselected to let EduLock pick the requested number automatically.")
                    if (assignableSubjects.isEmpty()) {
                        AppUiText("No subject is available for this child's Grade " + (selectedChild?.schoolGrade ?: 1) + " and age " + (selectedChild?.ageYears ?: 8) + ". Create a subject for the correct class or import matching curriculum questions.")
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
                        OutlinedButton(
                            onClick = {
                                val nextIds = if (subject.id in selectedTaskSubjectIds) {
                                    selectedTaskSubjectIds - subject.id
                                } else {
                                    selectedTaskSubjectIds + subject.id
                                }
                                selectedTaskSubjectIds = nextIds
                                execute {
                                    val latestSubjects = assignableSubjects.filter { it.id in nextIds }
                                    val pool = latestSubjects.flatMap { repository.loadQuestions(it.id, 500) }
                                        .filter { question ->
                                            question.gradeLevel == selectedChild?.schoolGrade &&
                                                selectedChild?.ageYears?.let { it in question.ageMin..question.ageMax } == true
                                        }
                                        .distinctBy { it.id }
                                    taskQuestionChoices = pool
                                    selectedTaskQuestionIds = selectedTaskQuestionIds.intersect(pool.map { it.id }.toSet())
                                    if (selectedTaskQuestionIds.isNotEmpty()) taskQuestionCount = selectedTaskQuestionIds.size.toString()
                                    notice = if (nextIds.isEmpty()) "No subjects selected. Choose at least one subject." else
                                        "${nextIds.size} subject(s) selected and ${pool.size} matching questions available."
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppUiText((if (subject.id in selectedTaskSubjectIds) "✓ Remove • " else "＋ Add • ") + subject.title + " • Grade " + subject.gradeLevel + " (" + subject.languageCode + ")")
                        }
                    }
                    if (selectedTaskSubjectIds.isNotEmpty()) {
                        OutlinedTextField(
                            value = taskQuestionSearchQuery,
                            onValueChange = { taskQuestionSearchQuery = it.take(200) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { AppUiText("Search questions to include") },
                            placeholder = { AppUiText("Find by question text, concept, or language") }
                        )
                        val visibleTaskQuestions = taskQuestionChoices.filter { question ->
                            taskQuestionSearchQuery.isBlank() ||
                                question.prompt.contains(taskQuestionSearchQuery, ignoreCase = true) ||
                                question.conceptKey.contains(taskQuestionSearchQuery, ignoreCase = true) ||
                                question.languageCode.contains(taskQuestionSearchQuery, ignoreCase = true)
                        }
                        AppUiText(
                            if (selectedTaskQuestionIds.isEmpty())
                                "${taskQuestionChoices.size} matching questions. Select any questions to combine them in one task, or use automatic selection."
                            else "${selectedTaskQuestionIds.size} questions selected for this task.",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val count = (taskQuestionCount.toIntOrNull() ?: 4).coerceIn(1, 20)
                                    selectedTaskQuestionIds = visibleTaskQuestions.shuffled().take(count).map { it.id }.toSet()
                                    taskQuestionCount = selectedTaskQuestionIds.size.toString()
                                },
                                enabled = !busy && visibleTaskQuestions.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) { AppUiText("Auto-select questions") }
                            OutlinedButton(
                                onClick = {
                                    selectedTaskQuestionIds = emptySet()
                                },
                                enabled = selectedTaskQuestionIds.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) { AppUiText("Clear selection") }
                        }
                        visibleTaskQuestions.take(200).forEach { question ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    selectedTaskQuestionIds = if (question.id in selectedTaskQuestionIds) {
                                        selectedTaskQuestionIds - question.id
                                    } else selectedTaskQuestionIds + question.id
                                    if (selectedTaskQuestionIds.isNotEmpty()) taskQuestionCount = selectedTaskQuestionIds.size.toString()
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = question.id in selectedTaskQuestionIds,
                                    onCheckedChange = { checked ->
                                        selectedTaskQuestionIds = if (checked) selectedTaskQuestionIds + question.id else selectedTaskQuestionIds - question.id
                                        if (selectedTaskQuestionIds.isNotEmpty()) taskQuestionCount = selectedTaskQuestionIds.size.toString()
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    AppUiText(question.prompt, style = MaterialTheme.typography.bodyMedium)
                                    AppUiText(
                                        question.conceptKey + " • " + question.languageCode.uppercase() + " • " +
                                            (if (question.gradeLevel == 0) "Kindergarten" else "Grade ${question.gradeLevel}"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (taskQuestionChoices.size > visibleTaskQuestions.size) {
                            AppUiText("Showing matching results. Use search to narrow the list.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Field(taskTitle, { taskTitle = it }, "Task title")
                    Field(
                        if (selectedTaskQuestionIds.isNotEmpty()) selectedTaskQuestionIds.size.toString() else taskQuestionCount,
                        { value -> if (selectedTaskQuestionIds.isEmpty()) taskQuestionCount = value.filter(Char::isDigit).take(3) },
                        "Questions in task (1–500; manual selection overrides this)",
                        keyboardType = KeyboardType.Number
                    )
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
                                AppUiText((if (taskRewardPackageName == app.packageName && taskRewardGroupId.isBlank()) "✓ " else "") + compactAppLabel(app.displayName, 22) + " only")
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
                            if (chosenSubjects.isEmpty()) throw IllegalArgumentException("Choose at least one subject for this task.")
                            val manualQuestionIds = selectedTaskQuestionIds.toList()
                            val requestedQuestionCount = if (manualQuestionIds.isNotEmpty()) manualQuestionIds.size
                                else taskQuestionCount.toIntOrNull() ?: 0
                            val totalTimeLimit = taskTimeLimitSeconds.toIntOrNull() ?: 0
                            require(totalTimeLimit == 0 || totalTimeLimit in 10..3600) {
                                "Total task time must be 0 (off) or between 10 and 3600 seconds."
                            }
                            val perQuestionTimeLimit = taskQuestionTimeLimitSeconds.toIntOrNull() ?: 0
                            require(perQuestionTimeLimit == 0 || perQuestionTimeLimit in 5..600) {
                                "Question time must be 0 (off) or between 5 and 600 seconds."
                            }
                            repository.addTask(
                                familyId, child, chosenSubjects, taskTitle,
                                requestedQuestionCount,
                                taskPassPercent.toIntOrNull() ?: -1,
                                taskRewardMinutes.toIntOrNull() ?: -1,
                                taskDailyCapMinutes.toIntOrNull() ?: -1,
                                taskRewardPackageName.takeIf { it.isNotBlank() },
                                taskRewardGroupId.takeIf { it.isNotBlank() },
                                totalTimeLimit.takeIf { it in 10..3600 },
                                taskRewardToBank,
                                perQuestionTimeLimit.takeIf { it in 5..600 },
                                manualQuestionIds
                            )
                            tasks = emptyList()
                            screen = AppScreen.PARENT_HOME
                            taskTitle = ""
                            taskRewardPackageName = ""
                            taskRewardGroupId = ""
                            taskRewardToBank = true
                            selectedTaskSubjectIds = emptySet()
                            taskQuestionChoices = emptyList()
                            selectedTaskQuestionIds = emptySet()
                            taskQuestionSearchQuery = ""
                            notice = "Task assigned to " + child.displayName + " with " + requestedQuestionCount + " question(s). Correct answers earn the configured reward, within task and daily caps."
                        }
                    }, enabled = !busy && selectedChild != null && selectedTaskSubjects.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { AppUiText("Assign task") }
                }

                AppScreen.PRACTICAL_TASKS -> ScreenFrame(
                    if (session?.accountRole == "parent") "Manage practical learning tasks" else "Practical learning • separate from written tasks",
                    onBack = { screen = if (session?.accountRole == "parent") AppScreen.PARENT_HOME else AppScreen.CHILD_HOME },
                    notice = notice,
                    busy = busy
                ) {
                    val parentViewing = session?.accountRole == "parent"
                    val targetChild = selectedChild ?: children.firstOrNull()
                    if (parentViewing) {
                        AppUiText("Save hands-on, physical, life-skill or creative tasks here. These activities stay separate from written questions.")
                        AppUiText("Child profile", fontWeight = FontWeight.SemiBold)
                        children.forEach { child ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = targetChild?.id == child.id,
                                    onClick = {
                                        selectedChild = child
                                        execute { practicalTasks = repository.loadPracticalTasksForParent(familyId, child.id) }
                                    }
                                )
                                AppUiText(child.displayName + " • Grade " + child.schoolGrade)
                            }
                        }
                        OutlinedTextField(
                            value = practicalTaskTitle,
                            onValueChange = { practicalTaskTitle = it.take(160) },
                            label = { AppUiText("Activity title") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = practicalTaskInstructions,
                            onValueChange = { practicalTaskInstructions = it.take(5000) },
                            label = { AppUiText("Steps / instructions") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                        OutlinedTextField(
                            value = practicalTaskMaterials,
                            onValueChange = { practicalTaskMaterials = it.take(1000) },
                            label = { AppUiText("Materials (comma-separated)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = practicalTaskSafety,
                            onValueChange = { practicalTaskSafety = it.take(1000) },
                            label = { AppUiText("Safety notes (required for physical tasks)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                AppUiText("Activity type")
                                listOf("practical","physical","life_skill","creative").forEach { kind ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = practicalTaskKind == kind, onClick = { practicalTaskKind = kind })
                                        AppUiText(kind.replace('_',' ').replaceFirstChar { it.uppercase() })
                                    }
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = practicalTaskMinutes,
                                    onValueChange = { practicalTaskMinutes = it.filter(Char::isDigit).take(3) },
                                    label = { AppUiText("Minutes") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                AppUiText("Ages 3–19 • choose age fit per child")
                            }
                        }
                        Button(
                            onClick = {
                                execute {
                                    val child = targetChild ?: throw IllegalStateException("Add a child profile first.")
                                    val minutes = practicalTaskMinutes.toIntOrNull()?.coerceIn(1,180)
                                        ?: throw IllegalArgumentException("Enter a duration from 1 to 180 minutes.")
                                    if (practicalTaskKind == "physical" && practicalTaskSafety.isBlank()) {
                                        throw IllegalArgumentException("Add safety notes for physical activities.")
                                    }
                                    val saved = repository.createPracticalTask(
                                        familyId = familyId,
                                        childId = child.id,
                                        title = practicalTaskTitle,
                                        instructions = practicalTaskInstructions,
                                        materialsJson = if (practicalTaskMaterials.isBlank()) "[]" else
                                            org.json.JSONArray(practicalTaskMaterials.split(',').map { it.trim() }.filter { it.isNotBlank() }).toString(),
                                        safetyNotes = practicalTaskSafety,
                                        activityKind = practicalTaskKind,
                                        ageMin = child.ageYears.coerceIn(3,19),
                                        ageMax = child.ageYears.coerceIn(3,19),
                                        expectedMinutes = minutes
                                    )
                                    practicalTasks = listOf(saved) + practicalTasks.filterNot { it.id == saved.id }
                                    practicalTaskTitle = ""
                                    practicalTaskInstructions = ""
                                    practicalTaskMaterials = ""
                                    practicalTaskSafety = ""
                                    notice = "Practical task saved to " + child.displayName + "'s profile."
                                }
                            },
                            enabled = !busy && targetChild != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("Save practical task") }
                        OutlinedButton(
                            onClick = {
                                execute {
                                    val child = targetChild ?: throw IllegalStateException("Select a child first.")
                                    practicalTasks = repository.loadPracticalTasksForParent(familyId, child.id)
                                    practicalSubmissions = repository.loadPracticalSubmissionsForParent(familyId, child.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("Refresh saved practical tasks & submissions") }
                        AppUiText("Completed practical tasks • parent review", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (practicalSubmissions.isEmpty()) {
                            AppUiText("No completed practical tasks to review yet.")
                        } else {
                            practicalSubmissions.forEach { submission ->
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AppUiText(submission.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        AppUiText("Status: " + submission.status.replace('_',' '))
                                        if (submission.completionNote.isNotBlank()) AppUiText("Child's note: " + submission.completionNote)
                                        if (submission.parentFeedback.isNotBlank()) AppUiText("Parent feedback: " + submission.parentFeedback)
                                        if (submission.status == "submitted") {
                                            OutlinedTextField(
                                                value = practicalReviewNotes[submission.id].orEmpty(),
                                                onValueChange = { practicalReviewNotes = practicalReviewNotes + (submission.id to it.take(1000)) },
                                                label = { AppUiText("Encouraging feedback for the child") },
                                                modifier = Modifier.fillMaxWidth(),
                                                minLines = 2
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        execute {
                                                            repository.reviewPracticalTaskSubmission(
                                                                submission.id, false, practicalReviewNotes[submission.id].orEmpty()
                                                            )
                                                            practicalSubmissions = repository.loadPracticalSubmissionsForParent(familyId, targetChild?.id)
                                                            notice = "Sent back for more practice with feedback."
                                                        }
                                                    },
                                                    enabled = !busy,
                                                    modifier = Modifier.weight(1f)
                                                ) { AppUiText("Needs practice") }
                                                Button(
                                                    onClick = {
                                                        execute {
                                                            repository.reviewPracticalTaskSubmission(
                                                                submission.id, true, practicalReviewNotes[submission.id].orEmpty()
                                                            )
                                                            practicalSubmissions = repository.loadPracticalSubmissionsForParent(familyId, targetChild?.id)
                                                            notice = "Practical task approved."
                                                        }
                                                    },
                                                    enabled = !busy,
                                                    modifier = Modifier.weight(1f)
                                                ) { AppUiText("Approve") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        AppUiText(
                            "Try a hands-on activity. Follow the safety notes and ask a parent for help when needed.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(
                            onClick = {
                                execute {
                                    val child = childProfile ?: throw IllegalStateException("Your linked child profile was not found.")
                                    practicalTasks = repository.loadPracticalTasks(child.id)
                                    practicalSubmissions = repository.loadMyPracticalSubmissions(child.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("Refresh my practical tasks and reviews") }
                        if (practicalSubmissions.isNotEmpty()) {
                            AppUiText("Parent review", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            practicalSubmissions.forEach { submission ->
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        AppUiText(submission.title, fontWeight = FontWeight.Bold)
                                        AppUiText("Status: " + submission.status.replace('_',' '))
                                        if (submission.parentFeedback.isNotBlank()) AppUiText("Parent tip: " + submission.parentFeedback)
                                    }
                                }
                            }
                        }
                    }
                    if (practicalTasks.isEmpty()) {
                        SummaryCard(
                            if (parentViewing) "No saved activities yet" else "No practical tasks yet",
                            "🧩",
                            if (parentViewing) "Create and save a practical activity above." else "Ask your parent to save a practical activity for your profile."
                        )
                    } else {
                        practicalTasks.forEach { practical ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AppUiText(practical.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    AppUiText(practical.instructions)
                                    if (practical.materialsJson != "[]") AppUiText("Materials: " + practical.materialsJson)
                                    if (practical.safetyNotes.isNotBlank()) {
                                        AppUiText("Safety: " + practical.safetyNotes, color = MaterialTheme.colorScheme.error)
                                    }
                                    AppUiText("About ${practical.expectedMinutes} minutes • Ages ${practical.ageMin}–${practical.ageMax}")
                                    if (!parentViewing) {
                                        OutlinedTextField(
                                            value = practicalCompletionNotes[practical.id].orEmpty(),
                                            onValueChange = { value ->
                                                practicalCompletionNotes = practicalCompletionNotes + (practical.id to value.take(2000))
                                            },
                                            label = { AppUiText("What did you do? (optional)") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 2
                                        )
                                        Button(
                                            onClick = {
                                                execute {
                                                    repository.submitPracticalTask(
                                                        practical.id,
                                                        practicalCompletionNotes[practical.id].orEmpty()
                                                    )
                                                    practicalCompletionNotes = practicalCompletionNotes - practical.id
                                                    notice = "Practical task submitted for parent review."
                                                }
                                            },
                                            enabled = !busy,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { AppUiText("Done • send to parent") }
                                    }
                                }
                            }
                        }
                    }
                }

                AppScreen.LEARNING_FEEDBACK -> ScreenFrame(
                    "My written answer feedback",
                    onBack = { screen = AppScreen.CHILD_HOME },
                    notice = notice,
                    busy = busy
                ) {
                    AppUiText(
                        "Use these tips to improve. Mistakes are part of learning—try again step by step.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (writtenAnswerFeedback.isEmpty()) {
                        SummaryCard("No feedback yet", "✍️", "Complete a written task to see spelling and accuracy feedback here.")
                    } else {
                        writtenAnswerFeedback.forEach { feedback ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AppUiText(feedback.conceptKey.replace('_',' ').replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    AppUiText("Your answer: " + feedback.suppliedAnswer)
                                    AppUiText("Target answer: " + feedback.expectedAnswer)
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        AppUiText("Accuracy: ${feedback.accuracyScore}%")
                                        AppUiText("Spelling: ${feedback.spellingScore}%")
                                    }
                                    AppUiText("Score: ${feedback.finalScore}/100", fontWeight = FontWeight.Bold)
                                    if (feedback.feedback.isNotBlank()) AppUiText(feedback.feedback)
                                    if (feedback.detectedErrorsJson != "[]") AppUiText("Next focus: " + feedback.detectedErrorsJson)
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { execute { writtenAnswerFeedback = repository.loadWrittenAnswerFeedback() } },
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("Refresh feedback") }
                }

                AppScreen.PHYSICAL_CHALLENGES -> ScreenFrame(
                    if (session?.accountRole == "child") "Physical activity challenges" else "Physical challenges & reward review",
                    onBack = { screen = if (session?.accountRole == "child") AppScreen.CHILD_HOME else AppScreen.PARENT_HOME },
                    notice = notice, busy = busy
                ) {
                    val isChildAccount = session?.accountRole == "child"
                    val learner = childProfile
                    if (!isChildAccount) {
                        AppUiText("Create safe, age-appropriate physical activities. Children submit compressed evidence; you review it and approve rewards.")
                        AppUiText("Assign challenge to", fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = { challengeTargetChildId = "all" },
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText((if (challengeTargetChildId == "all") "✓ " else "") + "All children") }
                        children.forEach { childOption ->
                            OutlinedButton(
                                onClick = { challengeTargetChildId = childOption.id },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AppUiText((if (challengeTargetChildId == childOption.id) "✓ " else "") + childOption.displayName + " • Grade " + childOption.schoolGrade + " • Age " + childOption.ageYears)
                            }
                        }
                        Field(challengeTitle, { challengeTitle = it.take(120) }, "Activity title (e.g. 10-minute walk)")
                        Field(challengeDescription, { challengeDescription = it.take(1000) }, "Instructions and safety notes")
                        Field(challengeRewardMinutes, { challengeRewardMinutes = it.filter(Char::isDigit).take(4) }, "Reward if approved (0–1440 minutes)", keyboardType = KeyboardType.Number)
                        AppUiText("Required evidence")
                        listOf("either" to "Photo or video", "photo" to "Photo only", "video" to "Video only").forEach { (value,label) ->
                            OutlinedButton(onClick = { challengeEvidenceType = value }, modifier = Modifier.fillMaxWidth()) {
                                AppUiText((if (challengeEvidenceType == value) "✓ " else "") + label)
                            }
                        }
                        OutlinedButton(onClick = { execute {
                            val created = repository.createPhysicalChallenge(
                                familyId, challengeTargetChildId.takeIf { it != "all" }, challengeTitle, challengeDescription,
                                challengeRewardMinutes.toIntOrNull() ?: 0, challengeEvidenceType
                            )
                            physicalChallenges = listOf(created) + physicalChallenges
                            challengeTitle = ""; challengeDescription = ""
                            notice = "Challenge created. The child can now see it."
                        } }, enabled = !busy && familyId.isNotBlank() && challengeTitle.trim().length >= 2, modifier = Modifier.fillMaxWidth()) {
                            AppUiText("＋ Create physical challenge")
                        }
                        OutlinedButton(onClick = { execute {
                            children = repository.loadChildren(familyId)
                            challengeSubmissions = repository.loadChallengeSubmissions(familyId)
                        } }, modifier = Modifier.fillMaxWidth()) { AppUiText("↻ Refresh submissions") }
                        AppUiText("Review submissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (challengeSubmissions.isEmpty()) AppUiText("No submissions yet.")
                        challengeSubmissions.forEach { submission ->
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppUiText(submission.title, fontWeight = FontWeight.Bold)
                                    AppUiText("Status: " + submission.status + " • Evidence: " + submission.evidenceType)
                                    if (submission.note.isNotBlank()) AppUiText("Child's note: " + submission.note)
                                    if (submission.evidenceType == "photo") {
                                        val bitmap = remember(submission.id) {
                                            try {
                                                val bytes = android.util.Base64.decode(submission.mediaBase64, android.util.Base64.DEFAULT)
                                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            } catch (_: Exception) { null }
                                        }
                                        if (bitmap != null) androidx.compose.foundation.Image(
                                            bitmap = bitmap.asImageBitmap(), contentDescription = "Challenge photo evidence",
                                            modifier = Modifier.fillMaxWidth().height(220.dp)
                                        )
                                        else AppUiText("Photo evidence could not be previewed.")
                                    } else {
                                        AppUiText("Video evidence attached • " + submission.mimeType)
                                        OutlinedButton(onClick = { execute {
                                            if (previewingSubmissionId == submission.id) {
                                                previewingSubmissionId = null
                                            } else {
                                                try {
                                                    val bytes = android.util.Base64.decode(submission.mediaBase64, android.util.Base64.DEFAULT)
                                                    val file = File(context.cacheDir, "challenge_" + submission.id + ".mp4")
                                                    if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
                                                    challengeVideoPreviewFiles = challengeVideoPreviewFiles + (submission.id to file)
                                                    previewingSubmissionId = submission.id
                                                    notice = "Playing compressed video evidence for parent review."
                                                } catch (e: Exception) {
                                                    notice = "Could not open video evidence: " + (e.message ?: "unknown error")
                                                }
                                            }
                                        } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                            AppUiText(if (previewingSubmissionId == submission.id) "Hide video preview" else "▶ Play video evidence")
                                        }
                                        val videoFile = challengeVideoPreviewFiles[submission.id]
                                        if (previewingSubmissionId == submission.id && videoFile != null) AndroidView(
                                            factory = { ctx -> VideoView(ctx).apply {
                                                setVideoURI(Uri.fromFile(videoFile))
                                                setMediaController(android.widget.MediaController(ctx))
                                                setOnPreparedListener { player -> player.isLooping = false; start() }
                                            } },
                                            modifier = Modifier.fillMaxWidth().height(220.dp)
                                        )
                                    }
                                    if (submission.reviewNote.isNotBlank()) AppUiText("Parent note: " + submission.reviewNote)
                                    if (submission.status == "submitted") {
                                        OutlinedButton(onClick = { execute {
                                            val result = repository.reviewChallengeSubmission(submission.id, false, "Please try again.")
                                            challengeSubmissions = repository.loadChallengeSubmissions(familyId)
                                            notice = "Submission rejected."
                                        } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { AppUiText("Reject / ask child to retry") }
                                        Button(onClick = { execute {
                                            val result = repository.reviewChallengeSubmission(submission.id, true)
                                            challengeSubmissions = repository.loadChallengeSubmissions(familyId)
                                            notice = "Approved. " + result.optInt("reward_minutes_awarded") + " reward minutes granted within the daily cap."
                                        } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { AppUiText("✓ Approve and grant reward") }
                                    } else {
                                        AppUiText("Reward approved: " + submission.rewardMinutes + " minutes")
                                    }
                                }
                            }
                        }
                    } else {
                        AppUiText("Choose a physical activity, complete it safely with a parent's permission, then send photo or short video proof. Your parent reviews the evidence before rewards are granted.")
                        OutlinedButton(onClick = { execute {
                            physicalChallenges = repository.loadPhysicalChallenges("", learner?.id)
                            challengeSubmissions = repository.loadChallengeSubmissions("", learner?.id)
                        } }, modifier = Modifier.fillMaxWidth()) { AppUiText("↻ Refresh activities") }
                        if (physicalChallenges.isEmpty()) AppUiText("No active challenges yet. Ask your parent to create one.")
                        physicalChallenges.filter { it.active && (it.childId == null || it.childId == learner?.id) }.forEach { challenge ->
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppUiText(challenge.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    AppUiText(challenge.description)
                                    AppUiText("Reward after parent approval: " + challenge.rewardMinutes + " minutes")
                                    AppUiText("Evidence required: " + challenge.evidenceType)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { selectedChallenge = challenge; challengePhotoPicker.launch("image/*") },
                                            enabled = !busy && challenge.evidenceType != "video",
                                            modifier = Modifier.weight(1f)
                                        ) { AppUiText("📷 Add photo") }
                                        OutlinedButton(
                                            onClick = { selectedChallenge = challenge; challengeVideoPicker.launch("video/mp4") },
                                            enabled = !busy && challenge.evidenceType != "photo",
                                            modifier = Modifier.weight(1f)
                                        ) { AppUiText("🎥 Add video") }
                                    }
                                    if (selectedChallenge?.id == challenge.id && evidenceKind != null) {
                                        AppUiText("Selected " + evidenceKind + " evidence (" + evidenceMimeType + ")")
                                        Field(evidenceNote, { evidenceNote = it.take(500) }, "Optional note for parent")
                                        Button(onClick = { execute {
                                            repository.submitChallengeEvidence(
                                                challenge.id, learner?.id ?: throw IOException("Child profile is missing."),
                                                evidenceKind ?: throw IOException("Choose a photo or video."),
                                                evidenceMimeType ?: throw IOException("Evidence format is missing."),
                                                evidenceBase64 ?: throw IOException("Choose evidence first."),
                                                android.util.Base64.decode(evidenceBase64, android.util.Base64.DEFAULT).size,
                                                evidenceNote
                                            )
                                            evidenceBase64 = null; evidenceMimeType = null; evidenceKind = null
                                            evidenceNote = ""; selectedChallenge = null
                                            challengeSubmissions = repository.loadChallengeSubmissions("", learner.id)
                                            notice = "Evidence sent to your parent. Rewards appear after approval."
                                        } }, enabled = !busy && evidenceBase64 != null, modifier = Modifier.fillMaxWidth()) {
                                            AppUiText("Send evidence to parent")
                                        }
                                    }
                                }
                            }
                        }
                        AppUiText("My submissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        challengeSubmissions.forEach { submission ->
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    AppUiText(submission.title, fontWeight = FontWeight.Bold)
                                    AppUiText("Status: " + submission.status)
                                    if (submission.reviewNote.isNotBlank()) AppUiText("Parent feedback: " + submission.reviewNote)
                                    if (submission.status == "approved") AppUiText("Reward granted: " + submission.rewardMinutes + " minutes")
                                }
                            }
                        }
                    }
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
                    AppUiText("Written answer accuracy & spelling", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        onClick = {
                            execute {
                                val childId = selectedChild?.id ?: children.firstOrNull()?.id
                                    ?: throw IllegalStateException("Add or select a child profile first.")
                                writtenAnswerFeedback = repository.loadWrittenAnswerFeedback(childId)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("Load spelling and accuracy feedback") }
                    if (writtenAnswerFeedback.isEmpty()) {
                        AppUiText("Select a child and load feedback to review exact answers, accuracy marks, spelling marks, and focus areas.")
                    } else {
                        writtenAnswerFeedback.forEach { feedback ->
                            val child = children.firstOrNull { it.id == feedback.childId }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    AppUiText(child?.displayName ?: "Child profile", fontWeight = FontWeight.SemiBold)
                                    AppUiText("Concept: " + feedback.conceptKey.replace('_', ' '))
                                    AppUiText("Submitted: " + feedback.suppliedAnswer)
                                    AppUiText("Expected: " + feedback.expectedAnswer)
                                    AppUiText("Accuracy: " + feedback.accuracyScore + "% • Spelling: " + feedback.spellingScore + "%")
                                    AppUiText("Final answer mark: " + feedback.finalScore + "/100", fontWeight = FontWeight.Bold)
                                    if (feedback.feedback.isNotBlank()) AppUiText("Practice tip: " + feedback.feedback)
                                    if (feedback.detectedErrorsJson != "[]") AppUiText("Focus areas: " + feedback.detectedErrorsJson)
                                }
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
                            AppUiText("$cleanedAppCount apps detected • " + allowedApps.count { it.enabled } + " allowed • " + allowedApps.count { !it.enabled } + " blocked")
                            AppUiText(if (child?.kidsModeEnabled == true) "Kids Mode on • enabled apps only" else "Kids Mode off • app rules still apply")
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
                        val appIsEnabled = existingRule?.enabled ?: (child?.kidsModeEnabled != true)
                        val hasEditedLimit = installedAppLimitEdits.containsKey(installed.packageName)
                        val limitText = installedAppLimitEdits[installed.packageName]
                            ?: existingRule?.dailyLimitMinutes?.takeIf { it > 0 }?.toString().orEmpty()
                        LaunchedEffect(installed.packageName, child?.id, existingRule?.id, existingRule?.dailyLimitMinutes, limitText, hasEditedLimit) {
                            delay(700)
                            val selected = child ?: return@LaunchedEffect
                            if (existingRule == null && !hasEditedLimit) return@LaunchedEffect
                            val limit = if (limitText.isBlank()) 0 else limitText.toIntOrNull() ?: return@LaunchedEffect
                            if (limit !in 0..1440 || existingRule?.dailyLimitMinutes == limit) return@LaunchedEffect
                            execute {
                                if (existingRule == null) repository.allowInstalledApp(selected.id, installed, limit)
                                else repository.updateAllowedAppLimit(existingRule.id, limit)
                                allowedApps = repository.loadAllowedApps(selected.id)
                                installedAppLimitEdits = installedAppLimitEdits - installed.packageName
                                notice = if (limit == 0) "${installed.displayName}: no daily cap saved."
                                    else "${installed.displayName}: ${limit}-minute daily limit saved."
                            }
                        }
                        val appGlyph = appIconGlyph(installed.packageName, installed.displayName)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Box(
                                        Modifier.size(26.dp).background(
                                            if (appIsEnabled) Color(0xFFE2F7EC) else Color(0xFFFDE8E8),
                                            RoundedCornerShape(10.dp)
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) { AppUiText(appGlyph, fontSize = 17.sp) }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        AppUiText(compactAppLabel(installed.displayName, 24), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        AppUiText(if (appIsEnabled) "Enabled" else "Disabled",
                                            color = if (appIsEnabled) Color(0xFF16804A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (existingRule != null) {
                                        OutlinedButton(
                                            onClick = {
                                                weeklyScheduleEditIds = if (existingRule.id in weeklyScheduleEditIds)
                                                    weeklyScheduleEditIds - existingRule.id else weeklyScheduleEditIds + existingRule.id
                                                if (existingRule.id !in weeklyScheduleEdits) {
                                                    weeklyScheduleEdits = weeklyScheduleEdits + (existingRule.id to AppWeeklySchedule.parse(existingRule.weeklyScheduleJson))
                                                }
                                            },
                                            enabled = !busy,
                                            modifier = Modifier.size(width = 30.dp, height = 29.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) { AppUiText("🗓", fontSize = 13.sp) }
                                    }
                                    Switch(
                                        checked = appIsEnabled,
                                        onCheckedChange = { checked ->
                                            execute {
                                                val selected = child ?: throw IllegalStateException("Select a child profile first.")
                                                if (existingRule == null) {
                                                    repository.allowInstalledApp(selected.id, installed, 0)
                                                    val created = repository.loadAllowedApps(selected.id).firstOrNull { it.packageName == installed.packageName }
                                                        ?: throw IllegalStateException("The server did not confirm this app rule.")
                                                    if (!checked) repository.updateAllowedAppEnabled(created.id, false)
                                                } else repository.updateAllowedAppEnabled(existingRule.id, checked)
                                                allowedApps = repository.loadAllowedApps(selected.id)
                                                notice = if (checked) "${installed.displayName} enabled; applies after child sync."
                                                    else "${installed.displayName} disabled; applies after child sync."
                                            }
                                        },
                                        enabled = !busy
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = limitText,
                                        onValueChange = { value -> installedAppLimitEdits = installedAppLimitEdits + (installed.packageName to value.filter(Char::isDigit).take(4)) },
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        singleLine = true,
                                        label = { AppUiText("Daily limit (min)") },
                                        placeholder = { AppUiText("0 / blank = no cap") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    }
                                if (existingRule != null && existingRule.id in weeklyScheduleEditIds) {
                                    val schedule = weeklyScheduleEdits[existingRule.id] ?: AppWeeklySchedule.parse(existingRule.weeklyScheduleJson)
                                    AppUiText("Choose allowed days and hours. Disabled days block this app all day. Times use the child's local 24-hour clock; overnight windows are supported.")
                                    AppWeeklySchedule.DAY_KEYS.forEachIndexed { dayIndex, dayKey ->
                                        val dayWindow = schedule.days[dayKey] ?: AppDayWindow(enabled = false)
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = if (dayWindow.enabled) Color(0xFFE1F7EF) else Color(0xFFFFEEF0))
                                        ) {
                                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                OutlinedButton(
                                                    onClick = { changeWeeklyWindow(existingRule.id, dayKey) { it.copy(enabled = !it.enabled) } },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) { AppUiText((if (dayWindow.enabled) "✓ " else "○ ") + AppWeeklySchedule.DAY_LABELS[dayIndex] + if (dayWindow.enabled) " • Allowed hours" else " • Blocked all day") }
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedTextField(
                                                        value = dayWindow.start,
                                                        onValueChange = { value -> changeWeeklyWindow(existingRule.id, dayKey) { it.copy(start = value.filter { c -> c.isDigit() || c == ':' }.take(5)) } },
                                                        modifier = Modifier.weight(1f), singleLine = true,
                                                        label = { AppUiText("Start") }, placeholder = { AppUiText("08:00") }
                                                    )
                                                    OutlinedTextField(
                                                        value = dayWindow.end,
                                                        onValueChange = { value -> changeWeeklyWindow(existingRule.id, dayKey) { it.copy(end = value.filter { c -> c.isDigit() || c == ':' }.take(5)) } },
                                                        modifier = Modifier.weight(1f), singleLine = true,
                                                        label = { AppUiText("End") }, placeholder = { AppUiText("20:00") }
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
                        val hasEditedLimit = appLimitEdits.containsKey(app.id)
                        val limitText = appLimitEdits[app.id] ?: app.dailyLimitMinutes.takeIf { it > 0 }?.toString().orEmpty()
                        LaunchedEffect(app.id, child?.id, app.dailyLimitMinutes, limitText, hasEditedLimit) {
                            delay(700)
                            if (!hasEditedLimit) return@LaunchedEffect
                            val limit = if (limitText.isBlank()) 0 else limitText.toIntOrNull() ?: return@LaunchedEffect
                            if (limit !in 0..1440 || limit == app.dailyLimitMinutes) return@LaunchedEffect
                            execute {
                                repository.updateAllowedAppLimit(app.id, limit)
                                allowedApps = child?.let { repository.loadAllowedApps(it.id) } ?: emptyList()
                                appLimitEdits = appLimitEdits - app.id
                                notice = if (limit == 0) "${app.displayName}: no daily cap saved." else "${app.displayName}: ${limit}-minute daily limit saved."
                            }
                        }
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Box(
                                        Modifier.size(24.dp).background(if (app.enabled) Color(0xFFE2F7EC) else Color(0xFFFDE8E8), RoundedCornerShape(9.dp)),
                                        contentAlignment = Alignment.Center
                                    ) { AppUiText(appIconGlyph(app.packageName, app.displayName), fontSize = 18.sp) }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        AppUiText(compactAppLabel(app.displayName, 24), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        AppUiText(if (app.enabled) "Enabled" else "Disabled", color = if (app.enabled) Color(0xFF16804A) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            weeklyScheduleEditIds = if (app.id in weeklyScheduleEditIds) weeklyScheduleEditIds - app.id else weeklyScheduleEditIds + app.id
                                            if (app.id !in weeklyScheduleEdits) weeklyScheduleEdits = weeklyScheduleEdits + (app.id to AppWeeklySchedule.parse(app.weeklyScheduleJson))
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.size(width = 30.dp, height = 30.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) { AppUiText("🗓", fontSize = 14.sp) }
                                    Switch(
                                        checked = app.enabled,
                                        onCheckedChange = { checked ->
                                            execute {
                                                repository.updateAllowedAppEnabled(app.id, checked)
                                                allowedApps = child?.let { repository.loadAllowedApps(it.id) } ?: emptyList()
                                                notice = if (checked) "${app.displayName} enabled; applies after child sync." else "${app.displayName} disabled; applies after child sync."
                                            }
                                        },
                                        enabled = !busy
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = limitText,
                                        onValueChange = { value -> appLimitEdits = appLimitEdits + (app.id to value.filter(Char::isDigit).take(4)) },
                                        modifier = Modifier.width(154.dp).height(48.dp),
                                        singleLine = true,
                                        label = { AppUiText("Daily min") },
                                        placeholder = { AppUiText("0 = unlimited") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            execute {
                                                repository.deleteAllowedApp(app.id)
                                                allowedApps = child?.let { repository.loadAllowedApps(it.id) } ?: emptyList()
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.size(width = 34.dp, height = 32.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) { AppUiText("🗑", fontSize = 15.sp) }
                                }
                                if (app.id in weeklyScheduleEditIds) {
                                    val schedule = weeklyScheduleEdits[app.id] ?: AppWeeklySchedule.parse(app.weeklyScheduleJson)
                                    AppUiText("Choose allowed days and hours. Disabled days block this app all day. Times use the child's local 24-hour clock.")
                                    AppWeeklySchedule.DAY_KEYS.forEachIndexed { dayIndex, dayKey ->
                                        val dayWindow = schedule.days[dayKey] ?: AppDayWindow(enabled = false)
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = if (dayWindow.enabled) Color(0xFFE1F7EF) else Color(0xFFFFEEF0))
                                        ) {
                                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                OutlinedButton(
                                                    onClick = { changeWeeklyWindow(app.id, dayKey) { it.copy(enabled = !it.enabled) } },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) { AppUiText((if (dayWindow.enabled) "✓ " else "○ ") + AppWeeklySchedule.DAY_LABELS[dayIndex] + if (dayWindow.enabled) " • Allowed hours" else " • Blocked all day") }
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedTextField(
                                                        value = dayWindow.start,
                                                        onValueChange = { value -> changeWeeklyWindow(app.id, dayKey) { it.copy(start = value.filter { c -> c.isDigit() || c == ':' }.take(5)) } },
                                                        modifier = Modifier.weight(1f), singleLine = true,
                                                        label = { AppUiText("Start") }, placeholder = { AppUiText("08:00") }
                                                    )
                                                    OutlinedTextField(
                                                        value = dayWindow.end,
                                                        onValueChange = { value -> changeWeeklyWindow(app.id, dayKey) { it.copy(end = value.filter { c -> c.isDigit() || c == ':' }.take(5)) } },
                                                        modifier = Modifier.weight(1f), singleLine = true,
                                                        label = { AppUiText("End") }, placeholder = { AppUiText("20:00") }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            execute {
                                                val edited = weeklyScheduleEdits[app.id] ?: AppWeeklySchedule.parse(app.weeklyScheduleJson)
                                                repository.updateAllowedAppWeeklySchedule(app.id, edited)
                                                allowedApps = child?.let { repository.loadAllowedApps(it.id) } ?: emptyList()
                                                weeklyScheduleEditIds = weeklyScheduleEditIds - app.id
                                                weeklyScheduleEdits = weeklyScheduleEdits - app.id
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
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { appGroupSelectedPackages = appGroupSelectedPackages + groupEligibleApps.map { it.packageName } },
                                    enabled = groupEligibleApps.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) { AppUiText("Select all", fontSize = 12.sp) }
                                OutlinedButton(
                                    onClick = { appGroupSelectedPackages = emptySet() },
                                    enabled = appGroupSelectedPackages.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) { AppUiText("Clear", fontSize = 12.sp) }
                            }
                            AppUiText("${appGroupSelectedPackages.size} app(s) selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (groupEligibleApps.isEmpty()) AppUiText("Enable an app above first, then refresh the app list before adding it to a group.")
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
                                        AppUiText(compactAppLabel(app.displayName, 23), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        AppUiText(compactAppLabel(app.packageName, 25), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
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
                    OutlinedButton(
                        onClick = { childLogoutCodeInput = ""; childLogoutDialogOpen = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("🚪 Log out • parent code required") }
                    Button(
                        onClick = {
                            val nextTask = activeTask?.takeIf { current -> tasks.any { it.id == current.id } }
                                ?: tasks.firstOrNull()
                            if (nextTask == null) {
                                notice = "No active tasks yet. Ask your parent to assign a learning task."
                            } else {
                                openChildTask(nextTask)
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("📚 Learning • Continue a task") }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                screen = AppScreen.PHYSICAL_CHALLENGES
                                execute {
                                    physicalChallenges = repository.loadPhysicalChallenges("", child?.id)
                                    challengeSubmissions = repository.loadChallengeSubmissions("", child?.id)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { AppUiText("🏃 Physical challenges") }
                        OutlinedButton(
                            onClick = {
                                screen = AppScreen.PRACTICAL_TASKS
                                execute { practicalTasks = repository.loadPracticalTasks(child?.id.orEmpty()) }
                            },
                            modifier = Modifier.weight(1f)
                        ) { AppUiText("🧪 Practical tasks") }
                    }
                    OutlinedButton(
                        onClick = {
                            screen = AppScreen.LEARNING_FEEDBACK
                            execute { writtenAnswerFeedback = repository.loadWrittenAnswerFeedback() }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { AppUiText("✍️ My answer feedback • spelling & accuracy") }
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
                    if (childEmergencyOptions.enabled) {
                        val emergencyApps = allowedApps
                            .filter { it.enabled && it.packageName in childEmergencyOptions.allowedPackages }
                            .distinctBy { it.packageName.lowercase(Locale.ROOT) }
                            .sortedBy { it.displayName.lowercase(Locale.ROOT) }
                        val todayInPakistan = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Karachi")).toString()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppUiText("🆘 Emergency app access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF72500A))
                                AppUiText("One approved app can be opened for " + childEmergencyOptions.accessMinutes + " minutes, once per Pakistan calendar day.", color = Color(0xFF72500A))
                                AppUiText("Your parent does not need to be online, but this phone needs internet to verify the code.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF72500A))
                                if (childEmergencyOptions.blockEmergencyAfterDailyLimit) {
                                    AppUiText(
                                        "Strict daily limit is on. Emergency access cannot extend an app after its daily limit is reached.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9A3412)
                                    )
                                }
                                if (childEmergencyOptions.lastUsedDate == todayInPakistan) {
                                    AppUiText("Emergency access has already been used today.", fontWeight = FontWeight.SemiBold, color = Color(0xFF9A3412))
                                } else if (emergencyApps.isEmpty()) {
                                    AppUiText("No emergency apps are currently enabled. Ask your parent to refresh the app settings.", color = Color(0xFF9A3412))
                                } else {
                                    Box(Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = { childEmergencyAppMenuExpanded = true },
                                            enabled = !busy,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val selectedEmergencyApp = emergencyApps.firstOrNull { it.packageName == childEmergencyTargetPackage }
                                            AppUiText(compactAppLabel(selectedEmergencyApp?.displayName ?: "Choose approved app", 22) + "  ▾")
                                        }
                                        androidx.compose.material3.DropdownMenu(
                                            expanded = childEmergencyAppMenuExpanded,
                                            onDismissRequest = { childEmergencyAppMenuExpanded = false },
                                            modifier = Modifier.heightIn(max = 300.dp)
                                        ) {
                                            emergencyApps.forEach { app ->
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { AppUiText((if (app.packageName == childEmergencyTargetPackage) "✓  " else "") + compactAppLabel(app.displayName, 22)) },
                                                    onClick = {
                                                        childEmergencyTargetPackage = app.packageName
                                                        childEmergencyAppMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Field(
                                        childEmergencyCodeInput,
                                        { childEmergencyCodeInput = it.filter(Char::isDigit).take(8) },
                                        "8-digit parent emergency code",
                                        keyboardType = KeyboardType.Number
                                    )
                                    Button(
                                        onClick = {
                                            execute {
                                                val launch = context.packageManager.getLaunchIntentForPackage(childEmergencyTargetPackage)
                                                    ?: throw IllegalStateException("This app is not installed on this phone. Ask your parent to refresh the approved app list.")
                                                val linkedChildId = childProfile?.id
                                                    ?: throw IllegalStateException("This child profile is no longer linked.")
                                                // Push the latest local usage total before the server checks strict daily limits.
                                                repository.syncAppUsage(linkedChildId)
                                                val result = repository.redeemChildEmergencyAccess(
                                                    childEmergencyCodeInput, childEmergencyTargetPackage
                                                )
                                                val bootCount = try {
                                                    Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
                                                } catch (_: Exception) { -1 }
                                                val deadline = android.os.SystemClock.elapsedRealtime() + result.expiresInSeconds * 1000L
                                                val entries = lockStatePreferences.getStringSet("emergency_access_grants", emptySet()).orEmpty().toMutableSet()
                                                entries.removeAll { it.substringBefore("=").equals(result.packageName, ignoreCase = true) }
                                                entries.add(result.packageName + "=" + deadline + "=" + bootCount)
                                                lockStatePreferences.edit().putStringSet("emergency_access_grants", entries).apply()
                                                childEmergencyOptions = childEmergencyOptions.copy(lastUsedDate = result.usedDate)
                                                childEmergencyCodeInput = ""
                                                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(launch)
                                                notice = "Emergency access enabled for " + (emergencyApps.firstOrNull { it.packageName == result.packageName }?.displayName ?: "the approved app") +
                                                    " for " + result.accessMinutes + " minutes. Today's emergency use has been recorded."
                                            }
                                        },
                                        enabled = !busy && childEmergencyCodeInput.length == 8 &&
                                            emergencyApps.any { it.packageName == childEmergencyTargetPackage },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { AppUiText("Unlock approved app once today") }
                                }
                            }
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
                    var childAppSearchQuery by remember { mutableStateOf("") }
                    var childAppFilter by remember { mutableStateOf("All") }
                    OutlinedTextField(
                        value = childAppSearchQuery,
                        onValueChange = { childAppSearchQuery = it.take(100) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        singleLine = true,
                        label = { AppUiText("Search my apps") },
                        placeholder = { AppUiText("Find an app by name") }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All", "Time left", "Locked").forEach { filter ->
                            if (childAppFilter == filter) {
                                Button(onClick = { childAppFilter = filter }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp)) {
                                    AppUiText(filter, fontSize = 12.sp)
                                }
                            } else {
                                OutlinedButton(onClick = { childAppFilter = filter }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp)) {
                                    AppUiText(filter, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    val uniqueChildApps = allowedApps.filter { app ->
                        app.enabled &&
                            (childAppSearchQuery.isBlank() || app.displayName.contains(childAppSearchQuery, ignoreCase = true) || app.packageName.contains(childAppSearchQuery, ignoreCase = true)) &&
                            if (app.packageName == "com.android.settings") true else {
                            val info = try { context.packageManager.getApplicationInfo(app.packageName, 0) } catch (_: Exception) { null }
                            info != null &&
                                (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                        }
                    }.distinctBy { it.packageName.lowercase(Locale.ROOT) }
                        .sortedWith(compareBy<AllowedApp> { it.displayName.lowercase(Locale.ROOT) }.thenBy { it.packageName })
                    var childAppsExpanded by remember { mutableStateOf(false) }
                    if (uniqueChildApps.isEmpty()) {
                        AppUiText("No parent-approved apps are currently available.")
                    } else {
                        OutlinedButton(
                            onClick = { childAppsExpanded = !childAppsExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText((if (childAppsExpanded) "Hide apps" else "Show apps") + " • " + uniqueChildApps.size + " available") }
                        if (childAppsExpanded) uniqueChildApps.forEach { app ->
                            val lockPrefs = context.getSharedPreferences("edulock_lock_state", Context.MODE_PRIVATE)
                            val usedMs = lockPrefs.getLong("app_used_millis_" + app.packageName, 0L).coerceAtLeast(0L)
                            val earnedMs = lockPrefs.getStringSet("app_earned_minutes", emptySet()).orEmpty()
                                .firstOrNull { it.startsWith(app.packageName + "=") }?.substringAfter("=")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                            val remaining = if (app.dailyLimitMinutes <= 0) Int.MAX_VALUE else (app.dailyLimitMinutes + earnedMs - (usedMs / 60_000L).toInt()).coerceAtLeast(0)
                            val currentBootCount = try {
                                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
                            } catch (_: Exception) { -1 }
                            val emergencyGrantActive = lockPrefs.getStringSet("emergency_access_grants", emptySet()).orEmpty().any { grant ->
                                val parts = grant.split("=")
                                parts.size == 3 && parts[0] == app.packageName &&
                                    (parts[1].toLongOrNull() ?: 0L) > android.os.SystemClock.elapsedRealtime() &&
                                    (parts[2].toIntOrNull() ?: -2) == currentBootCount
                            }
                            val hasTask = tasks.any { it.rewardPackageName == app.packageName } ||
                                tasks.any { task -> !task.rewardGroupId.isNullOrBlank() && appGroups.any { it.id == task.rewardGroupId && app.packageName in it.packageNames } }
                            val status = when {
                                emergencyGrantActive -> "Emergency access"
                                app.dailyLimitMinutes <= 0 -> "No app-specific cap"
                                remaining <= 0 -> "Time's up • complete a task"
                                else -> "$remaining min left"
                            }
                            val passesChildAppFilter = when (childAppFilter) {
                                "Locked" -> remaining <= 0 && !emergencyGrantActive
                                "Time left" -> remaining > 0 || emergencyGrantActive
                                else -> true
                            }
                            if (!passesChildAppFilter) return@forEach
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        AppUiText(compactAppLabel(app.displayName, 22), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        AppUiText(compactAppLabel(status, 24), color = if (remaining <= 0) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                        if (hasTask) AppUiText("A learning task unlocks this app.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Button(
                                        onClick = {
                                            val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                            if (launch == null) {
                                                notice = "This app is not installed on the child phone."
                                            } else if (remaining <= 0 && !emergencyGrantActive) {
                                                val task = tasks.firstOrNull { it.rewardPackageName == app.packageName }
                                                    ?: tasks.firstOrNull { t -> !t.rewardGroupId.isNullOrBlank() && appGroups.any { it.id == t.rewardGroupId && app.packageName in it.packageNames } }
                                                if (task != null) {
                                                    openChildTask(task)
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
                                    ) { AppUiText(if (remaining <= 0 && !emergencyGrantActive) "Unlock" else "Open") }
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
                                AppUiText(
                                    (task.timeLimitSeconds?.let { "Total time: " + it + " sec" } ?: "No total time limit") +
                                        " • " + (task.questionTimeLimitSeconds?.let { it.toString() + " sec per question" } ?: "No per-question limit"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Button(onClick = { openChildTask(task) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    AppUiText(
                                        if (activeTask?.id == task.id) "▶ Resume task" else "▶ Start task"
                                    )
                                }
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
                                    "Kids Mode is saved, but this phone has not confirmed active enforcement. Enable Accessibility and sync again. Kiosk-level enforcement requires device-owner provisioning."
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
                            tasks = repository.loadChildTasks(linked.id, linked.schoolGrade, linked.ageYears)
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

                AppScreen.PLAY_TASK -> ScreenFrame(activeTask?.title ?: "Task", onBack = { leaveChildTask() }, notice = notice, busy = busy) {
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
                                "Task time remaining",
                                String.format(java.util.Locale.ROOT, "%d:%02d", taskSecondsRemaining / 60, taskSecondsRemaining % 60),
                                "The task is submitted when this countdown reaches zero."
                            )
                        }
                        if (task.questionTimeLimitSeconds != null && question.id !in expiredQuestionIds) {
                            SummaryCard(
                                "Question time remaining",
                                questionSecondsRemaining.toString() + " sec",
                                "Each question has its own countdown. When it reaches zero, this question locks and the next one opens."
                            )
                        }
                        if (question.id in expiredQuestionIds) {
                            SummaryCard("Question locked", "Time ended", "Your saved answer cannot be changed after this question's timer expires.")
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
                            if (question.id in expiredQuestionIds) {
                                AppUiText("Time for this question has ended. This question is locked; move to the next question.")
                            } else if (question.questionType == "picture") {
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
                                    taskDeadlineAtMillis = null
                                    questionDeadlineAtMillis = null
                                    questionDeadlineQuestionId = null
                                    questionSecondsRemaining = 0
                                    expiredQuestionIds = emptySet()
                                    val linked = childProfile
                                    if (linked != null) {
                                        try {
                                            earnedToday = repository.refreshChildDeviceRestrictions(linked)
                                            rewardBankMinutes = repository.loadChildRewardBankMinutes(linked.id)
                                        } catch (_: Exception) {
                                            earnedToday = submitted.dailyUsedMinutes
                                            notice = "Your answers were submitted, but the updated reward balance could not be refreshed. Open the learning space while connected and sync again."
                                        }
                                        try { tasks = repository.loadChildTasks(linked.id, linked.schoolGrade, linked.ageYears) } catch (_: Exception) { /* preserve current list if the network is unavailable */ }
                                    }
                                    screen = AppScreen.TASK_RESULT
                                }
                            },
                            enabled = !busy && allAnswered,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppUiText(if (busy) "Submitting answers…" else "Submit answers (" + answers.count { it.value.isNotBlank() } + "/" + questions.size + ")")
                        }
                        OutlinedButton(
                            onClick = { leaveChildTask() },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { AppUiText("Save progress & leave task") }
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
                                subjects = repository.loadSubjects(familyId)
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
                        if (result.pendingSync) {
                            SummaryCard("Task saved offline", "Waiting to sync", "Your answers are saved on this device. EduLock will send them to the server when internet returns. Your score and rewards will appear after the server checks your answers.")
                            SummaryCard("Pending task submissions", repository.pendingOfflineAttemptCount().toString(), "Keep EduLock installed and open it again when you are online so your work can sync.")
                        } else {
                            SummaryCard("Your score", result.scorePercent.toString() + "%", if (result.passed) "Amazing work — you passed!" else "Keep practising. Every correct answer can earn reward minutes, even when you do not pass.")
                            SummaryCard("Reward earned", result.awardedMinutes.toString() + " minutes", if (result.rewardToBank) "Added to your Reward Bank." else "Sent to the destination selected by your parent.")
                            SummaryCard("Daily reward cap", result.dailyUsedMinutes.toString() + " / " + result.dailyCapMinutes + " minutes", "The server enforces this cap so answers cannot create unlimited rewards.")
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
                val uniqueApprovedApps = approvedApps
                    .distinctBy { it.packageName.lowercase(java.util.Locale.ROOT) }
                    .sortedWith(compareBy<AllowedApp> { it.displayName.lowercase(java.util.Locale.ROOT) }.thenBy { it.packageName })
                val selectedRewardApp = uniqueApprovedApps.firstOrNull { it.packageName == selectedPackage }
                var rewardAppsExpanded by remember { mutableStateOf(false) }
                AppUiText("Use reward on", fontWeight = FontWeight.Bold, color = Color(0xFF72500A))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { rewardAppsExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && uniqueApprovedApps.isNotEmpty()
                    ) {
                        AppUiText(compactAppLabel(selectedRewardApp?.displayName ?: "Choose an approved app", 22) + "  ▾")
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = rewardAppsExpanded,
                        onDismissRequest = { rewardAppsExpanded = false },
                        modifier = Modifier.heightIn(max = 320.dp)
                    ) {
                        uniqueApprovedApps.forEach { app ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { AppUiText((if (selectedPackage == app.packageName) "✓  " else "") + compactAppLabel(app.displayName, 22)) },
                                onClick = {
                                    onSelectPackage(app.packageName)
                                    rewardAppsExpanded = false
                                }
                            )
                        }
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
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.edulock_logo),
                        contentDescription = "EduLock",
                        modifier = Modifier.size(58.dp)
                    )
                }
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
