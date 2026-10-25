package com.edulock.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Settings.Secure
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private enum class AppScreen {
    AUTH, PAIR_CHILD, PARENT_HOME, CHILD_HOME, CHILDREN, ADD_CHILD,
    SUBJECTS, ADD_SUBJECT, QUESTIONS, ADD_QUESTION, ASSIGN_TASK,
    REPORTS, ALLOWED_APPS, PLAY_TASK, TASK_RESULT
}

@Composable
private fun EduLockApp(repository: EduLockRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(AppScreen.AUTH) }
    var session by remember { mutableStateOf<AuthSession?>(repository.currentSession()) }
    var accountRole by remember { mutableStateOf("parent") }
    var isRegistering by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
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
    var tasks by remember { mutableStateOf(emptyList<TaskRow>()) }
    var attempts by remember { mutableStateOf(emptyList<AttemptRow>()) }
    var allowedApps by remember { mutableStateOf(emptyList<AllowedApp>()) }
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
    var taskTitle by remember { mutableStateOf("") }
    var taskQuestionCount by remember { mutableStateOf("5") }
    var taskPassPercent by remember { mutableStateOf("70") }
    var taskRewardMinutes by remember { mutableStateOf("15") }
    var taskDailyCapMinutes by remember { mutableStateOf("60") }
    var appPackage by remember { mutableStateOf("") }
    var appDisplayName by remember { mutableStateOf("") }

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
        tasks = repository.loadChildTasks(linked.id)
        try {
            earnedToday = repository.refreshChildDeviceRestrictions(linked)
        } catch (e: Exception) {
            notice = "Signed in, but screen-time settings did not sync: " + (e.message ?: "try again")
        }
        screen = AppScreen.CHILD_HOME
    }

    suspend fun resolveSignedIn(newSession: AuthSession, suppliedCode: String = "") {
        session = newSession
        accountRole = newSession.accountRole
        if (newSession.accountRole == "parent") {
            childProfile = null
            familyId = repository.ensureParentFamily(displayName.ifBlank { "My family" })
            children = repository.loadChildren(familyId)
            subjects = repository.loadSubjects(familyId)
            screen = AppScreen.PARENT_HOME
        } else {
            finishChildLogin(newSession, suppliedCode)
        }
    }

    LaunchedEffect(Unit) {
        val restored = repository.restoreSession()
        if (restored != null) {
            session = restored
            accountRole = restored.accountRole
            try {
                resolveSignedIn(restored)
            } catch (e: Exception) {
                notice = e.message ?: "Could not restore the session. Please sign in again."
                screen = if (restored.accountRole == "child") AppScreen.PAIR_CHILD else AppScreen.AUTH
            }
        } else if (!repository.isConfigured) {
            notice = "Supabase is not configured yet. Add SUPABASE_URL and SUPABASE_ANON_KEY to the GitHub Actions secrets before building."
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (screen) {
                AppScreen.AUTH -> ScreenFrame("EduLock", notice = notice, busy = busy) {
                    Text("Learn first. Play with purpose.", style = MaterialTheme.typography.titleMedium)
                    Text("Secure parent access, multilingual learning and earned screen time.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { accountRole = "parent" }, enabled = !busy) { Text(if (accountRole == "parent") "✓ Parent" else "Parent") }
                        OutlinedButton(onClick = { accountRole = "child" }, enabled = !busy) { Text(if (accountRole == "child") "✓ Child" else "Child") }
                    }
                    if (isRegistering) {
                        Field(displayName, { displayName = it }, "Your name")
                    }
                    Field(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
                    Field(password, { password = it }, "Password (minimum 8 characters)", password = true)
                    if (accountRole == "child") {
                        Field(pairingCode, { pairingCode = it.filter(Char::isDigit).take(6) }, "Parent's 6-digit pairing code", keyboardType = KeyboardType.Number)
                        Text("If your email needs confirmation, confirm it first, then sign in and enter the pairing code.")
                    }
                    Button(
                        onClick = {
                            execute {
                                val result = repository.signUp(email, password, displayName, accountRole)
                                if (result.session == null) {
                                    notice = result.message
                                } else {
                                    resolveSignedIn(result.session, pairingCode)
                                    if (screen == AppScreen.PARENT_HOME || screen == AppScreen.CHILD_HOME) notice = result.message
                                }
                            }
                        },
                        enabled = !busy && isRegistering,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Create " + if (accountRole == "parent") "parent account" else "child account") }
                    Button(
                        onClick = {
                            execute {
                                val signedIn = repository.signIn(email, password, accountRole)
                                resolveSignedIn(signedIn, pairingCode)
                            }
                        },
                        enabled = !busy && !isRegistering,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sign in as " + if (accountRole == "parent") "parent" else "child") }
                    TextButton(onClick = { isRegistering = !isRegistering; notice = null }) {
                        Text(if (isRegistering) "Already registered? Sign in" else "New to EduLock? Create account")
                    }
                    if (!repository.isConfigured) {
                        Text("Configuration needed: set the public Supabase URL and anon/publishable key in the repository build secrets. Never add a service-role key to the Android app.")
                    }
                }

                AppScreen.PAIR_CHILD -> ScreenFrame("Link child account", onBack = {
                    repository.signOut(); session = null; screen = AppScreen.AUTH
                }, notice = notice, busy = busy) {
                    Text("Ask the parent to open this child's profile and generate a new pairing code. It expires in 10 minutes and can be used once.")
                    Field(pairingCode, { pairingCode = it.filter(Char::isDigit).take(6) }, "6-digit parent pairing code", keyboardType = KeyboardType.Number)
                    Button(onClick = {
                        execute {
                            repository.claimChildPairing(pairingCode)
                            val current = session ?: throw IllegalStateException("Please sign in again.")
                            finishChildLogin(current, "")
                        }
                    }, enabled = !busy && pairingCode.filter(Char::isDigit).length == 6, modifier = Modifier.fillMaxWidth()) {
                        Text("Securely link this child")
                    }
                    OutlinedButton(onClick = {
                        execute {
                            val current = session ?: throw IllegalStateException("Please sign in again.")
                            resolveSignedIn(current, pairingCode)
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Refresh linked profile") }
                }

                AppScreen.PARENT_HOME -> ScreenFrame("Parent dashboard", notice = notice, busy = busy) {
                    Text("Your family is protected by your signed-in parent account.")
                    SummaryCard("Children", children.size.toString() + " profile(s)", "Add children and link their own devices.")
                    SummaryCard("Learning", subjects.size.toString() + " subject(s)", "Create questions in Urdu, Sindhi, English or another language.")
                    Button(onClick = { screen = AppScreen.CHILDREN; execute { children = repository.loadChildren(familyId) } }, modifier = Modifier.fillMaxWidth()) { Text("Manage child profiles") }
                    Button(onClick = { screen = AppScreen.SUBJECTS; execute { subjects = repository.loadSubjects(familyId) } }, modifier = Modifier.fillMaxWidth()) { Text("Subjects & questions") }
                    Button(onClick = { selectedChild = null; selectedSubject = null; screen = AppScreen.ASSIGN_TASK }, modifier = Modifier.fillMaxWidth()) { Text("Assign learning task & reward") }
                    OutlinedButton(onClick = { screen = AppScreen.REPORTS; execute { attempts = repository.loadAttempts() } }, modifier = Modifier.fillMaxWidth()) { Text("Progress reports") }
                    OutlinedButton(onClick = {
                        screen = AppScreen.CHILDREN
                        execute { children = repository.loadChildren(familyId) }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Allowed apps & screen-time settings") }
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
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Configure allowed apps") }
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
                        Text("Question language: " + subject.languageCode + ". Use the chosen language's script; Urdu and Sindhi text are supported.")
                        Button(onClick = {
                            questionPrompt = ""; questionOptions = ""; questionAnswer = ""; questionType = "multiple_choice"
                            screen = AppScreen.ADD_QUESTION
                        }, modifier = Modifier.fillMaxWidth()) { Text("Add question") }
                        if (questions.isEmpty()) Text("No questions yet.")
                        questions.forEachIndexed { index, question ->
                            CompositionLocalProvider(LocalLayoutDirection provides directionFor(subject.languageCode)) {
                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text((index + 1).toString() + ". " + question.prompt, fontWeight = FontWeight.SemiBold)
                                        Text("Type: " + readableQuestionType(question.questionType))
                                        if (question.options.isNotEmpty()) Text("Options: " + question.options.joinToString(" • "))
                                    }
                                }
                            }
                        }
                    }
                }

                AppScreen.ADD_QUESTION -> ScreenFrame("Add question", onBack = { screen = AppScreen.QUESTIONS }, notice = notice, busy = busy) {
                    val lang = selectedSubject?.languageCode ?: "en"
                    Text("Language: " + lang + ". You can paste or type Urdu, Sindhi, English or another language.")
                    CompositionLocalProvider(LocalLayoutDirection provides directionFor(lang)) {
                        Field(questionPrompt, { questionPrompt = it }, "Question prompt")
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
                            Text("For a true/false question, the correct answer must be true or false.")
                        }
                        Field(questionAnswer, { questionAnswer = it }, "Correct answer (stored privately; never sent to child devices)")
                    }
                    Button(onClick = {
                        execute {
                            val subject = selectedSubject ?: throw IllegalStateException("Choose a subject first.")
                            val options = questionOptions.lines().map { it.trim() }.filter { it.isNotEmpty() }
                            repository.addQuestion(subject.id, questionPrompt, questionType, options, questionAnswer)
                            questions = repository.loadQuestions(subject.id)
                            screen = AppScreen.QUESTIONS
                            notice = "Question saved. The answer key stays server-side for grading."
                        }
                    }, enabled = !busy && questionPrompt.isNotBlank() && questionAnswer.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save question") }
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
                    OutlinedButton(onClick = { execute { attempts = repository.loadAttempts() } }, modifier = Modifier.fillMaxWidth()) { Text("Refresh report") }
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
                    Text("Add exact Android package names. Only these apps are allowed while reward time remains. Settings and the home screen stay available for recovery.")
                    Field(appDisplayName, { appDisplayName = it }, "App display name")
                    Field(appPackage, { appPackage = it }, "Package name (e.g. com.example.app)")
                    Button(onClick = {
                        execute {
                            val selected = child ?: throw IllegalStateException("Select a child profile first.")
                            repository.addAllowedApp(selected.id, appPackage, appDisplayName)
                            allowedApps = repository.loadAllowedApps(selected.id)
                            appDisplayName = ""; appPackage = ""
                            notice = "Allowed app saved. The list syncs to the child's device after the child signs in."
                        }
                    }, enabled = !busy && child != null && appPackage.isNotBlank() && appDisplayName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Add allowed app") }
                    if (allowedApps.isEmpty()) Text("No apps are allowed yet. With restrictions enabled, only EduLock and essential Android recovery screens remain accessible until an app is approved.")
                    allowedApps.forEach { app ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(app.displayName, fontWeight = FontWeight.SemiBold)
                                Text(app.packageName)
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
                    if (isAccessibilityServiceEnabled(context)) {
                        Text("Device restriction service: enabled on this device.")
                    } else {
                        Text("Automatic blocking needs your parent's permission and one-time activation in Android Accessibility settings on this device.")
                        Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Enable EduLock app restrictions")
                        }
                    }
                    Button(onClick = {
                        execute {
                            val linked = childProfile ?: throw IllegalStateException("Sign in to your child account again.")
                            tasks = repository.loadChildTasks(linked.id)
                            earnedToday = repository.refreshChildDeviceRestrictions(linked)
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
                                        questions = repository.loadQuestions(subjectId, task.questionCount)
                                        if (questions.isEmpty()) throw IllegalStateException("This task has no questions. Ask your parent to add some.")
                                        if (questions.size < task.questionCount) notice = "The task has fewer remaining questions than configured; you can answer the available questions."
                                        activeTask = task
                                        questionIndex = 0
                                        answers = emptyMap()
                                        screen = AppScreen.PLAY_TASK
                                    }
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Start task") }
                            }
                        }
                    }
                    OutlinedButton(onClick = {
                        repository.signOut()
                        session = null; childProfile = null; screen = AppScreen.AUTH; isRegistering = false
                    }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
                }

                AppScreen.PLAY_TASK -> ScreenFrame(activeTask?.title ?: "Task", onBack = { screen = AppScreen.CHILD_HOME }, notice = notice, busy = busy) {
                    val task = activeTask
                    val question = questions.getOrNull(questionIndex)
                    if (task != null && question != null) {
                        Text("Question " + (questionIndex + 1) + " of " + questions.size, style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(progress = { (questionIndex + 1).toFloat() / questions.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                        CompositionLocalProvider(LocalLayoutDirection provides directionFor(childProfile?.preferredLanguage ?: "en")) {
                            Text(question.prompt, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            if (question.questionType == "multiple_choice") {
                                question.options.forEach { option ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { answers = answers + (question.id to option) }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = answers[question.id] == option, onClick = { answers = answers + (question.id to option) })
                                        Text(option)
                                    }
                                }
                            } else if (question.questionType == "true_false") {
                                listOf("true", "false").forEach { value ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { answers = answers + (question.id to value) }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = answers[question.id] == value, onClick = { answers = answers + (question.id to value) })
                                        Text(if (value == "true") "True / صحيح" else "False / غلط")
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
                                        val submitted = repository.submitTask(task.id, answers)
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
private fun ScreenFrame(
    title: String,
    onBack: (() -> Unit)? = null,
    notice: String?,
    busy: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) TextButton(onClick = onBack) { Text("← Back") }
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (!notice.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(notice, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        content()
        Spacer(Modifier.height(12.dp))
    }
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
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
private fun SummaryCard(title: String, value: String, explanation: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(explanation, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun readableQuestionType(type: String): String = when (type) {
    "multiple_choice" -> "Multiple choice"
    "true_false" -> "True / false"
    "numeric" -> "Numeric"
    else -> "Short answer"
}

private fun directionFor(language: String): LayoutDirection =
    if (language.lowercase() in setOf("ur", "sd", "ar", "fa", "ps", "he")) LayoutDirection.Rtl else LayoutDirection.Ltr

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabled = Secure.getString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val serviceName = context.packageName + "/" + EduLockAccessibilityService::class.java.name
    return enabled.split(':').any { it.equals(serviceName, ignoreCase = true) }
}

class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = EduLockRepository(applicationContext)
        androidx.activity.compose.setContent {
            EduLockApp(repository)
        }
    }
}
