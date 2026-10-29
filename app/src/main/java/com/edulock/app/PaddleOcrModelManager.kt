package com.edulock.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.RandomAccessFile
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads the shared detector and only the recognition model needed for the selected language.
 * Models are stored in private app storage and reused offline after the first download.
 * Model files are from PaddlePaddle's official PP-OCRv5 Android inference archives.
 */
internal object PaddleOcrModelManager {
    data class ModelFiles(
        val detectionModel: File,
        val recognitionModel: File,
        val recognitionDictionary: File,
        val languageModel: String,
    )

    private const val HF_BASE = "https://huggingface.co/PaddlePaddle/"
    private const val MAX_MODEL_BYTES = 180L * 1024L * 1024L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .build()
    }

    fun modelForLanguage(languageCode: String): String {
        val code = languageCode.lowercase(Locale.ROOT).replace('_', '-')
        return when {
            code in setOf("sd", "ur", "ar", "fa", "ps", "ckb", "ug", "ku", "bal") ||
                code.startsWith("ar-") || code.startsWith("ur-") || code.startsWith("sd-") ||
                code.startsWith("ps-") -> "arabic_PP-OCRv5_mobile_rec"
            code in setOf("hi", "mr", "ne", "sa", "mai", "bh", "bho", "gom", "new", "sck") ||
                code.startsWith("hi-") || code.startsWith("ne-") -> "devanagari_PP-OCRv5_mobile_rec"
            code in setOf("ru", "be", "uk") || code.startsWith("ru-") || code.startsWith("uk-") ->
                "eslav_PP-OCRv5_mobile_rec"
            code in setOf("bg", "sr", "mn", "kk", "ky", "tg", "mk", "tt", "ba", "av", "ady", "kbd") ||
                code.startsWith("sr-cyrl") -> "cyrillic_PP-OCRv5_mobile_rec"
            code == "th" || code.startsWith("th-") -> "th_PP-OCRv5_mobile_rec"
            code == "el" || code.startsWith("el-") -> "el_PP-OCRv5_mobile_rec"
            code == "ta" || code.startsWith("ta-") -> "ta_PP-OCRv5_mobile_rec"
            code == "te" || code.startsWith("te-") -> "te_PP-OCRv5_mobile_rec"
            code == "ko" || code.startsWith("ko-") -> "korean_PP-OCRv5_mobile_rec"
            code == "en" || code.startsWith("en-") -> "en_PP-OCRv5_mobile_rec"
            code == "zh" || code.startsWith("zh-") || code == "ja" || code.startsWith("ja-") ->
                "PP-OCRv5_mobile_rec_onnx"
            else -> "latin_PP-OCRv5_mobile_rec"
        }
    }

    suspend fun ensureModels(
        context: Context,
        languageCode: String,
        onProgress: (String) -> Unit,
    ): ModelFiles {
        val modelName = modelForLanguage(languageCode.ifBlank { "en" })
        val root = File(context.filesDir, "ocr-models/paddleocr-v5").apply { mkdirs() }
        val detDir = File(root, "detector").apply { mkdirs() }
        val recDir = File(root, modelName).apply { mkdirs() }
        val detModel = File(detDir, "inference.onnx")
        val recModel = File(recDir, "inference.onnx")
        val dictionary = File(recDir, "inference.yml")

        if (!isReady(detModel, null)) {
            postProgress(onProgress, "Downloading multilingual OCR detector (one time)…")
            downloadFile(
                HF_BASE + "PP-OCRv5_mobile_det_onnx/resolve/main/inference.onnx?download=true",
                detModel,
                minBytes = 100_000L,
                onProgress = onProgress,
                label = "text detector"
            )
        }
        if (!isReady(recModel, dictionary)) {
            val repoName = if (modelName == "PP-OCRv5_mobile_rec_onnx") modelName else modelName + "_onnx"
            val repoUrl = HF_BASE + repoName + "/resolve/main/"
            postProgress(onProgress, "Downloading " + modelName.replace('_', ' ') + " OCR model and dictionary (one time)…")
            downloadFile(
                repoUrl + "inference.onnx?download=true",
                recModel,
                minBytes = 100_000L,
                onProgress = onProgress,
                label = "language recognition model"
            )
            downloadFile(
                repoUrl + "inference.yml?download=true",
                dictionary,
                minBytes = 512L,
                onProgress = onProgress,
                label = "language character dictionary"
            )
        }
        if (!isReady(detModel, null) || !isReady(recModel, dictionary)) {
            throw IOException("The selected OCR model failed its integrity check. Retry with a reliable internet connection.")
        }
        return ModelFiles(detModel, recModel, dictionary, modelName)
    }

    private fun isReady(model: File, dictionary: File?): Boolean =
        model.isFile && model.length() > 100_000L &&
            (dictionary == null || (dictionary.isFile && dictionary.length() > 512L))

    private fun postProgress(callback: (String) -> Unit, message: String) {
        mainHandler.post { callback(message) }
    }

    private fun downloadFile(
        url: String,
        target: File,
        minBytes: Long,
        onProgress: (String) -> Unit,
        label: String,
    ) {
        if (target.isFile && target.length() >= minBytes) return
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, target.name + ".part")
        temporary.delete()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "EduLock Android OCR/1.0")
            .header("Accept", "application/octet-stream,text/plain;q=0.9,*/*;q=0.8")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Could not download the " + label + " (HTTP " + response.code + "). Check internet and retry.")
                }
                val body = response.body ?: throw IOException("Model server returned an empty " + label + ".")
                val length = body.contentLength()
                if (length > MAX_MODEL_BYTES) throw IOException("Model response is unexpectedly large.")
                body.byteStream().use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var lastReported = -1
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_MODEL_BYTES) throw IOException("Model download exceeds the safe size limit.")
                            output.write(buffer, 0, count)
                            if (length > 0L) {
                                val percent = ((total * 100L) / length).toInt().coerceIn(0, 100)
                                if (percent / 10 != lastReported / 10) {
                                    lastReported = percent
                                    postProgress(onProgress, "Downloading " + label + "… " + percent + "%")
                                }
                            }
                        }
                        output.flush()
                        if (total < minBytes) throw IOException("Downloaded " + label + " is too small to be a valid model file.")
                    }
                }
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } catch (e: SocketTimeoutException) {
            temporary.delete()
            throw IOException("Downloading the " + label + " timed out. Reconnect to the internet and retry.", e)
        } catch (e: Exception) {
            temporary.delete()
            throw e
        }
    }
}
