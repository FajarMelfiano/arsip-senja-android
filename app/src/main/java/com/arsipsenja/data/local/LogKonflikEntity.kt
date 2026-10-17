package com.arsipsenja.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Catatan konflik sinkron dua arah yang bisa dilihat pengguna (Paket B2).
 *
 * Ditulis setiap kali [AturanKonflik] menyelesaikan sengketa antara operasi
 * lokal tertunda dan pembaruan realtime dari Telegram. Tidak pernah dihapus
 * otomatis dalam 90 hari agar pengguna bisa meninjau keputusan yang diambil
 * aplikasi atas nama mereka.
 */
@Entity(tableName = "log_konflik")
data class LogKonflikEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** ID target yang diperebutkan (berkasId atau "chatId:topicId"). */
    val targetId: String,
    /** Jenis operasi lokal yang terlibat, dari [JenisOperasi]. */
    val jenis: String,
    /** "LOKAL" atau "REMOTE" — lihat [AturanKonflik.Pemenang]. */
    val pemenang: String,
    /** Penjelasan Bahasa Indonesia dari [AturanKonflik.KeputusanKonflik.alasan]. */
    val alasan: String,
    /** Milis epoch saat dicatat. */
    val dicatatPada: Long = 0L,
)
