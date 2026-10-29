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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads the shared detector and only the recognition model needed for the selected language.
 * Models are stored in private app storage and reused offline after the first download.
 * Model files are downloaded from PaddlePaddle's official PP-OCRv5 ONNX model repositories.
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
        val base = code.substringBefore('-')
        return when {
            code.contains("-cyrl") || code == "az-cyrl" || code == "uz-cyrl" ->
                "cyrillic_PP-OCRv5_mobile_rec"
            base in setOf("sd", "ur", "ar", "fa", "ps", "ckb", "ug", "ku", "bal", "pnb", "ks") ->
                "arabic_PP-OCRv5_mobile_rec"
            base in setOf("hi", "mr", "ne", "sa", "mai", "bh", "bho", "gom", "new", "sck") ->
                "devanagari_PP-OCRv5_mobile_rec"
            base in setOf("ru", "be", "uk") -> "eslav_PP-OCRv5_mobile_rec"
            base in setOf("bg", "sr", "mn", "kk", "ky", "tg", "mk", "tt", "ba", "av", "ady", "kbd") ->
                "cyrillic_PP-OCRv5_mobile_rec"
            base == "th" -> "th_PP-OCRv5_mobile_rec"
            base == "el" -> "el_PP-OCRv5_mobile_rec"
            base == "ta" -> "ta_PP-OCRv5_mobile_rec"
            base == "te" -> "te_PP-OCRv5_mobile_rec"
            base == "ko" -> "korean_PP-OCRv5_mobile_rec"
            base == "en" -> "en_PP-OCRv5_mobile_rec"
            base in setOf("zh", "ja") -> "PP-OCRv5_mobile_rec_onnx"
            else -> "latin_PP-OCRv5_mobile_rec"
        }
    }

    suspend fun ensureModels(
        context: Context,
        languageCode: String,
        onProgress: (String) -> Unit,
    ): ModelFiles = withContext(Dispatchers.IO) {
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
        ModelFiles(detModel, recModel, dictionary, modelName)
    }

    /**
     * Script families without a dedicated PP-OCRv5 mobile model use local Tesseract models.
     * This provides an actual script-specific fallback instead of incorrectly running a Latin model.
     */
    fun tesseractFallbackLanguage(languageCode: String): String? {
        val code = languageCode.lowercase(Locale.ROOT).replace('_', '-')
        val base = code.substringBefore('-')
        // Codes are mapped only to traineddata files that exist in tessdata_fast.
        // PaddleOCR's dedicated model is preferred for the supported Arabic, Devanagari,
        // Latin, Cyrillic, CJK, Tamil, Telugu, Thai, Greek and Korean model families.
        return when (base) {
            "bn" -> "ben"
            "as" -> "asm"
            "gu" -> "guj"
            "pa" -> "pan"
            "or", "od" -> "ori"
            "kn" -> "kan"
            "ml" -> "mal"
            "si" -> "sin"
            "my" -> "mya"
            "km" -> "khm"
            "lo" -> "lao"
            "am" -> "amh"
            "he" -> "heb"
            "yi" -> "yid"
            "hy" -> "hye"
            "ka" -> "kat"
            "bo" -> "bod"
            "dz" -> "dzo"
            "iu" -> "iku"
            "ti" -> "tir"
            "chr" -> "chr"
            "dv" -> "div"
            "syr" -> "syr"
            else -> null
        }
    }

    /**
     * Last-resort mapping used if a dedicated PaddleOCR model cannot download or initialize.
     * Only codes with traineddata files in the official tessdata_fast repository are returned.
     */
    fun emergencyTesseractLanguage(languageCode: String): String? {
        val code = languageCode.lowercase(Locale.ROOT).replace('_', '-')
        return when {
            code in setOf("zh-tw", "zh-hant", "zh-hk", "zh-mo") -> "chi_tra"
            code.startsWith("zh") -> "chi_sim"
            code.startsWith("sr-latn") -> "srp_latn"
            code.startsWith("az-cyrl") -> "aze_cyrl"
            code.startsWith("uz-cyrl") -> "uzb_cyrl"
            else -> when (code.substringBefore('-')) {
                "af" -> "afr"
                "am" -> "amh"
                "ar" -> "ara"
                "as" -> "asm"
                "az" -> "aze"
                "be" -> "bel"
                "bn" -> "ben"
                "bo" -> "bod"
                "bg" -> "bul"
                "ca" -> "cat"
                "ceb" -> "ceb"
                "chr" -> "chr"
                "cs" -> "ces"
                "cy" -> "cym"
                "da" -> "dan"
                "de" -> "deu"
                "dv" -> "div"
                "dz" -> "dzo"
                "el" -> "ell"
                "en" -> "eng"
                "es" -> "spa"
                "et" -> "est"
                "eu" -> "eus"
                "fa" -> "fas"
                "fi" -> "fin"
                "fil" -> "fil"
                "fr" -> "fra"
                "ga" -> "gle"
                "gu" -> "guj"
                "he" -> "heb"
                "hi" -> "hin"
                "hr" -> "hrv"
                "hu" -> "hun"
                "hy" -> "hye"
                "id" -> "ind"
                "is" -> "isl"
                "it" -> "ita"
                "iu" -> "iku"
                "ja" -> "jpn"
                "ka" -> "kat"
                "kk" -> "kaz"
                "km" -> "khm"
                "kn" -> "kan"
                "ko" -> "kor"
                "ku" -> "kmr"
                "ky" -> "kir"
                "lo" -> "lao"
                "lt" -> "lit"
                "lv" -> "lav"
                "mk" -> "mkd"
                "ml" -> "mal"
                "mn" -> "mon"
                "mr" -> "mar"
                "ms" -> "msa"
                "my" -> "mya"
                "ne" -> "nep"
                "nl" -> "nld"
                "no" -> "nor"
                "or", "od" -> "ori"
                "pa" -> "pan"
                "pl" -> "pol"
                "ps" -> "pus"
                "pt" -> "por"
                "ro" -> "ron"
                "ru" -> "rus"
                "sa" -> "san"
                "sd" -> "snd"
                "si" -> "sin"
                "sk" -> "slk"
                "sl" -> "slv"
                "sq" -> "sqi"
                "sr" -> "srp"
                "sv" -> "swe"
                "sw" -> "swa"
                "syr" -> "syr"
                "ta" -> "tam"
                "te" -> "tel"
                "th" -> "tha"
                "ti" -> "tir"
                "tr" -> "tur"
                "tt" -> "tat"
                "ug" -> "uig"
                "uk" -> "ukr"
                "ur" -> "urd"
                "uz" -> "uzb"
                "vi" -> "vie"
                "yi" -> "yid"
                else -> null
            }
        }
    }

    suspend fun ensureTesseractData(
        context: Context,
        tessLanguage: String,
        onProgress: (String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        require(tessLanguage.matches(Regex("[a-z0-9_]{2,12}"))) { "Invalid OCR language model code." }
        val root = File(context.filesDir, "ocr-models/tesseract").apply { mkdirs() }
        val tessdata = File(root, "tessdata").apply { mkdirs() }
        val trainedData = File(tessdata, tessLanguage + ".traineddata")
        if (!trainedData.isFile || trainedData.length() < 200_000L) {
            postProgress(onProgress, "Downloading offline " + tessLanguage + " OCR fallback model…")
            downloadFile(
                "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/" + tessLanguage + ".traineddata",
                trainedData,
                minBytes = 200_000L,
                onProgress = onProgress,
                label = tessLanguage + " OCR model"
            )
        }
        if (!trainedData.isFile || trainedData.length() < 200_000L) {
            throw IOException("The " + tessLanguage + " OCR model could not be verified.")
        }
        trainedData
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
