package com.arsipsenja.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Status jeda dan prioritas untuk item antrean unggah.
 * Dipergunakan untuk mendukung fitur jeda manual dan jeda otomatis berdasarkan jaringan.
 */
@Entity(tableName = "upload_queue_pause")
data class UploadQueuePauseEntity(
    @PrimaryKey val workId: String, // UUID pekerjaan unggah, digunakan sebagai nama pekerjaan WorkManager
    val isPaused: Int = 0, // 0 = tidak jeda, 1 = jeda
    val priority: Int = 0 // 0 = normal, 1 = prioritas tinggi (untuk implementasi sederhana)
)