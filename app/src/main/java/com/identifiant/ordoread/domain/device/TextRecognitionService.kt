package com.identifiant.ordoread.domain.device

import android.graphics.Bitmap

interface TextRecognitionService {
    suspend fun extractTextFromBitmap(bitmap: Bitmap): String
}