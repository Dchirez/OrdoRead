package com.identifiant.ordoread.utils

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun saveImageToInternalStorage(context: Context, bitmap: Bitmap): File {
    val directory = File(context.filesDir, "prescriptions")
    if (!directory.exists()) {
        directory.mkdir()
    }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val file = File(directory, "ORDO_$timestamp.jpg")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
    }
    return file
}

fun getSavedPrescriptions(context: Context): List<File> {
    val directory = File(context.filesDir, "prescriptions")
    if (!directory.exists()) return emptyList()
    return directory.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
}