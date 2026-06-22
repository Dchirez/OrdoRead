package com.identifiant.ordoread.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gère le téléchargement du modèle Qwen3-0.6B GGUF depuis HuggingFace.
 * Le modèle est public (pas de token nécessaire).
 */
class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownload"

        // Qwen3-0.6B quantifié Q4_K_M (~490 Mo)
        private const val MODEL_URL =
            "https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf"
        private const val MODEL_NAME = "qwen3-0.6b-q4_k_m.gguf"
        private const val EXPECTED_SIZE_MB = 490L
    }

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progressPercent: Int, val downloadedMB: Long, val totalMB: Long) : DownloadState()
        object Completed : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    fun isModelDownloaded(): Boolean = LlamaCpp.isModelAvailable(context)

    fun getModelSizeMB(): Long = EXPECTED_SIZE_MB

    suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val modelFile = File(modelsDir, MODEL_NAME)
        val tempFile = File(modelsDir, "$MODEL_NAME.tmp")

        // Si déjà téléchargé, rien à faire
        if (modelFile.exists()) {
            _state.value = DownloadState.Completed
            return@withContext true
        }

        _state.value = DownloadState.Downloading(0, 0, EXPECTED_SIZE_MB)

        try {
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", "OrdoRead/1.0")

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val error = "Erreur HTTP $responseCode"
                Log.e(TAG, error)
                _state.value = DownloadState.Error(error)
                return@withContext false
            }

            val totalBytes = connection.contentLengthLong
            val totalMB = if (totalBytes > 0) totalBytes / (1024 * 1024) else EXPECTED_SIZE_MB

            Log.d(TAG, "Téléchargement: $totalMB Mo")

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var lastProgressUpdate = 0L

                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break

                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        // Mettre à jour la progression toutes les 500 Ko
                        if (downloaded - lastProgressUpdate > 512 * 1024) {
                            lastProgressUpdate = downloaded
                            val downloadedMB = downloaded / (1024 * 1024)
                            val percent = if (totalBytes > 0) {
                                (downloaded * 100 / totalBytes).toInt()
                            } else {
                                (downloadedMB * 100 / EXPECTED_SIZE_MB).toInt().coerceAtMost(99)
                            }
                            _state.value = DownloadState.Downloading(percent, downloadedMB, totalMB)
                        }
                    }
                }
            }

            connection.disconnect()

            // Renommer le fichier temporaire
            if (tempFile.renameTo(modelFile)) {
                Log.d(TAG, "Modèle téléchargé avec succès: ${modelFile.absolutePath}")
                _state.value = DownloadState.Completed
                true
            } else {
                _state.value = DownloadState.Error("Impossible de finaliser le fichier")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur de téléchargement", e)
            tempFile.delete()
            _state.value = DownloadState.Error(e.message ?: "Erreur inconnue")
            false
        }
    }

    fun cancelDownload() {
        // Le téléchargement est dans une coroutine, annuler le scope suffit
        val modelsDir = File(context.filesDir, "models")
        File(modelsDir, "$MODEL_NAME.tmp").delete()
        _state.value = DownloadState.Idle
    }
}
