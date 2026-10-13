package com.arsipsenja.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Berkas terindeks di arsip. Sumber kebenaran ada di Telegram; tabel ini
 * adalah indeks lokal untuk pencarian, statistik, dan pratinjau.
 */
@Entity(
    tableName = "berkas_arsip",
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["koleksiId"]),
        Index(value = ["nama"]),
        Index(value = ["tanggal"]),
    ],
)
data class BerkasArsipEntity(
    /** Kunci stabil: "chatId:messageId". */
    @PrimaryKey val id: String,
    val messageId: Long,
    val chatId: Long,
    /** Channel/koleksi tempat berkas berada; null bila yatim. */
    val koleksiId: Long?,
    val nama: String,
    val ukuran: Long,
    /** Epoch millis. */
    val tanggal: Long,
    val mime: String,
    /** Nilai [com.arsipsenja.util.TipeFile]. */
    val tipe: String,
    @ColumnInfo(defaultValue = "0") val nomorKatalog: Int,
    /** Identitas file di TDLib untuk unduh. */
    val tdFileId: Int,
    /** Jalur berkas lokal bila sudah tersedia di perangkat. */
    val tdLocalPath: String?,
    val deskripsi: String?,
    val dibuatPada: Long,
) {
    companion object {
        fun kunci(chatId: Long, messageId: Long) = "$chatId:$messageId"
    }
}
