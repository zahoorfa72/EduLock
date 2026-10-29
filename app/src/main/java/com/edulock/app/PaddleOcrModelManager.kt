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

    private const val BASE_URL = "https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/"
    private const val DETECTOR_ARCHIVE = "PP-OCRv5_mobile_det_onnx_infer.tar"
    private const val MAX_ARCHIVE_BYTES = 160L * 1024L * 1024L
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
            postProgress(onProgress, "Downloading multilingual text detector (required once)…")
            val archive = File(root, DETECTOR_ARCHIVE)
            downloadArchiveIfNeeded(archive.name, archive, onProgress)
            extractTarModel(archive, detDir, requireDictionary = false)
            archive.delete()
        }
        if (!isReady(recModel, dictionary)) {
            val archiveName = modelName + "_infer.tar"
            postProgress(onProgress, "Downloading " + modelName.replace('_', ' ') + " OCR model (required once)…")
            val archive = File(root, archiveName)
            downloadArchiveIfNeeded(archiveName, archive, onProgress)
            extractTarModel(archive, recDir, requireDictionary = true)
            archive.delete()
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

    private fun downloadArchiveIfNeeded(archiveName: String, target: File, onProgress: (String) -> Unit) {
        if (target.isFile && target.length() > 100_000L) return
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, target.name + ".part")
        temporary.delete()
        val request = Request.Builder()
            .url(BASE_URL + archiveName)
            .header("User-Agent", "EduLock Android OCR/1.0")
            .header("Accept", "application/octet-stream")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("OCR model download failed with HTTP " + response.code + ". Check internet and retry.")
                val body = response.body ?: throw IOException("OCR model server returned an empty response.")
                val length = body.contentLength()
                if (length > MAX_ARCHIVE_BYTES) throw IOException("OCR model archive is unexpectedly large.")
                body.byteStream().use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_ARCHIVE_BYTES) throw IOException("OCR model archive exceeds the safe download limit.")
                            output.write(buffer, 0, count)
                            if (length > 0L && total % (2L * 1024L * 1024L) < buffer.size) {
                                postProgress(onProgress, "Downloading OCR model… " + ((total * 100L) / length).coerceIn(0L, 100L) + "%")
                            }
                        }
                        output.flush()
                        if (total < 100_000L) throw IOException("Downloaded OCR model is too small to be valid.")
                    }
                }
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } catch (e: SocketTimeoutException) {
            temporary.delete()
            throw IOException("OCR model download timed out. Reconnect to the internet and retry.", e)
        } catch (e: Exception) {
            temporary.delete()
            throw e
        }
    }

    /** Extract only ONNX weights and the character dictionary from standard tar entries. */
    private fun extractTarModel(archive: File, outputDir: File, requireDictionary: Boolean) {
        outputDir.mkdirs()
        var foundModel = false
        var foundDictionary = false
        RandomAccessFile(archive, "r").use { input ->
            val header = ByteArray(512)
            while (input.filePointer + 512L <= input.length()) {
                input.readFully(header)
                if (header.all { it == 0.toByte() }) break
                val name = readTarString(header, 0, 100)
                val prefix = readTarString(header, 345, 155)
                val path = if (prefix.isBlank()) name else prefix + "/" + name
                val sizeText = readTarString(header, 124, 12).trim()
                val size = try { sizeText.toLong(8) } catch (_: Exception) {
                    throw IOException("Invalid OCR model archive entry size.")
                }
                if (size < 0L || size > MAX_ARCHIVE_BYTES) throw IOException("Invalid OCR model archive entry.")
                val type = header[156].toInt().toChar()
                val dataStart = input.filePointer
                val base = path.substringAfterLast('/').lowercase(Locale.ROOT)
                val outputName = when {
                    type != '\u0000' && type != '0' -> null
                    base == "inference.onnx" -> "inference.onnx"
                    requireDictionary && (base == "inference.yml" || base == "inference.yaml") -> "inference.yml"
                    else -> null
                }
                if (outputName != null) {
                    val destination = File(outputDir, outputName)
                    val part = File(outputDir, outputName + ".part")
                    input.seek(dataStart)
                    part.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var remaining = size
                        while (remaining > 0L) {
                            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            if (count <= 0) throw IOException("Truncated OCR model archive.")
                            output.write(buffer, 0, count)
                            remaining -= count
                        }
                    }
                    if (part.length() != size) {
                        part.delete()
                        throw IOException("OCR model file size verification failed.")
                    }
                    if (!part.renameTo(destination)) {
                        part.copyTo(destination, overwrite = true)
                        part.delete()
                    }
                    if (outputName == "inference.onnx") foundModel = true
                    if (outputName == "inference.yml") foundDictionary = true
                }
                val nextHeader = dataStart + ((size + 511L) / 512L) * 512L
                if (nextHeader > input.length()) throw IOException("Truncated OCR model archive.")
                input.seek(nextHeader)
            }
        }
        if (!foundModel || (requireDictionary && !foundDictionary)) {
            throw IOException(if (requireDictionary) "OCR archive lacks inference.onnx or inference.yml." else "OCR detector archive lacks inference.onnx.")
        }
    }

    private fun readTarString(header: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && header[end] != 0.toByte()) end++
        return String(header, offset, end - offset, Charsets.US_ASCII)
    }
}
