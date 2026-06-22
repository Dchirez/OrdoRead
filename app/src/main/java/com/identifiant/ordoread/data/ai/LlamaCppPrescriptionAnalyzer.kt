package com.identifiant.ordoread.data.ai

import android.content.Context
import android.util.Log
import com.identifiant.ordoread.domain.ai.Medication
import com.identifiant.ordoread.domain.ai.PrescriptionAnalyzer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Approche hybride : le parser regex extrait d'abord les médicaments,
 * puis Qwen3-0.6B corrige/valide le résultat.
 * Tâche simplifiée pour le LLM = réponse rapide et fiable.
 */
class LlamaCppPrescriptionAnalyzer(context: Context) : PrescriptionAnalyzer {

    companion object {
        private const val TAG = "LlamaCppAnalyzer"
    }

    private val llamaCpp = LlamaCpp(context)
    private val appContext = context

    suspend fun ensureReady(): Boolean = llamaCpp.ensureModelLoaded()

    fun isAvailable(): Boolean = LlamaCpp.isModelAvailable(appContext)

    /**
     * Corrige le résultat du parser regex en utilisant le LLM.
     * @param ocrText texte OCR brut (pour contexte)
     * @param regexResult résultat du parser regex à corriger
     */
    suspend fun correctRegexResult(ocrText: String, regexResult: List<Medication>): List<Medication> {
        if (!llamaCpp.ensureModelLoaded()) {
            throw Exception("Modèle LLM non disponible")
        }

        val prompt = buildCorrectionPrompt(ocrText, regexResult)
        Log.d(TAG, "Correction LLM en cours (prompt: ${prompt.length} chars)...")

        // Le premier med est pré-rempli dans le prompt, on n'a besoin que de la suite
        val maxTokens = (regexResult.size * 60).coerceIn(100, 500)
        val rawResponse = llamaCpp.generateText(prompt, maxTokens = maxTokens)
        Log.d(TAG, "Réponse LLM brute: $rawResponse")

        // Reconstruire la réponse complète (premier nom pré-rempli + réponse du modèle)
        val firstMedName = regexResult.first().description
        val fullResponse = "$firstMedName|$rawResponse"

        // Strip <think>...</think>, </think> résiduel et tokens ChatML parasites
        val response = fullResponse
            .replace(Regex("<think>[\\s\\S]*?</think>"), "")
            .replace(Regex("<think>[\\s\\S]*"), "") // think non fermé = tout est du raisonnement
            .replace("</think>", "")
            .replace(Regex("<\\|im_start\\|>"), "")
            .replace(Regex("<\\|im_end\\|>"), "")
            .trim()
        Log.d(TAG, "Réponse LLM nettoyée: $response")

        return try {
            // Essayer d'abord le format compact (attendu)
            parseCompactResponse(response, regexResult)
        } catch (e: Exception) {
            Log.w(TAG, "Parse compact échoué (${e.message}), essai JSON...")
            try {
                // Fallback : peut-être que le LLM a répondu en JSON
                parseResponse("[$response")
            } catch (e2: Exception) {
                Log.w(TAG, "Parse JSON aussi échoué (${e2.message}), retour regex tel quel")
                regexResult
            }
        }
    }

    override suspend fun analyzeText(extractedText: String): List<Medication> {
        // Cette méthode n'est plus utilisée directement dans l'approche hybride
        // mais on la garde pour compatibilité interface
        throw UnsupportedOperationException("Utiliser correctRegexResult() via SmartPrescriptionAnalyzer")
    }

