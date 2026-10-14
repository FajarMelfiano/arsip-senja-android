package com.arsipsenja.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.arsipsenja.util.TipeFile

/**
 * Koleksi arsip: satu channel Telegram privat (topicId = 0, kompatibel dengan
 * data lama) atau satu topik forum di dalam grup vault (topicId != 0).
 *
 * Primary key komposit (chatId, topicId) karena banyak topik berbagi
 * satu chatId vault yang sama.
 *
 * Metadata kategori kustom:
 * - [label]: teks pendek (maks 24 karakter) untuk chip, null bila kosong.
 * - [warna]: ARGB untuk dot/chip kategori, 0 = warna bawaan tema.
 * - [tipeFile]: CSV nama enum [TipeFile] yang dirutekan ke kategori ini.
 *   String kosong = menangani semua tipe (kompatibel data lama).
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
    @ColumnInfo(defaultValue = "NULL") val label: String? = null,
    @ColumnInfo(defaultValue = "0") val warna: Int = 0,
    @ColumnInfo(defaultValue = "''") val tipeFile: String = "",
    val jumlahBerkas: Int,
    val dibuatPada: Long,
    val terakhirSinkron: Long,
) {
    /** Identitas stabil untuk ViewModel, Worker, dan kueri. */
    val kunci: KunciKoleksi get() = KunciKoleksi(chatId, topicId)

    /** True bila koleksi ini adalah topik forum (bukan channel lama). */
    val adalahTopik: Boolean get() = topicId != 0

    /**
     * Himpunan tipe berkas yang dirutekan ke kategori ini.
     * Nilai kosong/invalid diabaikan; bila tidak ada satupun yang valid,
     * seluruh tipe ditangani (semantik "semua tipe").
     */
    val tipeDitangani: Set<TipeFile>
        get() =
            tipeFile
                .split(",")
                .mapNotNull { mentah ->
                    mentah.trim().takeIf { it.isNotEmpty() }?.let {
                        runCatching { TipeFile.valueOf(it) }.getOrNull()
                    }
                }.toSet()
                .ifEmpty { TipeFile.entries.toSet() }
}
