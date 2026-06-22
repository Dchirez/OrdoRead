package com.identifiant.ordoread.presentation

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identifiant.ordoread.data.device.ImagePreprocessor
import com.identifiant.ordoread.domain.ai.Medication
import com.identifiant.ordoread.domain.ai.PrescriptionAnalyzer
import com.identifiant.ordoread.domain.device.TextRecognitionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val textRecognitionService: TextRecognitionService,
    private val prescriptionAnalyzer: PrescriptionAnalyzer
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Étape 1 : afficher la prévisualisation pour validation par l'utilisateur */
    fun previewImage(bitmap: Bitmap) {
        _uiState.value = UiState.Preview(bitmap)
    }

    /** Étape 2 : l'utilisateur valide → lancer l'analyse */
    fun confirmAndProcess(bitmap: Bitmap) {
        if (_uiState.value is UiState.Processing) return

        viewModelScope.launch {
            _uiState.value = UiState.Processing
            try {
                // Preprocessing : grayscale + contraste pour améliorer l'OCR
                Log.d("MainViewModel", "Preprocessing image ${bitmap.width}x${bitmap.height}...")
                val preprocessed = ImagePreprocessor.preprocessLight(bitmap)
                Log.d("MainViewModel", "Preprocessing terminé: ${preprocessed.width}x${preprocessed.height}")

                val extractedText = textRecognitionService.extractTextFromBitmap(preprocessed)

                if (extractedText.trim().isEmpty()) {
                    // Fallback : réessayer sur l'image originale
                    Log.d("MainViewModel", "OCR vide après preprocessing, essai sur image brute...")
                    val fallbackText = textRecognitionService.extractTextFromBitmap(bitmap)

                    if (fallbackText.trim().isEmpty()) {
                        _uiState.value = UiState.Error("Aucun texte détecté sur l'image")
                        return@launch
                    }

                    val analysisResult = prescriptionAnalyzer.analyzeText(fallbackText)
                    if (analysisResult.isEmpty()) {
                        _uiState.value = UiState.Error("Aucun médicament détecté")
                    } else {
                        _uiState.value = UiState.Success(analysisResult, bitmap)
                    }
                    return@launch
                }

                val analysisResult = prescriptionAnalyzer.analyzeText(extractedText)
                if (analysisResult.isEmpty()) {
                    _uiState.value = UiState.Error("Aucun médicament détecté")
                } else {
                    _uiState.value = UiState.Success(analysisResult, bitmap)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Erreur système locale")
            }
        }
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }
}

sealed class UiState {
    object Idle : UiState()
    data class Preview(val bitmap: Bitmap) : UiState()
    object Processing : UiState()
    data class Success(val result: List<Medication>, val bitmap: Bitmap) : UiState()
    data class Error(val message: String) : UiState()
}
