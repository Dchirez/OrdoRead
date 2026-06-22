package com.identifiant.ordoread.data.ai

import android.content.Context
import android.util.Log
import com.identifiant.ordoread.data.parser.LocalPrescriptionAnalyzer
import com.identifiant.ordoread.domain.ai.Medication
import com.identifiant.ordoread.domain.ai.PrescriptionAnalyzer

/**
 * Analyseur hybride 100% offline :
 * 1. Parser regex extrait les médicaments (rapide, ~0ms)
 * 2. LLM local (Qwen3-0.6B) corrige/valide le résultat regex
 * 3. Si le LLM échoue ou n'est pas dispo → on garde le résultat regex
 *
 * Aucune donnée ne quitte le téléphone.
 */
class SmartPrescriptionAnalyzer(private val context: Context) : PrescriptionAnalyzer {

    private val llamaCppAnalyzer = LlamaCppPrescriptionAnalyzer(context)
    private val regexAnalyzer = LocalPrescriptionAnalyzer()

    override suspend fun analyzeText(extractedText: String): List<Medication> {
        // Étape 1 : Parser regex (toujours exécuté, très rapide)
        val regexResult = regexAnalyzer.analyzeText(extractedText)
        Log.d("SmartParser", "Regex a extrait: ${regexResult.size} médicaments")

        if (regexResult.isEmpty()) {
            Log.d("SmartParser", "Regex n'a rien trouvé, pas de correction LLM possible")
            return regexResult
        }

        // Étape 2 : Correction LLM (si modèle disponible)
        if (LlamaCpp.isModelAvailable(context)) {
            try {
                val corrected = llamaCppAnalyzer.correctRegexResult(extractedText, regexResult)
                if (corrected.isNotEmpty() && corrected.size == regexResult.size) {
                    // Fusionner intelligemment : le LLM corrige, le regex assure la stabilité
                    val merged = regexResult.zip(corrected).map { (regex, llm) ->
                        // Nom : prendre le LLM si il a corrigé un nom OCR (longueur similaire),
                        // sinon garder le regex (plus fiable pour les noms étranges)
                        val name = if (llm.description.isNotBlank() &&
                            llm.description.length >= regex.description.length * 0.5) {
                            llm.description
                        } else {
                            regex.description
                        }

                        // Hours : LLM prioritaire si il a trouvé quelque chose de raisonnable
                        val hours = when {
                            llm.isPriseLibre -> emptyList()
                            llm.hours.isNotEmpty() && llm.hours.all { it in 0..23 } -> llm.hours
                            regex.hours.isNotEmpty() -> regex.hours
                            else -> listOf(8) // fallback
                        }

                        // Duration : prendre le LLM sauf si absurde (0 ou >90 jours)
                        val duration = when {
                            llm.durationDays in 1..90 -> llm.durationDays
                            regex.durationDays in 1..90 -> regex.durationDays
                            else -> 7
                        }

                        // Prise libre : OR entre les deux (si l'un des deux détecte, c'est vrai)
                        val isPriseLibre = llm.isPriseLibre || regex.isPriseLibre

                        // Interval : prendre le plus grand des deux (le regex et le LLM peuvent chacun le détecter)
                        val interval = maxOf(llm.intervalHours, regex.intervalHours)

                        Medication(
                            description = name,
                            hours = if (isPriseLibre) emptyList() else hours,
                            durationDays = duration,
                            isPriseLibre = isPriseLibre,
                            intervalHours = interval,
                            addToCalendar = !isPriseLibre
                        )
                    }
                    Log.d("SmartParser", "Fusion regex+LLM: ${merged.size} médicaments")
                    merged.forEach { Log.d("SmartParser", "  -> ${it.description} h=${it.hours} d=${it.durationDays} libre=${it.isPriseLibre}") }
                    return merged
                }
                Log.d("SmartParser", "LLM résultat incompatible (${corrected.size} vs ${regexResult.size}), on garde regex")
            } catch (e: Exception) {
                Log.w("SmartParser", "LLM échoué (${e.message}), on garde le regex")
            }
        } else {
            Log.d("SmartParser", "Modèle LLM non disponible, résultat regex seul")
        }

        // Fallback : résultat regex tel quel
        return regexResult
    }

    fun release() {
        llamaCppAnalyzer.release()
    }
}