    private fun buildCorrectionPrompt(ocrText: String, regexResult: List<Medication>): String {
        // Format compact pour le résultat regex
        val regexCompact = regexResult.joinToString("\n") { med ->
            val h = if (med.hours.isEmpty()) "[]" else med.hours.toString()
            val libre = if (med.isPriseLibre) ",libre" else ""
            val interval = if (med.intervalHours > 0) ",interval=${med.intervalHours}h" else ""
            "${med.description}|h=$h|d=${med.durationDays}$libre$interval"
        }

        // Ne garder que les lignes OCR pertinentes
        val relevantOcr = ocrText.lines()
            .map { it.trim() }
            .filter { it.length in 5..100 }
            .take(12)
            .joinToString("\n")

        // Construire le premier médicament pré-rempli pour forcer le format
        val firstMed = regexResult.first()
        val firstLine = "${firstMed.description}|"

        return """<|im_start|>system
Fix medication data from French prescription OCR. Output ONLY corrected lines. No thinking, no explanation. Same number of lines, same pipe-separated format.<|im_end|>
<|im_start|>user
Paracetamol 1OOOmg comprimé|h=[8]|d=7
OCR: Paracetamol 1000mg 1cp matin midi et soir pendant 5 jours<|im_end|>
<|im_start|>assistant
Paracétamol 1000mg comprimé|h=[8,12,20]|d=5<|im_end|>
<|im_start|>user
Doliprane lOOOmg|h=[8,20]|d=7
OCR: Doliprane 1000mg si douleur pendant 10 jours toutes les 6 heures<|im_end|>
<|im_start|>assistant
Doliprane 1000mg|h=[]|d=10,libre,interval=6h<|im_end|>
<|im_start|>user
$regexCompact
OCR:
$relevantOcr<|im_end|>
<|im_start|>assistant
$firstLine"""
    }

    private fun parseCompactResponse(responseText: String, regexResult: List<Medication>): List<Medication> {
        val lines = responseText.trim().lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains("|") }
            .distinct()

        if (lines.size != regexResult.size) {
            throw Exception("Nombre de lignes différent: ${lines.size} vs ${regexResult.size}")
        }

        return lines.map { line ->
            val parts = line.split("|")
            val name = parts[0].trim()

            // Parse hours
            val hoursStr = parts.find { it.startsWith("h=") }?.removePrefix("h=") ?: "[]"
            val hours = Regex("\\d+").findAll(hoursStr).map { it.value.toInt() }.filter { it in 0..23 }.toList()

            // Parse duration
            val dStr = parts.find { it.startsWith("d=") }?.removePrefix("d=") ?: "7"
            val durationDays = Regex("^\\d+").find(dStr)?.value?.toIntOrNull() ?: 7

            // Parse flags
            val fullLine = parts.joinToString("|")
            val isPriseLibre = fullLine.contains("libre")
            val intervalMatch = Regex("interval=(\\d+)").find(fullLine)
            val intervalHours = intervalMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            Medication(
                description = name,
                hours = hours,
                durationDays = durationDays,
                isPriseLibre = isPriseLibre,
                intervalHours = intervalHours,
                addToCalendar = !isPriseLibre
            )
        }
    }

    private fun parseResponse(responseText: String): List<Medication> {
        val jsonStr = extractJsonArray(responseText)
            ?: throw Exception("Impossible de parser la réponse LLM")

        val jsonArray = JSONArray(jsonStr)
        val medications = mutableListOf<Medication>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val name = obj.getString("name")
            val hoursArray = obj.getJSONArray("hours")
            val hours = (0 until hoursArray.length()).map { hoursArray.getInt(it) }
            val durationDays = obj.optInt("durationDays", 7)
            val isPriseLibre = obj.optBoolean("isPriseLibre", false)
            val intervalHours = obj.optInt("intervalHours", 0)

            medications.add(
                Medication(
                    description = name,
                    hours = hours,
                    durationDays = durationDays,
                    isPriseLibre = isPriseLibre,
                    intervalHours = intervalHours,
                    addToCalendar = !isPriseLibre
                )
            )
        }

        Log.d(TAG, "LLM a corrigé: ${medications.size} médicaments")
        return medications
    }

    private fun extractJsonArray(text: String): String? {
        val start = text.indexOf('[')
        if (start == -1) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    fun release() = llamaCpp.release()
}
