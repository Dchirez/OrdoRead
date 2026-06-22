package com.identifiant.ordoread.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wrapper JNI pour llama.cpp.
 * Gère le chargement du modèle GGUF et la génération de texte.
 */
class LlamaCpp(private val context: Context) {

    companion object {
        private const val TAG = "LlamaCpp"
        private const val MODEL_NAME = "qwen3-0.6b-q4_k_m.gguf"

        init {
            System.loadLibrary("ordoread-llama")
        }

        fun getModelPath(context: Context): String {
            return File(context.filesDir, "models/$MODEL_NAME").absolutePath
        }

        fun isModelAvailable(context: Context): Boolean {
            return File(getModelPath(context)).exists()
        }
    }

    private external fun loadModel(modelPath: String): Boolean
    private external fun generate(prompt: String, maxTokens: Int): String
    private external fun freeModel()
    private external fun isModelLoaded(): Boolean

    suspend fun ensureModelLoaded(): Boolean = withContext(Dispatchers.IO) {
        if (isModelLoaded()) return@withContext true

        val modelPath = getModelPath(context)
        if (!File(modelPath).exists()) {
            Log.e(TAG, "Modèle non trouvé: $modelPath")
            return@withContext false
        }

        Log.d(TAG, "Chargement du modèle: $modelPath")
        val success = loadModel(modelPath)
        if (success) {
            Log.d(TAG, "Modèle chargé avec succès")
        } else {
            Log.e(TAG, "Échec du chargement du modèle")
        }
        success
    }

    suspend fun generateText(prompt: String, maxTokens: Int = 1024): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded()) {
            throw IllegalStateException("Modèle non chargé. Appeler ensureModelLoaded() d'abord.")
        }
        generate(prompt, maxTokens)
    }

    fun release() {
        freeModel()
        Log.d(TAG, "Modèle libéré")
    }
}
