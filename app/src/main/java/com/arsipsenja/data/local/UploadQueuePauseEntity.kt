package com.arsipsenja.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Status jeda, prioritas, dan parameter enqueue untuk item antrean unggah.
 * Dipergunakan untuk mendukung fitur jeda manual dan lanjutkan seketika.
 *
 * Kolom uri/koleksiId/topicId menyimpan parameter yang dibutuhkan untuk
 * membangun ulang WorkRequest saat "lanjutkan" ditekan — karena inputData
 * WorkRequest asli tidak bisa dibaca kembali dari WorkInfo.
 */
@Entity(tableName = "upload_queue_pause")
data class UploadQueuePauseEntity(
    @PrimaryKey val workId: String, // UUID pekerjaan unggah, digunakan sebagai nama pekerjaan WorkManager
    val isPaused: Int = 0, // 0 = tidak jeda, 1 = jeda
    val priority: Int = 0, // 0 = normal, 1 = prioritas tinggi (untuk implementasi sederhana)
    val uri: String = "", // URI SAF berkas (string dari uri.toString())
    val koleksiId: Long = 0L, // chatId koleksi tujuan
    val topicId: Int = 0, // id topik forum tujuan
)