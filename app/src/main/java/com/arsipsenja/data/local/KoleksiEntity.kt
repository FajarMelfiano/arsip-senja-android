package com.arsipsenja.data.local

import androidx.room.Entity

/**
 * Koleksi arsip: satu channel Telegram privat (topicId = 0, kompatibel dengan
 * data lama) atau satu topik forum di dalam grup vault (topicId != 0).
 *
 * Primary key komposit (chatId, topicId) karena banyak topik berbagi
 * satu chatId vault yang sama.
 */
@Entity(
    tableName = "koleksi",
    primaryKeys = ["chatId", "topicId"],
)
data class KoleksiEntity(
    val chatId: Long,
    val topicId: Int = 0,
    val nama: String,
    val deskripsi: String?,
    val jumlahBerkas: Int,
    val dibuatPada: Long,
    val terakhirSinkron: Long,
) {
    /** Identitas stabil untuk ViewModel, Worker, dan kueri. */
    val kunci: KunciKoleksi get() = KunciKoleksi(chatId, topicId)

    /** True bila koleksi ini adalah topik forum (bukan channel lama). */
    val adalahTopik: Boolean get() = topicId != 0
}
