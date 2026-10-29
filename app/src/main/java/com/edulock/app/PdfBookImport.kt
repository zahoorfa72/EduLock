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
import android.text.Html
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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

private data class StbbBook(
    val id: String,
    val title: String
)

private data class PdfSearchResult(
    val title: String,
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
    onCreateSubject: suspend (String, String, String, Int) -> SubjectRow,
    onSaveQuestions: suspend (SubjectRow, List<PdfQuestionDraft>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var destinationSubjects by remember(subjects) { mutableStateOf(subjects) }
    var countryCode by remember { mutableStateOf(defaultCountryCode.uppercase(Locale.ROOT).ifBlank { "PK" }) }
    var board by remember { mutableStateOf(defaultBoard(defaultCountryCode)) }
    var languageCode by remember { mutableStateOf(initialSubject?.languageCode ?: defaultLanguageCode.ifBlank { "en" }) }
    var languageMenu by remember { mutableStateOf(false) }
    var customLanguage by remember {
        mutableStateOf((initialSubject?.languageCode ?: defaultLanguageCode) !in pdfBookLanguages.map { it.first })
    }
    var grade by remember { mutableStateOf((initialSubject?.gradeLevel ?: 1).toString()) }
    var bookSubject by remember { mutableStateOf(initialSubject?.title ?: "") }
    var bookTitle by remember { mutableStateOf("") }
    var selectedSubjectId by remember { mutableStateOf(initialSubject?.id ?: subjects.firstOrNull()?.id.orEmpty()) }
    var countryMenu by remember { mutableStateOf(false) }
    var boardMenu by remember { mutableStateOf(false) }
    var subjectMenu by remember { mutableStateOf(false) }
    var customBoardName by remember { mutableStateOf("") }
    var subjectSearchQuery by remember { mutableStateOf("") }
    var directPdfUrl by remember { mutableStateOf("") }
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
    var savedPdfRefresh by remember { mutableStateOf(0) }
    var stbbBooks by remember { mutableStateOf<List<StbbBook>>(emptyList()) }
    var stbbBooksLoading by remember { mutableStateOf(false) }
    var pdfSearchResults by remember { mutableStateOf<List<PdfSearchResult>>(emptyList()) }
    var pdfSearchLoading by remember { mutableStateOf(false) }
    var showManualBrowser by remember { mutableStateOf(false) }

    val selectedSubject = destinationSubjects.firstOrNull { it.id == selectedSubjectId }
    val effectiveBoard = if (board.equals("Custom board", true)) customBoardName.trim().ifBlank { "Custom board" } else board
    val portalOptions = remember(countryCode, board, customBoardName, bookSubject, bookTitle, grade, languageCode) {
        portalsFor(countryCode, effectiveBoard, bookSubject, bookTitle, grade, languageCode)
    }

    suspend fun startExtraction(file: File, displayName: String) {
        working = true
        savedPdfRefresh++
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
                status = "Extracted " + parsed.size + " candidate questions. The PDF is kept on this device until you delete it manually. Review every prompt and answer before saving."
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
                status = "Copying the selected PDF into EduLock book storage…"
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
        status = "Downloading the PDF into EduLock book storage…"
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
        var downloadedFile: File? = null
        try {
            val file = downloadPdfToCache(context, url)
            downloadedFile = file
            val suggestedName = bookTitle.ifBlank { bookSubject + " - " + board + " class " + grade }
            val savedLocation = savePdfToDownloads(context, file, suggestedName)
            savedPdfRefresh++
            status = "PDF downloaded successfully to " + savedLocation + ". The in-app copy is also available until you delete it manually. Nothing was uploaded to Supabase."
            error = null
            pickedFileName = savedLocation
        } catch (e: Exception) {
            error = (e.message ?: "Could not download the PDF.") +
                if (downloadedFile == null) "" else " A valid in-app PDF copy remains available for manual deletion."
        } finally {
            working = false
        }
    }

    suspend fun findPdfDownloads() {
        if (pdfSearchLoading || working) return
        pdfSearchLoading = true
        pdfSearchResults = emptyList()
        error = null
        status = "Searching for direct PDF files for this curriculum…"
        try {
            val terms = listOf(
                bookTitle.trim(),
                bookSubject.trim(),
                "class " + grade,
                languageCode.ifBlank { "en" },
                effectiveBoard,
                countryCode,
                "textbook filetype:pdf"
            ).filter { it.isNotBlank() }.joinToString(" ")
            pdfSearchResults = searchDirectPdfResults(terms)
            status = if (pdfSearchResults.isEmpty()) {
                "No direct PDF files were found in this search. Try a shorter book/subject name or choose a PDF from your device."
            } else {
                "Found " + pdfSearchResults.size + " candidate PDF download(s). Tap Download beside a book; EduLock verifies the file is actually a PDF before saving it."
            }
        } catch (e: Exception) {
            error = e.message ?: "Could not search for direct PDF downloads. You can still choose a PDF from your device."
            status = null
        } finally {
            pdfSearchLoading = false
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
                    allCountryOptions().forEach { (code, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            countryCode = code
                            board = defaultBoard(code)
                            customBoardName = ""
                            countryMenu = false
                            activePortal = null
                            stbbBooks = emptyList()
                        })
                    }
                    DropdownMenuItem(text = { Text("Other / territory not listed") }, onClick = {
                        countryCode = "OTHER"
                        board = "Custom board"
                        customBoardName = ""
                        countryMenu = false
                        activePortal = null
                    })
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                OutlinedButton(onClick = { boardMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(effectiveBoard)
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
                if (board.equals("Custom board", true)) {
                    OutlinedTextField(
                        value = customBoardName,
                        onValueChange = { customBoardName = it.take(120); activePortal = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Enter any school board / curriculum") },
                        singleLine = true
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedButton(onClick = { languageMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(pdfBookLanguages.firstOrNull { it.first == languageCode }?.second ?: "Other (" + languageCode + ")")
                }
                DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                    pdfBookLanguages.forEach { (code, label) ->
                        DropdownMenuItem(text = { Text(label + " (" + code + ")") }, onClick = {
                            languageCode = code
                            customLanguage = false
                            languageMenu = false
                            activePortal = null
                        })
                    }
                    DropdownMenuItem(text = { Text("Other language code…") }, onClick = {
                        languageCode = ""
                        customLanguage = true
                        languageMenu = false
                        activePortal = null
                    })
                }
                if (customLanguage) {
                    OutlinedTextField(
                        value = languageCode,
                        onValueChange = { languageCode = it.filter { c -> c.isLetter() || c == '-' }.take(16) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Language code") },
                        singleLine = true
                    )
                }
            }
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
        val curriculumSubjects = remember(countryCode, effectiveBoard, grade, languageCode) {
            suggestedBookSubjects(countryCode, effectiveBoard, grade.toIntOrNull() ?: 1, languageCode)
        }
        Text("All-board subject catalogue", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = subjectSearchQuery,
            onValueChange = { subjectSearchQuery = it.take(100) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search subjects across countries and boards") },
            placeholder = { Text("Biology, robotics, music, accounting…") },
            singleLine = true
        )
        val matchingSubjects = remember(curriculumSubjects, subjectSearchQuery) {
            if (subjectSearchQuery.isBlank()) curriculumSubjects else curriculumSubjects.filter {
                it.contains(subjectSearchQuery.trim(), ignoreCase = true)
            }
        }
        val visibleSubjects = if (subjectSearchQuery.isBlank()) matchingSubjects.take(24) else matchingSubjects.take(48)
        Text(
            if (subjectSearchQuery.isBlank() && matchingSubjects.size > visibleSubjects.size)
                "Showing " + visibleSubjects.size + " of " + matchingSubjects.size + " subjects. Search above to find more."
            else visibleSubjects.size.toString() + " matching subject(s). You can also type any unlisted subject in the field below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visibleSubjects.chunked(3).forEach { subjectRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    subjectRow.forEach { suggestion ->
                        OutlinedButton(
                            onClick = { bookSubject = suggestion },
                            modifier = Modifier.weight(1f)
                        ) { Text(suggestion, maxLines = 2) }
                    }
                    repeat((3 - subjectRow.size).coerceAtLeast(0)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
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
            destinationSubjects.forEach { subject ->
                DropdownMenuItem(text = {
                    Text(subject.title + " • " + subject.languageCode + " • Grade " + subject.gradeLevel)
                }, onClick = {
                    selectedSubjectId = subject.id
                    bookSubject = subject.title
                    languageCode = subject.languageCode
                    customLanguage = subject.languageCode !in pdfBookLanguages.map { it.first }
                    grade = subject.gradeLevel.toString()
                    subjectMenu = false
                })
            }
        }
        if (destinationSubjects.isEmpty()) {
            Text("Create a subject first, then return here to import its PDF questions.", color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = {
                scope.launch {
                    working = true
                    error = null
                    try {
                        val title = bookSubject.trim()
                        require(title.isNotBlank()) { "Enter or select a subject name first." }
                        val created = onCreateSubject(
                            title,
                            languageCode.ifBlank { "en" },
                            countryCode,
                            grade.toIntOrNull() ?: 1
                        )
                        destinationSubjects = (destinationSubjects.filterNot { it.id == created.id } + created)
                        selectedSubjectId = created.id
                        bookSubject = created.title
                        status = "Subject ready: " + created.title + " • " + created.languageCode + " • Grade " + created.gradeLevel + ". Select a PDF or choose a direct textbook download below."
                    } catch (e: Exception) {
                        error = e.message ?: "Could not create or select this subject."
                    } finally {
                        working = false
                    }
                }
            },
            enabled = !working && bookSubject.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create / select this subject")
        }

        Button(
            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
            enabled = !working,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Choose PDF from device") }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Get a textbook PDF", style = MaterialTheme.typography.titleMedium)
                Text(
                    "1. Paste the actual PDF download link below, or 2. open an official textbook website further down and tap a book's PDF/download link. EduLock will save the PDF instead of sending you to another browser app.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = directPdfUrl,
                    onValueChange = { directPdfUrl = it.trim().take(2048) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Direct PDF download link (HTTPS)") },
                    placeholder = { Text("https://website.gov/textbook.pdf") },
                    singleLine = true
                )
                Button(
                    onClick = { scope.launch { downloadForDevice(directPdfUrl.trim()) } },
                    enabled = !working && directPdfUrl.trim().startsWith("https://", true),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Download PDF to device") }
                OutlinedButton(
                    onClick = { scope.launch { downloadForImport(directPdfUrl.trim()) } },
                    enabled = !working && directPdfUrl.trim().startsWith("https://", true),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Extract questions from PDF") }
                Text(
                    "Saved PDFs go to Downloads/EduLock Books. EduLock keeps an in-app copy until you delete it manually. Use a direct PDF file/download URL, not just a textbook website homepage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (countryCode.equals("PK", true) && effectiveBoard.contains("sindh", ignoreCase = true)) {
            Button(
                onClick = {
                    scope.launch {
                        stbbBooksLoading = true
                        error = null
                        status = "Finding Sindh Textbook Board books for class " + grade + "…"
                        try {
                            stbbBooks = fetchStbbBooks(grade.toIntOrNull() ?: 1, languageCode)
                            status = if (stbbBooks.isEmpty()) {
                                "No matching books were listed for this class and medium. Try All mediums or use the official catalogue below."
                            } else {
                                "Found " + stbbBooks.size + " book(s) listed by the official Sindh Textbook Board. Use Download PDF beside a book to save it directly without browsing the website."
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Could not load the official STBB book list. You can still use the catalogue below."
                            stbbBooks = emptyList()
                        } finally {
                            stbbBooksLoading = false
                        }
                    }
                },
                enabled = !working && !stbbBooksLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (stbbBooksLoading) "Finding textbook PDFs…" else "Find direct Sindh textbook PDF downloads")
            }
            if (stbbBooks.isNotEmpty()) {
                Text("Books available for class " + grade + " • " + (pdfBookLanguages.firstOrNull { it.first == languageCode }?.second ?: languageCode), style = MaterialTheme.typography.titleMedium)
                stbbBooks.forEach { book ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(book.title, style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val url = stbbPdfDownloadUrl(book.id)
                                        scope.launch { downloadForDevice(url) }
                                    },
                                    enabled = !working,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Download PDF") }
                                OutlinedButton(
                                    onClick = {
                                        val url = stbbPdfDownloadUrl(book.id)
                                        scope.launch { downloadForImport(url) }
                                    },
                                    enabled = !working,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Extract questions") }
                            }
                        }
                    }
                }
            }
        }

        Text("Find and download textbook PDFs", style = MaterialTheme.typography.titleMedium)
        Text(
            "No website page is opened. EduLock searches for downloadable PDF files and lists the results here. Tap Download next to the book you need.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = { scope.launch { findPdfDownloads() } },
            enabled = !working && !pdfSearchLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (pdfSearchLoading) "Searching for PDFs…" else "Find PDFs for this book")
        }
        if (pdfSearchLoading) {
            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (pdfSearchResults.isNotEmpty()) {
            Text("Direct PDF results", style = MaterialTheme.typography.titleMedium)
            pdfSearchResults.forEach { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(result.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            Uri.parse(result.url).host.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { scope.launch { downloadForDevice(result.url) } },
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Download PDF") }
                        OutlinedButton(
                            onClick = { scope.launch { downloadForImport(result.url) } },
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Extract questions") }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { showManualBrowser = !showManualBrowser; if (!showManualBrowser) activePortal = null },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showManualBrowser) "Close optional website browser" else "Need a website? Open optional browser")
        }
        Text(
            "Accuracy note: EduLock checks that a downloaded file really begins as a PDF before saving it. Search results and external sites are not guaranteed to work or contain the exact edition. Text-based PDFs extract best; scanned Urdu/Sindhi PDFs may need manual checking.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val savedPdfFiles = remember(savedPdfRefresh) { File(context.filesDir, "pdf-books").listFiles()?.filter { it.isFile && it.extension.equals("pdf", true) }?.sortedByDescending { it.lastModified() } ?: emptyList() }
        if (savedPdfFiles.isNotEmpty()) {
            Text("Saved PDFs on this device", style = MaterialTheme.typography.titleMedium)
            savedPdfFiles.forEach { savedPdf ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(savedPdf.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        if (savedPdf.delete()) {
                            savedPdfRefresh++
                            if (temporaryPdf == savedPdf) temporaryPdf = null
                            status = "Deleted " + savedPdf.name + " from this device."
                        } else error = "Could not delete this PDF."
                    }, enabled = !working) { Text("Delete") }
                }
            }
        }
        if (showManualBrowser) {
            Text("Optional website browser", style = MaterialTheme.typography.titleMedium)
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
        }
        if (showManualBrowser) activePortal?.let { portal ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(portal.title, style = MaterialTheme.typography.titleMedium)
                    if (!portal.title.startsWith("Search downloadable PDF copies")) {
                        OutlinedButton(
                            onClick = {
                                val query = Uri.encode(
                                    "\"$bookTitle\" \"$bookSubject\" class $grade $languageCode textbook filetype:pdf \"$effectiveBoard\" \"$countryCode\""
                                )
                                activePortal = BookPortal(
                                    "Search downloadable PDF copies",
                                    "Open a PDF result below, then choose Download PDF to device or Extract questions.",
                                    "https://www.google.com/search?q=" + query
                                )
                            },
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Can't download here? Find PDF downloads") }
                    }
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
                                        val pdfTarget = unwrapPdfRedirect(target)
                                        if (pdfTarget != null) {
                                            pendingPdfUrl = pdfTarget
                                            showPdfActions = true
                                            return true
                                        }
                                        return false
                                    }

                                    @Suppress("DEPRECATION")
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        val target = url ?: return false
                                        val pdfTarget = unwrapPdfRedirect(target)
                                        if (pdfTarget != null) {
                                            pendingPdfUrl = pdfTarget
                                            showPdfActions = true
                                            return true
                                        }
                                        return false
                                    }
                                }
                                setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                                    if (url.startsWith("https://", true) || url.startsWith("http://", true)) {
                                        // WebView's download callback also fires for endpoints without
                                        // a .pdf suffix. The downloader validates the response body.
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
                        "Open the official class catalogue, choose the matching book, then tap its Download PDF control. EduLock catches direct PDF links (including STBB download links) and offers Download PDF to device or Extract questions from PDF without opening another browser app.",
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
                text = { Text("Choose what to do with this book. Importing extracts questions locally and keeps the PDF in EduLock book storage until you delete it manually. Downloading saves a copy to Downloads/EduLock Books.") },
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
                                status = "Reviewed questions saved. Your PDF remains on this device; delete it manually when no longer needed."
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
                extractedText = ""
                candidates = emptyList()
                status = "Question review cleared. Saved PDFs remain on this device until manually deleted."
                error = null
            }, enabled = !working, modifier = Modifier.weight(1f)) { Text("Clear import") }
        }
        if (bookTitle.isNotBlank()) {
            Text("Book: " + bookTitle + " • " + countryCode + " • " + effectiveBoard + " • class " + grade + " • " + languageCode)
        } else {
            Text("Selected curriculum: " + countryCode + " • " + board + " • class " + grade + " • " + languageCode, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val pdfBookLanguages = listOf(
    "en" to "English", "ur" to "Urdu", "sd" to "Sindhi", "hi" to "Hindi",
    "bn" to "Bangla", "ar" to "Arabic", "pa" to "Punjabi", "ps" to "Pashto",
    "ta" to "Tamil", "te" to "Telugu", "fr" to "French", "es" to "Spanish",
    "de" to "German", "it" to "Italian", "pt" to "Portuguese", "ru" to "Russian",
    "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean", "tr" to "Turkish",
    "fa" to "Persian", "he" to "Hebrew", "id" to "Indonesian", "ms" to "Malay",
    "th" to "Thai", "vi" to "Vietnamese", "sw" to "Swahili", "ha" to "Hausa",
    "yo" to "Yoruba", "ig" to "Igbo", "am" to "Amharic", "so" to "Somali",
    "ne" to "Nepali", "si" to "Sinhala", "mr" to "Marathi", "gu" to "Gujarati",
    "kn" to "Kannada", "ml" to "Malayalam", "or" to "Odia", "as" to "Assamese",
    "sa" to "Sanskrit", "mai" to "Maithili", "kok" to "Konkani", "ks" to "Kashmiri",
    "doi" to "Dogri", "brx" to "Bodo", "mni" to "Meitei", "uz" to "Uzbek",
    "kk" to "Kazakh", "ky" to "Kyrgyz", "tg" to "Tajik", "tk" to "Turkmen",
    "az" to "Azerbaijani", "hy" to "Armenian", "ka" to "Georgian", "el" to "Greek",
    "nl" to "Dutch", "pl" to "Polish", "uk" to "Ukrainian", "ro" to "Romanian",
    "hu" to "Hungarian", "cs" to "Czech", "sk" to "Slovak", "sv" to "Swedish",
    "no" to "Norwegian", "da" to "Danish", "fi" to "Finnish", "sq" to "Albanian",
    "sr" to "Serbian", "hr" to "Croatian", "bs" to "Bosnian", "bg" to "Bulgarian",
    "rw" to "Kinyarwanda", "ki" to "Kikuyu", "zu" to "Zulu", "xh" to "Xhosa",
    "af" to "Afrikaans", "st" to "Sesotho", "tn" to "Setswana", "mg" to "Malagasy",
    "my" to "Burmese", "km" to "Khmer", "lo" to "Lao", "mn" to "Mongolian"
)

private fun suggestedBookSubjects(country: String, board: String, grade: Int, language: String): List<String> {
    val cc = country.uppercase(Locale.ROOT)
    val b = board.lowercase(Locale.ROOT)
    val primaryLanguages = listOf(
        "English", "Urdu", "Sindhi", "Punjabi", "Pashto", "Balochi", "Arabic", "Persian",
        "Hindi", "Bangla", "Tamil", "Telugu", "Marathi", "Gujarati", "Kannada", "Malayalam",
        "Odia", "Assamese", "Nepali", "Sinhala", "Chinese", "Japanese", "Korean", "French",
        "Spanish", "Portuguese", "German", "Russian", "Turkish", "Swahili", "Hausa", "Yoruba",
        "Indonesian", "Malay", "Thai", "Vietnamese", "Italian", "Dutch", "Greek", "Hebrew",
        "Local / heritage language", "Second language", "Foreign language"
    )
    val foundational = listOf(
        "Early Childhood Education", "Pre-literacy", "Reading", "Writing", "Phonics",
        "Numeracy", "Mathematics", "General Knowledge", "Environmental Studies",
        "General Science", "Social Studies", "Creative Arts", "Drawing / Art",
        "Music", "Dance", "Drama", "Physical Education", "Health Education",
        "Moral Education", "Civic Education", "Digital Literacy", "Computer Basics",
        "Life Skills", "Safety Education", "Nature Study", "Storytelling",
        "Religious Studies", "Islamic Studies", "Quran / Quranic Studies", "Ethics"
    )
    val primary = listOf(
        "Science", "Science and Technology", "Integrated Studies", "Humanities",
        "Geography", "History", "Civics", "Citizenship Education", "World Studies",
        "Local Studies", "Community Studies", "Pakistan Studies", "Bangladesh and Global Studies",
        "Indian Knowledge Systems", "Social and Political Life", "Computer Studies",
        "Computer Science", "Information and Communication Technology", "Coding",
        "Programming", "Robotics", "Engineering Design", "Design and Technology",
        "Technology Education", "Media Literacy", "Digital Citizenship", "Cyber Safety",
        "Financial Literacy", "Entrepreneurship", "Business Studies", "Economics",
        "Agriculture", "Food and Nutrition", "Home Economics", "Textiles",
        "Crafts and Handicrafts", "Visual Arts", "Performing Arts", "Photography",
        "Physical Wellbeing", "Sports", "Yoga", "Mindfulness", "Personal Development",
        "Social Emotional Learning", "Values Education", "Comparative Religion",
        "Environmental Education", "Climate Education", "Sustainability",
        "Disaster Risk Reduction", "First Aid", "Library Skills", "Research Skills"
    )
    val secondary = listOf(
        "General Mathematics", "Additional Mathematics", "Advanced Mathematics",
        "Further Mathematics", "Statistics", "Applied Mathematics", "Pure Mathematics",
        "Physics", "Chemistry", "Biology", "General Science", "Earth Science",
        "Environmental Science", "Marine Science", "Astronomy", "Space Science",
        "Computer Science", "Information Technology", "Software Development",
        "Artificial Intelligence", "Data Science", "Cybersecurity", "Web Development",
        "Electronics", "Engineering", "Engineering Science", "Biotechnology",
        "Anatomy and Physiology", "Health Science", "Agricultural Science",
        "Animal Husbandry", "Crop Science", "Forestry", "Geology", "Geography",
        "Human Geography", "Physical Geography", "World History", "National History",
        "Modern History", "Ancient History", "Political Science", "Government",
        "Civics", "Sociology", "Psychology", "Philosophy", "Anthropology",
        "Economics", "Macroeconomics", "Microeconomics", "Accounting",
        "Principles of Accounting", "Bookkeeping", "Commerce", "Business Studies",
        "Business Management", "Marketing", "Entrepreneurship", "Finance",
        "Banking", "Insurance", "Law", "Legal Studies", "International Relations",
        "Religious Studies", "Islamic Studies", "Islamiat", "Ethics", "Moral Philosophy",
        "Quran Studies", "Arabic Studies", "Bible Studies", "Theology",
        "Education", "Child Development", "Early Childhood Development",
        "Home Economics", "Family and Consumer Science", "Food Science",
        "Nutrition", "Hospitality", "Tourism", "Culinary Arts", "Fashion Design",
        "Textile Design", "Graphic Design", "Fine Arts", "Visual Arts",
        "Applied Arts", "Music", "Music Theory", "Theatre Studies", "Drama",
        "Film Studies", "Media Studies", "Journalism", "Communication Studies",
        "Creative Writing", "Literature", "World Literature", "Linguistics",
        "Translation Studies", "Classical Studies", "Latin", "Sanskrit",
        "Technical Drawing", "Technical Education", "Vocational Education",
        "Woodwork", "Metalwork", "Construction", "Automotive Technology",
        "Electrical Installation", "Renewable Energy", "Plumbing", "Welding",
        "Agricultural Technology", "Business Computing", "Office Administration",
        "Physical Education", "Sports Science", "Human Biology", "Public Health",
        "First Aid", "Environmental Management", "Climate Change", "Geospatial Science",
        "Geographic Information Systems", "Research Methods", "Statistics and Probability",
        "Career Education", "Life Skills", "Civic and Citizenship Education"
    )
    val gradeSpecific = when {
        grade <= 2 -> (foundational + primary.filter {
            it in setOf("Science", "Geography", "History", "Computer Studies", "Coding",
                "Design and Technology", "Financial Literacy", "Environmental Education", "Sustainability")
        } + primaryLanguages)
        grade <= 5 -> foundational + primary + primaryLanguages
        grade <= 8 -> foundational + primary + secondary.filter {
            it in setOf("Mathematics", "General Mathematics", "Physics", "Chemistry", "Biology",
                "Computer Science", "Information Technology", "Geography", "History", "Civics",
                "Economics", "Accounting", "Agricultural Science", "Programming", "Robotics",
                "Environmental Science", "Statistics", "Fine Arts", "Health Science", "Religious Studies")
        } + primaryLanguages
        else -> primary + secondary + primaryLanguages
    }
    val regional = when (cc) {
        "PK" -> listOf("Pakistan Studies", "Islamiat", "Ethics", "Sindhi Compulsory", "Urdu Compulsory",
            "English Compulsory", "Mazhabi Taleemat", "Asan Sindhi", "Tarjuma-tul-Quran",
            "Khyber Pakhtunkhwa Studies", "Balochistan Studies", "Computer Applications")
        "IN" -> listOf("Environmental Studies (EVS)", "Social Science", "Indian Knowledge Systems",
            "Constitution of India", "Work Education", "Art Education", "Health and Physical Education",
            "Vocational Education", "Sanskrit", "Hindi")
        "BD" -> listOf("Bangladesh and Global Studies", "Bangla", "Religion and Moral Education",
            "Digital Technology", "Agricultural Studies", "Career Education")
        "GB" -> listOf("English Language", "English Literature", "Combined Science", "Design and Technology",
            "Religious Education", "Personal, Social, Health and Economic Education", "Welsh")
        "US" -> listOf("English Language Arts", "Common Core Mathematics", "US History", "Civics",
            "Health and Wellness", "Career and Technical Education", "Advanced Placement")
        "CA" -> listOf("French Immersion", "Indigenous Studies", "Canadian History", "Canadian Geography")
        "AU" -> listOf("Humanities and Social Sciences (HASS)", "Aboriginal and Torres Strait Islander Histories",
            "Civics and Citizenship", "Health and Physical Education")
        "NZ" -> listOf("Te Reo Māori", "Aotearoa New Zealand Histories", "Technology")
        "ZA" -> listOf("Life Orientation", "Economic and Management Sciences", "Creative Arts",
            "Technology", "Life Sciences", "Natural Sciences")
        "NG" -> listOf("Civic Education", "Basic Science and Technology", "Social Studies",
            "Christian Religious Studies", "Islamic Religious Studies", "National Values Education")
        "KE" -> listOf("Integrated Science", "Social Studies", "Indigenous Language", "Pre-Technical Studies",
            "Agriculture and Nutrition", "Christian Religious Education", "Islamic Religious Education")
        "GH" -> listOf("Our World Our People", "Career Technology", "Creative Arts and Design",
            "Religious and Moral Education", "Ghanaian Language")
        "PH" -> listOf("Araling Panlipunan", "Edukasyon sa Pagpapakatao", "Filipino",
            "Edukasyong Pantahanan at Pangkabuhayan", "Mother Tongue", "MAPEH", "Makabansa")
        "MY" -> listOf("Bahasa Melayu", "Pendidikan Islam", "Pendidikan Moral", "Sejarah", "Reka Bentuk dan Teknologi")
        "ID" -> listOf("Bahasa Indonesia", "Pendidikan Pancasila", "Pendidikan Agama", "Prakarya", "Muatan Lokal")
        "SG" -> listOf("Character and Citizenship Education", "Mother Tongue Language", "Project Work")
        "LK" -> listOf("Sinhala", "Tamil", "Buddhism", "Hinduism", "Christianity", "Civic Education")
        "NP" -> listOf("Nepali", "Social Studies and Human Values Education", "Occupation, Business and Technology")
        "AE", "SA", "QA", "KW", "BH", "OM", "JO", "EG" -> listOf("Arabic", "Islamic Education",
            "Moral Education", "Social Studies", "National Education")
        "FR" -> listOf("Philosophy", "Enseignement moral et civique", "Sciences de la vie et de la Terre")
        "DE" -> listOf("Deutsch", "Politik", "Wirtschaft", "Naturwissenschaften")
        "ES" -> listOf("Lengua Castellana", "Conocimiento del Medio", "Educación en Valores Cívicos y Éticos")
        "BR" -> listOf("Língua Portuguesa", "História do Brasil", "Geografia do Brasil")
        "MX" -> listOf("Formación Cívica y Ética", "Lengua Materna", "Saberes y Pensamiento Científico")
        "CN" -> listOf("Chinese Language", "Morality and Rule of Law", "Labor Education")
        "JP" -> listOf("Japanese Language", "Moral Education", "生活科", "総合的な学習の時間")
        "KR" -> listOf("Korean Language", "Moral Education", "Practical Arts")
        else -> emptyList()
    }
    val boardSpecific = when {
        b.contains("cambridge") || b.contains("caie") || b.contains("igcse") ->
            listOf("Cambridge Global Perspectives", "Global Perspectives", "Environmental Management", "Enterprise")
        b.contains("pearson") || b.contains("edexcel") -> listOf("Global Citizenship", "Statistics")
        b.contains("baccalaureate") || b == "ib" || b.contains("(ib)") ->
            listOf("Theory of Knowledge", "Creativity Activity Service", "Extended Essay", "Global Politics")
        b.contains("common core") || b.contains("american") ->
            listOf("English Language Arts", "US History", "US Government", "Personal Finance", "AP Capstone")
        else -> emptyList()
    }
    return (gradeSpecific + regional + boardSpecific).distinct()
}

private fun allCountryOptions(): List<Pair<String, String>> =
    Locale.getISOCountries().map { code ->
        code to Locale("", code).getDisplayCountry(Locale.ENGLISH).ifBlank { code }
    }.filter { it.second.isNotBlank() }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase(Locale.ROOT) }

private fun defaultBoard(country: String): String = when (country.uppercase(Locale.ROOT)) {
    "PK" -> "Punjab (PECTAA)"
    "IN" -> "NCERT"
    "BD" -> "NCTB"
    "GB" -> "National Curriculum"
    "US" -> "State / District"
    "CA" -> "Provincial curriculum"
    "AU" -> "Australian Curriculum"
    "NZ" -> "New Zealand Curriculum"
    "ZA" -> "CAPS / DBE"
    "NG" -> "National curriculum"
    "KE" -> "CBC / KICD"
    "GH" -> "NaCCA curriculum"
    "PH" -> "DepEd K–12"
    "MY" -> "KSSR / KSSM"
    "SG" -> "MOE Singapore"
    else -> "National curriculum"
}

private fun boardsFor(country: String): List<String> {
    val global = listOf(
        "National curriculum", "Public school / regional board",
        "Cambridge International (CAIE)", "Pearson Edexcel", "International Baccalaureate (IB)",
        "American curriculum", "French curriculum", "Australian curriculum",
        "Oxford International Curriculum", "Montessori curriculum", "Other international curriculum",
        "Custom board"
    )
    val regional = when (country.uppercase(Locale.ROOT)) {
        "PK" -> listOf("Punjab (PECTAA)", "Punjab eLearn", "Sindh (STBB)", "Federal (FBISE)",
            "Khyber Pakhtunkhwa (KPTBB)", "Balochistan (BBISE)", "Azad Jammu & Kashmir", "Gilgit-Baltistan",
            "Cambridge International (CAIE)", "Pearson Edexcel", "Other provincial / private board")
        "IN" -> listOf("NCERT", "CBSE", "CISCE / ICSE / ISC", "NIOS", "IB", "Cambridge International (CAIE)",
            "Maharashtra State Board", "Tamil Nadu State Board", "Karnataka State Board", "Kerala State Board",
            "West Bengal Board", "UP Board", "Bihar Board", "Rajasthan Board", "Gujarat Board",
            "Madhya Pradesh Board", "Andhra Pradesh Board", "Telangana Board", "Punjab School Education Board",
            "Haryana Board", "Assam Board", "Odisha Board", "Other State Board")
        "BD" -> listOf("NCTB", "Madrasah Education Board", "English Version", "English Medium / Cambridge", "Other board")
        "GB" -> listOf("National Curriculum", "England", "Wales", "Scotland", "Northern Ireland",
            "AQA", "OCR", "Pearson Edexcel", "WJEC / Eduqas", "SQA", "CCEA")
        "US" -> listOf("Common Core", "State / District", "AP / College Board", "IB", "Texas TEKS",
            "California Curriculum", "New York State", "Florida Standards", "Other state / district")
        "CA" -> listOf("Provincial curriculum", "Ontario Curriculum", "British Columbia Curriculum", "Alberta Curriculum",
            "Quebec Curriculum", "Saskatchewan Curriculum", "Manitoba Curriculum", "Atlantic Provinces", "Other province / territory")
        "AU" -> listOf("Australian Curriculum (ACARA)", "NSW syllabus", "Victorian Curriculum", "Queensland Curriculum",
            "Western Australia Curriculum", "South Australia Curriculum", "Tasmania Curriculum", "ACT Curriculum", "NT Curriculum")
        "NZ" -> listOf("New Zealand Curriculum", "Te Marautanga o Aotearoa", "NCEA", "Cambridge International (CAIE)")
        "ZA" -> listOf("CAPS / DBE", "IEB", "South African National Curriculum", "Cambridge International (CAIE)")
        "NG" -> listOf("NERDC", "WAEC / WASSCE", "NECO", "Lagos State scheme", "Other state scheme")
        "KE" -> listOf("CBC / KICD", "KCSE / KICD", "Cambridge International (CAIE)")
        "GH" -> listOf("NaCCA curriculum", "WAEC", "Cambridge International (CAIE)")
        "PH" -> listOf("DepEd K–12", "MATATAG Curriculum", "CHED", "Cambridge International (CAIE)")
        "MY" -> listOf("KSSR / KSSM", "MOE Malaysia", "Cambridge International (CAIE)")
        "SG" -> listOf("MOE Singapore", "Singapore-Cambridge", "IB")
        "ID" -> listOf("Kurikulum Merdeka", "Kurikulum 2013", "Cambridge International (CAIE)")
        "LK" -> listOf("National curriculum / NIE", "GCE O/L and A/L")
        "NP" -> listOf("CDC Nepal", "National Curriculum Development Centre")
        "AE", "SA", "QA", "KW", "BH", "OM", "JO", "EG" ->
            listOf("National curriculum / Ministry of Education", "International Baccalaureate (IB)",
                "Cambridge International (CAIE)", "Pearson Edexcel")
        "FR" -> listOf("Programme national", "Baccalauréat", "Cambridge International (CAIE)")
        "DE" -> listOf("Landeslehrplan / State curriculum", "Abitur", "IB")
        "ES" -> listOf("Currículo nacional / autónomo", "Bachillerato", "IB")
        "BR" -> listOf("BNCC", "ENEM", "State curriculum")
        "MX" -> listOf("SEP / Plan de Estudios", "State curriculum")
        "CN" -> listOf("National curriculum / Ministry of Education", "International curriculum")
        "JP" -> listOf("MEXT Course of Study", "IB")
        "KR" -> listOf("National Curriculum / MOE", "CSAT curriculum")
        else -> emptyList()
    }
    return (regional + global).distinct()
}

private fun portalsFor(country: String, board: String, subject: String, title: String, grade: String, language: String): List<BookPortal> {
    val cc = country.uppercase(Locale.ROOT)
    val b = board.lowercase(Locale.ROOT)
    val curated = when {
        cc == "PK" && b.contains("sindh") -> listOf(
            BookPortal("Sindh Textbook Board eBooks", "Official STBB catalogue • Classes Katchi–XII • choose your medium, then tap Download PDF", "https://portal.stbb.edu.pk/ebooks/?medium=" + Uri.encode(when (language) { "sd" -> "Sindhi"; "ur" -> "Urdu"; "en" -> "English"; else -> "All" }))
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
    val query = Uri.encode("\"" + title + "\" \"" + subject + "\" class " + grade + " " + language + " textbook filetype:pdf official " + country + " " + board + " government book download")
    val exactSearch = BookPortal(
        "Search direct PDF results for this book",
        "Prioritizes PDF files for the selected country, board, class, subject and language inside EduLock",
        "https://www.google.com/search?q=" + query
    )
    // The dedicated STBB in-app downloader is available above, so avoid sending Sindh-board users to a generic Google results page.
    return if (cc == "PK" && b.contains("sindh")) curated else (curated + exactSearch).take(4)
}

private fun stbbPdfDownloadUrl(bookId: String): String =
    "https://portal.stbb.edu.pk/ebooks/pdf_proxy.php?download=1&id=" + Uri.encode(bookId)

private suspend fun fetchStbbBooks(grade: Int, languageCode: String): List<StbbBook> =
    withContext(Dispatchers.IO) {
        // STBB uses class id 1 for Class I, 2 for Katchi, and 3 onward for Class II–XII.
        val classId = if (grade <= 1) 1 else grade + 1
        val baseUrl = "https://portal.stbb.edu.pk/ebooks/class.php?id=" + classId
        val anchorPattern = Regex("""(?is)<a\b[^>]*href=["'](?:\./)?book\.php\?id=(\d+)["'][^>]*>(.*?)</a>""")

        fun parseBookLinks(html: String): List<StbbBook> =
            anchorPattern.findAll(html).mapNotNull { match ->
                val id = match.groupValues[1]
                val title = match.groupValues[2]
                    .replace(Regex("""(?is)<[^>]+>"""), " ")
                    .replace("&amp;", "&")
                    .replace("&nbsp;", " ")
                    .replace("&#39;", "'")
                    .replace("&quot;", "\"")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                if (title.isBlank()) null else StbbBook(id, title)
            }.distinctBy { it.id }.toList()

        val collectedBooks = mutableListOf<StbbBook>()
        // STBB paginates larger classes (for example Class VI has 26 editions); load subsequent pages too.
        for (page in 1..6) {
            val pageUrl = if (page == 1) baseUrl else baseUrl + "&page=" + page
            val request = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", "EduLock textbook catalogue")
                .build()
            val html = bookHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (page > 1) "" else throw IllegalStateException(
                        "STBB returned HTTP " + response.code + " while listing textbooks."
                    )
                } else {
                    response.body?.string().orEmpty()
                }
            }
            val pageBooks = parseBookLinks(html)
            if (pageBooks.isEmpty()) break
            val previousCount = collectedBooks.size
            pageBooks.forEach { book ->
                if (collectedBooks.none { it.id == book.id }) collectedBooks.add(book)
            }
            if (collectedBooks.size == previousCount || pageBooks.size < 20) break
        }

        val medium = when (languageCode.lowercase(Locale.ROOT)) {
            "sd" -> "sindhi"
            "ur" -> "urdu"
            "en" -> "english"
            else -> ""
        }
        val mediumBooks = if (medium.isBlank()) collectedBooks else collectedBooks.filter {
            it.title.contains(medium, ignoreCase = true)
        }
        if (mediumBooks.isNotEmpty()) mediumBooks else collectedBooks
    }

private fun unwrapPdfRedirect(url: String): String? {
    if (looksLikePdfUrl(url)) return url
    return try {
        val uri = Uri.parse(url)
        val candidates = listOf("url", "q", "adurl", "target", "file")
            .mapNotNull { key -> uri.getQueryParameter(key) }
        candidates.firstOrNull { looksLikePdfUrl(it) }
    } catch (_: Exception) {
        null
    }
}

private fun looksLikePdfUrl(url: String): Boolean {
    val lower = url.lowercase(Locale.ROOT)
    val path = lower.substringBefore('?').substringBefore('#')
    return path.endsWith(".pdf") ||
        lower.contains("download=pdf") ||
        lower.contains("download=1") ||
        lower.contains("download=true") ||
        lower.contains("format=pdf") ||
        lower.contains("/download/") ||
        lower.contains("pdf_proxy.php") ||
        lower.contains("attachment=1") ||
        lower.contains("filetype=pdf") ||
        lower.contains("type=pdf") ||
        lower.contains("export=download") ||
        lower.contains("disposition=attachment")
}

private suspend fun copyUriToCache(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val folder = File(context.filesDir, "pdf-books").apply { mkdirs() }
    val out = File(folder, "picked-" + System.currentTimeMillis() + ".pdf")
    try {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open the selected PDF.")
        input.use { source -> out.outputStream().use { target -> source.copyTo(target) } }
        validatePdfFile(out)
        out
    } catch (e: Exception) {
        out.delete()
        throw e
    }
}

private suspend fun downloadPdfToCache(context: Context, url: String): File = withContext(Dispatchers.IO) {
    require(url.startsWith("https://", true)) { "Only secure HTTPS book downloads are allowed." }
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
        .header("Accept", "application/pdf,application/octet-stream;q=0.9,*/*;q=0.8")
        .header("Referer", if (url.startsWith("https://portal.stbb.edu.pk/ebooks/", true)) "https://portal.stbb.edu.pk/ebooks/" else url)
        .build()
    bookHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IllegalStateException("The book website returned HTTP " + response.code + ".")
        val body = response.body ?: throw IllegalStateException("The book website returned an empty file.")
        val length = body.contentLength()
        require(length <= 120L * 1024L * 1024L || length < 0L) { "PDF exceeds the 120 MB import limit." }
        val folder = File(context.filesDir, "pdf-books").apply { mkdirs() }
        val out = File(folder, "download-" + System.currentTimeMillis() + ".pdf")
        try {
            body.byteStream().use { input -> out.outputStream().use { output ->
                val prefix = ByteArray(5)
                val firstCount = input.read(prefix)
                if (firstCount < 5 || String(prefix, 0, 5, Charsets.US_ASCII) != "%PDF-") {
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
        } catch (e: Exception) {
            out.delete()
            throw e
        }
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
