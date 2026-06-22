package com.identifiant.ordoread.domain.ai

data class Medication(
    val description: String,
    val hours: List<Int>,
    val durationDays: Int = 7,
    val isPriseLibre: Boolean = false,
    val intervalHours: Int = 0,
    val addToCalendar: Boolean = true
)

interface PrescriptionAnalyzer {
    suspend fun analyzeText(extractedText: String): List<Medication>
}