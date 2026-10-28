package com.nobre.cameranobre.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PropertyStorageHelper(private val context: Context) {

    private val prefs = context.getSharedPreferences("camera_nobre_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_ACTIVE_PROPERTY = "active_property"
        const val KEY_RECENT_PROPERTIES = "recent_properties"
        const val BASE_ALBUM_NAME = "IMÓVEIS 2026"
        const val DEFAULT_PROPERTY = "Câmera Livre"
    }

    var activeProperty: String
        get() = prefs.getString(KEY_ACTIVE_PROPERTY, DEFAULT_PROPERTY) ?: DEFAULT_PROPERTY
        set(value) {
            prefs.edit().putString(KEY_ACTIVE_PROPERTY, value).apply()
            saveToRecents(value)
        }

    fun getRecentProperties(): List<String> {
        val raw = prefs.getString(KEY_RECENT_PROPERTIES, "") ?: ""
        if (raw.isBlank()) return listOf("Alphaville I B29 - Clóvis", "Adrianópolis Casa - Rui Nicolau", "Ponta Negra Cobertura")
        return raw.split("|").filter { it.isNotBlank() }
    }

    private fun saveToRecents(property: String) {
        if (property == DEFAULT_PROPERTY || property.isBlank()) return
        val current = getRecentProperties().toMutableList()
        current.remove(property)
        current.add(0, property)
        if (current.size > 15) current.removeAt(current.lastIndex)
        prefs.edit().putString(KEY_RECENT_PROPERTIES, current.joinToString("|")).apply()
    }

    fun getRelativePath(): String {
        val folder = if (activeProperty == DEFAULT_PROPERTY) {
            "DCIM/$BASE_ALBUM_NAME/Geral"
        } else {
            "DCIM/$BASE_ALBUM_NAME/$activeProperty"
        }
        return "$folder/"
    }

    fun createPhotoContentValues(extension: String = "jpg"): ContentValues {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "NOBRE_${timeStamp}.$extension"
        val relativePath = getRelativePath()

        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
    }

    fun createVideoContentValues(): ContentValues {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "NOBRE_VID_${timeStamp}.mp4"
        val relativePath = getRelativePath()

        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
    }

    fun createTempFile(prefix: String, suffix: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        return File(context.cacheDir, "${prefix}_${timeStamp}$suffix")
    }
}
