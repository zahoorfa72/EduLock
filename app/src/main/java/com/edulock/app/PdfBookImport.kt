package com.edulock.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.DownloadListener
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class PdfQuestionDraft(
    val prompt: String,
    val optionsText: String = "",
    val answer: String = "",
    val solution: String = "",
    val questionType: String = "short_answer",
    val languageCode: String = "en",
    val include: Boolean = true
)

private data class BookPortal(
    val title: String,
    val subtitle: String,
    val url: String
)

private val bookHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

@Composable
internal fun PdfBookImportScreen(
    subjects: List<SubjectRow>,
    initialSubject: SubjectRow?,
    defaultCountryCode: String,
    defaultLanguageCode: String,
    onBack: () -> Unit,
    onSaveQuestions: suspend (SubjectRow, List<PdfQuestionDraft>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var countryCode by remember { mutableStateOf(defaultCountryCode.uppercase(Locale.ROOT).ifBlank { "PK" }) }
    var board by remember { mutableStateOf(defaultBoard(defaultCountryCode)) }
    var languageCode by remember { mutableStateOf(initialSubject?.languageCode ?: defaultLanguageCode.ifBlank { "en" }) }
    var grade by remember { mutableStateOf((initialSubject?.gradeLevel ?: 1).toString()) }
    var bookSubject by remember { mutableStateOf(initialSubject?.title ?: "") }
    var bookTitle by remember { mutableStateOf("") }
    var selectedSubjectId by remember { mutableStateOf(initialSubject?.id ?: subjects.firstOrNull()?.id.orEmpty()) }
    var countryMenu by remember { mutableStateOf(false) }
    var boardMenu by remember { mutableStateOf(false) }
    var subjectMenu by remember { mutableStateOf(false) }
    var activePortal by remember { mutableStateOf<BookPortal?>(null) }
    var pendingPdfUrl by remember { mutableStateOf<String?>(null) }
    var showPdfActions by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<PdfQuestionDraft>>(emptyList()) }
    var temporaryPdf by remember { mutableStateOf<File?>(null) }
    var pickedFileName by remember { mutableStateOf("") }

    val cleanupPdf by rememberUpdatedState(temporaryPdf)
    DisposableEffect(Unit) {
        onDispose { cleanupPdf?.delete() }
    }

    val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId }
    val portalOptions = remember(countryCode, board, bookSubject, bookTitle, grade, languageCode) {
        portalsFor(countryCode, board, bookSubject, bookTitle, grade, languageCode)
    }

    suspend fun startExtraction(file: File, displayName: String) {
        working = true
        error = null
        status = "Reading PDF on this device. No PDF is uploaded to Supabase."
        try {
            val extracted = extractPdfText(context, file)
            val parsed = detectPdfQuestions(extracted, languageCode)
            extractedText = extracted
            pickedFileName = displayName
            temporaryPdf = file
            candidates = parsed
            if (parsed.isNotEmpty()) {
                // Delete the app-owned temporary PDF as soon as questions are safely in memory.
                file.delete()
                temporaryPdf = null
                status = "Extracted " + parsed.size + " candidate questions. Review every prompt and answer before saving."
            } else {
                status = "Text was extracted, but no clear question boundaries were found. Review the text and try parsing again."
            }
        } catch (e: Exception) {
            error = e.message ?: "Could not read this PDF. Try a text-based PDF or a smaller file."
            status = null
            temporaryPdf = file
        } finally {
            working = false
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                working = true
                error = null
                status = "Copying the selected PDF to temporary app storage…"
                try {
                    val copied = copyUriToCache(context, uri)
                    startExtraction(copied, "Selected PDF")
                } catch (e: Exception) {
                    error = e.message ?: "Unable to read the selected PDF."
                    working = false
                    status = null
                }
            }
        }
    }

    suspend fun downloadForImport(url: String) {
        working = true
        error = null
        status = "Downloading the PDF into temporary app storage…"
        try {
            val file = downloadPdfToCache(context, url)
            startExtraction(file, url.substringAfterLast('/').substringBefore('?').ifBlank { "Textbook.pdf" })
        } catch (e: Exception) {
            error = e.message ?: "The website did not provide a downloadable PDF. Try the portal’s download link or select a PDF from your device."
            working = false
            status = null
        }
    }

    suspend fun downloadForDevice(url: String) {
        working = true
        error = null
        status = "Downloading the book to your device’s Downloads folder…"
        try {
            val file = downloadPdfToCache(context, url)
            val suggestedName = bookTitle.ifBlank { bookSubject + " - " + board + " class " + grade }
            val savedLocation = savePdfToDownloads(context, file, suggestedName)
            file.delete()
            status = "PDF downloaded to device storage. It was not uploaded to Supabase."
            error = null
            pickedFileName = savedLocation
        } catch (e: Exception) {
            error = e.message ?: "Could not download the PDF. The source may require a sign-in or a direct PDF URL."
        } finally {
            working = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Import questions from a PDF book",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "Choose curriculum details and the destination subject. PDFs are processed on this device; they are never stored in Supabase. Questions are saved only after you review the extracted content and provide or confirm correct answers.",
            style = MaterialTheme.typography.bodyMedium
        )

        Text("Curriculum and book selection", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedButton(onClick = { countryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Country: " + countryCode)
                }
                DropdownMenu(expanded = countryMenu, onDismissRequest = { countryMenu = false }) {
                    listOf("PK" to "Pakistan", "IN" to "India", "BD" to "Bangladesh", "GB" to "United Kingdom", "US" to "United States", "AE" to "United Arab Emirates", "OTHER" to "Other country").forEach { (code, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            countryCode = code
                            board = defaultBoard(code)
                            countryMenu = false
                            activePortal = null
                        })
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                OutlinedButton(onClick = { boardMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(board)
                }
                DropdownMenu(expanded = boardMenu, onDismissRequest = { boardMenu = false }) {
                    boardsFor(countryCode).forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            board = option
                            boardMenu = false
                            activePortal = null
                        })
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = languageCode,
                onValueChange = { languageCode = it.filter { c -> c.isLetter() || c == '-' }.take(16) },
                modifier = Modifier.weight(1f),
                label = { Text("Book language code") },
                singleLine = true
            )
            OutlinedTextField(
                value = grade,
                onValueChange = { grade = it.filter(Char::isDigit).take(2) },
                modifier = Modifier.weight(1f),
                label = { Text("Class / grade") },
                singleLine = true
            )
        }
        OutlinedTextField(
            value = bookSubject,
            onValueChange = { bookSubject = it.take(120) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Subject / book topic") },
            singleLine = true
        )
        OutlinedTextField(
            value = bookTitle,
            onValueChange = { bookTitle = it.take(180) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Book title (optional)") },
            singleLine = true
        )

        Text("Save questions into an existing subject", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { subjectMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedSubject?.title ?: "Choose destination subject")
        }
        DropdownMenu(expanded = subjectMenu, onDismissRequest = { subjectMenu = false }) {
            subjects.forEach { subject ->
                DropdownMenuItem(text = {
                    Text(subject.title + " • " + subject.languageCode + " • Grade " + subject.gradeLevel)
                }, onClick = {
                    selectedSubjectId = subject.id
                    bookSubject = subject.title
                    languageCode = subject.languageCode
                    grade = subject.gradeLevel.toString()
                    subjectMenu = false
                })
            }
        }
        if (subjects.isEmpty()) {
            Text("Create a subject first, then return here to import its PDF questions.", color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
            enabled = !working,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Choose PDF from device") }

        Text("Suggested textbook websites", style = MaterialTheme.typography.titleMedium)
        Text(
            "Official sources vary by board. The websites below open inside EduLock. Tap a PDF link, then choose whether to import its questions or keep the PDF in device storage.",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Accuracy note: text-based PDFs usually extract best. Scanned PDFs use on-device Latin-script OCR, which may not correctly read scanned Urdu/Sindhi or other non-Latin text. Check every question and answer before saving.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        portalOptions.forEach { portal ->
            OutlinedButton(
                onClick = { activePortal = portal },
                enabled = !working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(portal.title, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(portal.subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        activePortal?.let { portal ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(portal.title, style = MaterialTheme.typography.titleMedium)
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(420.dp),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val target = request?.url?.toString() ?: return false
                                        if (looksLikePdfUrl(target)) {
                                            pendingPdfUrl = target
                                            showPdfActions = true
                                            return true
                                        }
                                        return false
                                    }
                                }
                                setDownloadListener(DownloadListener { url, _, _, mimeType, _ ->
                                    if (url.startsWith("https://") || url.startsWith("http://")) {
                                        pendingPdfUrl = url
                                        showPdfActions = true
                                    } else {
                                        error = "This download link cannot be handled safely."
                                    }
                                })
                                tag = portal.url
                                loadUrl(portal.url)
                            }
                        },
                        update = { view ->
                            if (view.tag != portal.url) {
                                view.tag = portal.url
                                view.loadUrl(portal.url)
                            }
                        }
                    )
                    Text(
                        "If a website shows a web page instead of a PDF file, use its own book search/download control. EduLock intercepts PDF downloads without opening another browser app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showPdfActions && pendingPdfUrl != null) {
            AlertDialog(
                onDismissRequest = { showPdfActions = false; pendingPdfUrl = null },
                title = { Text("PDF detected") },
                text = { Text("Choose what to do with this book. Importing extracts questions locally and removes EduLock’s temporary PDF after extraction; downloading keeps a copy in device storage.") },
                confirmButton = {
                    Column {
                        Button(
                            onClick = {
                                val url = pendingPdfUrl ?: return@Button
                                showPdfActions = false
                                pendingPdfUrl = null
                                scope.launch { downloadForImport(url) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Extract questions from PDF") }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                val url = pendingPdfUrl ?: return@OutlinedButton
                                showPdfActions = false
                                pendingPdfUrl = null
                                scope.launch { downloadForDevice(url) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Download PDF to device") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPdfActions = false; pendingPdfUrl = null }) { Text("Cancel") }
                }
            )
        }

        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        if (working) {
            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (extractedText.isNotBlank() && candidates.isEmpty() && !working) {
            Text("Extracted text preview", style = MaterialTheme.typography.titleMedium)
            Text(extractedText.take(12000), style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    candidates = detectPdfQuestions(extractedText, languageCode)
                    status = if (candidates.isEmpty()) "No clear questions detected. This may be a scanned/non-question chapter or the document’s layout is not supported. You can select another PDF." else "Review the candidate questions below."
                    if (candidates.isNotEmpty()) {
                        temporaryPdf?.delete()
                        temporaryPdf = null
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Try detecting questions again") }
        }

        if (candidates.isNotEmpty()) {
            Text("Review extracted questions (" + candidates.count { it.include } + " selected)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Extracted prompts can contain formatting errors. Confirm every correct answer; EduLock does not guess answers when the PDF does not provide an unambiguous answer key.",
                style = MaterialTheme.typography.bodySmall
            )
            candidates.forEachIndexed { index, draft ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = draft.include,
                                onCheckedChange = { included ->
                                    candidates = candidates.mapIndexed { idx, q -> if (idx == index) q.copy(include = included) else q }
                                }
                            )
                            Text("Question " + (index + 1), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                candidates = candidates.filterIndexed { idx, _ -> idx != index }
                            }) { Text("Remove") }
                        }
                        OutlinedTextField(
                            value = draft.prompt,
                            onValueChange = { value ->
                                candidates = candidates.mapIndexed { idx, q -> if (idx == index) q.copy(prompt = value) else q }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Question prompt") },
                            minLines = 2
                        )
                        OutlinedTextField(
                            value = draft.optionsText,
                            onValueChange = { value ->
                                candidates = candidates.mapIndexed { idx, q -> if (idx == index) q.copy(optionsText = value, questionType = if (value.lines().count { it.isNotBlank() } >= 2) "multiple_choice" else "short_answer") else q }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Options (one per line; leave blank for short answer)") },
                            minLines = 2
                        )
                        OutlinedTextField(
                            value = draft.answer,
                            onValueChange = { value ->
                                candidates = candidates.mapIndexed { idx, q -> if (idx == index) q.copy(answer = value) else q }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Correct answer (required)") },
                            minLines = 1
                        )
                        OutlinedTextField(
                            value = draft.solution,
                            onValueChange = { value ->
                                candidates = candidates.mapIndexed { idx, q -> if (idx == index) q.copy(solution = value) else q }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Parent-only explanation (optional)") },
                            minLines = 1
                        )
                        Text(
                            if (draft.questionType == "multiple_choice") "Type: multiple choice" else "Type: short answer",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Button(
                onClick = {
                    val chosen = candidates.filter { it.include }
                    val subject = selectedSubject
                    when {
                        subject == null -> error = "Choose an existing destination subject first."
                        chosen.isEmpty() -> error = "Select at least one question to save."
                        languageCode.trim().matches(Regex("[A-Za-z][A-Za-z0-9-]{1,15}")).not() -> error = "Enter a valid language code, such as en, ur or sd."
                        chosen.any { it.prompt.isBlank() } -> error = "Every selected question needs a prompt."
                        chosen.any { it.answer.isBlank() } -> error = "Fill the correct answer for each selected question."
                        chosen.any {
                            it.questionType == "multiple_choice" &&
                                (it.optionsText.lines().map(String::trim).filter(String::isNotEmpty).size < 2 ||
                                    it.optionsText.lines().map(String::trim).filter(String::isNotEmpty).none { option -> option.equals(it.answer.trim(), true) })
                        } -> error = "For every multiple-choice question, the correct answer must exactly match one of its options."
                        else -> scope.launch {
                            working = true
                            error = null
                            try {
                                val finalDrafts = chosen.map { it.copy(languageCode = languageCode.trim().lowercase(Locale.ROOT)) }
                                onSaveQuestions(subject, finalDrafts)
                                candidates = emptyList()
                                extractedText = ""
                                temporaryPdf?.delete()
                                temporaryPdf = null
                                status = "Reviewed questions saved. Any temporary import PDF has been deleted from EduLock storage."
                            } catch (e: Exception) {
                                error = e.message ?: "Import failed. Your extracted questions remain on this screen; check duplicates before retrying."
                            } finally {
                                working = false
                            }
                        }
                    }
                },
                enabled = !working && selectedSubject != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save reviewed questions to subject") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, enabled = !working, modifier = Modifier.weight(1f)) { Text("Back") }
            OutlinedButton(onClick = {
                temporaryPdf?.delete()
                temporaryPdf = null
                extractedText = ""
                candidates = emptyList()
                status = "Temporary import content cleared."
                error = null
            }, enabled = !working, modifier = Modifier.weight(1f)) { Text("Clear import") }
        }
        if (bookTitle.isNotBlank()) {
            Text("Book: " + bookTitle + " • " + countryCode + " • " + board + " • class " + grade + " • " + languageCode)
        } else {
            Text("Selected curriculum: " + countryCode + " • " + board + " • class " + grade + " • " + languageCode, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun defaultBoard(country: String): String = when (country.uppercase(Locale.ROOT)) {
    "PK" -> "Punjab (PECTAA)"
    "IN" -> "NCERT"
    "BD" -> "NCTB"
    "GB" -> "National Curriculum"
    "US" -> "State / District"
    else -> "Official board"
}

private fun boardsFor(country: String): List<String> = when (country.uppercase(Locale.ROOT)) {
    "PK" -> listOf("Punjab (PECTAA)", "Punjab eLearn", "Sindh (STBB)", "Federal (FBISE)", "Khyber Pakhtunkhwa", "Balochistan", "Other board")
    "IN" -> listOf("NCERT", "CBSE", "State Board", "Other board")
    "BD" -> listOf("NCTB", "Other board")
    "GB" -> listOf("National Curriculum", "England exam board", "Scotland curriculum", "Other board")
    "US" -> listOf("Common Core", "State / District", "Other board")
    else -> listOf("Official board", "Other board")
}

private fun portalsFor(country: String, board: String, subject: String, title: String, grade: String, language: String): List<BookPortal> {
    val cc = country.uppercase(Locale.ROOT)
    val b = board.lowercase(Locale.ROOT)
    val curated = when {
        cc == "PK" && b.contains("sindh") -> listOf(
            BookPortal("Sindh Textbook Board eBooks", "Official board portal • Sindhi, Urdu and English • Classes Katchi–XII", "https://ebooks.stbb.edu.pk/")
        )
        cc == "PK" && b.contains("elearn") -> listOf(
            BookPortal("eLearn Punjab", "Punjab government digital textbooks • science and maths", "https://www.elearn.gov.pk/")
        )
        cc == "PK" && (b.contains("punjab") || b.contains("pectaa")) -> listOf(
            BookPortal("PECTAA e-books", "Punjab Curriculum & Textbook Authority official e-book catalogue", "https://pectaa.edu.pk/books-and-publications/"),
            BookPortal("eLearn Punjab", "Punjab government digital textbooks • science and maths", "https://www.elearn.gov.pk/")
        )
        cc == "IN" -> listOf(
            BookPortal("NCERT official textbooks", "Classes I–XII • Hindi, English, Urdu and more", "https://ncert.nic.in/textbook.php")
        )
        cc == "BD" -> listOf(
            BookPortal("NCTB official textbooks", "Bangladesh National Curriculum and Textbook Board", "https://nctb.gov.bd/")
        )
        else -> emptyList()
    }
    val query = Uri.encode("\"" + title + "\" \"" + subject + "\" class " + grade + " " + language + " textbook PDF official " + country + " " + board + " government book download")
    val exactSearch = BookPortal(
        "Search for this exact book selection",
        "Matches country, board, class, subject and language; results stay inside EduLock",
        "https://www.google.com/search?q=" + query
    )
    return (curated + exactSearch).take(4)
}

private fun looksLikePdfUrl(url: String): Boolean {
    val lower = url.lowercase(Locale.ROOT)
    return lower.substringBefore('?').substringBefore('#').endsWith(".pdf") ||
        lower.contains("download=pdf") || lower.contains("format=pdf") ||
        lower.contains("/download/")
}

private suspend fun copyUriToCache(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val folder = File(context.cacheDir, "pdf-book-import").apply { mkdirs() }
    val out = File(folder, "picked-" + System.currentTimeMillis() + ".pdf")
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Could not open the selected PDF.")
    input.use { source -> out.outputStream().use { target -> source.copyTo(target) } }
    validatePdfFile(out)
    out
}

private suspend fun downloadPdfToCache(context: Context, url: String): File = withContext(Dispatchers.IO) {
    require(url.startsWith("https://", true)) { "Only secure HTTPS book downloads are allowed." }
    val request = Request.Builder().url(url).header("User-Agent", "EduLock textbook importer").build()
    bookHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IllegalStateException("The book website returned HTTP " + response.code + ".")
        val body = response.body ?: throw IllegalStateException("The book website returned an empty file.")
        val type = body.contentType()?.toString().orEmpty()
        val length = body.contentLength()
        require(length <= 120L * 1024L * 1024L || length < 0L) { "PDF exceeds the 120 MB import limit." }
        val folder = File(context.cacheDir, "pdf-book-import").apply { mkdirs() }
        val out = File(folder, "download-" + System.currentTimeMillis() + ".pdf")
        body.byteStream().use { input -> out.outputStream().use { output ->
            val prefix = ByteArray(5)
            val firstCount = input.read(prefix)
            if (firstCount < 5 || String(prefix, 0, 5, Charsets.US_ASCII) != "%PDF-") {
                out.delete()
                throw IllegalStateException("This website link did not return a direct PDF file. Select the book’s PDF download link.")
            }
            output.write(prefix)
            val buffer = ByteArray(64 * 1024)
            var copied = prefix.size.toLong()
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                copied += count
                require(copied <= 120L * 1024L * 1024L) { "PDF exceeds the 120 MB import limit." }
                output.write(buffer, 0, count)
            }
        } }
        validatePdfFile(out)
        out
    }
}

private fun validatePdfFile(file: File) {
    require(file.exists() && file.length() >= 8L) { "The selected file is empty." }
    require(file.length() <= 120L * 1024L * 1024L) { "PDF exceeds the 120 MB import limit." }
    file.inputStream().use { input ->
        val header = ByteArray(5)
        require(input.read(header) == 5 && String(header, Charsets.US_ASCII) == "%PDF-") {
            "The selected file is not a valid PDF."
        }
    }
}

private suspend fun savePdfToDownloads(context: Context, source: File, displayName: String): String =
    withContext(Dispatchers.IO) {
        val safeName = displayName.substringAfterLast('/').substringBefore('?')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .let { if (it.endsWith(".pdf", true)) it else it + ".pdf" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/EduLock Books")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Android could not create a file in Downloads.")
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("Android could not write the downloaded PDF.")
                val published = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                context.contentResolver.update(uri, published, null, null)
                "Downloads/EduLock Books/" + safeName
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            }
        } else {
            val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "EduLock Books")
                .apply { mkdirs() }
            val destination = File(folder, safeName)
            source.inputStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            destination.absolutePath
        }
    }

private suspend fun extractPdfText(context: Context, file: File): String {
    validatePdfFile(file)
    val digitalText = try {
        withContext(Dispatchers.IO) {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(file).use { document ->
                require(document.numberOfPages in 1..300) { "This PDF must contain 1–300 pages for import." }
                PDFTextStripper().getText(document)
            }
        }
    } catch (e: Exception) {
        if (e.message?.contains("300 pages") == true) throw e
        ""
    }
    if (digitalText.replace(Regex("\\s+"), "").length >= 80) return digitalText

    // OCR fallback for image-only/scanned PDFs. ML Kit Latin OCR may not recognize all
    // Urdu/Sindhi scripts; parents should always review and correct OCR results.
    return withContext(Dispatchers.IO) {
        val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        fileDescriptor.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount in 1..120) { "Scanned PDFs must contain 1–120 pages for on-device OCR." }
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    val result = StringBuilder()
                    for (index in 0 until renderer.pageCount) {
                        val page = renderer.openPage(index)
                        try {
                            val scale = minOf(2.0, 1700.0 / maxOf(page.width, page.height).toDouble())
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                                result.append("\n\n").append(text.text)
                            } finally {
                                bitmap.recycle()
                            }
                        } finally {
                            page.close()
                        }
                    }
                    result.toString()
                } finally {
                    recognizer.close()
                }
            }
        }
    }
}

private fun detectPdfQuestions(rawText: String, language: String): List<PdfQuestionDraft> {
    val normalized = rawText
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace('\u0000', ' ')
    val lines = normalized.lines()
    val numberedPattern = Regex("""(?i)^\s*(?:(?:question|ques|q)\s*)?\d{1,3}\s*[\).:\-]\s*\S.*$""")
    val starts = lines.indices.filter { numberedPattern.matches(lines[it]) }
    val blocks = mutableListOf<String>()
    if (starts.size >= 3) {
        for (i in starts.indices) {
            val start = starts[i]
            val end = if (i + 1 < starts.size) starts[i + 1] else lines.size
            val block = lines.subList(start, end).joinToString("\n").trim()
            if (block.length >= 12) blocks.add(block)
        }
    } else {
        val paragraphs = normalized.split(Regex("""\n\s*\n+"""))
            .map { it.trim() }
            .filter { it.length >= 12 }
        for (paragraph in paragraphs) {
            if (paragraph.contains('?') || paragraph.contains('؟') ||
                Regex("""(?i)^\s*(?:what|why|how|when|where|who|which|explain|define|describe|calculate|find|solve|state|write|compare|choose|mention|name)\b""").containsMatchIn(paragraph)) {
                blocks.add(paragraph)
            } else {
                val questions = Regex("""(?s)([^?؟\n]{8,420}[?؟])""").findAll(paragraph).map { it.value.trim() }.toList()
                blocks.addAll(questions)
            }
        }
    }

    val optionPattern = Regex("""(?im)^\s*(?:\(?[A-Da-d]\)?[).:]|\([1-4]\)|[1-4][).]|(?:الف|ا|ب|پ|ت|ث|ج|چ|ح|خ|د|ڈ|ر|ز|س|ش|ف|ق|ک|گ|ل|م|ن|و|ہ|ھ|ی)[).:])\s*(.+?)\s*$""")
    val answerPattern = Regex("""(?im)^\s*(?:correct\s+answer|answer|ans)\s*[:\-]\s*(.+?)\s*$""")
    val unique = linkedMapOf<String, PdfQuestionDraft>()
    for (block in blocks) {
        val trimmed = block.replace(Regex("""(?m)^\s*(?:Page\s*)?\d+\s*$"""), "").trim()
        val options = optionPattern.findAll(trimmed).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.distinct().toList()
        val answerLine = answerPattern.find(trimmed)?.groupValues?.get(1)?.trim().orEmpty()
        val promptLines = trimmed.lines().filterNot { optionPattern.matches(it) || answerPattern.matches(it) }
        val prompt = promptLines.joinToString(" ")
            .replace(Regex("""(?i)^\s*(?:(?:question|ques|q)\s*)?\d{1,3}\s*[\).:\-]\s*"""), "")
            .replace(Regex("""\s+"""), " ").trim()
        if (prompt.length !in 8..1000) continue
        if (prompt.count { it.isLetterOrDigit() } < 6) continue
        val type = if (options.size >= 2) "multiple_choice" else "short_answer"
        var answer = ""
        if (answerLine.isNotBlank()) {
            answer = if (options.size >= 2) {
                val letter = answerLine.trim().trimEnd('.', ')', ' ').uppercase(Locale.ROOT)
                when {
                    letter.length == 1 && letter[0] in 'A'..'D' -> options.getOrNull(letter[0] - 'A').orEmpty()
                    else -> options.firstOrNull { it.equals(answerLine, true) }.orEmpty()
                }
            } else answerLine
        }
        val key = prompt.lowercase(Locale.ROOT).replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
        if (key.length >= 8 && !unique.containsKey(key)) {
            unique[key] = PdfQuestionDraft(
                prompt = prompt,
                optionsText = options.joinToString("\n"),
                answer = answer,
                questionType = type,
                languageCode = language
            )
        }
        if (unique.size >= 80) break
    }
    return unique.values.toList()
}
