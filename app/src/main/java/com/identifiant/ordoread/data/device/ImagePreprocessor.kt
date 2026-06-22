package com.identifiant.ordoread.data.device

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * Preprocessing d'image pour améliorer la qualité OCR.
 * Transforme une photo brute en "scan" : niveaux de gris, contraste augmenté, netteté.
 */
object ImagePreprocessor {

    /**
     * Applique le pipeline complet de preprocessing.
     * Photo brute → image optimisée pour OCR.
     */
    fun preprocess(bitmap: Bitmap): Bitmap {
        // 1. Redimensionner si trop grand (ML Kit n'a pas besoin de >2000px)
        val resized = resizeIfNeeded(bitmap, maxDimension = 2000)

        // 2. Conversion en niveaux de gris + augmentation du contraste
        val highContrast = applyGrayscaleAndContrast(resized, contrast = 1.8f, brightness = -40f)

        // 3. Binarisation adaptative (texte noir sur fond blanc)
        val binarized = adaptiveBinarize(highContrast)

        return binarized
    }

    /**
     * Version légère : juste grayscale + léger boost de contraste.
     * ML Kit gère bien les images en niveaux de gris — on ne fait que l'aider.
     */
    fun preprocessLight(bitmap: Bitmap): Bitmap {
        val resized = resizeIfNeeded(bitmap, maxDimension = 2000)
        return applyGrayscaleAndContrast(resized, contrast = 1.2f, brightness = -10f)
    }

    private fun resizeIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / maxSide
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Convertit en niveaux de gris et augmente le contraste via ColorMatrix.
     * contrast: 1.0 = normal, >1 = plus de contraste
     * brightness: 0 = normal, <0 = plus sombre (compense le contraste)
     */
    private fun applyGrayscaleAndContrast(bitmap: Bitmap, contrast: Float, brightness: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()

        // Matrice : grayscale + contraste
        val grayscaleMatrix = ColorMatrix().apply {
            setSaturation(0f) // Grayscale
        }

        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))

        // Combiner les deux matrices
        grayscaleMatrix.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(grayscaleMatrix)

        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    /**
     * Binarisation adaptative par blocs.
     * Pour chaque bloc de l'image, calcule un seuil local.
     * Plus robuste que Otsu global quand l'éclairage est inégal.
     */
    private fun adaptiveBinarize(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val output = IntArray(width * height)
        val blockSize = 31 // Taille du bloc pour le seuil local (doit être impair)
        val offset = 10 // Offset par rapport à la moyenne locale (plus élevé = plus de noir)

        // Calcul de l'image intégrale pour moyenne locale rapide
        val integral = LongArray(width * height)
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                val idx = y * width + x
                val gray = pixels[idx] and 0xFF // Déjà en grayscale, prendre un channel
                rowSum += gray
                integral[idx] = rowSum + if (y > 0) integral[(y - 1) * width + x] else 0L
            }
        }

        val halfBlock = blockSize / 2

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val gray = pixels[idx] and 0xFF

                // Calculer la moyenne locale via l'image intégrale
                val x1 = (x - halfBlock).coerceAtLeast(0)
                val y1 = (y - halfBlock).coerceAtLeast(0)
                val x2 = (x + halfBlock).coerceAtMost(width - 1)
                val y2 = (y + halfBlock).coerceAtMost(height - 1)

                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                var sum = integral[y2 * width + x2]
                if (x1 > 0) sum -= integral[y2 * width + (x1 - 1)]
                if (y1 > 0) sum -= integral[(y1 - 1) * width + x2]
                if (x1 > 0 && y1 > 0) sum += integral[(y1 - 1) * width + (x1 - 1)]

                val threshold = (sum / count).toInt() - offset

                // Binarisation : pixel noir si en-dessous du seuil, blanc sinon
                output[idx] = if (gray < threshold) Color.BLACK else Color.WHITE
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(output, 0, width, 0, 0, width, height)
        return result
    }
}
